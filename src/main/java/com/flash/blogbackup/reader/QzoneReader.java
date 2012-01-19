package com.flash.blogbackup.reader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.diandian.framework.tuple.TwoTuple;
import com.diandian.transfer.helper.HttpHelper;
import com.diandian.transfer.logic.ExternalBlogItemLogic;
import com.diandian.transfer.model.ExternalBlog;
import com.diandian.transfer.model.ExternalBlogInfo;
import com.diandian.transfer.model.ExternalBlogItem;
import com.diandian.transfer.service.FetcherException;
import com.diandian.transfer.service.InvalidUserException;
import com.diandian.transfer.service.ServiceUnavailableException;

public class QzoneReader extends AbstractBlogReader {

    private Log logger = LogFactory.getLog(getClass());

    public QzoneReader(ExternalBlogItemLogic externalBlogItemLogic) {
        super(externalBlogItemLogic);
    }

    private static final String checkUrl = "http://ptlogin2.qq.com/check";

    private static final String loginUrl = "http://ptlogin2.qq.com/login";

    private String verifyCode = null;

    private boolean login(HttpClient client, String username, String password) {
        GetMethod get = new GetMethod(checkUrl);//?uin=233017404&appid=15000101r=0.7245196805735249");
        get.addRequestHeader("Referer", "http://qzone.qq.com/");
        NameValuePair[] params = new NameValuePair[] {// 
        new NameValuePair("uin", username),//
                new NameValuePair("appid", "15000101"),//
                new NameValuePair("r", "0.7245196805735249") };
        get.setQueryString(params);
        try {
            int response = client.executeMethod(get);
            if (response == HttpStatus.SC_OK) {
                String content = get.getResponseBodyAsString();
                if (logger.isDebugEnabled()) {
                    logger.debug("check response " + content);
                }
                Pattern p = Pattern.compile("ptui_checkVC\\(\'.{1}\',\'(.*)\'\\);");
                Matcher m = p.matcher(content);
                if (m.find()) {
                    verifyCode = m.group(1);
                    if (logger.isDebugEnabled()) {
                        logger.debug(verifyCode);
                    }
                    if (verifyCode.length() == 4) {//普通验证
                        return normalLogin(client, username, password);
                    } else {//验证码验证

                    }
                }
            }

        } catch (Exception e) {
            logger.warn("login connect failed", e);
        } finally {
            get.releaseConnection();
        }
        return false;
    }

    public String getVerifyCode() {
        return verifyCode;
    }

    //    public boolean 

    private boolean normalLogin(HttpClient client, String username, String password) {
        String passwordEncode = pswEncode(password, verifyCode);
        if (logger.isDebugEnabled()) {
            logger.debug("encode password" + passwordEncode);
        }
        GetMethod get = new GetMethod(loginUrl);
        NameValuePair[] params = new NameValuePair[] {// 
                new NameValuePair("action", "2-11-8491"),//
                new NameValuePair("aid", "15000101"),//
                new NameValuePair("dumy", ""),//
                new NameValuePair("fp", "loginerroralert"),//
                new NameValuePair("from_ui", "1"),//
                new NameValuePair("h", "1"),//
                new NameValuePair("p", passwordEncode),//
                new NameValuePair("ptredirect", "1"),//
                new NameValuePair("ptredirect", "1"),//
                new NameValuePair("u", username),//
                new NameValuePair("u1", "http://imgcache.qq.com/qzone/v5/loginsucc.html?para=izone"),//
                new NameValuePair("verifycode", verifyCode),//

        };
        get.setQueryString(params);
        try {
            int response = client.executeMethod(get);
            if (response == HttpStatus.SC_OK) {
                String content = get.getResponseBodyAsString();
                if (logger.isDebugEnabled()) {
                    logger.debug("login result for qzone, username (" + username + ")" + content);
                }
                if (content.contains("loginsucc")) {
                    return true;
                } else {
                    saveHtml("login but can't find blog, username: (" + username + ")\t", content);
                }
            }

            if (logger.isDebugEnabled()) {
                logger.debug(get.getResponseBodyAsString());
            }
        } catch (Exception e) {
            logger.warn("login connect failed", e);
        } finally {
            get.releaseConnection();
        }

        return false;
    }

    private static String pswEncode(String password, String token) {
        byte[] temp = DigestUtils.md5(password);
        temp = DigestUtils.md5(temp);
        String tempString = DigestUtils.md5Hex(temp);
        tempString = tempString.toUpperCase();
        tempString = tempString + token;
        tempString = DigestUtils.md5Hex(tempString);
        return tempString.toUpperCase();
    }

    @Override
    public List<TwoTuple<String, String>> fetchBlogList(String username, String password)
            throws ServiceUnavailableException, InvalidUserException {
        HttpClient client = HttpHelper.makeHttpClient();
        boolean loginSuccess = login(client, username, password);
        logger.info("userblog " + username + "lognin success: " + loginSuccess);
        return null;
    }

    private String listPage = "http://b1.cnc.qzone.qq.com/cgi-bin/blognew/blog_output_titlelist?uin=%s&num=%d&from=%d";

    private String itemPage = "http://b1.cnc.qzone.qq.com/cgi-bin/blognew/blog_output_data?uin=%s&blogid=%s";

    //默认取15条文章列表为一页
    private final int ITEM_PERPAGE = 15;

    @Override
    public int fetchAll(ExternalBlog externalBlog) throws FetcherException {
        String loginReferer = externalBlog.getUrl();
        HttpClient client = HttpHelper.makeHttpClient();

        // 超时长一些
        client.getParams().setSoTimeout(30 * 1000);
        client.getParams().setConnectionManagerTimeout(30 * 1000);

        //QQ号
        final String username = externalBlog.getUrl().substring(
                externalBlog.getUrl().lastIndexOf('/') + 1);

        Set<String> urlList = new HashSet<String>();
        int index = 0;
        Set<String> urlListTemp = getItemUrlPerList(readUrl(client,
                String.format(listPage, username, ITEM_PERPAGE, index), loginReferer, "gb2312"));
        //读列表
        while (!CollectionUtils.isEmpty(urlListTemp) && !urlList.containsAll(urlListTemp)) {
            urlList.addAll(urlListTemp);
            index += ITEM_PERPAGE;
            String content = readUrl(client,
                    String.format(listPage, username, ITEM_PERPAGE, index), loginReferer, "gb2312");
            urlListTemp = getItemUrlPerList(content);
            for (String url : urlListTemp) {
                saveItem(
                        externalBlog,
                        url,
                        readUrl(client, String.format(itemPage, username, url), loginReferer,
                                "gb2312"));
            }
            logger.info("username:" + username + ",read list page " + index);
        }
        for (String url : urlList) {
            logger.debug(url);
        }

        return urlList.size();
    }

    private void saveItem(ExternalBlog externalBlog, String url, String html) {
        String title = parseTitle(html);
        String content = parseContent(html);
        Date date = parseDate(html);
        String tags = parseTag(html);
        saveItem(new ExternalBlogItem(externalBlog.getId(), externalBlog.getBlogId(), "Qzone blog "
                + url, title, content, tags, date));
    }

    private String parseTitle(String html) {
        Pattern p = Pattern.compile("\"title\":\"(.*?)\"", Pattern.DOTALL);
        Matcher m = p.matcher(html);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    private String parseContent(String html) {
        Pattern p = Pattern.compile("<!--\\s+日志内容\\s+开始\\s+-->(.*?)<!--\\s+日志内容\\s+结束\\s+-->",
                Pattern.DOTALL);
        Matcher m = p.matcher(html);
        if (m.find()) {
            return m.group(1);
        } else {
            return "";
        }
    }

    private Date parseDate(String html) {
        Pattern p = Pattern.compile("\"pubtime\":(\\d+),", Pattern.DOTALL);
        Matcher m = p.matcher(html);
        if (m.find()) {
            return new Date(Long.valueOf(m.group(1)) * 1000);
        }
        return new Date();
    }

    private String parseTag(String html) {
        Pattern p = Pattern.compile("<div\\s+class=\"tags\">(.*?)</div>", Pattern.DOTALL);
        Matcher m = p.matcher(html);
        if (m.find()) {
            String tagContent = m.group(1);
            p = Pattern.compile("<a\\s+href=\"javascript:;\"\\s+title=\"(.*?)\"");
            m = p.matcher(tagContent);
            List<String> tags = new ArrayList<String>();
            while (m.find()) {
                tags.add(m.group(1));
            }
            return StringUtils.join(tags, ",");
        }
        return "";
    }

    private Set<String> getItemUrlPerList(String content) {
        Set<String> listCurrent = new HashSet<String>(ITEM_PERPAGE);
        // logger.debug(content);
        Pattern p = Pattern.compile("_Callback\\(\\{\"blogids\":\\[([\\d,\\s]*?)\\]");
        Matcher m = p.matcher(content);
        if (m.find()) {
            listCurrent.addAll(Arrays.asList(m.group(1).split(",\\s")));
        }
        return listCurrent;
    }

    public static void main(String args[]) {
        //!RC6 hyh@.guest E74D79D4640918A929442A729A8E3D80
        System.out.println(System.currentTimeMillis());
        // System.out.println(DigestUtils.md5Hex("098890DDE069E9ABAD63F19A0D9E1F32AAAA"));
    }

    /* (non-Javadoc)
     * @see com.diandian.transfer.reader.ExternalBlogReader#fetchBlogInfo(java.lang.String, java.lang.String)
     */
    @Override
    public ExternalBlogInfo fetchBlogInfo(String username, String password)
            throws ServiceUnavailableException, InvalidUserException {
        // TODO Auto-generated method stub
        return null;
    }

}

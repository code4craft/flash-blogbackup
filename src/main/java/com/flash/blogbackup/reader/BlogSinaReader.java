package com.flash.blogbackup.reader;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.ParseException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
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

/**
 * 
 * @author cairne
 * @Date 2011-7-15
 * 
 */
public class BlogSinaReader extends AbstractBlogReader {

    private Log logger = LogFactory.getLog(getClass());

    public BlogSinaReader(ExternalBlogItemLogic externalBlogItemLogic) {
        super(externalBlogItemLogic);
    }

    @Override
    public List<TwoTuple<String, String>> fetchBlogList(String username, String password)
            throws ServiceUnavailableException, InvalidUserException {
        throw new UnsupportedOperationException();
    }

    private String loginUrl = "http://login.sina.com.cn/sso/login.php?client=ssologin.js(v1.3.12)";

    private String loginReferer = "http://blog.sina.com.cn/";

    private String loginDomain = "login.sina.com.cn";

    /**
     * 计算password加密后值
     * 
     * @return
     */
    private String encodePassword(String password, String servertime, String nonce) {
        password = DigestUtils.shaHex(password);
        password = DigestUtils.shaHex(password);
        password = DigestUtils.shaHex(password + servertime + nonce);
        return password;

    }

    private void setPostPara(PostMethod post, String username, String servertime,//
            String nonce, String password) throws UnsupportedEncodingException {
        post.setParameter("username", username);
        post.setParameter("useticket", "0");
        post.setParameter("entry", "blog");
        post.setParameter("gateway", "1");
        post.setParameter("servertime", servertime);
        post.setParameter("nonce", nonce);
        post.setParameter("pwencode", "wsse");
        post.setParameter("returntype", "IFRAME");
        post.setParameter("callback", "parent.sinaSSOController.loginCallBack");
        post.setParameter("from", "referer:blog.sina.com.cn/");
        post.setParameter("savestate", "30");
        post.setParameter("password", password);
        post.setParameter("service", "sso");
        post.setParameter("setdomain", "1");
    }

    /**
     * nonce是是加密密码用数据
     * 
     * @param content
     * @return
     */
    private String getNonce(String content) {
        String nonce = null;
        Pattern p = Pattern.compile("\"nonce\":\"([^\"]+)\"");
        Matcher m = p.matcher(content);
        if (m.find()) {
            nonce = m.group(1);
        }
        return nonce;
    }

    /**
     * servertime是加密密码用数据
     * 
     * @param content
     * @return
     */
    private String getServerTime(String content) {
        String servertime = null;
        Pattern p = Pattern.compile("\"servertime\":(\\d+),");
        Matcher m = p.matcher(content);
        if (m.find()) {
            servertime = m.group(1);
        }
        return servertime;
    }

    private boolean login(HttpClient client, String username, String password)
            throws ServiceUnavailableException {

        //获取加密码
        String preloginUrl;
        try {
            preloginUrl = "http://login.sina.com.cn/sso/prelogin.php?"
                    + "entry=miniblog&callback=sinaSSOController.preloginCallBack&user="
                    + URLEncoder.encode(username, "UTF-8") + "&client=ssologin.js(v1.3.12)";

            String servertime, nonce; //加密用的两个字符串
            String content = readUrlWithOutWait(client, preloginUrl, loginReferer);
            servertime = getServerTime(content);
            nonce = getNonce(content);
            if (StringUtils.isEmpty(servertime) || StringUtils.isEmpty(nonce)) {
                return false;
            }
            //登录
            PostMethod post = new PostMethod(loginUrl);
            post.getParams().setParameter(HttpMethodParams.SINGLE_COOKIE_HEADER, true);
            post.addRequestHeader("Referer", loginReferer);
            post.addRequestHeader("Content-Type", "application/x-www-form-urlencoded;charset=GBK");
            password = encodePassword(password, servertime, nonce);
            setPostPara(post, username, servertime, nonce, password);
            try {
                int response = client.executeMethod(post);
                if (response == HttpStatus.SC_OK) {
                    content = post.getResponseBodyAsString();
                    Pattern p = Pattern.compile("parent\\.sinaSSOController\\.loginCallBack\\("
                            + "\\{\"retcode\":0,\"uid\":\"\\d+\"\\}");
                    Matcher m = p.matcher(content);
                    if (m.find()) {
                        return true;
                    } else {
                        saveHtml("can't login, username: (" + username + ")\t", content);
                    }
                }
            } catch (IOException e) {
                logger.error("login exception: ", e);// e.printStackTrace();
            } finally {
                post.releaseConnection();
            }
        } catch (UnsupportedEncodingException e1) {
            logger.error("encode username exception", e1);
        }
        return false;
    }

    private boolean hasMoreListPage(String listPageContent, String listUrlPathRegex,
            int listPageIndex) {
        Pattern p = Pattern.compile(listUrlPathRegex + (listPageIndex + 1) + "\\.html");
        Matcher m = p.matcher(listPageContent);
        if (m.find()) {
            return true;
        } else {
            return false;
        }
    }

    private HashSet<String> getItemUrlPerList(String content) {
        HashSet<String> list = new HashSet<String>();
        Pattern p = Pattern.compile("<a[^<]+title=\".*?\" target=\"_blank\" href=\"(.*?)\">");
        Matcher m = p.matcher(content);
        while (m.find()) {
            String url = m.group(1);
            list.add(url);
        }
        p = Pattern.compile("<a title=\".*?\" target=\"_blank\" href=\"(.*?)\">");
        m = p.matcher(content);
        while (m.find()) {
            String url = m.group(1);
            list.add(url);
        }

        return list;
    }

    /**
     * externalBlog为博客首页
     */
    @Override
    public int fetchAll(ExternalBlog externalBlog) throws FetcherException {
        HttpClient client = HttpHelper.makeHttpClient();

        // 超时长一些
        client.getParams().setSoTimeout(30 * 1000);
        client.getParams().setConnectionManagerTimeout(30 * 1000);

        //读首页
        String content = readUrl(client, externalBlog.getUrl(), loginReferer);
        String listUrlPath, listUrlPathRegex;

        if (StringUtils.containsIgnoreCase(content,
                "http://blog.sina.com.cn/main_v5/ria/private.html")) {
            logger.warn(String.format(CANT_ACCESS, "sina", externalBlog.getUrl()));
            throw new FetcherException(FetcherException.ExceptionType.NO_PRIVILEGE_ERROR);
        }

        //获取列表页地址
        Pattern p = Pattern.compile("(http://blog\\.sina\\.com\\.cn"
                + "/s/articlelist_\\d+_\\d+_)\\d+.html");
        Matcher m = p.matcher(content);
        if (!m.find()) {
            return 0;
        } else {
            listUrlPath = m.group(1);
            listUrlPathRegex = listUrlPath.replace(".", "\\.");
        }
        int count = 0;
        int listPageIndex = 1;
        boolean hasMoreListPage = true;
        while (hasMoreListPage) {
            //抓取列表页
            content = readUrl(client, listUrlPath + listPageIndex + ".html", loginReferer, "UTF-8");
            logger.info("read blog list page: " + listPageIndex);

            //获取每页文章url列表
            HashSet<String> itemsUrlPerList = getItemUrlPerList(content);

            //处理url列表
            for (String url : itemsUrlPerList) {
                logger.info("read blog post: " + url);
                if (!isItemExist(url, externalBlog.getBlogId())) {
                    saveItem(externalBlog, url, readUrl(client, url, loginReferer, "UTF-8"));
                    count++;
                }
            }

            //使用正则判断是否还有下一页的链接
            hasMoreListPage = hasMoreListPage(content, listUrlPathRegex, listPageIndex);

            if (hasMoreListPage) listPageIndex++;
        }
        return count;
    }

    private void saveItem(ExternalBlog externalBlog, String url, String html) {
        Date date = parseDate(html);
        String title = parseTitle(html);
        String content = parseContent(html);
        String tags = parseTags(html);
        saveItem(new ExternalBlogItem(externalBlog.getId(), externalBlog.getBlogId(), url, title,
                content, tags, date));
    }

    private Date parseDate(String html) {
        Pattern p = Pattern.compile("<span class=\"time SG_txtc\">\\((\\d{4}-\\d{1,2}-\\d{1,2}\\s+"
                + "\\d{1,2}:\\d{1,2}:\\d{1,2})\\)", Pattern.DOTALL);
        Matcher m = p.matcher(html);
        if (m.find()) {
            String date = m.group(1);

            try {
                return DateUtils.parseDate(date, new String[] { "yyyy-MM-dd HH:mm:ss" });
            } catch (ParseException e) {
                logger.warn("错误的日期：" + date, e);
            }

        }
        return new Date();
    }

    private String parseTitle(String html) {
        Pattern pattern = Pattern
                .compile("class=\"titName SG_txta\">([^<]*?)</h2>", Pattern.DOTALL);
        Matcher m = pattern.matcher(html);
        if (m.find()) {
            return cleanTag(m.group(1));
        } else {
            return "";
        }
    }

    private String parseTags(String html) {
        //从JS中提取
        Pattern pattern = Pattern.compile("var\\s+\\$tag=\'(.*?)\';");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";

    }

    /**
     * 使用正则抓取内容
     * 
     * @param html
     * @return
     */
    private String parseContent(String html) {
        Pattern pattern = Pattern.compile(
                "<div\\s+id=\"sina_keyword_ad_area2\"\\s+class=\"articalContent\\s+\">"
                        + "(.*)</div>[^<]*<!--\\s*正\\s*文\\s*结\\s*束\\s*-->",// 
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher m = pattern.matcher(html);
        if (m.find()) {
            String content = m.group(1), //
            temp = content;
            //下面开始处理页面特殊情况
            Pattern pClean = Pattern.compile("<span[^>]*style=\"display:none;\"=.*?</span>",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher mClean = pClean.matcher(content);
            temp = mClean.replaceAll("");
            //去掉wbr标签，防止截断错误
            pClean = Pattern.compile("<[/]{0,1}wbr>", Pattern.CASE_INSENSITIVE);
            mClean = pClean.matcher(temp);
            temp = mClean.replaceAll("");
            //替换图片地址，将隐藏地址替换成当前地址
            pClean = Pattern.compile("src=[\'\"][^\"\']+[\'\"]\\s+real_src\\s*=",
                    Pattern.CASE_INSENSITIVE);
            mClean = pClean.matcher(temp);
            temp = mClean.replaceAll("src=");
            //去掉div标签
            temp = temp.replaceAll("<[/]{0,1}div[^>]*>", "");
            temp = temp.replaceAll("<[/]{0,1}DIV[^>]*>", "");
            return temp;
        }
        return "";

    }

    /* (non-Javadoc)
     * @see com.diandian.transfer.reader.ExternalBlogReader#fetchBlogInfo(java.lang.String, java.lang.String)
     */
    @Override
    public ExternalBlogInfo fetchBlogInfo(String username, String password)
            throws ServiceUnavailableException, InvalidUserException {
        ExternalBlogInfo externalBlogInfo = null;
        String blogUrl;
        HttpClient client = HttpHelper.makeHttpClient();
        if (!login(client, username, password)) {
            logger.info(String.format(LOGIN_FAILED, "sina", username));
            throw new InvalidUserException();
        }
        Cookie[] cookies = client.getState().getCookies();
        //换新的client进行登录，避免返回提示信息
        client = HttpHelper.makeHttpClient();
        for (Cookie cookie : cookies) {
            if (cookie.getDomain().equals(loginDomain)) {
                Pattern p = Pattern.compile("ALC=.*?uid%3D(\\d+)");
                Matcher m = p.matcher(cookie.toString());
                //ALC=cv%3D1.0%26es%3De7bf2a1f5753f27159e235e430159db9%26bt%3D1311231394%26et%3D1313823394%26uid%3D1487828712
                if (m.find()) {
                    blogUrl = loginReferer + "u/" + m.group(1);
                    String content = readUrlWithOutWait(client, blogUrl, loginReferer, "UTF-8");
                    p = Pattern.compile("<span id=\"blognamespan\".*?>(.*?)</span>");
                    m = p.matcher(content);
                    if (m.find()) {
                        String blogName = m.group(1);
                        externalBlogInfo = new ExternalBlogInfo(blogName, blogUrl, false);
                        logger.info(String.format(FIND_BLOG, "sina", username, blogName, blogUrl));
                    } else {
                        if (StringUtils.containsIgnoreCase(content,
                                "http://blog.sina.com.cn/main_v5/ria/private.html")) {
                            externalBlogInfo = new ExternalBlogInfo("unkown", blogUrl, true);
                        } else {
                            saveHtml(String.format(LOGIN_BUT_NOBLOG, "sina", username), content);
                        }
                    }
                }
            }
        }

        return externalBlogInfo;
    }

}

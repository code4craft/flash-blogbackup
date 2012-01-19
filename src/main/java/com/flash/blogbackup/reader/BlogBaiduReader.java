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

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
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

public class BlogBaiduReader extends AbstractBlogReader {

    private Log logger = LogFactory.getLog(getClass());

    public BlogBaiduReader(ExternalBlogItemLogic externalBlogItemLogic) {
        super(externalBlogItemLogic);
    }

    private final String loginUrl = "https://passport.baidu.com/?login";

    private final String referer = "http://hi.baidu.com/";

    private boolean login(HttpClient client, String username, String password) {
        PostMethod post = new PostMethod(loginUrl);
        post.setRequestHeader("Content-Type", "text/html;charset=gbk");
        post.setRequestHeader("referer", referer);
        post.setParameter("username", username);
        post.setParameter("password", password);
        post.setParameter("mem_pass", "on");
        post.setParameter("tpl", "sp");
        post.setParameter("tpl_reg", "sp");
        post.setParameter("u", referer);
        post.setParameter("Submit", "");
        try {
            int repsonse = client.executeMethod(post);
            if (repsonse == HttpStatus.SC_OK) {
                String html = post.getResponseBodyAsString();
                if (html.contains("url=url.replace(/^\\.\\//gi,\"http://passport.baidu.com/\");")) {
                    return true;
                } else {
                    saveHtml(String.format(CANT_LOGIN, "baidu", username), html);
                }
            }
        } catch (HttpException e) {
            logger.warn("login io error ", e);
        } catch (IOException e) {
            logger.warn("login io error ", e);
        } finally {
            post.releaseConnection();
        }
        return false;
    }

    @Override
    public List<TwoTuple<String, String>> fetchBlogList(String username, String password)
            throws ServiceUnavailableException, InvalidUserException {
        throw new UnsupportedOperationException();
    }

    private String readBaiduBlogUrl(HttpClient client) {
        String url = "http://hi.baidu.com/";
        int retry = 0;
        try {
            while (retry < 3) {
                try {
                    GetMethod get = new GetMethod(url);
                    get.getParams().setParameter(HttpMethodParams.SINGLE_COOKIE_HEADER, true);
                    try {

                        int result = client.executeMethod(get);
                        if (result == HttpStatus.SC_OK) {
                            return get.getResponseBodyAsString();
                        }
                    } finally {
                        get.releaseConnection();
                    }
                } catch (IOException e) {
                    logger.warn("IO异常，重试 " + url, e);
                }
                retry++;
            }
        } catch (IllegalArgumentException e) {
            logger.warn("url异常，放弃 " + url, e);
        }
        return "";
    }

    @Override
    public int fetchAll(ExternalBlog externalBlog) throws FetcherException {
        HttpClient client = HttpHelper.makeHttpClient();

        // 超时长一些
        client.getParams().setSoTimeout(30 * 1000);
        client.getParams().setConnectionManagerTimeout(30 * 1000);

        //读首页
        String content;
        String listUrlPath = externalBlog.getUrl() + "/index/";

        int count = 0;
        int listPageIndex = 0;
        boolean hasMoreListPage = true;
        while (hasMoreListPage) {
            //抓取列表页
            content = readUrl(client, listUrlPath + listPageIndex, referer);
            logger.info("read blog list page: " + listPageIndex);

            //获取每页文章url列表
            HashSet<String> itemsUrlPerList = getItemUrlPerList(content);
            if (CollectionUtils.isEmpty(itemsUrlPerList)) {
                hasMoreListPage = false;
            }
            //处理url列表
            for (String url : itemsUrlPerList) {
                logger.info("read blog post: " + url);
                if (!isItemExist(url, externalBlog.getBlogId())) {
                    saveItem(externalBlog, url, readUrl(client, url, referer));
                    count++;
                }
            }

            if (hasMoreListPage) listPageIndex++;
        }
        return count;
    }

    private HashSet<String> getItemUrlPerList(String content) {
        HashSet<String> set = new HashSet<String>();
        Pattern p = Pattern
                .compile("<a\\s+href=\"/([^/]*?/blog/item/[\\d\\w]*?\\.html)\"\\s+target=\"_blank\">");
        Matcher m = p.matcher(content);
        while (m.find()) {
            set.add(referer + m.group(1));
            if (logger.isDebugEnabled()) {
                logger.debug("find page " + referer + m.group(1));
            }
        }
        return set;

    }

    private void saveItem(ExternalBlog externalBlog, String url, String html) {
        Date date = parseDate(html);
        String title = parseTitle(html);
        String content = parseContent(html);
        String tags = parseTags(html);
        saveItem(new ExternalBlogItem(externalBlog.getId(), externalBlog.getBlogId(), url, title,
                content, tags, date));
    }

    private String parseTitle(String html) {
        Pattern p = Pattern.compile("<div\\s+class=\"tit\">([^<]*?)</div>", Pattern.DOTALL);
        Matcher m = p.matcher(html);
        if (m.find()) {
            String title = m.group(1);
            title = title.replaceAll("\r", "");
            title = title.replaceAll("\n", "");
            title = title.replaceAll("\\s+", "");
            return title;
        }
        return "";
    }

    private Date parseDate(String html) {
        Pattern p = Pattern.compile("<div\\s+class=\"date\">([^<]*?)</div>", Pattern.DOTALL);
        Matcher m = p.matcher(html);
        if (m.find()) {
            String dateStr = m.group(1);
            String pureDate = null;
            try {

                pureDate = dateStr.replaceAll("[/年月]", "-").replaceAll("[^\\d\\-\\:\\s]", "")
                        .replaceAll("\\s+", " ").trim();
                Date date = DateUtils.parseDate(pureDate, new String[] { "yyyy-MM-dd HH:mm" });
                //下午时间，则+12小时
                if (dateStr.contains("P.M.") || dateStr.contains("下午")) {
                    date = DateUtils.addHours(date, 12);
                }

                return date;
            } catch (ParseException e) {
                logger.warn("错误的日期：" + dateStr + "//" + pureDate, e);
            }
        }
        return new Date();
    }

    private String parseContent(String html) {
        String content;
        Pattern p = Pattern.compile(
                "<div\\s+id=\"blog_text\"\\s+class=\"cnt\"\\s*>(.*?)</div></td></tr></table>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(html);
        if (m.find()) {
            content = m.group(1);
            return content;
        }
        return "";
    }

    private String parseTags(String html) {
        Pattern p = Pattern.compile(
                "<a\\s+href=\"/[^/]+?/blog/category/.*?\"\\s+title=\"查看该分类中所有文章\">类别：(.*?)</a>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(html);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    @Override
    public ExternalBlogInfo fetchBlogInfo(String username, String password)
            throws ServiceUnavailableException, InvalidUserException {
        ExternalBlogInfo externalBlogInfo = null;
        HttpClient client = HttpHelper.makeHttpClient();
        if (!login(client, username, password)) {
            logger.info(String.format(LOGIN_FAILED, "baidu", username));
            throw new InvalidUserException();
        }
        logger.info(String.format(LOGIN_SUCCESS, "baidu", username));
        String shortUrl = username;
        try {
            shortUrl = URLEncoder.encode(username, "gbk");
        } catch (UnsupportedEncodingException e) {
            logger.warn("url encode error ", e);
        }

        String urlContent = readBaiduBlogUrl(client);
        Pattern p = Pattern.compile("location\\.href=\"/(.*?)/ihome\";");
        Matcher m = p.matcher(urlContent);
        if (m.find()) {
            shortUrl = m.group(1);
        }
        String blogUrl = "http://hi.baidu.com/" + shortUrl + "/blog/";

        String blogPage = readUrlWithOutWait(client, blogUrl, referer);

        p = Pattern.compile("class=\"titlink\"\\s*title=\"(.*?)\\s*http:.*?\"");
        m = p.matcher(blogPage);
        if (m.find()) {
            externalBlogInfo = new ExternalBlogInfo(m.group(1), blogUrl, false);
            logger.info(String.format(FIND_BLOG, "baidu", username, externalBlogInfo.getTitle(),
                    externalBlogInfo.getUrl()));
        } else {
            saveHtml(String.format(LOGIN_BUT_NOBLOG, "baidu", username), blogPage);
        }
        return externalBlogInfo;
    }
}

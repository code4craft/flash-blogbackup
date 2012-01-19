package com.flash.blogbackup.reader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.ParseException;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.ServiceUnavailableException;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 
 * @author cairne
 * @Date 2011-7-19
 * 
 */
public class Blog163Reader extends AbstractBlogReader {

    private Log logger = LogFactory.getLog(getClass());

    public Blog163Reader(ExternalBlogItemLogic externalBlogItemLogic) {
        super(externalBlogItemLogic);
    }

    @Override
    public ExternalBlogInfo fetchBlogInfo(String username, String password)
            throws ServiceUnavailableException, InvalidUserException {
        HttpClient client = HttpHelper.makeHttpClient();
        if (!login(client, username, password)) {
            logger.info(String.format(LOGIN_FAILED, "163", username));
            throw new InvalidUserException();
        }
        logger.info(String.format(LOGIN_SUCCESS, "163", username));
        String content;
        ExternalBlogInfo externalBlogInfo = null;
        try {
            content = readUrl(
                    client,
                    "http://blog.163.com/loginGate.do?username="
                            + URLEncoder.encode(username, "UTF-8")
                            + "&blogActivation=true&from=login", loginReferer);
            for (BlogInfoFetcher fetcher : blogInfoFetchers) {
                externalBlogInfo = fetcher.fetch(content, username);
                if (externalBlogInfo != null) {
                    break;
                }
            }
            externalBlogInfo = checkResult(externalBlogInfo, username, content);
            return externalBlogInfo;
        } catch (UnsupportedEncodingException e) {
            logger.warn("url encode error!", e);
            throw new InvalidUserException();
        }
    }

    public boolean checkPrivacy(String url) {
        HttpClient client = HttpHelper.makeHttpClient();
        GetMethod get = new GetMethod(url);
        try {
            int reponse = client.executeMethod(get);
            if (reponse == HttpStatus.SC_FORBIDDEN) {
                return true;
            }
        } catch (HttpException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            get.releaseConnection();
        }
        return false;
    }

    @Override
    public List<TwoTuple<String, String>> fetchBlogList(String username, String password)
            throws ServiceUnavailableException, InvalidUserException {
        throw new UnsupportedOperationException();
    }

    private ExternalBlogInfo checkResult(ExternalBlogInfo externalBlogInfo, String username,
            String content) {
        if (externalBlogInfo != null) {
            externalBlogInfo.setPrivacy(checkPrivacy(externalBlogInfo.getUrl()));
            String usernameNoAt = username;
            if (username.contains("@")) {
                usernameNoAt = StringUtils.substring(username, 0, username.indexOf("@"));
            }
            if (!externalBlogInfo.getUrl().contains(usernameNoAt)) {
                saveHtml(String.format(LOGIN_BUT_NOBLOG, "163", username), content);
                externalBlogInfo = null;
            } else {
                logger.info(String.format(FIND_BLOG, "163", username, externalBlogInfo.getTitle(),
                        externalBlogInfo.getUrl()));
            }
        } else {
            saveHtml(String.format(LOGIN_BUT_NOBLOG, "163", username), content);
        }
        return externalBlogInfo;
    }

    private List<BlogInfoFetcher> blogInfoFetchers = com.diandian.framework.utils.CollectionUtils
            .<BlogInfoFetcher> asList(new BlogInfoFetcher() {

                /**
                 * 解析方法1
                 */
                @Override
                public ExternalBlogInfo fetch(String content, String username) {
                    Pattern p = Pattern
                            .compile("baseUrl:\'(http://.*?\\.*\\.blog\\.163\\.com.*?/)\'");
                    Matcher m = p.matcher(content);
                    if (!m.find()) {
                        return null;
                    }
                    String blogUrl = m.group(1);
                    p = Pattern.compile("<title>(.*?)</title>");
                    m = p.matcher(content);
                    if (m.find()) {
                        String blogName = m.group(1);
                        return new ExternalBlogInfo(blogName, blogUrl, false);
                    }
                    return null;
                }
            }, new BlogInfoFetcher() {

                /**
                 * 解析方法2
                 */
                @Override
                public ExternalBlogInfo fetch(String content, String username) {
                    Pattern p = Pattern
                            .compile("\"(http://[^\"]*?\\.*blog\\.163\\.com[^\"]*?/)manage/\"");
                    Matcher m = p.matcher(content);
                    if (!m.find()) {
                        return null;
                    }
                    String blogUrl = m.group(1);
                    p = Pattern.compile("<title>(.*?)</title>");
                    m = p.matcher(content);
                    if (m.find()) {
                        String blogName = m.group(1);
                        return new ExternalBlogInfo(blogName, blogUrl, false);
                    }
                    return null;
                }

            });

    private String loginUrl = "https://reg.163.com/logins.jsp";

    private String loginReferer = "http://blog.163.com";

    private HttpClient login(HttpClient client, String username, String password) {

        PostMethod post = new PostMethod(loginUrl);
        post.getParams().setParameter(HttpMethodParams.SINGLE_COOKIE_HEADER, true);
        post.addRequestHeader("Referer", loginReferer);
        post.setParameter("password", password);
        post.setParameter("username", username);
        post.setParameter("product", "blog");
        post.setParameter("type", "1");
        post.setParameter("savelogin", "1");
        post.setParameter("url", "http://blog.163.com/loginGate.do?blogActivation=true&from=login");
        post.setParameter("setCookieCheck", "on");
        try {
            int response = client.executeMethod(post);
            if (response == HttpStatus.SC_OK) {
                String html = post.getResponseBodyAsString();
                Pattern p = Pattern.compile("正在登录");
                Matcher m = p.matcher(html);
                if (m.find()) {
                    return client;
                } else {}
            }
        } catch (HttpException e) {
            logger.error(e);//e.printStackTrace();
        } catch (IOException e) {
            logger.error(e);//e.printStackTrace();
        } finally {
            post.releaseConnection();
        }

        return null;

    }

    /**
     * 获取文章url列表，网易博客是通过dwr实现的，该方法模拟dwr请求，返回类似json的文本内容
     * 
     * @param username
     * @param userId
     * @param client
     * @return
     */
    private String getListPage(String username, String userId, HttpClient client, int index) {
        PostMethod post = null;
        try {
            post = new PostMethod("http://api.blog.163.com/" + URLEncoder.encode(username, "UTF-8")
                    + "/dwr/call/plaincall/BlogBeanNew.getBlogs.dwr");
            post.setRequestHeader("Referer", "http://api.blog.163.com/crossdomain.html?t=20100205");
            String requestContent = "callCount=1\n" + "scriptSessionId=${scriptSessionId}187\n"
                    + "c0-scriptName=BlogBeanNew\n" + "c0-methodName=getBlogs\n" + "c0-id=0" + "\n"
                    + "c0-param0=number:" + userId + "\n" + "c0-param1=number:" + index + "\n"
                    + "c0-param2=number:10\n" + "batchId=87654" + index; //param2是上限文章数，batchId是随便写的
            post.setRequestEntity(new InputStreamRequestEntity(new ByteArrayInputStream(
                    requestContent.getBytes())));
            post.setRequestHeader("Content-Type", "text/plain; charset=UTF-8");
            post.setRequestHeader("Content-Length", String.valueOf(requestContent.length()));
            try {

                logger.info("post url: " + post.getURI() + " index" + index);
                int response = client.executeMethod(post);

                if (response == HttpStatus.SC_OK) {
                    return post.getResponseBodyAsString();
                }
            } catch (HttpException e) {
                logger.error(e); //e.printStackTrace();
            } catch (IOException e) {
                logger.error(e);// e.printStackTrace();
            } finally {
                post.releaseConnection();
            }
        } catch (UnsupportedEncodingException e1) {
            logger.warn("url encode error", e1);
        }

        return null;
    }

    private Set<String> getItemUrlPerList(String content) {
        if (StringUtils.isBlank(content)) {
            return Collections.emptySet();
        }
        HashSet<String> list = new HashSet<String>();
        Pattern p = Pattern.compile("permalink=\"(blog/static/\\d*)");
        Matcher m = p.matcher(content);
        while (m.find()) {
            list.add(m.group(1));
        }
        return list;
    }

    /**
     * externalBlog为博客首页
     */
    @Override
    public int fetchAll(ExternalBlog externalBlog) throws FetcherException {

        if (checkPrivacy(externalBlog.getUrl())) {
            logger.warn(String.format(CANT_ACCESS, "163", externalBlog.getUrl()));
            throw new FetcherException(FetcherException.ExceptionType.NO_PRIVILEGE_ERROR);
        }
        HttpClient client = HttpHelper.makeHttpClient();

        // 超时长一些
        client.getParams().setSoTimeout(30 * 1000);
        client.getParams().setConnectionManagerTimeout(30 * 1000);

        //获取参数
        String content = readUrl(client, externalBlog.getUrl(), loginReferer);

        String userId, username;
        Pattern p = Pattern.compile("UD\\.host\\s*=\\s*\\{\\s*userId:(\\d+)");
        Matcher m = p.matcher(content);
        if (!m.find()) return 0;
        else {
            userId = m.group(1);
        }
        //前缀博客名，为false则为后缀博客名
        boolean prefix = true;
        p = Pattern.compile("http://(.*?)\\.blog\\.163\\.com");
        m = p.matcher(externalBlog.getUrl());
        if (!m.find()) {
            //后缀域名
            p = Pattern.compile("http://blog\\.163\\.com/(.*?)/");
            m = p.matcher(externalBlog.getUrl());
            prefix = false;
            if (m.find()) {
                username = m.group(1);
            } else {
                return 0;
            }
        } else {
            username = m.group(1);
        }
        int count = 0;
        HashSet<String> list = new HashSet<String>();
        boolean hasMoreListPage = true;
        int listPageIndex = 0;
        int triedTime = 0, MAX_TIME = 10000;
        while (hasMoreListPage) {
            triedTime++;
            if (triedTime > MAX_TIME) {
                break;
            }
            //获取页面列表
            content = getListPage(username, userId, client, listPageIndex * 10);
            if (logger.isDebugEnabled()) {
                logger.debug(content.length());
            }
            Set<String> newList = getItemUrlPerList(content);
            if (CollectionUtils.isEmpty(newList) || !CollectionUtils.isEmpty(list)
                    && list.containsAll(newList)) {
                hasMoreListPage = false;
            } else {
                hasMoreListPage = true;
                list.addAll(newList);
            }
            for (String urlShort : newList) {
                String url = null;
                try {
                    if (prefix) {
                        url = "http://" + URLEncoder.encode(username, "UTF-8") + ".blog.163.com/"
                                + urlShort + "/";
                    } else {
                        url = "http://blog.163.com/" + URLEncoder.encode(username, "UTF-8") + "/"
                                + urlShort + "/";
                    }

                } catch (UnsupportedEncodingException e) {
                    logger.error(e);
                }
                if (!isItemExist(url, externalBlog.getBlogId())) {
                    logger.info("read " + url);
                    saveItem(externalBlog, url, readUrl(client, url, loginReferer));
                    count++;
                }
            }
            if (logger.isDebugEnabled()) {
                logger.debug("proccessing item count: " + count);
            }
            listPageIndex++;
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
        Pattern p = Pattern.compile("<span\\s+class=\"blogsep\">(\\d{4}-\\d{1,2}-\\d{1,2}\\s+"
                + "\\d{1,2}:\\d{1,2}:\\d{1,2})", Pattern.DOTALL);
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
        Pattern pattern = Pattern.compile("blogTitle:\'(.*?)\'", Pattern.DOTALL);
        Matcher m = pattern.matcher(html);
        if (m.find()) {
            return cleanTag(m.group(1));
        } else {
            return "";
        }
    }

    private String parseTags(String html) {
        //可能有多个标签
        Pattern pattern = Pattern.compile("var\\s*wumiiTags\\s*=\\s*\"(.*?)\"");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return "";
        }

    }

    /**
     * 使用正则抓取内容
     * 
     * @param html
     * @return
     */
    private String parseContent(String html) {
        Pattern pattern = Pattern.compile(
                "<div\\s+class=\"bct.*?>(.*?)<div class=\"ptc phide ztag\">", Pattern.DOTALL
                        | Pattern.CASE_INSENSITIVE);
        Matcher m = pattern.matcher(html);
        if (m.find()) {
            String content = m.group(1), temp = content;
            Pattern pSpan = Pattern.compile("<span[^>]*style=\"display:none;\"=.*?</span>",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher mSpan = pSpan.matcher(content);
            while (mSpan.find()) {
                temp = temp.replace(mSpan.group(), "");
            }
            temp = temp.replaceAll("<[/]{0,1}div[^>]*>", "");
            temp = temp.replaceAll("<[/]{0,1}DIV[^>]*>", "");
            return temp;
        }
        return "";

    }
}

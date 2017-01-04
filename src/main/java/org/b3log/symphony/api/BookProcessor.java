/*
 * Symphony - A modern community (forum/SNS/blog) platform written in Java.
 * Copyright (C) 2012-2017,  b3log.org & hacpai.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.b3log.symphony.api;

import org.apache.commons.lang.StringUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.logging.Level;
import org.b3log.latke.logging.Logger;
import org.b3log.latke.model.User;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.servlet.HTTPRequestContext;
import org.b3log.latke.servlet.HTTPRequestMethod;
import org.b3log.latke.servlet.annotation.After;
import org.b3log.latke.servlet.annotation.Before;
import org.b3log.latke.servlet.annotation.RequestProcessing;
import org.b3log.latke.servlet.annotation.RequestProcessor;
import org.b3log.latke.util.Requests;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Book;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.processor.advice.LoginCheck;
import org.b3log.symphony.processor.advice.stopwatch.StopwatchEndAdvice;
import org.b3log.symphony.processor.advice.stopwatch.StopwatchStartAdvice;
import org.b3log.symphony.service.ArticleMgmtService;
import org.b3log.symphony.service.BookQueryService;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Book processor.
 * <p>
 * <ul>
 * <li>Gets articles with the specified tags (/apis/articles?tags=tag1,tag2&p=1&size=10), GET</li>
 * </ul>
 * </p>
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.0.0.1, Jan 4, 2017
 * @since 1.9.0
 */
@RequestProcessor
public class BookProcessor {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(BookProcessor.class);

    /**
     * Book query service.
     */
    @Inject
    private BookQueryService bookQueryService;

    /**
     * Article management service.
     */
    @Inject
    private ArticleMgmtService articleMgmtService;

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    /**
     * Shares a book.
     *
     * @param context  the specified context
     * @param request  the specified request
     * @param response the specified response
     * @throws Exception exception
     */
    @RequestProcessing(value = "/book/share", method = HTTPRequestMethod.POST)
    @Before(adviceClass = {StopwatchStartAdvice.class, LoginCheck.class})
    @After(adviceClass = {StopwatchEndAdvice.class})
    public void shareBook(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response) {
        JSONObject requestJSONObject;
        try {
            requestJSONObject = Requests.parseRequestJSONObject(request, context.getResponse());
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Parses request failed", e);
            context.renderJSON(false);
            context.renderJSONValue(Keys.MSG, langPropsService.get("bookQueryFailedLabel"));

            return;
        }

        String isbn = requestJSONObject.optString(Common.ISBN);
        isbn = StringUtils.trim(isbn);

        if (StringUtils.isBlank(isbn)) {
            context.renderJSON(false);
            context.renderJSONValue(Keys.MSG, langPropsService.get("bookQueryFailedLabel"));

            return;
        }

        final JSONObject user = (JSONObject) request.getAttribute(User.USER);
        final String userId = user.optString(Keys.OBJECT_ID);
        final String userEmail = user.optString(User.USER_EMAIL);

        final JSONObject book = bookQueryService.getBookByISBN(isbn);
        if (null == book) {
            context.renderJSON(false);
            context.renderJSONValue(Keys.MSG, langPropsService.get("bookQueryFailedLabel"));

            return;
        }

        final JSONObject addArticleRequest = new JSONObject();
        addArticleRequest.put(Article.ARTICLE_TITLE, ":books: 《" + book.optString(Book.BOOK_TITLE) + "》纸质实体书免费送啦！");
        addArticleRequest.put(Article.ARTICLE_TAGS, "书单," + book.optString(Book.BOOK_TAGS));

        final StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("## " + book.optString(Book.BOOK_TITLE) + "\n\n");
        contentBuilder.append("![" + book.optString(Book.BOOK_TITLE).replace("[", " ").replace("]", " ")
                + book.optString(Book.BOOK_TITLE) + "](" + book.optString(Book.BOOK_IMG_URL) + ")\n\n");

        JSONArray author = book.optJSONArray(Book.BOOK_AUTHOR);
        final StringBuilder authorBuilder = new StringBuilder();
        for (int i = 0; i < author.length(); i++) {
            authorBuilder.append("* ").append(author.optString(i)).append("\n");
        }

        contentBuilder.append("### 作者\n\n" + authorBuilder.toString() + "\n\n");
        contentBuilder.append(book.optString(Book.BOOK_AUTHOR_INTRO) + "\n\n");

        final JSONArray translator = book.optJSONArray(Book.BOOK_TRANSLATOR);
        if (translator.length() > 0) {
            final StringBuilder translatorBuilder = new StringBuilder();
            for (int i = 0; i < translator.length(); i++) {
                translatorBuilder.append("* ").append(translator.optString(i)).append("\n");
            }
            contentBuilder.append("### 译者\n\n" + translatorBuilder.toString() + "\n\n");
        }

        contentBuilder.append("### 内容简介\n\n" + book.optString(Book.BOOK_SUMMARY) + "\n\n");

        contentBuilder.append("### 目录\n\n" + book.optString(Book.BOOK_CATALOG) + "\n\n");

        contentBuilder.append("### 其他\n\n")
                .append("* 出版社：" + book.optString(Book.BOOK_PUBLISHER) + "\n");
        if (StringUtils.isNotBlank(book.optString(Book.BOOK_SERIES))) {
            contentBuilder.append("* 丛　书：" + book.optString(Book.BOOK_SERIES) + "\n");
        }

        contentBuilder.append("* 副标题：" + book.optString(Book.BOOK_SUB_TITLE) + "\n")
                .append("* 原作名：" + book.optString(Book.BOOK_ORIGINAL_TITLE) + "\n")
                .append("* 出版年：" + book.optString(Book.BOOK_PUBLISH_DATE) + "\n")
                .append("* 总页数：" + book.optString(Book.BOOK_PAGES) + "\n")
                .append("* 定　价：" + book.optString(Book.BOOK_PRICE) + "\n")
                .append("* 装　帧：" + book.optString(Book.BOOK_BINDING) + "\n")
                .append("* ISBN：" + book.optString(Book.BOOK_ISBN13) + "\n\n");

        contentBuilder.append("----\n\n").append("## 关于『书单』\n" +
                "\n" +
                "书单是黑客派社区的一个纸质书共享活动，所有书均来自捐赠，原则上当前的书籍持有者有义务将书寄送给需要的会员。我们鼓励你在书籍上**留下笔迹**，任何信息都行，让其他人可以看到一些有意思的内容也是蛮不错的 :sweat_smile: \n" +
                "\n" +
                "### 共享意味着什么\n" +
                "\n" +
                "一旦你共享了一本书，就会使用你的社区账号自动发一篇书籍共享帖，这意味着你做了一个**承诺**：将书送到需要的人手中。如果有同城的书籍需求者回帖，就面交吧！\n" +
                "\n" +
                "### 如何参与\n" +
                "\n" +
                "1. 使用微信扫描如下二维码，进入黑客派社区小程序\n" +
                "    ![3c04bd33b54a493aa97107a94a1ae706.png](https://img.hacpai.com/file/2017/1/3c04bd33b54a493aa97107a94a1ae706.png) \n" +
                "2. 按照小程序的指引开始即可\n" +
                "\n" +
                "### 一点思考\n" +
                "\n" +
                "类似共享书籍的事情有很多人做过，比如：\n" +
                "\n" +
                "* 摆摆书架\n" +
                "* 青番茄\n" +
                "* 书巢\n" +
                "* 丢书大作战\n" +
                "* 很多社区的书籍交换\n" +
                "\n" +
                "大家的出发点都是想让这个世界变得更好。黑客派的『书单』将作为长期活动持续下去，大家随时都能参与进来，让你我的生活变得更丰富有趣！");

        addArticleRequest.put(Article.ARTICLE_CONTENT, contentBuilder.toString() + "\n\n");
        addArticleRequest.put(Article.ARTICLE_EDITOR_TYPE, 0);
        addArticleRequest.put(Article.ARTICLE_AUTHOR_EMAIL, userEmail);
        addArticleRequest.put(Article.ARTICLE_AUTHOR_ID, userId);
        addArticleRequest.put(Article.ARTICLE_TYPE, Article.ARTICLE_TYPE_C_BOOK);

        String ua = request.getHeader("User-Agent");
        addArticleRequest.put(Article.ARTICLE_UA, ua);

        addArticleRequest.put(Article.ARTICLE_ANONYMOUS_VIEW, Article.ARTICLE_ANONYMOUS_VIEW_C_ALLOW);

        try {
            final String articleId = articleMgmtService.addArticle(addArticleRequest);

            final JSONObject ret = new JSONObject();
            ret.put(Keys.STATUS_CODE, true);
            ret.put(Book.BOOK, book);
            ret.put(Common.URL, Latkes.getServePath() + "/article/" + articleId);

            context.renderJSON(ret);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Shares book failed", e);

            context.renderJSON(false);
            context.renderJSONValue(Keys.MSG, langPropsService.get("bookQueryFailedLabel"));
        }
    }

    /**
     * Gets a book.
     *
     * @param context  the specified context
     * @param request  the specified request
     * @param response the specified response
     * @throws Exception exception
     */
    @RequestProcessing(value = "/book/info", method = HTTPRequestMethod.POST)
    @Before(adviceClass = {StopwatchStartAdvice.class, LoginCheck.class})
    @After(adviceClass = {StopwatchEndAdvice.class})
    public void getBook(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response)
            throws Exception {
        final JSONObject requestJSONObject = Requests.parseRequestJSONObject(request, context.getResponse());
        String isbn = requestJSONObject.optString(Common.ISBN);
        isbn = StringUtils.trim(isbn);

        if (StringUtils.isBlank(isbn)) {
            context.renderJSON(false);
            context.renderJSONValue(Keys.MSG, langPropsService.get("bookQueryFailedLabel"));

            return;
        }

        final JSONObject book = bookQueryService.getBookByISBN(isbn);
        if (null == book) {
            context.renderJSON(false);
            context.renderJSONValue(Keys.MSG, langPropsService.get("bookQueryFailedLabel"));

            return;
        }

        final JSONObject ret = new JSONObject();
        ret.put(Keys.STATUS_CODE, true);
        ret.put(Book.BOOK, book);

        context.renderJSON(ret);
    }
}
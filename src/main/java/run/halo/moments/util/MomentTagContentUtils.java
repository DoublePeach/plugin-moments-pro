package run.halo.moments.util;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.springframework.web.util.UriUtils;
import run.halo.moments.Moment;

/**
 * Utilities for reading and mutating tag marks embedded in moment content.
 */
public final class MomentTagContentUtils {

    private static final String TAG_CLASS = "tag";

    private MomentTagContentUtils() {
    }

    /**
     * Remove all tag links from rendered HTML.
     *
     * @param html rendered HTML content
     * @return HTML without {@code a.tag} elements
     */
    public static String stripAllTagsFromHtml(String html) {
        if (StringUtils.isBlank(html)) {
            return html;
        }
        Document document = Jsoup.parseBodyFragment(html);
        document.select("a." + TAG_CLASS).remove();
        return document.body().html();
    }

    /**
     * Rename a tag in both raw and rendered HTML content.
     */
    public static void renameTagInContent(Moment.MomentContent content, String oldName,
        String newName) {
        if (content == null || StringUtils.isAnyBlank(oldName, newName)) {
            return;
        }
        if (StringUtils.isNotBlank(content.getHtml())) {
            content.setHtml(renameTagInHtml(content.getHtml(), oldName, newName));
        }
        if (StringUtils.isNotBlank(content.getRaw())) {
            content.setRaw(renameTagInHtml(content.getRaw(), oldName, newName));
        }
    }

    /**
     * Remove a tag from both raw and rendered HTML content.
     */
    public static void removeTagFromContent(Moment.MomentContent content, String tagName) {
        if (content == null || StringUtils.isBlank(tagName)) {
            return;
        }
        if (StringUtils.isNotBlank(content.getHtml())) {
            content.setHtml(removeTagFromHtml(content.getHtml(), tagName));
        }
        if (StringUtils.isNotBlank(content.getRaw())) {
            content.setRaw(removeTagFromHtml(content.getRaw(), tagName));
        }
    }

    private static String renameTagInHtml(String html, String oldName, String newName) {
        Document document = Jsoup.parseBodyFragment(html);
        Elements tagLinks = document.select("a." + TAG_CLASS);
        for (Element tagLink : tagLinks) {
            if (!tagNameMatches(tagLink, oldName)) {
                continue;
            }
            tagLink.text(newName);
            tagLink.attr("href", buildTagHref(newName));
        }
        return document.body().html();
    }

    private static String removeTagFromHtml(String html, String tagName) {
        Document document = Jsoup.parseBodyFragment(html);
        Elements tagLinks = document.select("a." + TAG_CLASS);
        for (Element tagLink : tagLinks) {
            if (tagNameMatches(tagLink, tagName)) {
                tagLink.remove();
            }
        }
        return document.body().html();
    }

    private static boolean tagNameMatches(Element tagLink, String tagName) {
        String text = StringUtils.trimToEmpty(tagLink.text());
        if (StringUtils.equals(text, tagName)) {
            return true;
        }
        String href = tagLink.attr("href");
        if (StringUtils.isBlank(href)) {
            return false;
        }
        String queryTag = extractTagFromHref(href);
        return StringUtils.equals(queryTag, tagName);
    }

    private static String extractTagFromHref(String href) {
        int index = href.indexOf("tag=");
        if (index < 0) {
            return null;
        }
        return UriUtils.decode(href.substring(index + 4), StandardCharsets.UTF_8);
    }

    private static String buildTagHref(String tagName) {
        return "/moments?tag=" + UriUtils.encode(Objects.requireNonNull(tagName), StandardCharsets.UTF_8);
    }
}
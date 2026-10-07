package com.ninsky.cronos.kitchen.recipe;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

import java.util.regex.Pattern;

/** K5: allow-list sanitiser of recipe process HTML (§5.3); never trusts the client's markup. */
public final class ProcessHtmlSanitizer {

    public static final int MAX_LENGTH = 100_000;

    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern ENTITY_SPACES = Pattern.compile("&nbsp;|&#160;|[\\s\\u00A0]");

    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowElements("p", "br", "strong", "b", "em", "i", "u", "s", "h1", "h2", "h3", "ol", "ul", "li", "blockquote", "code", "pre",
                    "a", "span")
            .allowUrlProtocols("http", "https", "mailto")
            .allowAttributes("href").onElements("a")
            .allowAttributes("target").matching(Pattern.compile("_blank")).onElements("a")
            .requireRelsOnLinks("noopener", "noreferrer")
            .allowAttributes("class").matching(Pattern.compile("ql-[a-z0-9-]+( ql-[a-z0-9-]+)*")).onElements("span")
            .allowWithoutAttributes("span")
            .toFactory();

    private ProcessHtmlSanitizer() {
    }

    /** Sanitised HTML, or null when nothing but markup/whitespace remains. */
    public static String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String clean = POLICY.sanitize(html).strip();
        String text = ENTITY_SPACES.matcher(TAGS.matcher(clean).replaceAll("")).replaceAll("");
        return text.isEmpty() ? null : clean;
    }
}

/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nu.fgv.spexipedia.server.revision;

import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.html.LinkResolver;
import com.vladsch.flexmark.html.LinkResolverFactory;
import com.vladsch.flexmark.html.renderer.LinkResolverBasicContext;
import com.vladsch.flexmark.html.renderer.LinkStatus;
import com.vladsch.flexmark.html.renderer.ResolvedLink;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown (CommonMark with tables) to HTML for articles and spex page texts. Anyone logged in writes articles, so raw
 * HTML is escaped and only http(s), mailto and relative links are kept. {@code [[Title]]} and {@code [[Title|text]]}
 * link to articles by title. {@code [[spexify:album/<id>|text]]} and the like link to material in another FGV
 * application ({@link AppRef}), shown as a link to those who may open it there and as text to everyone else.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Component
public class MarkdownRenderer {

    private static final Pattern WIKI_LINK = Pattern.compile("\\[\\[([^\\[\\]|\\n]{1,200})(?:\\|([^\\[\\]\\n]{1,200}))?]]");
    private static final Set<String> SCHEMES = Set.of("http", "https", "mailto");
    private static final Pattern HEADING = Pattern.compile("<h([1-6]) id=\"([^\"]+)\">(.*?)</h\\1>", Pattern.DOTALL);

    private static final Pattern APP_REF = Pattern.compile(
            "(?i)(spexify|spexflix|spexgram|arkiv|spexarkivet):([a-z]+)/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})");

    /** Where links to missing articles go: the new article page, with the title. */
    public static final String NEW_ARTICLE = "/articles/new?title=";

    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownRenderer() {
        final MutableDataSet options = new MutableDataSet()
                .set(Parser.EXTENSIONS, List.of(TablesExtension.create(), AutolinkExtension.create()))
                .set(HtmlRenderer.ESCAPE_HTML, true)
                .set(HtmlRenderer.SUPPRESS_HTML, false)
                .set(HtmlRenderer.SOFT_BREAK, "\n")
                // Headings get ids, for the table of contents (spec 4.5)
                .set(HtmlRenderer.GENERATE_HEADER_ID, true)
                .set(HtmlRenderer.RENDER_HEADER_ID, true);

        this.parser = Parser.builder(options).build();
        this.renderer = HtmlRenderer.builder(options).linkResolverFactory(new SafeLinks()).build();
    }

    /**
     * Material in another FGV application that an article links to, e.g. {@code spexify:album/<id>}: the application
     * ({@code spexify}, {@code spexflix}, {@code spexgram}, {@code arkiv}), the kind of material and its id.
     */
    public record AppRef(String app, String kind, java.util.UUID id) {

        /** The reference in the link, or empty when the link is to an article. */
        public static java.util.Optional<AppRef> parse(final String link) {
            final Matcher matcher = APP_REF.matcher(link.strip());

            if (!matcher.matches()) {
                return java.util.Optional.empty();
            }

            final String app = matcher.group(1).toLowerCase(Locale.ROOT);

            return java.util.Optional.of(new AppRef("spexarkivet".equals(app) ? "arkiv" : app, matcher.group(2).toLowerCase(Locale.ROOT),
                    java.util.UUID.fromString(matcher.group(3).toLowerCase(Locale.ROOT))));
        }
    }

    /**
     * The Markdown as HTML; wiki links go where {@code articlePath} says (by title), or to a search when it says null.
     * Links to other applications' material are text only.
     */
    public String render(final @Nullable String markdown, final Function<String, @Nullable String> articlePath) {
        return render(markdown, articlePath, _ -> null);
    }

    /**
     * The Markdown as HTML; wiki links go where {@code articlePath} says (by title), or to a search when it says null,
     * and links to other applications' material where {@code appLink} says, or are text only when it says null.
     */
    public String render(final @Nullable String markdown, final Function<String, @Nullable String> articlePath,
                         final Function<AppRef, @Nullable String> appLink) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }

        final Matcher matcher = WIKI_LINK.matcher(markdown);
        final StringBuilder resolved = new StringBuilder();

        while (matcher.find()) {
            final String title = matcher.group(1).strip();
            final String text = matcher.group(2) == null ? title : matcher.group(2).strip();
            final java.util.Optional<AppRef> app = AppRef.parse(title);

            if (app.isPresent()) {
                final String url = appLink.apply(app.get());
                final String label = matcher.group(2) == null ? app.get().app() + " " + app.get().kind() : text;

                matcher.appendReplacement(resolved, Matcher.quoteReplacement(url == null ? label.replace("[", "\\[")
                        : "[" + label.replace("]", "\\]") + "](" + url + ")"));
                continue;
            }

            final String path = articlePath.apply(title);
            // A link to a missing article opens a new one with the title; the web shows such links in red
            final String target = path != null ? path : NEW_ARTICLE + java.net.URLEncoder.encode(title, java.nio.charset.StandardCharsets.UTF_8);

            matcher.appendReplacement(resolved, Matcher.quoteReplacement("[" + text.replace("]", "\\]") + "](" + target + ")"));
        }
        matcher.appendTail(resolved);

        final Node document = parser.parse(resolved.toString());

        return renderer.render(document);
    }

    /**
     * The text without Markdown, for previews and the search index.
     */
    public String plain(final @Nullable String markdown) {
        if (markdown == null) {
            return "";
        }
        return HtmlUtils.htmlUnescape(render(markdown, _ -> null).replaceAll("<[^>]+>", " ")).replaceAll("\\s+", " ").strip();
    }

    /**
     * The headings of rendered HTML, for a table of contents.
     */
    public static List<Heading> headings(final String html) {
        return HEADING.matcher(html).results()
                .map(result -> new Heading(Integer.parseInt(result.group(1)), result.group(2),
                        HtmlUtils.htmlUnescape(result.group(3).replaceAll("<[^>]+>", "")).strip()))
                .toList();
    }

    public record Heading(int level, String anchor, String text) {
    }

    /**
     * Article titles that the Markdown links to with {@code [[Title]]}.
     */
    public List<String> wikiLinks(final @Nullable String markdown) {
        if (markdown == null) {
            return List.of();
        }
        return WIKI_LINK.matcher(markdown).results().map(result -> result.group(1).strip())
                .filter(title -> AppRef.parse(title).isEmpty()).toList();
    }

    private static boolean safe(final String url) {
        final String trimmed = url.strip();
        final int colon = trimmed.indexOf(':');
        final int slash = trimmed.indexOf('/');

        if (colon < 0 || (slash >= 0 && slash < colon) || trimmed.startsWith("#") || trimmed.startsWith("?")) {
            return true;
        }
        return SCHEMES.contains(trimmed.substring(0, colon).toLowerCase(Locale.ROOT));
    }

    private static final class SafeLinks implements LinkResolverFactory {

        @Override
        public @Nullable Set<Class<?>> getAfterDependents() {
            return null;
        }

        @Override
        public @Nullable Set<Class<?>> getBeforeDependents() {
            return null;
        }

        @Override
        public boolean affectsGlobalScope() {
            return false;
        }

        @Override
        public LinkResolver apply(final LinkResolverBasicContext context) {
            return (node, ctx, link) -> safe(link.getUrl()) ? link
                    : new ResolvedLink(link.getLinkType(), "#", link.getAttributes(), LinkStatus.INVALID);
        }
    }
}

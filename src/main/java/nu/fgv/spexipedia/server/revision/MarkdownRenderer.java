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
 * link to articles by title.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Component
public class MarkdownRenderer {

    private static final Pattern WIKI_LINK = Pattern.compile("\\[\\[([^\\[\\]|\\n]{1,200})(?:\\|([^\\[\\]\\n]{1,200}))?]]");
    private static final Set<String> SCHEMES = Set.of("http", "https", "mailto");

    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownRenderer() {
        final MutableDataSet options = new MutableDataSet()
                .set(Parser.EXTENSIONS, List.of(TablesExtension.create(), AutolinkExtension.create()))
                .set(HtmlRenderer.ESCAPE_HTML, true)
                .set(HtmlRenderer.SUPPRESS_HTML, false)
                .set(HtmlRenderer.SOFT_BREAK, "\n");

        this.parser = Parser.builder(options).build();
        this.renderer = HtmlRenderer.builder(options).linkResolverFactory(new SafeLinks()).build();
    }

    /**
     * The Markdown as HTML; wiki links go where {@code articlePath} says (by title), or to a search when it says null.
     */
    public String render(final @Nullable String markdown, final Function<String, @Nullable String> articlePath) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }

        final Matcher matcher = WIKI_LINK.matcher(markdown);
        final StringBuilder resolved = new StringBuilder();

        while (matcher.find()) {
            final String title = matcher.group(1).strip();
            final String text = matcher.group(2) == null ? title : matcher.group(2).strip();
            final String path = articlePath.apply(title);
            final String target = path != null ? path : "/search?q=" + java.net.URLEncoder.encode(title, java.nio.charset.StandardCharsets.UTF_8);

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
     * Article titles that the Markdown links to with {@code [[Title]]}.
     */
    public List<String> wikiLinks(final @Nullable String markdown) {
        if (markdown == null) {
            return List.of();
        }
        return WIKI_LINK.matcher(markdown).results().map(result -> result.group(1).strip()).toList();
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

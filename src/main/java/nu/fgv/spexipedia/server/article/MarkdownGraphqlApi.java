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

package nu.fgv.spexipedia.server.article;

import nu.fgv.spexipedia.server.basedata.BaseDataDtos.SpexCategoryDto;
import nu.fgv.spexipedia.server.content.ContentDtos.CharacterDto;
import nu.fgv.spexipedia.server.page.SpexPageDto;
import org.jspecify.annotations.Nullable;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/**
 * The Markdown texts of spex pages, characters and categories as HTML, rendered as articles are:
 * HTML escaped, links made safe and {@code [[Title]]} resolved for the viewer. The web shows these, never the Markdown.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Controller
public class MarkdownGraphqlApi {

    private final ArticleService articles;

    public MarkdownGraphqlApi(final ArticleService articles) {
        this.articles = articles;
    }

    private @Nullable String html(final @Nullable String markdown) {
        return markdown == null || markdown.isBlank() ? null : articles.html(markdown);
    }

    @SchemaMapping(typeName = "SpexPage")
    public @Nullable String descriptionHtml(final SpexPageDto page) {
        return html(page.description());
    }

    @SchemaMapping(typeName = "SpexPage")
    public @Nullable String synopsisHtml(final SpexPageDto page) {
        return html(page.synopsis());
    }

    @SchemaMapping(typeName = "SpexPage")
    public @Nullable String vestDescriptionHtml(final SpexPageDto page) {
        return html(page.vestDescription());
    }

    @SchemaMapping(typeName = "Character")
    public @Nullable String descriptionHtml(final CharacterDto character) {
        return html(character.description());
    }

    @SchemaMapping(typeName = "SpexCategory")
    public @Nullable String descriptionHtml(final SpexCategoryDto category) {
        return html(category.description());
    }
}

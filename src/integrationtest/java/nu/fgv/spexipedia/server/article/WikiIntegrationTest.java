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

import nu.fgv.spexipedia.server.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Articles as a wiki (spec 4.5): a tree, nested categories, links, renames with redirects.
 */
class WikiIntegrationTest extends AbstractIntegrationTest {

    private String article(final String title, final String body, final String parentId) {
        return graphQl(MEMBER).document("""
                        mutation($t: String!, $b: String!, $p: ID) { articleCreate(input: {title: $t, body: $b, visibility: PUBLIC, parentId: $p}) { id } }""")
                .variable("t", title).variable("b", body).variable("p", parentId).execute()
                .path("articleCreate.id").entity(String.class).get();
    }

    @Test
    void articlesFormATreeThatNeverLoops() {
        final String traditions = article("Traditioner", "Om traditionerna.", null);
        final String cup = article("Adrian-cupen", "En cup.", traditions);
        final String winners = article("Vinnare", "De som vann.", cup);

        graphQl(MEMBER).document("query($id: ID!) { article(id: $id) { ancestors { title } parent { title } } }")
                .variable("id", winners).execute()
                .path("article.ancestors[*].title").entityList(String.class).containsExactly("Traditioner", "Adrian-cupen")
                .path("article.parent.title").entity(String.class).isEqualTo("Adrian-cupen");
        expectError(graphQl(AUTHOR).document("mutation($id: ID!, $p: ID) { articleMove(id: $id, parentId: $p) { id } }")
                .variable("id", traditions).variable("p", winners).execute(), "BAD_REQUEST");
        // An article with children is trashed only with them
        expectError(graphQl(AUTHOR).document("mutation($id: ID!) { articleTrash(id: $id) }").variable("id", cup).execute(), "BAD_REQUEST");
        graphQl(AUTHOR).document("mutation($id: ID!) { articleTrash(id: $id, withChildren: true) }").variable("id", cup).execute()
                .path("articleTrash").entity(Boolean.class).isEqualTo(true);
        graphQl(MEMBER).document("query($id: ID!) { article(id: $id) { children { title } } }").variable("id", traditions).execute()
                .path("article.children").entityList(Object.class).hasSize(0);
    }

    @Test
    void linksGiveWhatLinksHereWantedArticlesAndRedirectsOnRename() {
        final String cup = article("Adrian-cupen", "En cup.", null);

        article("Traditioner", "Se [[Adrian-cupen]] och [[Spexikon]].", null);
        graphQl(MEMBER).document("query($id: ID!) { article(id: $id) { backlinks { title } } }").variable("id", cup).execute()
                .path("article.backlinks[*].title").entityList(String.class).containsExactly("Traditioner");
        graphQl(MEMBER).document("{ wantedArticles { title links } }").execute()
                .path("wantedArticles[*].title").entityList(String.class).containsExactly("Spexikon");

        graphQl(MEMBER).document("mutation($id: ID!) { articleUpdate(id: $id, input: {title: \"Adrianpokalen\", body: \"En cup.\", visibility: PUBLIC}) { slug } }")
                .variable("id", cup).execute().path("articleUpdate.slug").hasValue();
        graphQl(null).document("{ redirect(title: \"Adrian-cupen\") }").execute()
                .path("redirect").entity(String.class).isEqualTo("/articles/adrian-cupen");
    }

    @Test
    void categoriesNestAndListTheArticlesBelowThem() {
        final String props = graphQl(MEMBER).document("mutation { articleCategoryCreate(input: {name: \"Rekvisita\"}) { id } }").execute()
                .path("articleCategoryCreate.id").entity(String.class).get();
        final String crowns = graphQl(MEMBER).document("mutation($p: [ID!]) { articleCategoryCreate(input: {name: \"Kronor\", parentIds: $p}) { id parents { name } } }")
                .variable("p", List.of(props)).execute()
                .path("articleCategoryCreate.parents[*].name").entityList(String.class).containsExactly("Rekvisita")
                .path("articleCategoryCreate.id").entity(String.class).get();

        graphQl(MEMBER).document("mutation($c: [ID!]) { articleCreate(input: {title: \"Guldkronan\", body: \"Blank.\", visibility: PUBLIC, categoryIds: $c}) { id } }")
                .variable("c", List.of(crowns)).execute().path("articleCreate.id").hasValue();
        graphQl(MEMBER).document("query($c: ID) { articles(categoryId: $c, includeSubcategories: true) { title } }").variable("c", props).execute()
                .path("articles[*].title").entityList(String.class).containsExactly("Guldkronan");
        graphQl(MEMBER).document("query($c: ID!) { articleCategory(id: $c) { subcategories { name } } }").variable("c", props).execute()
                .path("articleCategory.subcategories[*].name").entityList(String.class).containsExactly("Kronor");
        // A category never becomes its own ancestor
        expectError(graphQl(AUTHOR).document("mutation($id: ID!, $p: [ID!]) { articleCategoryUpdate(id: $id, input: {name: \"Rekvisita\", parentIds: $p}) { id } }")
                .variable("id", props).variable("p", List.of(crowns)).execute(), "BAD_REQUEST");
    }
}

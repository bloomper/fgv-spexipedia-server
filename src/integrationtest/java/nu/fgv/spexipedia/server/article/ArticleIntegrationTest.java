package nu.fgv.spexipedia.server.article;

import nu.fgv.spexipedia.server.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Articles (4.5 of the spec): every logged-in user writes, authors publish, members' articles stay with members.
 */
class ArticleIntegrationTest extends AbstractIntegrationTest {

    private String create(final String user, final String title, final String visibility) {
        return graphQl(user).document("mutation($t: String!, $v: Visibility!) { articleCreate(input: {title: $t, body: \"Text <script>x</script>\", visibility: $v}) { id published } }")
                .variable("t", title).variable("v", visibility).execute()
                .path("articleCreate.published").entity(Boolean.class).isEqualTo(false)
                .path("articleCreate.id").entity(String.class).get();
    }

    @Test
    void membersWriteAuthorsPublishAndVisitorsReadPublishedPublicOnes() {
        final String article = create(MEMBER, "Ölrepet", "PUBLIC");
        final String members = create(MEMBER, "Anekdot", "MEMBERS");

        graphQl(null).document("query($id: ID!) { article(id: $id) { title } }").variable("id", article).execute().path("article").valueIsNull();
        expectError(graphQl(MEMBER).document("mutation($id: ID!) { articleSetPublished(id: $id, published: true) { published } }")
                .variable("id", article).execute(), "FORBIDDEN");
        graphQl(AUTHOR).document("mutation($id: ID!) { articleSetPublished(id: $id, published: true) { published } }")
                .variable("id", article).execute().path("articleSetPublished.published").entity(Boolean.class).isEqualTo(true);
        graphQl(AUTHOR).document("mutation($id: ID!) { articleSetPublished(id: $id, published: true) { published } }")
                .variable("id", members).execute().path("articleSetPublished.published").entity(Boolean.class).isEqualTo(true);

        graphQl(null).document("query($id: ID!) { article(id: $id) { title html } }").variable("id", article).execute()
                .path("article.title").entity(String.class).isEqualTo("Ölrepet")
                .path("article.html").entity(String.class).satisfies(html -> assertThat(html).doesNotContain("<script>"));
        graphQl(null).document("query($id: ID!) { article(id: $id) { title } }").variable("id", members).execute().path("article").valueIsNull();
        graphQl(MEMBER).document("query($id: ID!) { article(id: $id) { title } }").variable("id", members).execute()
                .path("article.title").entity(String.class).isEqualTo("Anekdot");
    }

    @Test
    void savingOverSomeoneElsesNewerRevisionIsRefused() {
        final String article = create(MEMBER, "Spexikon", "PUBLIC");
        final String base = graphQl(MEMBER).document("query($id: ID!) { article(id: $id) { revision { id } } }").variable("id", article).execute()
                .path("article.revision.id").entity(String.class).get();
        final String update = "mutation($id: ID!, $b: ID, $body: String!) { articleUpdate(id: $id, baseRevisionId: $b, input: {title: \"Spexikon\", body: $body, visibility: PUBLIC}) { body } }";

        graphQl(EDITOR).document(update).variable("id", article).variable("b", base).variable("body", "Ny").execute()
                .path("articleUpdate.body").entity(String.class).isEqualTo("Ny");
        expectError(graphQl(MEMBER).document(update).variable("id", article).variable("b", base).variable("body", "Min").execute(), "CONFLICT");
    }

    @Test
    void aHiddenRevisionsTextIsShownOnlyToAuthors() {
        final String article = create(MEMBER, "Kulissen", "PUBLIC");
        final String revision = graphQl(MEMBER).document("query($id: ID!) { article(id: $id) { revision { id } } }").variable("id", article).execute()
                .path("article.revision.id").entity(String.class).get();

        expectError(graphQl(MEMBER).document("mutation($id: ID!) { revisionSetHidden(id: $id, hidden: true) { hidden } }")
                .variable("id", revision).execute(), "FORBIDDEN");
        graphQl(AUTHOR).document("mutation($id: ID!) { revisionSetHidden(id: $id, hidden: true) { hidden } }")
                .variable("id", revision).execute().path("revisionSetHidden.hidden").entity(Boolean.class).isEqualTo(true);
        graphQl(MEMBER).document("query($id: ID!) { article(id: $id) { history { hidden body authorName } } }").variable("id", article).execute()
                .path("article.history[0].hidden").entity(Boolean.class).isEqualTo(true)
                .path("article.history[0].body").valueIsNull()
                .path("article.history[0].authorName").valueIsNull();
        graphQl(AUTHOR).document("query($id: ID!) { article(id: $id) { history { body authorName } } }").variable("id", article).execute()
                .path("article.history[0].body").entity(String.class).isEqualTo("Text <script>x</script>")
                .path("article.history[0].authorName").entity(String.class).isEqualTo("Lisa Test");
    }
}

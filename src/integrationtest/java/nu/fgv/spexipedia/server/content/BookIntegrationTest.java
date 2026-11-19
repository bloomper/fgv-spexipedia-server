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

package nu.fgv.spexipedia.server.content;

import nu.fgv.spexipedia.server.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Books: printed books of the spex' songs, not recordings.
 */
class BookIntegrationTest extends AbstractIntegrationTest {

    private static final String CREATE = "mutation($t: String!, $v: Int) { bookCreate(input: {title: $t, volume: $v, publishedYear: \"1990\"}) { id slug } }";
    private static final String SONGS = """
            mutation($id: ID!, $songs: [BookSongInput!]!) {
              bookSetSongs(id: $id, songs: $songs) { songs { position title textOnly number { id } } }
            }""";

    private String number(final String spex, final String title) {
        return graphQl(EDITOR).document("mutation($s: ID!, $t: String!) { numberCreate(spexId: $s, input: {act: 1, title: $t}) { id } }")
                .variable("s", spex).variable("t", title).execute().path("numberCreate.id").entity(String.class).get();
    }

    @Test
    void editorsKeepBooksOfNumbersThatVisitorsSeeOncePublished() {
        final String spex = createSpex("1987", "Fridolf", null);
        final String overture = number(spex, "Ouvertyr");
        final String finale = number(spex, "Final");

        expectError(graphQl(AUTHOR).document(CREATE).variable("t", "Chalmersspex I").variable("v", 1).execute(), "FORBIDDEN");

        final String book = graphQl(EDITOR).document(CREATE).variable("t", "Chalmersspex I").variable("v", 1).execute()
                .path("bookCreate.id").entity(String.class).get();

        graphQl(EDITOR).document(SONGS).variable("id", book).variable("songs", java.util.List.of(
                        java.util.Map.of("numberId", finale, "textOnly", true),
                        java.util.Map.of("numberId", overture),
                        java.util.Map.of("title", "Tackkören")))
                .execute()
                .path("bookSetSongs.songs[*].title").entityList(String.class).containsExactly("Final", "Ouvertyr", "Tackkören")
                .path("bookSetSongs.songs[0].textOnly").entity(Boolean.class).isEqualTo(true)
                .path("bookSetSongs.songs[2].number").valueIsNull();

        // Unpublished: only editors see it, from the list or the number
        graphQl(null).document("{ books { id } }").execute().path("books").entityList(Object.class).hasSize(0);
        graphQl(null).document("query($id: ID!) { number(id: $id) { books { book { title } } } }").variable("id", finale).execute()
                .path("number.books").entityList(Object.class).hasSize(0);
        graphQl(EDITOR).document("{ books { title } }").execute().path("books[*].title").entityList(String.class).contains("Chalmersspex I");

        graphQl(EDITOR).document("mutation($id: ID!) { bookSetPublished(id: $id, published: true) { published } }")
                .variable("id", book).execute().path("bookSetPublished.published").entity(Boolean.class).isEqualTo(true);
        graphQl(null).document("query($id: ID!) { number(id: $id) { books { textOnly book { title volume } } } }").variable("id", finale).execute()
                .path("number.books[0].book.title").entity(String.class).isEqualTo("Chalmersspex I")
                .path("number.books[0].textOnly").entity(Boolean.class).isEqualTo(true);
        graphQl(null).document("{ book(id: \"chalmersspex-i\") { songs { title } } }").execute()
                .path("book.songs[*].title").entityList(String.class).containsExactly("Final", "Ouvertyr", "Tackkören");

        // A new book gets the first kind; an editor picks another from the kinds admins keep
        graphQl(EDITOR).document("query($id: ID!) { book(id: $id) { kind { nameSv } } }").variable("id", book).execute()
                .path("book.kind.nameSv").entity(String.class).isEqualTo("Spexbok");

        final String anniversary = graphQl(null).document("{ kinds(type: BOOK) { id nameSv } }").execute()
                .path("kinds[?(@.nameSv == 'Jubileumsbok')].id").entityList(String.class).get().getFirst();

        graphQl(EDITOR).document("mutation($id: ID!, $k: ID) { bookUpdate(id: $id, input: {title: \"FGV 50 år\", kindId: $k}) { kind { nameSv } } }")
                .variable("id", book).variable("k", anniversary).execute()
                .path("bookUpdate.kind.nameSv").entity(String.class).isEqualTo("Jubileumsbok");

        // A number in a book stays until it is taken out of the book
        expectError(graphQl(EDITOR).document("mutation($id: ID!) { numberDelete(id: $id) }").variable("id", finale).execute(), "BAD_REQUEST");
    }
}

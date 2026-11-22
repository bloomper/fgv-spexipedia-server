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

package nu.fgv.spexipedia.server.page;

import nu.fgv.spexipedia.server.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * The sections of spex pages (spec 4.2 and 4.2.1): the section kinds' order is the default, a spex may keep its own,
 * and sections may show material of the other applications.
 */
class SectionIntegrationTest extends AbstractIntegrationTest {

    private static final String GROUP = "01900000-0000-7000-8000-000000000701";
    private static final String CHARACTERS = "01900000-0000-7000-8000-000000000702";
    private static final String SCENOGRAPHY = "01900000-0000-7000-8000-000000000703";
    private static final String LINKS = "01900000-0000-7000-8000-000000000712";
    private static final String RECORD = "01900000-0000-7000-8000-000000000723";
    private static final String DEFAULT_ORDER = "GROUP CHARACTERS IMAGES MANUSCRIPT MUSIC RECORDS REBUS VEST BOARD TASKS IMAGES LINKS";
    private static final String SECTIONS = "query($id: ID!) { spex(id: $id) { sectionOrder sections(includeHidden: true) { id content title hidden kind { id } } } }";
    private static final String SET = "mutation($id: ID!, $s: [PageSectionInput!]!) { spexSetSections(spexId: $id, sections: $s) { id } }";

    private String order(final String user, final String spex) {
        return String.join(" ", graphQl(user).document(SECTIONS).variable("id", spex).execute()
                .path("spex.sections[*].content").entityList(String.class).get());
    }

    @Test
    void aPageHasTheDefaultOrderUntilAnAuthorGivesItItsOwn() {
        final String spex = createSpex("1984", "Lasse-Maja", null);

        publish(spex);
        graphQl(AUTHOR).document(SECTIONS).variable("id", spex).execute()
                .path("spex.sectionOrder").entity(String.class).isEqualTo("DEFAULT")
                .path("spex.sections[0].title").entity(String.class).isEqualTo("The ensemble")
                .path("spex.sections[0].id").entity(String.class).isEqualTo(GROUP);
        org.assertj.core.api.Assertions.assertThat(order(AUTHOR, spex)).isEqualTo(DEFAULT_ORDER);

        // Links moved up and renamed, the characters hidden; the others take their places by the default order, the group
        // first as nothing in the list comes before it
        graphQl(AUTHOR).document(SET).variable("id", spex).variable("s", List.of(
                Map.of("kindId", LINKS, "title", "Allt annat"),
                Map.of("kindId", CHARACTERS, "hidden", true),
                Map.of("kindId", SCENOGRAPHY))).execute().path("spexSetSections.id").entity(String.class).isEqualTo(spex);

        graphQl(AUTHOR).document(SECTIONS).variable("id", spex).execute()
                .path("spex.sectionOrder").entity(String.class).isEqualTo("OWN")
                .path("spex.sections[1].title").entity(String.class).isEqualTo("Allt annat")
                .path("spex.sections[2].hidden").entity(Boolean.class).isEqualTo(true);
        org.assertj.core.api.Assertions.assertThat(order(AUTHOR, spex))
                .isEqualTo("GROUP LINKS CHARACTERS IMAGES MANUSCRIPT MUSIC RECORDS REBUS VEST BOARD TASKS IMAGES");
        // Visitors do not see hidden sections, even when they ask for them
        org.assertj.core.api.Assertions.assertThat(order(null, spex))
                .isEqualTo("GROUP LINKS IMAGES MANUSCRIPT MUSIC RECORDS REBUS VEST BOARD TASKS IMAGES");

        // An empty list goes back to the default order
        graphQl(AUTHOR).document(SET).variable("id", spex).variable("s", List.of()).execute().path("spexSetSections.id").hasValue();
        org.assertj.core.api.Assertions.assertThat(order(AUTHOR, spex)).isEqualTo(DEFAULT_ORDER);
    }

    @Test
    void aNewImageSectionTakesItsPlaceByTheDefaultOrderOnPagesWithTheirOwn() {
        final String spex = createSpex("1985", "Vasa", null);

        graphQl(AUTHOR).document(SET).variable("id", spex).variable("s", List.of(Map.of("kindId", LINKS), Map.of("kindId", GROUP)))
                .execute().path("spexSetSections.id").hasValue();

        final String costumes = graphQl(ADMIN).document("""
                        mutation { kindSave(input: {type: SECTION, content: IMAGES, nameSv: "Kostymer", nameEn: "Costumes", subtitleSv: "Kläderna"}) {
                            id position imageKinds { nameSv } } }""").execute()
                .path("kindSave.imageKinds[*].nameSv").entityList(String.class).containsExactly("Kostymer")
                .path("kindSave.id").entity(String.class).get();

        // Last in the default order, so after the last section of the list before it: the group
        graphQl(AUTHOR).document(SECTIONS).variable("id", spex).execute()
                .path("spex.sections[*].kind.id").entityList(String.class).satisfies(ids -> {
                    org.assertj.core.api.Assertions.assertThat(ids.get(0)).isEqualTo(LINKS);
                    org.assertj.core.api.Assertions.assertThat(ids.getLast()).isEqualTo(costumes);
                });
        // The contents with one kind are never created by admins
        expectError(graphQl(ADMIN).document("mutation { kindSave(input: {type: SECTION, content: VEST, nameSv: \"Väst 2\", nameEn: \"Vest 2\"}) { id } }")
                .execute(), "BAD_REQUEST");
    }

    @Test
    void aRevivalShowsItsOriginalsOrderUntilItHasItsOwn() {
        final String spex = createSpex("1986", "Napoleon", null);
        final String revival = graphQl(EDITOR).document("mutation($p: ID!) { spexRevivalCreate(parentId: $p, year: \"2016\") { id } }")
                .variable("p", spex).execute().path("spexRevivalCreate.id").entity(String.class).get();

        graphQl(AUTHOR).document(SET).variable("id", spex).variable("s", List.of(Map.of("kindId", LINKS), Map.of("kindId", GROUP))).execute()
                .path("spexSetSections.id").hasValue();
        graphQl(AUTHOR).document(SECTIONS).variable("id", revival).execute()
                .path("spex.sectionOrder").entity(String.class).isEqualTo("ORIGINAL")
                .path("spex.sections[0].kind.id").entity(String.class).isEqualTo(LINKS)
                .path("spex.sections[1].kind.id").entity(String.class).isEqualTo(GROUP);
    }

    @Test
    void sectionsAreCheckedAndOnlyAuthorsArrangeThem() {
        final String spex = createSpex("1987", "Gustav Vasa", null);

        expectError(graphQl(MEMBER).document(SET).variable("id", spex).variable("s", List.of(Map.of("kindId", LINKS))).execute(), "FORBIDDEN");
        expectError(graphQl(AUTHOR).document(SET).variable("id", spex)
                .variable("s", List.of(Map.of("kindId", LINKS), Map.of("kindId", LINKS))).execute(), "BAD_REQUEST");
        // A section of another application needs its material, and the others take none
        expectError(graphQl(AUTHOR).document(SET).variable("id", spex).variable("s", List.of(Map.of("kindId", RECORD))).execute(), "BAD_REQUEST");
        expectError(graphQl(AUTHOR).document(SET).variable("id", spex)
                .variable("s", List.of(Map.of("kindId", LINKS, "refId", "01900000-0000-7000-8000-000000009999"))).execute(), "BAD_REQUEST");
    }

    @Test
    void aRecordFromSpexifyIsShownToEveryoneWithoutItsAudio() {
        final String spex = createSpex("1988", "Katarina", null);
        final String album = "0199a000-0000-7000-8000-000000000001";

        publish(spex);
        WIREMOCK.stubFor(post(urlEqualTo("/spexify/graphql")).withRequestBody(containing("spexipediaAlbum")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"data": {"spexipediaAlbum": {"id": "%s", "title": "Katarina", "year": 1988, "kindNameSv": "Studioinspelning",
                          "kindNameEn": "Studio recording", "mediumNameSv": "LP", "mediumNameEn": "LP", "catalogueNumber": "CEP 019",
                          "released": null, "recorded": null, "conductor": null, "spexId": null, "coverUrl": "https://spexify.test/media/1/x/albums/a/cover",
                          "backCoverUrl": null, "tracks": [{"id": "0199a000-0000-7000-8000-000000000002", "side": "A", "discNumber": 1,
                          "trackNumber": 1, "title": "Ouvertyr", "numberId": null, "hasAudio": true,
                          "audioUrl": "https://spexify.test/media/1/x/tracks/t/audio"}]}}}""".formatted(album))));
        graphQl(AUTHOR).document(SET).variable("id", spex)
                .variable("s", List.of(Map.of("kindId", RECORD, "refId", album, "title", "Originalinspelningen"))).execute()
                .path("spexSetSections.id").hasValue();

        // The defaults the list does not name come before it; the record is last
        graphQl(null).document("""
                        query($id: ID!) { spex(id: $id) { sections { content title refId record { catalogueNumber tracks { title audioUrl } } } } }""")
                .variable("id", spex).execute()
                .path("spex.sections[11].content").entity(String.class).isEqualTo("LINKS")
                .path("spex.sections[12].content").entity(String.class).isEqualTo("SPEXIFY_ALBUM")
                .path("spex.sections[12].title").entity(String.class).isEqualTo("Originalinspelningen")
                .path("spex.sections[12].record.catalogueNumber").entity(String.class).isEqualTo("CEP 019")
                .path("spex.sections[12].record.tracks[0].title").entity(String.class).isEqualTo("Ouvertyr")
                .path("spex.sections[12].record.tracks[0].audioUrl").valueIsNull();
    }
}

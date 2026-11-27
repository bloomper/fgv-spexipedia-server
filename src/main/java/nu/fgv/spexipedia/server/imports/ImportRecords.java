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

package nu.fgv.spexipedia.server.imports;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The records Spexipedia's import API accepts (spec 5.11). Kinds are given by name or alias. A spex is given by its id,
 * or, for one created in the same package, by its source key.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public final class ImportRecords {

    private ImportRecords() {
    }

    /**
     * A file uploaded to {@code /api/import/files/{sha256}}, and the name to show.
     */
    public record FileRef(String sha256, @Nullable String name) {
    }

    public record Batch(@Nullable Boolean createMissingKinds,
                        @Nullable List<ImportSpex> spex,
                        @Nullable List<ImportPage> pages,
                        @Nullable List<ImportCharacter> characters,
                        @Nullable List<ImportScene> scenes,
                        @Nullable List<ImportNumber> numbers,
                        @Nullable List<ImportArticleCategory> articleCategories,
                        @Nullable List<ImportArticle> articles,
                        @Nullable List<ImportRedirect> redirects) {
    }

    /**
     * A spex: matched by id when given, else created (spex packages only). A revival names its parent by id or source
     * key; it takes its title and category from the parent.
     */
    public record ImportSpex(String sourceKey, @Nullable UUID id, @Nullable String title, String year,
                             @Nullable String category, @Nullable UUID parentId, @Nullable String parentSourceKey,
                             @Nullable FileRef poster) {
    }

    /**
     * A spex page's texts, facts and images. Left-out fields are left as they are.
     */
    public record ImportPage(String sourceKey, @Nullable UUID spexId, @Nullable String spexSourceKey,
                             @Nullable String premiere, @Nullable String introduction,
                             @Nullable String shortDescription, @Nullable String description, @Nullable String synopsis,
                             @Nullable String vestDescription, @Nullable FileRef poster, @Nullable FileRef rebus,
                             @Nullable FileRef rebusSolution, @Nullable List<ImportFact> facts,
                             @Nullable List<ImportPageImage> images) {
    }

    public record ImportFact(String kind, @Nullable String value) {
    }

    /**
     * An image of a page section: SCENOGRAPHY or OTHER with an image kind of that use (the first when left out), or
     * VEST.
     */
    public record ImportPageImage(String section, @Nullable String kind, FileRef file, @Nullable String caption) {
    }

    public record ImportCharacter(String sourceKey, @Nullable UUID spexId, @Nullable String spexSourceKey, String name,
                                  @Nullable String description, @Nullable Integer position,
                                  @Nullable List<ImportCharacterImage> images) {
    }

    public record ImportCharacterImage(@Nullable String kind, FileRef file, @Nullable String caption) {
    }

    public record ImportScene(String sourceKey, @Nullable UUID spexId, @Nullable String spexSourceKey,
                              @Nullable Integer act, @Nullable Integer position, String title, @Nullable String place,
                              @Nullable Boolean intervalAfter) {
    }

    /**
     * A number. Credits are names; characters are names of the spex's characters.
     */
    public record ImportNumber(String sourceKey, @Nullable UUID spexId, @Nullable String spexSourceKey,
                               @Nullable Integer act, @Nullable Integer position, String title, @Nullable String kind,
                               @Nullable String originalTitle, @Nullable String originalArtist,
                               @Nullable String fromWork,
                               @Nullable String firstLine, @Nullable List<ImportCredit> credits,
                               @Nullable List<String> characters) {
    }

    /**
     * A credit by name (spec 5.3): a name that is exactly one published spexare becomes a reference to them; otherwise
     * someone outside FGV when {@code outsideFgv}, else an unresolved credit for editors to resolve.
     */
    public record ImportCredit(String kind, String name, @Nullable Boolean outsideFgv) {
    }

    public record ImportArticleCategory(String sourceKey, String name, @Nullable String description,
                                        @Nullable List<String> parents) {
    }

    /**
     * An article in Markdown. Images are files the body refers to as {@code ![caption](import:<key>)}; the import
     * replaces the reference with the image's address.
     */
    public record ImportArticle(String sourceKey, String title, String body, @Nullable String visibility,
                                @Nullable List<String> categories, @Nullable String parentSourceKey,
                                @Nullable List<ImportArticleImage> images) {
    }

    public record ImportArticleImage(String key, FileRef file, @Nullable String caption) {
    }

    /**
     * An old MediaWiki title and where it leads: a path, or the page of a record imported with a source key.
     */
    public record ImportRedirect(String sourceKey, String oldTitle, @Nullable String target,
                                 @Nullable String targetSourceKey) {
    }
}

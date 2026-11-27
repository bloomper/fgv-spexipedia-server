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

package nu.fgv.spexipedia.server.mcp;

import nu.fgv.spexipedia.server.basedata.BaseDataDtos.SpexDto;
import nu.fgv.spexipedia.server.basedata.BaseDataService;
import nu.fgv.spexipedia.server.basedata.Spex;
import nu.fgv.spexipedia.server.content.ContentDtos.CharacterDto;
import nu.fgv.spexipedia.server.content.ContentDtos.NumberDto;
import nu.fgv.spexipedia.server.content.ContentService;
import nu.fgv.spexipedia.server.kind.KindService;
import nu.fgv.spexipedia.server.content.SpexCharacter;
import nu.fgv.spexipedia.server.content.SpexNumber;
import nu.fgv.spexipedia.server.page.SpexPageService;
import nu.fgv.spexipedia.server.util.error.ResourceNotFoundException;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The MCP tools for what is on spex pages: characters, scenes and numbers. Characters and numbers are base data, read
 * by anyone by id; lists by spex follow the GraphQL API's rules (retired ones for EDITOR). EDITOR adds characters,
 * scenes and numbers by pasting text, as in the web. Records are Spexify's and books Spexarkivet's.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Component
public class ContentMcpTools {

    private static final String SPEX_ID = "Spex id or slug";

    private final ContentService content;
    private final BaseDataService baseData;
    private final SpexPageService pages;
    private final McpToolSupport support;
    private final KindService kinds;

    public ContentMcpTools(final ContentService content, final BaseDataService baseData,
                           final SpexPageService pages, final McpToolSupport support, final KindService kinds) {
        this.kinds = kinds;
        this.content = content;
        this.baseData = baseData;
        this.pages = pages;
        this.support = support;
    }

    @McpTool(name = "list_characters",
            description = "List the characters on a spex's page, in order: an original's own, a revival's after its changes to its original's. Retired ones only for EDITOR.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public McpToolSupport.ListResult<McpCharacter> listCharacters(@McpToolParam(description = SPEX_ID) final String spexId) {
        return support.call(() -> new McpToolSupport.ListResult<>(characters(content.characters(spex(spexId).key()))));
    }

    @McpTool(name = "get_character",
            description = "Get a character (a role) by id: the spex it belongs to, its description and the numbers it sings.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public McpCharacterDetails getCharacter(@McpToolParam(description = "Character id") final String id) {
        return support.call(() -> {
            final CharacterDto character = content.character(id).orElseThrow(() -> new ResourceNotFoundException(SpexCharacter.class, id));

            return new McpCharacterDetails(characters(List.of(character)).getFirst(), SpexMcpTools.McpSpexRef.of(baseData.spexByKey(character.spexKey())),
                    content.numbersOfCharacter(character.key()).stream().map(SpexMcpTools.McpNumber::of).toList());
        });
    }

    @McpTool(name = "list_numbers",
            description = "List the numbers (songs) on a spex's page, by act and position, with their original songs. Retired ones only for EDITOR.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public McpToolSupport.ListResult<SpexMcpTools.McpNumber> listNumbers(@McpToolParam(description = SPEX_ID) final String spexId) {
        return support.call(() -> new McpToolSupport.ListResult<>(content.numbers(spex(spexId).key()).stream().map(SpexMcpTools.McpNumber::of).toList()));
    }

    @McpTool(name = "get_number",
            description = "Get a number (song) by id: the spex it belongs to and its original song.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public McpNumberDetails getNumber(@McpToolParam(description = "Number id") final String id) {
        return support.call(() -> {
            final NumberDto number = content.number(id).orElseThrow(() -> new ResourceNotFoundException(SpexNumber.class, id));

            return new McpNumberDetails(SpexMcpTools.McpNumber.of(number), SpexMcpTools.McpSpexRef.of(baseData.spexByKey(number.spexKey())));
        });
    }

    @McpTool(name = "paste_characters",
            description = "EDITOR: add characters to a spex as text, one per line: the name, optionally followed by ' – ' and a description. They are added after the existing ones; on a revival, as its own. Returns the characters on its page afterwards.",
            annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public McpToolSupport.ListResult<McpCharacter> pasteCharacters(@McpToolParam(description = SPEX_ID) final String spexId,
                                                                   @McpToolParam(description = "One character per line") final String text) {
        return support.call(() -> new McpToolSupport.ListResult<>(characters(content.pasteCharacters(spex(spexId).id(), text))));
    }

    @McpTool(name = "paste_scenes",
            description = "EDITOR: add scenes to a spex as text, one per line: 'Title' or 'Title – Place'; a line '--' marks an interval after the scene before; a line 'Akt 2' starts an act. Returns its scenes afterwards.",
            annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public McpToolSupport.ListResult<SpexMcpTools.McpScene> pasteScenes(@McpToolParam(description = SPEX_ID) final String spexId,
                                                                        @McpToolParam(description = "One scene per line") final String text) {
        return support.call(() -> new McpToolSupport.ListResult<>(content.pasteScenes(spex(spexId).id(), text).stream().map(SpexMcpTools.McpScene::of).toList()));
    }

    @McpTool(name = "paste_numbers",
            description = "EDITOR: add numbers (songs) to a spex as text, one per line: 'Title' or 'Title (Original song – Original artist)'; a line 'Akt 2' starts an act. Returns its numbers afterwards.",
            annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public McpToolSupport.ListResult<SpexMcpTools.McpNumber> pasteNumbers(@McpToolParam(description = SPEX_ID) final String spexId,
                                                                          @McpToolParam(description = "One number per line") final String text) {
        return support.call(() -> new McpToolSupport.ListResult<>(content.pasteNumbers(spex(spexId).id(), text).stream().map(SpexMcpTools.McpNumber::of).toList()));
    }

    private SpexDto spex(final String id) {
        return baseData.spex(id).orElseThrow(() -> new ResourceNotFoundException(Spex.class, id));
    }

    private List<McpCharacter> characters(final List<CharacterDto> characters) {
        return characters.stream().map(McpCharacter::of).toList();
    }

    /**
     * A character (a role) on a spex's page.
     */
    public record McpCharacter(UUID id, String name, @Nullable String description, boolean retired) {

        static McpCharacter of(final CharacterDto character) {
            return new McpCharacter(character.id(), character.name(), character.description(), character.retired());
        }
    }

    public record McpCharacterDetails(McpCharacter character, SpexMcpTools.McpSpexRef spex,
                                      List<SpexMcpTools.McpNumber> numbers) {
    }

    public record McpNumberDetails(SpexMcpTools.McpNumber number, SpexMcpTools.McpSpexRef spex) {
    }
}

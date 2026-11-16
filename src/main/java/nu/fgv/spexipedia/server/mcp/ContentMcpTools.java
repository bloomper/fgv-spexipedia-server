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
import nu.fgv.spexipedia.server.content.ContentDtos.FigureDto;
import nu.fgv.spexipedia.server.content.ContentDtos.NumberDto;
import nu.fgv.spexipedia.server.content.ContentDtos.RecordingDto;
import nu.fgv.spexipedia.server.content.ContentDtos.TrackDto;
import nu.fgv.spexipedia.server.content.ContentService;
import nu.fgv.spexipedia.server.content.Figure;
import nu.fgv.spexipedia.server.content.Medium;
import nu.fgv.spexipedia.server.content.Recording;
import nu.fgv.spexipedia.server.content.RecordingService;
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
 * The MCP tools for what is on spex pages: characters, scenes, numbers, figures and recordings. Characters and numbers
 * are base data, read by anyone by id; lists by spex, figures' characters and recordings follow the GraphQL API's rules
 * (retired ones and unpublished recordings for EDITOR, figures' characters on published pages unless AUTHOR). EDITOR
 * adds characters, scenes, numbers and tracks by pasting text, as in the web.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Component
public class ContentMcpTools {

    private static final String SPEX_ID = "Spex id or slug";

    private final ContentService content;
    private final RecordingService recordings;
    private final BaseDataService baseData;
    private final SpexPageService pages;
    private final McpToolSupport support;

    public ContentMcpTools(final ContentService content, final RecordingService recordings, final BaseDataService baseData,
                           final SpexPageService pages, final McpToolSupport support) {
        this.content = content;
        this.recordings = recordings;
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
            description = "Get a character by id: the spex it belongs to, its figure, description and the numbers it sings.",
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

    @McpTool(name = "list_figures",
            description = "List figures, by name: who or what characters portray across spex, e.g. a historical person.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public McpToolSupport.ListResult<McpFigure> listFigures() {
        return support.call(() -> new McpToolSupport.ListResult<>(content.figures().stream().map(McpFigure::of).toList()));
    }

    @McpTool(name = "get_figure",
            description = "Get a figure by id or slug, with its characters by year: those on published pages, all for AUTHOR.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public McpFigureDetails getFigure(@McpToolParam(description = "Figure id or slug") final String id) {
        return support.call(() -> {
            final FigureDto figure = content.figure(id).orElseThrow(() -> new ResourceNotFoundException(Figure.class, id));
            final Set<Long> published = pages.publishedSpexKeys();
            final boolean author = pages.isAuthor();
            final List<CharacterDto> characters = content.charactersOfFigure(figure.key()).stream()
                    .filter(character -> author || published.contains(character.spexKey()))
                    .toList();
            final Map<Long, SpexDto> spex = baseData.spexByKeys(characters.stream().map(CharacterDto::spexKey).collect(Collectors.toSet()));

            return new McpFigureDetails(McpFigure.of(figure), characters.stream()
                    .map(character -> new McpFigureCharacter(character.id(), character.name(), SpexMcpTools.McpSpexRef.of(spex.get(character.spexKey()))))
                    .toList());
        });
    }

    @McpTool(name = "list_recordings",
            description = "List recordings (records and albums of spex music) by catalogue number: published ones, all for EDITOR.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public McpToolSupport.ListResult<SpexMcpTools.McpRecordingRef> listRecordings() {
        return support.call(() -> new McpToolSupport.ListResult<>(recordings.recordings().stream().map(SpexMcpTools.McpRecordingRef::of).toList()));
    }

    @McpTool(name = "get_recording",
            description = "Get a recording by id or slug, with its tracks: a published one, any for EDITOR.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public McpRecording getRecording(@McpToolParam(description = "Recording id or slug") final String id) {
        return support.call(() -> recording(recordings.recording(id).orElseThrow(() -> new ResourceNotFoundException(Recording.class, id))));
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

    @McpTool(name = "paste_recording_tracks",
            description = "EDITOR: set a recording's tracks as text, one per line: 'A1 Title' or 'Title'; a line 'Sida B' starts a side. Each title is matched to a number of the recording's spex. Returns the recording with its tracks.",
            annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public McpRecording pasteRecordingTracks(@McpToolParam(description = "Recording id or slug") final String recordingId,
                                             @McpToolParam(description = "One track per line") final String text) {
        return support.call(() -> {
            final RecordingDto recording = recordings.recording(recordingId).orElseThrow(() -> new ResourceNotFoundException(Recording.class, recordingId));

            return recording(recordings.pasteTracks(recording.id(), text));
        });
    }

    private SpexDto spex(final String id) {
        return baseData.spex(id).orElseThrow(() -> new ResourceNotFoundException(Spex.class, id));
    }

    private List<McpCharacter> characters(final List<CharacterDto> characters) {
        final Set<Long> figureKeys = characters.stream().map(CharacterDto::figureKey).filter(Objects::nonNull).collect(Collectors.toSet());
        final Map<Long, FigureDto> figures = figureKeys.isEmpty() ? Map.of() : content.figuresByKeys(figureKeys);

        return characters.stream().map(character -> McpCharacter.of(character, figures)).toList();
    }

    private McpRecording recording(final RecordingDto recording) {
        return new McpRecording(SpexMcpTools.McpRecordingRef.of(recording), recording.medium(),
                recording.spexKey() == null ? null : SpexMcpTools.McpSpexRef.of(baseData.spexByKey(recording.spexKey())),
                recording.recorded(), recording.description(),
                recordings.tracks(recording.key()).stream().map(McpTrack::of).toList());
    }

    /**
     * A character on a spex's page.
     *
     * @param figure the name of the figure (e.g. a historical person) the character portrays, if any
     */
    public record McpCharacter(UUID id, String name, @Nullable String figure, @Nullable String description,
                               boolean retired) {

        static McpCharacter of(final CharacterDto character, final Map<Long, FigureDto> figures) {
            final FigureDto figure = character.figureKey() == null ? null : figures.get(character.figureKey());

            return new McpCharacter(character.id(), character.name(), figure == null ? null : figure.name(), character.description(),
                    character.retired());
        }
    }

    public record McpCharacterDetails(McpCharacter character, SpexMcpTools.McpSpexRef spex,
                                      List<SpexMcpTools.McpNumber> numbers) {
    }

    public record McpNumberDetails(SpexMcpTools.McpNumber number, SpexMcpTools.McpSpexRef spex) {
    }

    public record McpFigure(UUID id, String slug, String name, @Nullable String description) {

        static McpFigure of(final FigureDto figure) {
            return new McpFigure(figure.id(), figure.slug(), figure.name(), figure.description());
        }
    }

    public record McpFigureCharacter(UUID id, String name, SpexMcpTools.McpSpexRef spex) {
    }

    public record McpFigureDetails(McpFigure figure, List<McpFigureCharacter> characters) {
    }

    public record McpRecording(SpexMcpTools.McpRecordingRef recording, Medium medium,
                               SpexMcpTools.@Nullable McpSpexRef spex, @Nullable String recorded,
                               @Nullable String description, List<McpTrack> tracks) {
    }

    /**
     * A track, with the title of its number when it is one.
     */
    public record McpTrack(@Nullable String side, int position, String title) {

        static McpTrack of(final TrackDto track) {
            return new McpTrack(track.side(), track.position(), track.title());
        }
    }
}

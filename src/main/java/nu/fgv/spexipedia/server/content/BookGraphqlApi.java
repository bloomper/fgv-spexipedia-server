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

import nu.fgv.spexipedia.server.content.ContentDtos.NumberDto;
import nu.fgv.spexipedia.server.content.ContentDtos.BookDto;
import nu.fgv.spexipedia.server.content.ContentDtos.BookSongDto;
import nu.fgv.spexipedia.server.image.ImageDto;
import nu.fgv.spexipedia.server.image.ImageService;
import nu.fgv.spexipedia.server.kind.Kind;
import nu.fgv.spexipedia.server.kind.KindService;
import nu.fgv.spexipedia.server.util.Ids;
import org.jspecify.annotations.Nullable;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * GraphQL for books and their songs, and the books a number is in.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Controller
public class BookGraphqlApi {

    private final BookService service;
    private final ContentService content;
    private final ImageService imageService;
    private final KindService kindService;

    public BookGraphqlApi(final BookService service, final ContentService content, final ImageService imageService,
                          final KindService kindService) {
        this.service = service;
        this.content = content;
        this.imageService = imageService;
        this.kindService = kindService;
    }

    @QueryMapping
    public List<BookDto> books() {
        return service.books();
    }

    @QueryMapping
    public @Nullable BookDto book(@Argument final String id) {
        return service.book(id).orElse(null);
    }

    @MutationMapping
    public BookDto bookCreate(@Argument final Map<String, Object> input) {
        return service.create(input(input));
    }

    @MutationMapping
    public BookDto bookUpdate(@Argument final String id, @Argument final Map<String, Object> input) {
        return service.update(Ids.require(id), input(input));
    }

    @MutationMapping
    public BookDto bookSetSongs(@Argument final String id, @Argument final List<Map<String, Object>> songs) {
        return service.setSongs(Ids.require(id), songs.stream()
                .map(song -> new BookService.SongInput(Ids.optional((String) song.get("numberId")), (String) song.get("title"),
                        Boolean.TRUE.equals(song.get("textOnly"))))
                .toList());
    }

    @MutationMapping
    public BookDto bookSetPublished(@Argument final String id, @Argument final boolean published) {
        return service.setPublished(Ids.require(id), published);
    }

    @MutationMapping
    public boolean bookDelete(@Argument final String id) {
        return service.delete(Ids.require(id));
    }

    static BookService.BookInput input(final Map<String, Object> input) {
        return new BookService.BookInput((String) input.get("title"), Ids.optional((String) input.get("kindId")), (Integer) input.get("volume"),
                (String) input.get("publishedYear"), (String) input.get("description"), Ids.optional((String) input.get("coverImageId")));
    }

    @SchemaMapping(typeName = "Book")
    public @Nullable Kind kind(final BookDto book) {
        return kindService.byKeys(List.of(book.kindKey())).get(book.kindKey());
    }

    @SchemaMapping(typeName = "Book")
    public List<BookSongDto> songs(final BookDto book) {
        return service.songs(book.key());
    }

    @SchemaMapping(typeName = "Book")
    public @Nullable ImageDto cover(final BookDto book) {
        return book.coverKey() == null ? null : imageService.visible(imageService.byIds(List.of(book.coverKey())).get(book.coverKey()));
    }

    @BatchMapping(typeName = "BookSong", field = "number")
    public Map<BookSongDto, @Nullable NumberDto> songNumber(final List<BookSongDto> songs) {
        final Map<Long, NumberDto> numbers = content.numbersByKeys(songs.stream().map(BookSongDto::numberKey)
                .filter(Objects::nonNull).distinct().toList());
        final Map<BookSongDto, @Nullable NumberDto> result = new HashMap<>();

        songs.forEach(song -> result.put(song, song.numberKey() == null ? null : numbers.get(song.numberKey())));
        return result;
    }

    @BatchMapping(typeName = "BookSong", field = "book")
    public Map<BookSongDto, BookDto> songBook(final List<BookSongDto> songs) {
        final Map<Long, BookDto> books = service.booksByKeys(songs.stream().map(BookSongDto::bookKey).distinct().toList());
        final Map<BookSongDto, BookDto> result = new HashMap<>();

        songs.forEach(song -> result.put(song, books.get(song.bookKey())));
        return result;
    }

    @BatchMapping(typeName = "Number", field = "books")
    public Map<NumberDto, List<BookSongDto>> numberBooks(final List<NumberDto> numbers) {
        final Map<Long, List<BookSongDto>> songs = service.songsOfNumbers(numbers.stream().map(NumberDto::key).distinct().toList());
        final Map<NumberDto, List<BookSongDto>> result = new HashMap<>();

        numbers.forEach(number -> result.put(number, songs.getOrDefault(number.key(), List.of())));
        return result;
    }
}

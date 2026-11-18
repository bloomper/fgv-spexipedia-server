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

import nu.fgv.spexipedia.server.audit.AuditService;
import nu.fgv.spexipedia.server.content.ContentDtos.BookDto;
import nu.fgv.spexipedia.server.content.ContentDtos.BookSongDto;
import nu.fgv.spexipedia.server.content.ContentRepositories.NumberRepository;
import nu.fgv.spexipedia.server.content.ContentRepositories.BookRepository;
import nu.fgv.spexipedia.server.content.ContentRepositories.BookSongRepository;
import nu.fgv.spexipedia.server.image.ImageService;
import nu.fgv.spexipedia.server.kind.KindService;
import nu.fgv.spexipedia.server.kind.KindType;
import nu.fgv.spexipedia.server.search.IndexEvent;
import nu.fgv.spexipedia.server.search.SearchKind;
import nu.fgv.spexipedia.server.user.Caller;
import nu.fgv.spexipedia.server.user.CallerService;
import nu.fgv.spexipedia.server.util.Ids;
import nu.fgv.spexipedia.server.util.Slugs;
import nu.fgv.spexipedia.server.util.Texts;
import nu.fgv.spexipedia.server.util.Uuids;
import nu.fgv.spexipedia.server.util.error.BadRequestException;
import nu.fgv.spexipedia.server.util.error.ResourceNotFoundException;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Books (e.g. the books Chalmersspex I, II …) and their songs. A book is printed, not a recording: its songs are the spex' numbers
 * (or free titles), in the book's order, some with only the words. Anyone reads published ones; EDITOR reads and
 * changes all.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Service
public class BookService {

    static final int MAX_SONGS = 1000;

    private final BookRepository repository;
    private final BookSongRepository songRepository;
    private final NumberRepository numberRepository;
    private final ImageService imageService;
    private final KindService kindService;
    private final CallerService callerService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    public BookService(final BookRepository repository, final BookSongRepository songRepository,
                           final NumberRepository numberRepository, final ImageService imageService, final KindService kindService,
                           final CallerService callerService, final AuditService auditService,
                           final ApplicationEventPublisher events) {
        this.repository = repository;
        this.songRepository = songRepository;
        this.numberRepository = numberRepository;
        this.imageService = imageService;
        this.kindService = kindService;
        this.callerService = callerService;
        this.auditService = auditService;
        this.events = events;
    }

    private boolean editor() {
        return callerService.currentIfAny().map(Caller::isEditor).orElse(false);
    }

    @Transactional(readOnly = true)
    public List<BookDto> books() {
        final boolean editor = editor();

        return repository.findAllInOrder().stream()
                .filter(book -> editor || book.isPublished())
                .map(ContentDtos::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<BookDto> book(final String idOrSlug) {
        final boolean editor = editor();

        return Ids.parse(idOrSlug).flatMap(repository::findByUuid).or(() -> repository.findBySlug(idOrSlug))
                .filter(book -> editor || book.isPublished())
                .map(ContentDtos::toDto);
    }

    @Transactional(readOnly = true)
    public Map<Long, BookDto> booksByKeys(final Collection<Long> keys) {
        return repository.findAllById(keys).stream().map(ContentDtos::toDto)
                .collect(Collectors.toMap(BookDto::key, book -> book));
    }

    @Transactional(readOnly = true)
    public List<BookSongDto> songs(final long bookKey) {
        return repository.findById(bookKey)
                .map(book -> songRepository.findByBookOrderByPositionAsc(book).stream().map(ContentDtos::toDto).toList())
                .orElse(List.of());
    }

    /**
     * The books each number is in, as the viewer may see them: the songs, grouped by number key.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<BookSongDto>> songsOfNumbers(final Collection<Long> numberKeys) {
        if (numberKeys.isEmpty()) {
            return Map.of();
        }

        final boolean editor = editor();

        return songRepository.findByNumberKeys(numberKeys).stream()
                .filter(song -> editor || song.getBook().isPublished())
                .map(ContentDtos::toDto)
                .collect(Collectors.groupingBy(BookSongDto::numberKey, java.util.LinkedHashMap::new, Collectors.toList()));
    }

    @Transactional
    public BookDto create(final BookInput input) {
        callerService.current().requireEditor();

        final Book book = new Book();

        book.setUuid(Uuids.v7());
        apply(book, input);
        book.setSlug(Slugs.unique(book.getTitle(), "bok", repository::existsBySlug));
        repository.save(book);
        events.publishEvent(new IndexEvent(SearchKind.BOOK, book.getUuid()));
        auditService.record("CREATE", "BOOK", book.getUuid(), "Skapade boken " + book.getTitle());
        return ContentDtos.toDto(book);
    }

    @Transactional
    public BookDto update(final UUID id, final BookInput input) {
        callerService.current().requireEditor();

        final Book book = require(id);

        apply(book, input);
        events.publishEvent(new IndexEvent(SearchKind.BOOK, id));
        auditService.record("UPDATE", "BOOK", id, "Ändrade boken " + book.getTitle());
        return ContentDtos.toDto(book);
    }

    @Transactional
    public BookDto setPublished(final UUID id, final boolean published) {
        callerService.current().requireEditor();

        final Book book = require(id);

        book.setPublished(published);
        events.publishEvent(new IndexEvent(SearchKind.BOOK, id));
        auditService.record(published ? "PUBLISH" : "UNPUBLISH", "BOOK", id,
                (published ? "Publicerade boken " : "Avpublicerade boken ") + book.getTitle());
        return ContentDtos.toDto(book);
    }

    @Transactional
    public boolean delete(final UUID id) {
        callerService.current().requireEditor();

        final Book book = require(id);

        repository.delete(book);
        events.publishEvent(new IndexEvent(SearchKind.BOOK, id));
        auditService.record("DELETE", "BOOK", id, "Tog bort boken " + book.getTitle());
        return true;
    }

    /**
     * Replaces the songs, in the order given.
     */
    @Transactional
    public BookDto setSongs(final UUID id, final List<SongInput> songs) {
        callerService.current().requireEditor();
        if (songs.size() > MAX_SONGS) {
            throw new BadRequestException("error.tooMany", MAX_SONGS);
        }

        final Book book = require(id);

        songRepository.deleteByBook(book);
        songRepository.flush();
        for (int i = 0; i < songs.size(); i++) {
            final SongInput input = songs.get(i);
            final BookSong song = new BookSong();

            song.setUuid(Uuids.v7());
            song.setBook(book);
            song.setPosition(i + 1);
            if (input.numberId() != null) {
                song.setNumber(numberRepository.findByUuid(input.numberId())
                        .orElseThrow(() -> new ResourceNotFoundException(SpexNumber.class, input.numberId())));
            } else {
                song.setTitle(Texts.required(input.title(), 255, "title"));
            }
            song.setTextOnly(input.textOnly());
            songRepository.save(song);
        }
        events.publishEvent(new IndexEvent(SearchKind.BOOK, id));
        auditService.record("UPDATE", "BOOK", id, "Ändrade sångerna i boken " + book.getTitle());
        return ContentDtos.toDto(book);
    }

    @Transactional(readOnly = true)
    public @Nullable Long version(final UUID id) {
        return repository.findByUuid(id).map(Book::getVersion).orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean isPublished(final UUID id) {
        return repository.findByUuid(id).map(Book::isPublished).orElse(false);
    }

    private void apply(final Book book, final BookInput input) {
        book.setTitle(Texts.required(input.title(), 255, "title"));
        // A kind given, else the one it has, else the first: a new book gets the first, as a recording gets its medium
        book.setKind(input.kindId() != null ? kindService.require(input.kindId(), KindType.BOOK, null, book.getKind())
                : book.getKind() != null ? book.getKind()
                : kindService.first(KindType.BOOK, null).orElseThrow(() -> new BadRequestException("error.field.invalid", "kindId")));
        if (input.volume() != null && (input.volume() < 1 || input.volume() > 999)) {
            throw new BadRequestException("error.field.invalid", "volume");
        }
        book.setVolume(input.volume());
        book.setPublishedYear(Texts.partialDate(input.publishedYear()));
        book.setDescription(Texts.optional(input.description(), 50_000, "description"));
        book.setCover(input.coverImageId() == null ? null : imageService.requireUsable(input.coverImageId()));
    }

    private Book require(final UUID id) {
        return repository.findByUuid(id).orElseThrow(() -> new ResourceNotFoundException(Book.class, id));
    }

    public record BookInput(String title, @Nullable UUID kindId, @Nullable Integer volume, @Nullable String publishedYear,
                                @Nullable String description, @Nullable UUID coverImageId) {
    }

    public record SongInput(@Nullable UUID numberId, @Nullable String title, boolean textOnly) {
    }
}

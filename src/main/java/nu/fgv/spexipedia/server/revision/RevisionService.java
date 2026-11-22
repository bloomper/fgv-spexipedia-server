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

package nu.fgv.spexipedia.server.revision;

import nu.fgv.spexipedia.server.audit.AuditService;
import nu.fgv.spexipedia.server.util.StoredText;
import nu.fgv.spexipedia.server.user.Caller;
import nu.fgv.spexipedia.server.user.CallerService;
import nu.fgv.spexipedia.server.util.Uuids;
import nu.fgv.spexipedia.server.util.error.ResourceNotFoundException;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Revisions of articles and spex page texts (section 4.5 of the spec): every save is one, the history shows who changed
 * what and when, and an author can hide a revision's text.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Service
public class RevisionService {

    private final RevisionRepository repository;
    private final CallerService callerService;
    private final AuditService auditService;
    private final Clock clock;

    public RevisionService(final RevisionRepository repository, final CallerService callerService,
                           final AuditService auditService, final Clock clock) {
        this.repository = repository;
        this.callerService = callerService;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Records a save, by the caller (or the system for the sample data).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Revision record(final RevisionTarget target, final UUID targetId, final @Nullable String field,
                           final String title, final String body, final @Nullable String comment) {
        final Revision previous = field == null
                ? repository.findFirstByTargetAndTargetIdOrderByIdDesc(target, targetId).orElse(null)
                : repository.findFirstByTargetAndTargetIdAndFieldOrderByIdDesc(target, targetId, field).orElse(null);
        final Caller caller = callerService.currentIfAny().orElse(null);
        final Revision revision = new Revision();

        revision.setUuid(Uuids.v7());
        revision.setTarget(target);
        revision.setTargetId(targetId);
        revision.setField(field);
        revision.setTitle(title.length() > 255 ? title.substring(0, 255) : title);
        revision.setBody(body);
        revision.setPreviousId(previous == null ? null : previous.getId());
        revision.setComment(comment == null || comment.isBlank() ? null : comment.strip().substring(0, Math.min(500, comment.strip().length())));
        revision.setAuthor(caller == null ? "system" : caller.subject());
        revision.setAuthorName(caller == null ? "Spexipedia" : caller.displayName());
        revision.setCreatedAt(clock.instant());
        return repository.save(revision);
    }

    public Optional<Revision> latest(final RevisionTarget target, final UUID targetId, final @Nullable String field) {
        return field == null
                ? repository.findFirstByTargetAndTargetIdOrderByIdDesc(target, targetId)
                : repository.findFirstByTargetAndTargetIdAndFieldOrderByIdDesc(target, targetId, field);
    }

    @Transactional(readOnly = true)
    public List<RevisionDto> history(final RevisionTarget target, final UUID targetId) {
        return toDtos(repository.findByTargetAndTargetIdOrderByIdDesc(target, targetId));
    }

    @Transactional(readOnly = true)
    public Optional<Revision> find(final UUID id) {
        return repository.findByUuid(id);
    }

    @Transactional(readOnly = true)
    public Page<Revision> visible(final Collection<UUID> articles, final @Nullable Collection<UUID> pages, final int page, final int size) {
        final Collection<UUID> someArticles = articles.isEmpty() ? List.of(new UUID(0, 0)) : articles;
        final PageRequest request = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 200));

        if (pages == null) {
            return repository.findVisibleAllPages(someArticles, request);
        }
        return repository.findVisible(someArticles, pages.isEmpty() ? List.of(new UUID(0, 0)) : pages, request);
    }

    /**
     * AUTHOR. Hides or shows a revision's text.
     */
    @Transactional
    public RevisionDto setHidden(final UUID id, final boolean hidden) {
        final Caller caller = callerService.current();

        caller.requireAuthor();

        final Revision revision = repository.findByUuid(id).orElseThrow(() -> new ResourceNotFoundException(Revision.class, id));

        revision.setHidden(hidden);
        revision.setHiddenBy(hidden ? caller.subject() : null);
        auditService.record(hidden ? "HIDE" : "SHOW", "REVISION", id,
                StoredText.of(hidden ? "audit.revision.hide" : "audit.revision.show", revision.getTitle()));
        return toDtos(List.of(revision)).getFirst();
    }

    @Transactional(readOnly = true)
    public List<RevisionDto> toDtos(final List<Revision> revisions) {
        final boolean author = callerService.currentIfAny().map(Caller::isAuthor).orElse(false);
        final Map<Long, UUID> previous = repository.findAllById(revisions.stream().map(Revision::getPreviousId)
                        .filter(java.util.Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(Revision::getId, Revision::getUuid));

        return revisions.stream()
                .map(revision -> new RevisionDto(revision.getUuid(), revision.getTarget(), revision.getTargetId(),
                        revision.getTitle(), revision.getField(), revision.isHidden() && !author ? null : revision.getBody(),
                        revision.getPreviousId() == null ? null : previous.get(revision.getPreviousId()), revision.getComment(),
                        revision.isHidden(), author ? revision.getAuthorName() : null, revision.getCreatedAt()))
                .toList();
    }
}

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

package nu.fgv.spexipedia.server.redirect;

import nu.fgv.spexipedia.server.audit.AuditService;
import nu.fgv.spexipedia.server.audit.AuditText;
import nu.fgv.spexipedia.server.user.CallerService;
import nu.fgv.spexipedia.server.util.Texts;
import nu.fgv.spexipedia.server.util.Uuids;
import nu.fgv.spexipedia.server.util.error.BadRequestException;
import nu.fgv.spexipedia.server.util.error.ResourceNotFoundException;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Redirects from the MediaWiki's old titles (section 5.7 of the spec): {@code /<Title>} and
 * {@code /index.php?title=<Title>} lead to the page that replaced them. Titles are compared as the MediaWiki does,
 * underscores as spaces, and also ignoring case. Admins keep the table.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Service
public class RedirectService {

    private final JdbcClient jdbc;
    private final CallerService callerService;
    private final AuditService auditService;

    public RedirectService(final JdbcClient jdbc, final CallerService callerService, final AuditService auditService) {
        this.jdbc = jdbc;
        this.callerService = callerService;
        this.auditService = auditService;
    }

    public static String normalize(final String title) {
        return title.replace('_', ' ').replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Where the old title leads, or empty.
     */
    @Transactional(readOnly = true)
    public Optional<String> lookup(final String title) {
        return jdbc.sql("SELECT target FROM redirect WHERE normalized_title = :t").param("t", normalize(title)).query(String.class).optional();
    }

    @Transactional(readOnly = true)
    public List<RedirectDto> list() {
        callerService.current().requireAdmin();
        return jdbc.sql("SELECT uuid, old_title, target FROM redirect ORDER BY normalized_title")
                .query((rs, _) -> new RedirectDto(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3)))
                .list();
    }

    @Transactional
    public RedirectDto save(final @Nullable UUID id, final String oldTitle, final String target) {
        callerService.current().requireAdmin();
        return put(id, oldTitle, target);
    }

    /**
     * Without checking the caller, for the sample data and the migration.
     */
    @Transactional
    public RedirectDto put(final @Nullable UUID id, final String oldTitle, final String target) {
        final String title = Texts.required(oldTitle, 255, "oldTitle");
        final String path = Texts.required(target, 1000, "target");

        if (!path.startsWith("/") || path.startsWith("//")) {
            throw new BadRequestException("error.redirect.target");
        }
        try {
            if (id == null) {
                final UUID uuid = Uuids.v7();

                jdbc.sql("INSERT INTO redirect (uuid, old_title, normalized_title, target) VALUES (:u, :o, :n, :t)")
                        .param("u", uuid.toString()).param("o", title).param("n", normalize(title)).param("t", path).update();
                auditService.record("CREATE", "REDIRECT", uuid, AuditText.of("audit.redirect.create", title, path));
                return new RedirectDto(uuid, title, path);
            }
            if (jdbc.sql("UPDATE redirect SET old_title = :o, normalized_title = :n, target = :t WHERE uuid = :u")
                    .param("u", id.toString()).param("o", title).param("n", normalize(title)).param("t", path).update() == 0) {
                throw new ResourceNotFoundException("error.notFound.Redirect", id);
            }
            auditService.record("UPDATE", "REDIRECT", id, AuditText.of("audit.redirect.update", title, path));
            return new RedirectDto(id, title, path);
        } catch (final DuplicateKeyException e) {
            throw new BadRequestException("error.redirect.duplicate", title);
        }
    }

    @Transactional
    public boolean delete(final UUID id) {
        callerService.current().requireAdmin();
        if (jdbc.sql("DELETE FROM redirect WHERE uuid = :u").param("u", id.toString()).update() == 0) {
            throw new ResourceNotFoundException("error.notFound.Redirect", id);
        }
        auditService.record("DELETE", "REDIRECT", id, AuditText.of("audit.redirect.delete"));
        return true;
    }

    /**
     * Without checking the caller, for the import.
     */
    @Transactional(readOnly = true)
    public Optional<RedirectDto> byId(final UUID id) {
        return jdbc.sql("SELECT uuid, old_title, target FROM redirect WHERE uuid = :u").param("u", id.toString())
                .query((rs, _) -> new RedirectDto(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3)))
                .optional();
    }

    /**
     * The redirect of an old title, without checking the caller, for the import.
     */
    @Transactional(readOnly = true)
    public Optional<UUID> idOf(final String oldTitle) {
        return jdbc.sql("SELECT uuid FROM redirect WHERE normalized_title = :n").param("n", normalize(oldTitle))
                .query((rs, _) -> UUID.fromString(rs.getString(1))).optional();
    }

    /**
     * Without checking the caller, for the import's forget.
     */
    @Transactional
    public boolean remove(final UUID id) {
        return jdbc.sql("DELETE FROM redirect WHERE uuid = :u").param("u", id.toString()).update() == 1;
    }

    public record RedirectDto(UUID id, String oldTitle, String target) {
    }
}

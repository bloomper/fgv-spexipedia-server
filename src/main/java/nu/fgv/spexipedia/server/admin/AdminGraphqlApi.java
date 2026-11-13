package nu.fgv.spexipedia.server.admin;

import nu.fgv.spexipedia.server.article.ArticleDtos.ArticleDto;
import nu.fgv.spexipedia.server.article.ArticleService;
import nu.fgv.spexipedia.server.audit.AuditEntry;
import nu.fgv.spexipedia.server.audit.AuditFilter;
import nu.fgv.spexipedia.server.audit.AuditService;
import nu.fgv.spexipedia.server.image.ImageDto;
import nu.fgv.spexipedia.server.image.ImageService;
import nu.fgv.spexipedia.server.redirect.RedirectService;
import nu.fgv.spexipedia.server.settings.SettingsService;
import nu.fgv.spexipedia.server.user.CallerService;
import nu.fgv.spexipedia.server.util.Ids;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * GraphQL for the audit log, the trash, redirects and the settings.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Controller
public class AdminGraphqlApi {

    private final AuditService auditService;
    private final SettingsService settingsService;
    private final ArticleService articleService;
    private final ImageService imageService;
    private final RedirectService redirectService;
    private final CallerService callerService;

    public AdminGraphqlApi(final AuditService auditService, final SettingsService settingsService,
                           final ArticleService articleService, final ImageService imageService,
                           final RedirectService redirectService, final CallerService callerService) {
        this.auditService = auditService;
        this.settingsService = settingsService;
        this.articleService = articleService;
        this.imageService = imageService;
        this.redirectService = redirectService;
        this.callerService = callerService;
    }

    @QueryMapping
    public Map<String, Object> auditLog(@Argument final int page, @Argument final int size,
                                        @Argument final @Nullable Map<String, Object> filter) {
        final AuditFilter auditFilter = filter == null ? null : new AuditFilter((String) filter.get("entityType"),
                (String) filter.get("action"), (String) filter.get("actorId"), (Instant) filter.get("from"),
                (Instant) filter.get("to"), (String) filter.get("q"), (String) filter.get("entityId"));
        final Page<AuditEntry> entries = auditService.page(page, size, auditFilter);

        return Map.of("entries", entries.getContent().stream().map(AdminGraphqlApi::toDto).toList(),
                "totalCount", (int) entries.getTotalElements(), "page", entries.getNumber(), "size", entries.getSize());
    }

    private static AuditLogEntryDto toDto(final AuditEntry entry) {
        return new AuditLogEntryDto(String.valueOf(entry.getId()), entry.getAt(), entry.getActorSubject(), entry.getActorName(),
                entry.getAction(), entry.getEntityType(), entry.getEntityId(), entry.getSummary());
    }

    @QueryMapping
    public List<AuditActorDto> auditActors() {
        return auditService.actors().entrySet().stream().map(entry -> new AuditActorDto(entry.getKey(), entry.getValue())).toList();
    }

    @QueryMapping
    public SettingsService.SettingsDto settings() {
        return settingsService.read();
    }

    @MutationMapping
    public SettingsService.SettingsDto settingsUpdate(@Argument final Map<String, String> input) {
        return settingsService.update(new SettingsService.SettingsDto(input.get("welcomeSv"), input.get("welcomeEn"),
                input.get("publicWelcomeSv"), input.get("publicWelcomeEn"), input.get("contactEmail"), null));
    }

    @QueryMapping
    public TrashDto trash() {
        return new TrashDto(articleService.trashed(), imageService.trashed());
    }

    @MutationMapping
    public int trashEmpty() {
        callerService.current().requireAdmin();
        return articleService.emptyTrash() + imageService.emptyTrash();
    }

    @QueryMapping
    public List<RedirectService.RedirectDto> redirects() {
        return redirectService.list();
    }

    @MutationMapping
    public RedirectService.RedirectDto redirectSave(@Argument final Map<String, String> input) {
        return redirectService.save(Ids.optional(input.get("id")), input.get("oldTitle"), input.get("target"));
    }

    @MutationMapping
    public boolean redirectDelete(@Argument final String id) {
        return redirectService.delete(Ids.require(id));
    }

    public record AuditLogEntryDto(String id, Instant at, String actorId, @Nullable String actorName, String action,
                                   String entityType, @Nullable String entityId, String summary) {
    }

    public record AuditActorDto(String id, @Nullable String name) {
    }

    public record TrashDto(List<ArticleDto> articles, List<ImageDto> images) {
    }
}

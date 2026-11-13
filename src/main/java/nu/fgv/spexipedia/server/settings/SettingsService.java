package nu.fgv.spexipedia.server.settings;

import nu.fgv.spexipedia.server.audit.AuditService;
import nu.fgv.spexipedia.server.user.Caller;
import nu.fgv.spexipedia.server.user.CallerService;
import nu.fgv.spexipedia.server.util.Locales;
import nu.fgv.spexipedia.server.util.Texts;
import nu.fgv.spexipedia.server.util.error.BadRequestException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

/**
 * Reads and changes the settings. Everyone reads the start page's text; only admins read and change the settings.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Service
public class SettingsService {

    private final SettingsRepository repository;
    private final CallerService callerService;
    private final AuditService auditService;
    private final Clock clock;

    public SettingsService(final SettingsRepository repository, final CallerService callerService,
                           final AuditService auditService, final Clock clock) {
        this.repository = repository;
        this.callerService = callerService;
        this.auditService = auditService;
        this.clock = clock;
    }

    static SettingsDto toDto(final Settings settings) {
        return new SettingsDto(settings.getWelcomeSv(), settings.getWelcomeEn(), settings.getPublicWelcomeSv(),
                settings.getPublicWelcomeEn(), settings.getContactEmail(), settings.getLastModifiedAt());
    }

    @Transactional(readOnly = true)
    public Settings current() {
        return repository.findById(Settings.ID).orElseGet(Settings::new);
    }

    /**
     * The start page's text in the viewer's language, the public one for visitors.
     */
    @Transactional(readOnly = true)
    public @Nullable String welcome(final @Nullable Locale locale) {
        final Settings settings = current();
        final boolean member = callerService.currentIfAny().map(Caller::isUser).orElse(false);
        final boolean english = Locales.supported(locale).equals(Locale.ENGLISH);

        return member
                ? english ? settings.getWelcomeEn() : settings.getWelcomeSv()
                : english ? settings.getPublicWelcomeEn() : settings.getPublicWelcomeSv();
    }

    @Transactional(readOnly = true)
    public SettingsDto read() {
        callerService.current().requireAdmin();
        return toDto(current());
    }

    @Transactional
    public SettingsDto update(final SettingsDto input) {
        final Caller caller = callerService.current();

        caller.requireAdmin();
        if (input.contactEmail() != null && !input.contactEmail().isBlank() && !input.contactEmail().contains("@")) {
            throw new BadRequestException("error.settings.invalid");
        }
        return save(input, caller.subject());
    }

    /**
     * Without checking the caller, for the sample data.
     */
    @Transactional
    public SettingsDto save(final SettingsDto input, final String by) {
        final Settings settings = repository.findById(Settings.ID).orElseGet(Settings::new);

        settings.setWelcomeSv(Texts.optional(input.welcomeSv(), 20_000, "welcomeSv"));
        settings.setWelcomeEn(Texts.optional(input.welcomeEn(), 20_000, "welcomeEn"));
        settings.setPublicWelcomeSv(Texts.optional(input.publicWelcomeSv(), 20_000, "publicWelcomeSv"));
        settings.setPublicWelcomeEn(Texts.optional(input.publicWelcomeEn(), 20_000, "publicWelcomeEn"));
        settings.setContactEmail(Texts.optional(input.contactEmail(), 255, "contactEmail"));
        settings.setLastModifiedBy(by);
        settings.setLastModifiedAt(clock.instant());
        repository.save(settings);
        auditService.record("UPDATE", "SETTINGS", null, "Ändrade inställningarna");
        return toDto(settings);
    }

    public record SettingsDto(@Nullable String welcomeSv, @Nullable String welcomeEn, @Nullable String publicWelcomeSv,
                              @Nullable String publicWelcomeEn, @Nullable String contactEmail,
                              @Nullable Instant lastModifiedAt) {
    }
}

package nu.fgv.spexipedia.server.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * The one row of settings (section 5.12 of the spec): the start page's texts and the contact address. The consumers to
 * notify are in {@code sync_consumer}, and the task categories shown as the board are marked on the categories.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Entity
@Table(name = "settings")
@Getter
@Setter
public class Settings {

    public static final long ID = 1L;

    @Id
    private Long id = ID;

    @Column(name = "welcome_sv")
    private @Nullable String welcomeSv;

    @Column(name = "welcome_en")
    private @Nullable String welcomeEn;

    @Column(name = "public_welcome_sv")
    private @Nullable String publicWelcomeSv;

    @Column(name = "public_welcome_en")
    private @Nullable String publicWelcomeEn;

    @Column(name = "contact_email")
    private @Nullable String contactEmail;

    @Version
    private Long version;

    @Column(name = "last_modified_by")
    private @Nullable String lastModifiedBy;

    @Column(name = "last_modified_at")
    private @Nullable Instant lastModifiedAt;
}

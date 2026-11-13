package nu.fgv.spexipedia.server.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Someone who has logged in to Spexipedia: the Keycloak subject, username and display name, nothing else. Deleted 36
 * months after they were last seen.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subject", nullable = false, updatable = false)
    private String subject;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "display_name")
    private @Nullable String displayName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
}

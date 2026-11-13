package nu.fgv.spexipedia.server.user;

import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;

import java.util.Set;

/**
 * The caller of the current request with a token: their Keycloak subject, username, display name, roles on
 * {@code fgv-spexipedia} and the other applications they have access to. Requests without a token have no caller and
 * see what visitors see.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public record Caller(String subject, String username, @Nullable String displayName, Set<Role> roles, Set<App> apps) {

    public Caller {
        roles = Set.copyOf(roles);
        apps = Set.copyOf(apps);
    }

    public boolean isUser() {
        return roles.contains(Role.USER);
    }

    /**
     * Authors write the texts and images of spex pages and publish articles; editors and admins can too.
     */
    public boolean isAuthor() {
        return isUser() && (roles.contains(Role.AUTHOR) || roles.contains(Role.EDITOR) || roles.contains(Role.ADMIN));
    }

    /**
     * Editors change base data and the structured parts of spex pages; admins can too.
     */
    public boolean isEditor() {
        return isUser() && (roles.contains(Role.EDITOR) || roles.contains(Role.ADMIN));
    }

    public boolean isAdmin() {
        return isUser() && roles.contains(Role.ADMIN);
    }

    public boolean isSync() {
        return roles.contains(Role.SYNC);
    }

    public boolean isRevivalService() {
        return roles.contains(Role.REVIVAL);
    }

    public boolean hasAccess(final App app) {
        return apps.contains(app);
    }

    public void requireUser() {
        if (!isUser()) {
            throw new AccessDeniedException("USER required");
        }
    }

    public void requireAuthor() {
        if (!isAuthor()) {
            throw new AccessDeniedException("AUTHOR required");
        }
    }

    public void requireEditor() {
        if (!isEditor()) {
            throw new AccessDeniedException("EDITOR required");
        }
    }

    public void requireAdmin() {
        if (!isAdmin()) {
            throw new AccessDeniedException("ADMIN required");
        }
    }
}

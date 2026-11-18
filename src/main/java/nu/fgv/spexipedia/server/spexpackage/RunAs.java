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

package nu.fgv.spexipedia.server.spexpackage;

import nu.fgv.spexipedia.server.user.Role;
import nu.fgv.spexipedia.server.util.security.SecurityUtil;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/**
 * Runs a package's background work as the editor who uploaded it, with {@code IMPORT} so that Spexipedia's import
 * engine takes the batch (spec 5.11): what the package creates is recorded as theirs. Only this server's own jobs
 * make such a caller; no token from outside does.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
final class RunAs {

    private RunAs() {
    }

    static <T> T uploader(final String subject, final @Nullable String name, final Supplier<T> work) {
        final Jwt jwt = Jwt.withTokenValue("spex-package")
                .header("alg", "none")
                .subject(subject)
                .claim("preferred_username", name == null ? subject : name)
                .claim("name", name == null ? subject : name)
                .claim("azp", SecurityUtil.CLIENT_ID)
                .issuedAt(Instant.now())
                .build();
        final String prefix = "ROLE_" + SecurityUtil.CLIENT_ID + "_";
        final List<GrantedAuthority> authorities = List.of(Role.USER, Role.AUTHOR, Role.EDITOR, Role.IMPORT).stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(prefix + role.name())).toList();
        final SecurityContext previous = SecurityContextHolder.getContext();
        final SecurityContext context = SecurityContextHolder.createEmptyContext();

        context.setAuthentication(new JwtAuthenticationToken(jwt, authorities));
        SecurityContextHolder.setContext(context);
        try {
            return work.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}

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

package nu.fgv.spexipedia.server;

import nu.fgv.spexipedia.server.user.App;
import nu.fgv.spexipedia.server.user.Caller;
import nu.fgv.spexipedia.server.user.Role;

import java.util.Set;

/**
 * Callers for unit tests, one per role.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public final class TestCallers {

    private TestCallers() {
    }

    public static Caller member() {
        return new Caller("s-member", "tina@spexipedia.test", "Tina", Set.of(Role.USER), Set.of());
    }

    public static Caller memberWithApps() {
        return new Caller("s-apps", "tom@spexipedia.test", "Tom", Set.of(Role.USER), Set.of(App.SPEXREGISTER, App.SPEXGRAM, App.SPEXIFY));
    }

    public static Caller author() {
        return new Caller("s-author", "agnes@spexipedia.test", "Agnes", Set.of(Role.USER, Role.AUTHOR), Set.of());
    }

    public static Caller editor() {
        return new Caller("s-editor", "erik@spexipedia.test", "Erik", Set.of(Role.USER, Role.EDITOR), Set.of());
    }

    public static Caller admin() {
        return new Caller("s-admin", "anna@spexipedia.test", "Anna", Set.of(Role.USER, Role.ADMIN), Set.of());
    }

    public static Caller noAccess() {
        return new Caller("s-none", "nils@spexipedia.test", "Nils", Set.of(), Set.of());
    }

    public static Caller syncService() {
        return new Caller("client:spexregister-service", "service-account-spexregister-service", null, Set.of(Role.SYNC), Set.of());
    }
}

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

package nu.fgv.spexipedia.server.user;

/**
 * The other FGV applications Spexipedia links to, by their Keycloak client. A user has access to one when their token
 * carries its {@code USER} role (the web clients carry each other's {@code USER} roles).
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public enum App {
    SPEXREGISTER("spexregister"),
    SPEXGRAM("fgv-spexgram"),
    SPEXIFY("fgv-spexify"),
    SPEXFLIX("fgv-spexflix"),
    ARCHIVE("fgv-archive"),
    REVIVAL("fgv-revival");

    private final String clientId;

    App(final String clientId) {
        this.clientId = clientId;
    }

    public String clientId() {
        return clientId;
    }
}

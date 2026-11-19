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

package nu.fgv.spexipedia.server.util.data;

import java.util.UUID;

/**
 * The ids of the default kinds that Flyway creates (V1_2). Only the sample data and the tests refer to them; the
 * application itself never refers to a kind by id or name (spec 4.6).
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public final class DefaultKinds {

    public static final UUID SONG = id(101);
    public static final UUID INSTRUMENTAL = id(103);
    public static final UUID OVERTURE = id(104);
    public static final UUID FINALE = id(107);
    public static final UUID LYRICS = id(201);
    public static final UUID MUSIC = id(202);
    public static final UUID ARRANGEMENT = id(203);
    public static final UUID CHOREOGRAPHY = id(204);
    public static final UUID SKETCH = id(401);
    public static final UUID MAKEUP = id(402);
    public static final UUID COSTUME = id(403);
    public static final UUID BACKDROP = id(411);
    public static final UUID STAGE = id(412);
    public static final UUID PROPS = id(413);
    public static final UUID OTHER_IMAGES = id(421);
    public static final UUID VENUE = id(501);
    public static final UUID PERFORMANCES = id(502);
    public static final UUID SETTING = id(503);

    private DefaultKinds() {
    }

    private static UUID id(final int n) {
        return UUID.fromString("01900000-0000-7000-8000-%012d".formatted(n));
    }
}

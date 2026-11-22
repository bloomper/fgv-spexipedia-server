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

package nu.fgv.spexipedia.server.revision;

import nu.fgv.spexipedia.server.util.StoredText;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.Locale;

/**
 * A revision's comment in the reader's language: what Spexipedia writes itself (an article created without a
 * comment, a version restored) is a {@link StoredText}; what a person wrote is shown as it is.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Controller
public class RevisionTextsGraphqlApi {

    private final MessageSource messages;

    public RevisionTextsGraphqlApi(final MessageSource messages) {
        this.messages = messages;
    }

    @SchemaMapping(typeName = "Revision", field = "comment")
    public @Nullable String comment(final RevisionDto revision, final Locale locale) {
        return revision.comment() == null ? null : StoredText.decode(revision.comment()).in(messages, locale);
    }
}

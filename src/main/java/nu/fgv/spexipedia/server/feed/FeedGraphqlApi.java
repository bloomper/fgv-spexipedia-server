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

package nu.fgv.spexipedia.server.feed;

import nu.fgv.spexipedia.server.util.Ids;
import nu.fgv.spexipedia.server.util.StoredText;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The change feed for the other applications, and its consumers for admins.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Controller
public class FeedGraphqlApi {

    private final MessageSource messages;
    private final ChangeFeedService feed;
    private final SyncConsumerService consumers;

    public FeedGraphqlApi(final ChangeFeedService feed, final SyncConsumerService consumers, final MessageSource messages) {
        this.feed = feed;
        this.consumers = consumers;
        this.messages = messages;
    }

    /** Why the latest notification failed, in the reader's language. */
    @SchemaMapping(typeName = "SyncConsumer", field = "lastError")
    public @Nullable String lastError(final SyncConsumerService.SyncConsumerDto consumer, final Locale locale) {
        return consumer.lastError() == null ? null : StoredText.decode(consumer.lastError()).in(messages, locale);
    }

    @QueryMapping
    public BaseDataChangePageDto baseDataChanges(@Argument final @Nullable String after, @Argument final @Nullable Integer first) {
        final ChangeFeedService.Page page = feed.page(after, first == null ? ChangeFeedService.MAX_PAGE : first);

        return new BaseDataChangePageDto(page.entries(), page.cursor(), page.hasMore());
    }

    public record BaseDataChangePageDto(List<ChangeFeedEntry> changes, @Nullable String cursor, boolean hasMore) {
    }

    @SchemaMapping(typeName = "BaseDataChange")
    public String sequence(final ChangeFeedEntry entry) {
        return String.valueOf(entry.getSequence());
    }

    @SchemaMapping(typeName = "BaseDataChange")
    public String id(final ChangeFeedEntry entry) {
        return entry.getRecordId().toString();
    }

    @SchemaMapping(typeName = "BaseDataChange")
    public ChangeType change(final ChangeFeedEntry entry) {
        return entry.getChangeType();
    }

    @SchemaMapping(typeName = "BaseDataChange")
    public FeedRecords.@Nullable FeedSpexCategory spexCategory(final ChangeFeedEntry entry) {
        return entry.getKind() == nu.fgv.spexipedia.server.basedata.BaseDataKind.SPEX_CATEGORY ? (FeedRecords.FeedSpexCategory) feed.payload(entry) : null;
    }

    @SchemaMapping(typeName = "BaseDataChange")
    public FeedRecords.@Nullable FeedSpex spex(final ChangeFeedEntry entry) {
        return entry.getKind() == nu.fgv.spexipedia.server.basedata.BaseDataKind.SPEX ? (FeedRecords.FeedSpex) feed.payload(entry) : null;
    }

    @SchemaMapping(typeName = "BaseDataChange")
    public FeedRecords.@Nullable FeedTaskCategory taskCategory(final ChangeFeedEntry entry) {
        return entry.getKind() == nu.fgv.spexipedia.server.basedata.BaseDataKind.TASK_CATEGORY ? (FeedRecords.FeedTaskCategory) feed.payload(entry) : null;
    }

    @SchemaMapping(typeName = "BaseDataChange")
    public FeedRecords.@Nullable FeedTask task(final ChangeFeedEntry entry) {
        return entry.getKind() == nu.fgv.spexipedia.server.basedata.BaseDataKind.TASK ? (FeedRecords.FeedTask) feed.payload(entry) : null;
    }

    @SchemaMapping(typeName = "BaseDataChange")
    public FeedRecords.@Nullable FeedCharacter character(final ChangeFeedEntry entry) {
        return entry.getKind() == nu.fgv.spexipedia.server.basedata.BaseDataKind.CHARACTER ? (FeedRecords.FeedCharacter) feed.payload(entry) : null;
    }

    @SchemaMapping(typeName = "BaseDataChange")
    public FeedRecords.@Nullable FeedNumber number(final ChangeFeedEntry entry) {
        return entry.getKind() == nu.fgv.spexipedia.server.basedata.BaseDataKind.NUMBER ? (FeedRecords.FeedNumber) feed.payload(entry) : null;
    }

    @QueryMapping
    public List<SyncConsumerService.SyncConsumerDto> syncConsumers() {
        return consumers.list();
    }

    @MutationMapping
    public SyncConsumerService.SyncConsumerDto syncConsumerSave(@Argument final Map<String, Object> input) {
        return consumers.save(Ids.optional((String) input.get("id")), (String) input.get("name"), (String) input.get("clientId"),
                (String) input.get("notifyUrl"), Boolean.TRUE.equals(input.get("enabled")));
    }

    @MutationMapping
    public boolean syncConsumerDelete(@Argument final String id) {
        return consumers.delete(Ids.require(id));
    }
}

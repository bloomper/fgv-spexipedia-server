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

package nu.fgv.spexipedia.server.basedata;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * The base data a Flyway migration inserts (V1_5: FGV's spex, revivals, categories and tasks) has neither change feed
 * entries nor search documents. At start, and after the sample data's wipe, every spex category, task category, spex
 * and task without a change feed entry gets one, written as the services write it, and is indexed; categories before
 * what refers to them, originals before their revivals.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Slf4j
@Component
public class SeededBaseData {

    private final BaseDataService service;
    private final SpexCategoryRepository categoryRepository;
    private final SpexRepository spexRepository;
    private final TaskCategoryRepository taskCategoryRepository;
    private final TaskRepository taskRepository;
    private final JdbcClient jdbc;
    private final TransactionTemplate transactionTemplate;

    public SeededBaseData(final BaseDataService service, final SpexCategoryRepository categoryRepository, final SpexRepository spexRepository,
                          final TaskCategoryRepository taskCategoryRepository, final TaskRepository taskRepository, final JdbcClient jdbc,
                          final TransactionTemplate transactionTemplate) {
        this.service = service;
        this.categoryRepository = categoryRepository;
        this.spexRepository = spexRepository;
        this.taskCategoryRepository = taskCategoryRepository;
        this.taskRepository = taskRepository;
        this.jdbc = jdbc;
        this.transactionTemplate = transactionTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void onReady() {
        publish();
    }

    /**
     * Writes the missing change feed entries; returns how many.
     */
    public int publish() {
        final int published = Objects.requireNonNull(transactionTemplate.execute(_ -> {
            int count = 0;
            Set<String> fed = fed(BaseDataKind.SPEX_CATEGORY);

            for (final SpexCategory category : categoryRepository.findAll(Sort.by("id"))) {
                if (!fed.contains(category.getUuid().toString())) {
                    service.changed(category);
                    count++;
                }
            }
            fed = fed(BaseDataKind.TASK_CATEGORY);
            for (final TaskCategory category : taskCategoryRepository.findAll(Sort.by("id"))) {
                if (!fed.contains(category.getUuid().toString())) {
                    service.changed(category);
                    count++;
                }
            }
            fed = fed(BaseDataKind.SPEX);
            for (final Spex spex : spexRepository.findAll().stream()
                    .sorted(Comparator.comparing((Spex spex) -> spex.getParent() != null).thenComparing(Spex::getId)).toList()) {
                if (!fed.contains(spex.getUuid().toString())) {
                    service.changed(spex);
                    count++;
                }
            }
            fed = fed(BaseDataKind.TASK);
            for (final Task task : taskRepository.findAll(Sort.by("id"))) {
                if (!fed.contains(task.getUuid().toString())) {
                    service.changed(task);
                    count++;
                }
            }
            return count;
        }));

        if (published > 0) {
            log.info("Published {} seeded base data records to the change feed", published);
        }
        return published;
    }

    private Set<String> fed(final BaseDataKind kind) {
        return new HashSet<>(jdbc.sql("SELECT DISTINCT record_id FROM change_feed_entry WHERE kind = :kind")
                .param("kind", kind.name()).query(String.class).list());
    }
}

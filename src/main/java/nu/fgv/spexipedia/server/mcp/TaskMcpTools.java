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

package nu.fgv.spexipedia.server.mcp;

import nu.fgv.spexipedia.server.basedata.BaseDataDtos.TaskCategoryDto;
import nu.fgv.spexipedia.server.basedata.BaseDataDtos.TaskDto;
import nu.fgv.spexipedia.server.basedata.BaseDataService;
import nu.fgv.spexipedia.server.basedata.Task;
import nu.fgv.spexipedia.server.basedata.TaskCategory;
import nu.fgv.spexipedia.server.util.error.ResourceNotFoundException;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.util.StringUtils.hasText;

/**
 * The MCP tools for tasks and task categories. Base data is public, so anyone with access reads it, retired records
 * included when asked for.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Component
public class TaskMcpTools {

    private final BaseDataService baseData;
    private final McpToolSupport support;

    public TaskMcpTools(final BaseDataService baseData, final McpToolSupport support) {
        this.baseData = baseData;
        this.support = support;
    }

    @McpTool(name = "list_tasks",
            description = "List tasks (what a person can do in a spex, e.g. director or musician) by category and name, each with its category. Optionally narrowed to one task category.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public McpToolSupport.ListResult<McpTask> listTasks(
            @McpToolParam(required = false, description = "Only tasks of this task category") final @Nullable String categoryId,
            @McpToolParam(required = false, description = "Also retired tasks, defaults to false") final @Nullable Boolean includeRetired) {
        return support.call(() -> {
            final boolean retired = Boolean.TRUE.equals(includeRetired);
            final List<TaskDto> tasks = hasText(categoryId) ? baseData.tasksOfCategory(category(categoryId).key(), retired) : baseData.tasks(retired);

            return new McpToolSupport.ListResult<>(toMcp(tasks));
        });
    }

    @McpTool(name = "get_task",
            description = "Get a task, with its category.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public McpTask getTask(@McpToolParam(description = "Task id") final String id) {
        return support.call(() -> toMcp(List.of(baseData.task(id).orElseThrow(() -> new ResourceNotFoundException(Task.class, id)))).getFirst());
    }

    @McpTool(name = "list_task_categories",
            description = "List task categories by name: whether their tasks have a character and a vocal part (actorPresent), are shown as the board on spex pages (board), or are instruments (instruments).",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public McpToolSupport.ListResult<McpTaskCategory> listTaskCategories(
            @McpToolParam(required = false, description = "Also retired categories, defaults to false") final @Nullable Boolean includeRetired) {
        return support.call(() -> new McpToolSupport.ListResult<>(baseData.taskCategories(Boolean.TRUE.equals(includeRetired)).stream()
                .map(McpTaskCategory::of).toList()));
    }

    @McpTool(name = "get_task_category",
            description = "Get a task category, with its tasks (not retired) by name.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public McpTaskCategoryDetails getTaskCategory(@McpToolParam(description = "Task category id") final String id) {
        return support.call(() -> {
            final TaskCategoryDto category = category(id);

            return new McpTaskCategoryDetails(McpTaskCategory.of(category), baseData.tasksOfCategory(category.key(), false).stream()
                    .map(task -> new McpTaskRef(task.id(), task.name()))
                    .toList());
        });
    }

    private TaskCategoryDto category(final String id) {
        return baseData.taskCategory(id).orElseThrow(() -> new ResourceNotFoundException(TaskCategory.class, id));
    }

    private List<McpTask> toMcp(final List<TaskDto> tasks) {
        final Map<Long, TaskCategoryDto> categories = baseData.taskCategoriesByKeys(tasks.stream().map(TaskDto::categoryKey)
                .collect(Collectors.toSet()));

        return tasks.stream()
                .map(task -> {
                    final TaskCategoryDto category = categories.get(task.categoryKey());

                    return new McpTask(task.id(), task.name(), category == null ? null : new McpTaskCategoryRef(category.id(), category.name()),
                            task.retired());
                })
                .toList();
    }

    public record McpTask(UUID id, String name, @Nullable McpTaskCategoryRef category, boolean retired) {
    }

    public record McpTaskRef(UUID id, String name) {
    }

    public record McpTaskCategoryRef(UUID id, String name) {
    }

    public record McpTaskCategory(UUID id, String name, boolean actorPresent, boolean board, boolean instruments,
                                  boolean retired) {

        static McpTaskCategory of(final TaskCategoryDto category) {
            return new McpTaskCategory(category.id(), category.name(), category.actorPresent(), category.board(), category.instruments(),
                    category.retired());
        }
    }

    public record McpTaskCategoryDetails(McpTaskCategory category, List<McpTaskRef> tasks) {
    }
}

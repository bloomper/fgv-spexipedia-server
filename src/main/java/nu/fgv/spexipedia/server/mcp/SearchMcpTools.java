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

import nu.fgv.spexipedia.server.search.SearchService;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * The MCP tool for searching all of Spexipedia, as its search box does.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Component
public class SearchMcpTools {

    private final SearchService searchService;
    private final McpToolSupport support;

    public SearchMcpTools(final SearchService searchService, final McpToolSupport support) {
        this.searchService = searchService;
        this.support = support;
    }

    @McpTool(name = "search",
            description = "Search Spexipedia: spex, characters, figures, numbers, original songs, recordings and articles that match the words, among what you may see. People are never searched. Each hit has its kind, id, title, a snippet and its path in the web.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public McpToolSupport.ListResult<SearchService.Hit> search(
            @McpToolParam(description = "Words to search for") final String query,
            @McpToolParam(required = false, description = "1-50, default 20") final @Nullable Integer limit) {
        return support.call(() -> new McpToolSupport.ListResult<>(searchService.search(query, limit == null ? 20 : Math.clamp(limit, 1, 50))));
    }
}

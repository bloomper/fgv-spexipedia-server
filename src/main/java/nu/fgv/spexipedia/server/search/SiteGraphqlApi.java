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

package nu.fgv.spexipedia.server.search;

import nu.fgv.spexipedia.server.article.ArticleGraphqlApi;
import nu.fgv.spexipedia.server.basedata.BaseDataDtos.SpexDto;
import nu.fgv.spexipedia.server.basedata.BaseDataService;
import nu.fgv.spexipedia.server.page.SpexPageDto;
import nu.fgv.spexipedia.server.page.SpexPageService;
import nu.fgv.spexipedia.server.people.PeopleService;
import nu.fgv.spexipedia.server.people.PersonDto;
import nu.fgv.spexipedia.server.redirect.RedirectService;
import nu.fgv.spexipedia.server.revision.RevisionDto;
import nu.fgv.spexipedia.server.settings.SettingsService;
import org.jspecify.annotations.Nullable;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.time.Clock;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * GraphQL for the site as a whole: the start page, search, the sitemap, old MediaWiki titles, and picking spexare.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Controller
public class SiteGraphqlApi {

    private final SearchService searchService;
    private final SettingsService settingsService;
    private final BaseDataService baseData;
    private final SpexPageService pageService;
    private final ArticleGraphqlApi articleApi;
    private final RedirectService redirectService;
    private final PeopleService peopleService;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    public SiteGraphqlApi(final SearchService searchService, final SettingsService settingsService, final BaseDataService baseData,
                          final SpexPageService pageService,
                          final ArticleGraphqlApi articleApi, final RedirectService redirectService,
                          final PeopleService peopleService, final Clock clock) {
        this.searchService = searchService;
        this.settingsService = settingsService;
        this.baseData = baseData;
        this.pageService = pageService;
        this.articleApi = articleApi;
        this.redirectService = redirectService;
        this.peopleService = peopleService;
        this.clock = clock;
    }

    @QueryMapping
    public StartPageDto startPage(final Locale locale) {
        final Set<Long> published = pageService.publishedSpexKeys();
        final List<SpexDto> spex = baseData.spexAll(false).stream().filter(s -> published.contains(s.key())).toList();
        final SpexDto spexOfTheYear = spex.stream().filter(s -> !s.revival()).max(Comparator.comparing(SpexDto::year))
                .or(() -> spex.stream().max(Comparator.comparing(SpexDto::year)))
                .orElse(null);
        final Map<Integer, List<SpexDto>> decades = new TreeMap<>(spex.stream()
                .collect(Collectors.groupingBy(s -> Integer.parseInt(s.year()) / 10 * 10)));

        return new StartPageDto(settingsService.welcome(locale), spexOfTheYear, thisDay(),
                articleApi.latest(10),
                decades.entrySet().stream().map(entry -> new DecadeDto(entry.getKey(), entry.getValue())).toList());
    }

    private List<ThisDayDto> thisDay() {
        final LocalDate today = LocalDate.now(clock);
        final MonthDay day = MonthDay.from(today);
        final List<ThisDayDto> entries = new ArrayList<>();

        for (final SpexPageDto page : pageService.publishedWithPremiere()) {
            final LocalDate premiere = LocalDate.parse(Objects.requireNonNull(page.premiere()));

            if (MonthDay.from(premiere).equals(day) && premiere.isBefore(today)) {
                final SpexDto spex = baseData.spexByKey(page.spexKey());

                entries.add(new ThisDayDto(premiere.toString(), "Premiär för " + spex.label(), spex));
            }
        }
        entries.sort(Comparator.comparing(ThisDayDto::date).reversed());
        return entries;
    }

    @QueryMapping
    public List<SearchService.Hit> search(@Argument final String q, @Argument final int limit) {
        return searchService.search(q, limit);
    }

    @QueryMapping
    public List<SitemapEntryDto> sitemap() {
        return searchService.publicPaths().stream().map(path -> new SitemapEntryDto(path, null)).toList();
    }

    @QueryMapping
    public @Nullable String redirect(@Argument final String title) {
        return redirectService.lookup(title).orElse(null);
    }

    @QueryMapping
    public List<PersonDto> spexareSearch(@Argument final String q) {
        return peopleService.search(q);
    }

    public record StartPageDto(@Nullable String welcome, @Nullable SpexDto spexOfTheYear, List<ThisDayDto> thisDay,
                               List<RevisionDto> recentChanges, List<DecadeDto> decades) {
    }

    public record ThisDayDto(String date, String text, @Nullable SpexDto spex) {
    }

    public record DecadeDto(int decade, List<SpexDto> spex) {
    }

    public record SitemapEntryDto(String path, java.time.@Nullable Instant lastModifiedAt) {
    }
}

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

import nu.fgv.spexipedia.server.basedata.BaseDataDtos.SpexDto;
import nu.fgv.spexipedia.server.basedata.BaseDataService;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * GraphQL for spex packages (spec 5.14). EDITOR; the service checks.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Controller
public class PackageGraphqlApi {

    private final PackageService service;
    private final BaseDataService baseData;
    private final MessageSource messages;

    public PackageGraphqlApi(final PackageService service, final BaseDataService baseData, final MessageSource messages) {
        this.service = service;
        this.baseData = baseData;
        this.messages = messages;
    }

    @QueryMapping
    public List<SpexPackage> spexPackages() {
        return service.packages();
    }

    @QueryMapping
    public @Nullable SpexPackage spexPackage(@Argument final UUID id) {
        return service.spexPackage(id);
    }

    @MutationMapping
    public SpexPackage spexPackagePreview(@Argument final UUID id) {
        return service.preview(id);
    }

    @MutationMapping
    public SpexPackage spexPackageApply(@Argument final UUID id) {
        return service.apply(id);
    }

    @MutationMapping
    public @Nullable Void spexPackageDelete(@Argument final UUID id) {
        service.delete(id);
        return null;
    }

    @SchemaMapping(typeName = "SpexPackage", field = "id")
    public UUID id(final SpexPackage pkg) {
        return pkg.getUuid();
    }

    @SchemaMapping(typeName = "SpexPackage", field = "zipSize")
    public @Nullable Double zipSize(final SpexPackage pkg) {
        return pkg.getZipSize() == null ? null : pkg.getZipSize().doubleValue();
    }

    @SchemaMapping(typeName = "SpexPackage", field = "zipReceived")
    public double zipReceived(final SpexPackage pkg) {
        return pkg.getZipReceived();
    }

    @SchemaMapping(typeName = "SpexPackage", field = "spex")
    public @Nullable SpexDto spex(final SpexPackage pkg) {
        return pkg.getSpexId() == null ? null : baseData.spexByIds(List.of(pkg.getSpexId())).get(pkg.getSpexId());
    }

    /**
     * Progress or why it failed, in the reader's language.
     */
    @SchemaMapping(typeName = "SpexPackage", field = "message")
    public @Nullable String message(final SpexPackage pkg, final Locale locale) {
        return pkg.getMessage() == null ? null : PackageText.decode(pkg.getMessage()).in(messages, locale);
    }

    @SchemaMapping(typeName = "SpexPackageReport", field = "unmatched")
    public List<String> unmatched(final PackageReport report, final Locale locale) {
        return report.unmatched().stream().map(text -> text.in(messages, locale)).toList();
    }

    @SchemaMapping(typeName = "SpexPackageReport", field = "warnings")
    public List<String> warnings(final PackageReport report, final Locale locale) {
        return report.warnings().stream().map(text -> text.in(messages, locale)).toList();
    }

    @SchemaMapping(typeName = "SpexPackageApp", field = "message")
    public @Nullable String message(final PackageReport.AppReport app, final Locale locale) {
        return app.message() == null ? null : app.message().in(messages, locale);
    }

    @SchemaMapping(typeName = "SpexPackageItem", field = "message")
    public @Nullable String message(final PackageReport.Item item, final Locale locale) {
        return item.message() == null ? null : item.message().in(messages, locale);
    }

    @SchemaMapping(typeName = "SpexPackageItem", field = "warnings")
    public List<String> warnings(final PackageReport.Item item, final Locale locale) {
        return item.warnings().stream().map(text -> text.in(messages, locale)).toList();
    }

    @SchemaMapping(typeName = "SpexPackage", field = "preview")
    public @Nullable PackageReport preview(final SpexPackage pkg) {
        return pkg.getPreview() == null ? null : service.report(pkg.getPreview());
    }

    @SchemaMapping(typeName = "SpexPackage", field = "results")
    public @Nullable PackageReport results(final SpexPackage pkg) {
        return pkg.getResults() == null ? null : service.report(pkg.getResults());
    }
}

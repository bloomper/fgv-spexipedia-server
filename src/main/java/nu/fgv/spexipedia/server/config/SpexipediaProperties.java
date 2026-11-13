package nu.fgv.spexipedia.server.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Spexipedia's settings under {@code spexipedia.*}.
 *
 * @param timeZone the zone schedules run in and "today" is in
 * @param webUrl   the web's address, for links in the feed (posters, logos) and to pages
 * @author Anders Jacobsson
 * @since 1.0
 */
@ConfigurationProperties(prefix = "spexipedia")
public record SpexipediaProperties(String timeZone,
                                   String webUrl,
                                   Keycloak keycloak,
                                   Security security,
                                   Media media,
                                   Apps apps,
                                   Data data) {

    /**
     * @param serviceClientId     {@code fgv-spexipedia-service}, which calls the other applications
     * @param serviceClientSecret its secret
     * @param adminClientId       {@code fgv-admin}, only for the sample data import, which grants roles
     * @param adminClientSecret   its secret
     */
    public record Keycloak(String url, String realm, String serviceClientId, String serviceClientSecret,
                           String adminClientId, String adminClientSecret) {
    }

    /**
     * @param authorizedParties the clients ({@code azp}) whose tokens the API accepts: the web and the service accounts
     *                          of the applications that read the change feed or create revivals
     */
    public record Security(List<String> authorizedParties) {
    }

    /**
     * @param root          where images are kept: {@code images/<id>/} with the original and its variants
     * @param uploads       unfinished uploads
     * @param baseUrl       where {@code spexipedia-media} serves {@code root}, e.g. {@code https://spexipedia.fgv.nu/media}
     * @param maxUploadSize the largest image accepted
     * @param magick        ImageMagick's command, {@code magick} (7) or {@code convert} (6)
     */
    public record Media(Path root, Path uploads, String baseUrl, DataSize maxUploadSize, String magick) {
    }

    /**
     * The other applications: their GraphQL APIs on the shared network, and their webs' addresses for links.
     */
    public record Apps(App spexregister, App spexgram, App spexify, App spexflix, App archive, Duration cacheTtl,
                       Duration timeout) {
    }

    /**
     * @param graphqlUrl the API, e.g. {@code http://spexregister-api:8080/api/graphql}; blank when not deployed
     * @param webUrl     the web, for links; blank when not deployed
     */
    public record App(@Nullable String graphqlUrl, @Nullable String webUrl) {
    }

    /**
     * @param importSampleData   whether to replace everything with sample data at start
     * @param sampleDataSize     {@code small} or {@code large}
     * @param sampleUserPassword the sample users' password
     */
    public record Data(boolean importSampleData, String sampleDataSize, String sampleUserPassword) {
    }
}

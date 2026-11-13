package nu.fgv.spexipedia.server.image;

import nu.fgv.spexipedia.server.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.client.HttpClientErrorException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Image uploads (5.10 of the spec): authors only, images only, variants made at once.
 */
class ImageIntegrationTest extends AbstractIntegrationTest {

    private static byte[] png() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        ImageIO.write(new BufferedImage(1600, 900, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private Map<?, ?> upload(final String user, final byte[] content, final String name) {
        final MultipartBodyBuilder body = new MultipartBodyBuilder();

        body.part("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return name;
            }
        });
        return http().post().uri("/api/images?kind=POSTER")
                .header(HttpHeaders.AUTHORIZATION, token(user))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body.build())
                .retrieve()
                .body(Map.class);
    }

    @Test
    void anAuthorsUploadGetsVariantsNoWiderThanTheOriginal() throws Exception {
        final Map<?, ?> image = upload(AUTHOR, png(), "affisch.png");

        assertThat(image.get("width")).isEqualTo(1600);
        assertThat((String) image.get("pageUrl")).endsWith("/page.webp");
        for (final String variant : new String[]{"thumbnail.webp", "page.webp", "full.webp", "page.jpg"}) {
            assertThat(DATA.resolve("media/images/" + image.get("id") + "/" + variant)).exists();
        }

        final BufferedImage full = ImageIO.read(DATA.resolve("media/images/" + image.get("id") + "/full.jpg").toFile());

        assertThat(full.getWidth()).isEqualTo(1600);
        assertThat(Files.size(DATA.resolve("media/images/" + image.get("id") + "/thumbnail.jpg"))).isPositive();
    }

    @Test
    void membersMayNotUploadAndNonImagesAreRefused() {
        assertThatThrownBy(() -> upload(MEMBER, new byte[]{1, 2, 3}, "x.png"))
                .isInstanceOfSatisfying(HttpClientErrorException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
        assertThatThrownBy(() -> upload(AUTHOR, "inte en bild".getBytes(), "x.png"))
                .isInstanceOfSatisfying(HttpClientErrorException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
    }
}

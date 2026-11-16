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

package nu.fgv.spexipedia.server.image;

import nu.fgv.spexipedia.server.config.SpexipediaProperties;
import nu.fgv.spexipedia.server.util.error.BadRequestException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs the ImageMagick on the path, as CI and the image have it.
 */
class ImageMagickTest {

    @TempDir
    Path folder;

    @BeforeAll
    static void imageMagickIsInstalled() {
        final String path = System.getenv("PATH");

        assumeTrue(path != null && java.util.Arrays.stream(path.split(File.pathSeparator))
                .anyMatch(dir -> Files.isExecutable(Path.of(dir, "magick")) || Files.isExecutable(Path.of(dir, "convert"))));
    }

    private ImageMagick magick() {
        final SpexipediaProperties properties = mock(SpexipediaProperties.class);

        when(properties.media()).thenReturn(new SpexipediaProperties.Media(folder, folder, "", DataSize.ofMegabytes(50), "auto"));
        return new ImageMagick(properties);
    }

    private Path image(final String format, final int width, final int height) throws IOException {
        final Path file = folder.resolve("upload-" + format);

        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, file.toFile());
        return file;
    }

    @Test
    void readsTheFormatFromTheFirstBytes() throws IOException {
        assertThat(ImageMagick.sniff(image("png", 4, 3))).isEqualTo(ImageMagick.Format.PNG);
        assertThat(ImageMagick.sniff(image("jpeg", 4, 3))).isEqualTo(ImageMagick.Format.JPEG);

        final Path svg = Files.writeString(folder.resolve("picture.png"), "<svg xmlns=\"http://www.w3.org/2000/svg\"/>");

        assertThat(ImageMagick.sniff(svg)).isNull();
    }

    @Test
    void identifiesAcceptedImages() throws IOException {
        final ImageMagick.Info info = magick().identify(image("png", 40, 30));

        assertThat(info.contentType()).isEqualTo("image/png");
        assertThat(info.width()).isEqualTo(40);
        assertThat(info.height()).isEqualTo(30);
    }

    @Test
    void refusesOtherFormatsWhateverTheirName() throws IOException {
        final Path mvg = Files.writeString(folder.resolve("poster.jpg"),
                "push graphic-context\nviewbox 0 0 10 10\nimage over 0,0 0,0 \"text:/etc/passwd\"\npop graphic-context\n",
                StandardCharsets.UTF_8);

        assertThatThrownBy(() -> magick().identify(mvg)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void refusesAFileThatOnlyStartsLikeAnImage() throws IOException {
        final Path broken = folder.resolve("broken");

        Files.write(broken, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, '<', 's', 'v', 'g'});
        assertThatThrownBy(() -> magick().identify(broken)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void resizesToWebpAndJpeg() throws IOException {
        final ImageMagick magick = magick();
        final Path source = image("png", 400, 300);
        final Path webp = folder.resolve("page.webp");
        final Path jpeg = folder.resolve("page.jpg");

        magick.resize(source, 100, webp);
        magick.resize(source, 100, jpeg);

        assertThat(ImageMagick.sniff(webp)).isEqualTo(ImageMagick.Format.WEBP);
        assertThat(ImageMagick.sniff(jpeg)).isEqualTo(ImageMagick.Format.JPEG);
        assertThat(magick.identify(jpeg).width()).isEqualTo(100);
    }
}

package nu.fgv.spexipedia.server.util.data;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Random;

/**
 * Generated images for the sample data: coloured patterns with a caption, never real images (section 5.13 of the spec).
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
final class SampleImages {

    private SampleImages() {
    }

    /**
     * A PNG of the given size: a gradient in the hue, a pattern seeded by the seed, and the lines of text centred.
     */
    static byte[] png(final int width, final int height, final float hue, final long seed, final List<String> lines) {
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = image.createGraphics();
        final Random random = new Random(seed);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, Color.getHSBColor(hue, 0.55f, 0.35f), width, height, Color.getHSBColor((hue + 0.12f) % 1, 0.65f, 0.15f)));
        g.fillRect(0, 0, width, height);
        for (int i = 0; i < 14; i++) {
            g.setColor(Color.getHSBColor((hue + random.nextFloat() * 0.2f) % 1, 0.5f, 0.4f + random.nextFloat() * 0.4f));
            g.setStroke(new BasicStroke(2 + random.nextInt(8)));
            final int size = 40 + random.nextInt(Math.max(41, width / 2));

            if (i % 3 == 0) {
                g.drawOval(random.nextInt(width), random.nextInt(height), size, size);
            } else if (i % 3 == 1) {
                g.drawRect(random.nextInt(width), random.nextInt(height), size, size / 2);
            } else {
                g.drawLine(random.nextInt(width), random.nextInt(height), random.nextInt(width), random.nextInt(height));
            }
        }
        g.setColor(new Color(201, 165, 92));
        g.setStroke(new BasicStroke(Math.max(2, width / 160f)));
        g.drawRect(width / 24, height / 24, width - width / 12, height - height / 12);

        int fontSize = Math.max(14, width / 12);
        final int lineHeight = (int) (fontSize * 1.3);
        int y = height / 2 - (lines.size() - 1) * lineHeight / 2;

        for (final String line : lines) {
            Font font = new Font(Font.SERIF, Font.BOLD, fontSize);
            FontMetrics metrics = g.getFontMetrics(font);

            while (metrics.stringWidth(line) > width * 0.85 && font.getSize() > 10) {
                font = font.deriveFont((float) font.getSize() - 2);
                metrics = g.getFontMetrics(font);
            }
            g.setFont(font);
            g.setColor(new Color(0, 0, 0, 120));
            g.drawString(line, (width - metrics.stringWidth(line)) / 2 + 2, y + 2);
            g.setColor(new Color(242, 237, 230));
            g.drawString(line, (width - metrics.stringWidth(line)) / 2, y);
            y += lineHeight;
            fontSize = Math.max(12, fontSize * 2 / 3);
        }
        g.dispose();

        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            ImageIO.write(image, "png", out);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}

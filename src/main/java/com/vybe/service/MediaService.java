package com.vybe.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;

/**
 * Video processing helpers: duration probing and thumbnail generation.
 * Uses ffmpeg/ffprobe when installed; otherwise falls back to a generated artwork thumbnail.
 */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    private final String ffmpeg;
    private final String ffprobe;
    private volatile Boolean ffmpegAvailable;

    public MediaService(@Value("${vybe.ffmpeg.path}") String ffmpeg,
                        @Value("${vybe.ffprobe.path}") String ffprobe) {
        this.ffmpeg = ffmpeg;
        this.ffprobe = ffprobe;
    }

    public boolean isFfmpegAvailable() {
        if (ffmpegAvailable == null) {
            ffmpegAvailable = run(List.of(ffmpeg, "-version"), 10) != null;
            log.info("ffmpeg available: {}", ffmpegAvailable);
        }
        return ffmpegAvailable;
    }

    public OptionalInt probeDuration(Path video) {
        if (!isFfmpegAvailable()) return OptionalInt.empty();
        String out = run(List.of(ffprobe, "-v", "error", "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1", video.toString()), 30);
        if (out == null) return OptionalInt.empty();
        try {
            double d = Double.parseDouble(out.trim().split("\\s+")[0]);
            return OptionalInt.of((int) Math.round(d));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    /** Extracts one frame from the video into {@code out} (JPEG). */
    public boolean extractFrame(Path video, double atSeconds, Path out) {
        if (!isFfmpegAvailable()) return false;
        String at = String.format(Locale.US, "%.2f", Math.max(0, atSeconds));
        String res = run(List.of(ffmpeg, "-y", "-ss", at, "-i", video.toString(), "-frames:v", "1",
                "-vf", "scale=1280:-2", "-q:v", "3", out.toString()), 60);
        try {
            return res != null && Files.exists(out) && Files.size(out) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    /** Renders a branded gradient artwork with the video title. */
    public void generateArtwork(String title, long seed, Path out) {
        int w = 1280, h = 720;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            float hue = (Math.abs(seed * 37) % 360) / 360f;
            Color a = Color.getHSBColor(0.72f + hue * 0.08f, 0.75f, 0.55f);
            Color b = Color.getHSBColor(0.58f + hue * 0.06f, 0.85f, 0.45f);
            g.setPaint(new GradientPaint(0, 0, new Color(12, 10, 20), w, h, new Color(20, 18, 32)));
            g.fillRect(0, 0, w, h);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
            g.setPaint(new RadialGradientPaint(new Point(w / 4, h / 3), 520f, new float[]{0f, 1f},
                    new Color[]{a, new Color(0, 0, 0, 0)}));
            g.fill(new Ellipse2D.Double(-300, -300, 1100, 1100));
            g.setPaint(new RadialGradientPaint(new Point(w * 3 / 4, h * 2 / 3), 560f, new float[]{0f, 1f},
                    new Color[]{b, new Color(0, 0, 0, 0)}));
            g.fill(new Ellipse2D.Double(400, -100, 1300, 1300));
            g.setComposite(AlphaComposite.SrcOver);

            g.setColor(new Color(255, 255, 255, 40));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
            g.drawString("VYBE", 64, 92);

            g.setColor(Color.WHITE);
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, 68);
            g.setFont(font);
            List<String> lines = wrap(title == null ? "" : title, g.getFontMetrics(), w - 128);
            int lineH = g.getFontMetrics().getHeight();
            int y = h - 90 - (Math.min(lines.size(), 3) - 1) * lineH;
            for (int i = 0; i < Math.min(lines.size(), 3); i++) {
                String line = lines.get(i);
                if (i == 2 && lines.size() > 3) line = line + "…";
                g.drawString(line, 64, y + i * lineH);
            }
        } finally {
            g.dispose();
        }
        try {
            ImageIO.write(img, "jpg", out.toFile());
        } catch (IOException e) {
            log.warn("Cannot write artwork thumbnail: {}", e.getMessage());
        }
    }

    private List<String> wrap(String text, FontMetrics fm, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = cur.length() == 0 ? word : cur + " " + word;
            if (fm.stringWidth(candidate) > maxWidth && cur.length() > 0) {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(candidate);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    /** Runs a process, returns its stdout or null on failure/timeout. */
    private String run(List<String> cmd, int timeoutSeconds) {
        Process p = null;
        try {
            p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            Process proc = p;
            Thread reader = new Thread(() -> {
                try (InputStream in = proc.getInputStream()) {
                    in.transferTo(buf);
                } catch (IOException ignored) {
                    // process ended
                }
            });
            reader.setDaemon(true);
            reader.start();
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            reader.join(2000);
            return p.exitValue() == 0 ? buf.toString(StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (p != null) p.destroyForcibly();
            return null;
        }
    }
}

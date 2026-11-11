package com.vybe.service;

import com.vybe.entity.Chapter;
import com.vybe.entity.Video;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds chapters from timestamps in a description, e.g.
 * <pre>
 * 00:00 Вступление
 * 02:15 - Основная часть
 * 1:02:30 Итоги
 * </pre>
 */
public final class ChapterParser {

    private static final Pattern LINE = Pattern.compile(
            "^\\s*[\\[(]?((?:\\d{1,2}:)?\\d{1,2}:\\d{2})[\\])]?\\s*[-–—|:]?\\s*(.{1,100}?)\\s*$");

    private ChapterParser() {}

    public static List<Chapter> parse(String description, int durationSeconds, Video video) {
        List<Chapter> result = new ArrayList<>();
        if (description == null || description.isBlank()) return result;
        Set<Integer> seen = new HashSet<>();
        for (String line : description.split("\\R")) {
            Matcher m = LINE.matcher(line);
            if (!m.matches()) continue;
            int start = toSeconds(m.group(1));
            String title = m.group(2).trim();
            if (title.isEmpty() || !seen.add(start)) continue;
            if (durationSeconds > 0 && start >= durationSeconds) continue;
            Chapter c = new Chapter();
            c.setVideo(video);
            c.setStartSeconds(start);
            c.setTitle(title.length() > 100 ? title.substring(0, 100) : title);
            result.add(c);
        }
        result.sort(Comparator.comparingInt(Chapter::getStartSeconds));
        if (result.size() < 2) return new ArrayList<>();
        return result;
    }

    public static int toSeconds(String ts) {
        String[] p = ts.split(":");
        int s = 0;
        for (String part : p) s = s * 60 + Integer.parseInt(part);
        return s;
    }
}

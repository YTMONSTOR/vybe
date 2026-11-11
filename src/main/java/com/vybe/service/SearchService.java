package com.vybe.service;

import com.vybe.dto.ApiDtos.Suggestion;
import com.vybe.entity.Tag;
import com.vybe.entity.User;
import com.vybe.entity.Video;
import com.vybe.repository.TagRepository;
import com.vybe.repository.UserRepository;
import com.vybe.repository.VideoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Smart Search — matches title, description, tags and channel names, then ranks by relevance.
 * All queries are parameterised JPQL, user input never reaches SQL as text.
 */
@Service
public class SearchService {

    private final VideoRepository videos;
    private final TagRepository tags;
    private final UserRepository users;

    public SearchService(VideoRepository videos, TagRepository tags, UserRepository users) {
        this.videos = videos;
        this.tags = tags;
        this.users = users;
    }

    public record Result(Video video, double score) {}

    @Transactional(readOnly = true)
    public List<Video> search(String query, String sort, String duration, Long categoryId, int limit) {
        String q = normalize(query);
        if (q.isEmpty()) return List.of();
        List<String> terms = terms(q);
        Map<Long, Video> found = new LinkedHashMap<>();
        for (String term : terms) {
            for (Video v : videos.searchByTerm(like(term), PageRequest.of(0, 300))) found.putIfAbsent(v.getId(), v);
        }
        List<Result> ranked = new ArrayList<>();
        for (Video v : found.values()) {
            if (categoryId != null && (v.getCategory() == null || !v.getCategory().getId().equals(categoryId))) continue;
            if (!matchesDuration(v, duration)) continue;
            ranked.add(new Result(v, score(v, q, terms)));
        }
        Comparator<Result> cmp = switch (sort == null ? "" : sort) {
            case "date" -> Comparator.comparing((Result r) -> r.video().getCreatedAt()).reversed();
            case "views" -> Comparator.comparingLong((Result r) -> r.video().getViews()).reversed();
            default -> Comparator.comparingDouble(Result::score).reversed();
        };
        ranked.sort(cmp);
        return ranked.stream().limit(limit).map(Result::video).toList();
    }

    @Transactional(readOnly = true)
    public List<User> channels(String query, int limit) {
        String q = normalize(query);
        if (q.isEmpty()) return List.of();
        return users.searchChannels(like(q), PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<Suggestion> suggest(String query) {
        String q = normalize(query);
        if (q.length() < 1) return List.of();
        Set<String> seen = new HashSet<>();
        List<Suggestion> out = new ArrayList<>();
        String prefix = escape(q) + "%";
        for (String t : videos.suggestTitles(prefix, PageRequest.of(0, 5))) add(out, seen, t, "video");
        for (String t : videos.suggestTitles(like(q), PageRequest.of(0, 6))) add(out, seen, t, "video");
        for (String t : tags.suggest(prefix, PageRequest.of(0, 3))) add(out, seen, "#" + t, "tag");
        for (String n : users.suggestNames(like(q), PageRequest.of(0, 2))) add(out, seen, n, "channel");
        return out.size() > 8 ? out.subList(0, 8) : out;
    }

    private void add(List<Suggestion> out, Set<String> seen, String text, String type) {
        if (seen.add(text.toLowerCase(Locale.ROOT))) out.add(new Suggestion(text, type));
    }

    private double score(Video v, String phrase, List<String> terms) {
        String title = v.getTitle().toLowerCase(Locale.ROOT);
        String desc = v.getDescription() == null ? "" : v.getDescription().toLowerCase(Locale.ROOT);
        String author = (v.getAuthor().getDisplayName() + " " + v.getAuthor().getUsername()).toLowerCase(Locale.ROOT);
        double s = 0;
        if (title.contains(phrase)) s += 6;
        if (title.startsWith(phrase)) s += 3;
        for (String term : terms) {
            if (title.contains(term)) s += 3;
            if (desc.contains(term)) s += 1;
            if (author.contains(term)) s += 1.5;
            for (Tag t : v.getTags()) {
                if (t.getName().equals(term)) s += 2.5;
                else if (t.getName().contains(term)) s += 1.2;
            }
        }
        s += 0.3 * Math.log10(v.getViews() + 1);
        return s;
    }

    private boolean matchesDuration(Video v, String duration) {
        if (duration == null || duration.isBlank()) return true;
        int d = v.getDurationSeconds();
        return switch (duration) {
            case "short" -> d > 0 && d < 240;
            case "medium" -> d >= 240 && d <= 1200;
            case "long" -> d > 1200;
            default -> true;
        };
    }

    static String normalize(String q) {
        if (q == null) return "";
        String s = q.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (s.startsWith("#")) s = s.substring(1);
        return s.length() > 100 ? s.substring(0, 100) : s;
    }

    private static List<String> terms(String q) {
        List<String> t = new ArrayList<>();
        for (String part : q.split(" ")) {
            String p = part.replaceFirst("^#", "");
            if (p.length() >= 2 && !t.contains(p)) t.add(p);
            if (t.size() >= 6) break;
        }
        if (t.isEmpty()) t.add(q);
        return t;
    }

    /** Escapes LIKE wildcards in user input. */
    private static String like(String term) {
        return "%" + escape(term) + "%";
    }

    private static String escape(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

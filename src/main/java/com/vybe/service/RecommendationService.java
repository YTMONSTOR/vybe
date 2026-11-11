package com.vybe.service;

import com.vybe.dto.ScoredVideo;
import com.vybe.entity.*;
import com.vybe.repository.SubscriptionRepository;
import com.vybe.repository.VideoRepository;
import com.vybe.repository.WatchHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Smart Feed — a content based + social recommendation engine.
 * <p>
 * Each candidate gets a score built from: category affinity and tag affinity (learned from watch
 * history), subscriptions, author affinity, popularity, freshness, like ratio and a small daily
 * jitter for variety. Already watched videos are pushed down, and the final list is re-ranked so a
 * single author never dominates the feed.
 */
@Service
public class RecommendationService {

    private final VideoRepository videos;
    private final WatchHistoryRepository history;
    private final SubscriptionRepository subscriptions;

    public RecommendationService(VideoRepository videos, WatchHistoryRepository history,
                                 SubscriptionRepository subscriptions) {
        this.videos = videos;
        this.history = history;
        this.subscriptions = subscriptions;
    }

    @Transactional(readOnly = true)
    public List<ScoredVideo> smartFeed(User user, int offset, int limit) {
        List<Video> candidates = videos.findTop500ByVisibilityOrderByCreatedAtDesc(Visibility.PUBLIC);
        if (candidates.isEmpty()) return List.of();

        Map<Long, Double> catAffinity = new HashMap<>();
        Map<Long, Double> tagAffinity = new HashMap<>();
        Map<Long, Double> authorAffinity = new HashMap<>();
        Map<Long, String> catNames = new HashMap<>();
        Set<Long> watched = new HashSet<>();
        Set<Long> subscribed = new HashSet<>();

        if (user != null) {
            List<WatchHistory> recent = history.findTop100ByUserOrderByWatchedAtDesc(user);
            int i = 0;
            for (WatchHistory h : recent) {
                Video v = h.getVideo();
                watched.add(v.getId());
                // recent history weighs more; deep watches weigh more than quick skips
                double recency = Math.exp(-i / 30.0);
                double depth = 0.4 + 0.6 * (h.getProgressPercent() / 100.0);
                double w = recency * depth;
                if (v.getCategory() != null) {
                    catAffinity.merge(v.getCategory().getId(), w, Double::sum);
                    catNames.put(v.getCategory().getId(), v.getCategory().getName());
                }
                for (Tag t : v.getTags()) tagAffinity.merge(t.getId(), w, Double::sum);
                authorAffinity.merge(v.getAuthor().getId(), w, Double::sum);
                i++;
            }
            subscribed.addAll(subscriptions.channelIdsOf(user));
        }
        double maxCat = max(catAffinity), maxTag = max(tagAffinity), maxAuthor = max(authorAffinity);
        long maxViews = candidates.stream().mapToLong(Video::getViews).max().orElse(1);
        LocalDateTime now = LocalDateTime.now();
        int day = LocalDate.now().getDayOfYear();

        List<ScoredVideo> scored = new ArrayList<>();
        for (Video v : candidates) {
            double cat = v.getCategory() == null ? 0 : catAffinity.getOrDefault(v.getCategory().getId(), 0.0) / maxCat;
            double tag = 0;
            for (Tag t : v.getTags()) tag += tagAffinity.getOrDefault(t.getId(), 0.0);
            tag = Math.min(1.0, tag / maxTag);
            double author = authorAffinity.getOrDefault(v.getAuthor().getId(), 0.0) / maxAuthor;
            boolean sub = subscribed.contains(v.getAuthor().getId());
            double popularity = Math.log10(v.getViews() + 1) / Math.log10(maxViews + 10);
            double ageDays = Duration.between(v.getCreatedAt(), now).toHours() / 24.0;
            double freshness = Math.exp(-ageDays / 10.0);
            double quality = (v.getLikes() + 1.0) / (v.getLikes() + v.getDislikes() + 2.0);
            long seed = (v.getId() * 2654435761L) ^ ((user == null ? 0 : user.getId()) * 40503L) ^ day;
            double jitter = (Math.floorMod(seed, 1000)) / 1000.0 * 0.35;

            double score = 2.2 * cat + 1.6 * tag + 0.8 * author + (sub ? 1.6 : 0)
                    + 1.0 * popularity + 1.3 * freshness + 0.6 * quality + jitter;
            if (watched.contains(v.getId())) score -= 2.5;
            if (user != null && v.getAuthor().getId().equals(user.getId())) score -= 1.5;

            String reason;
            if (sub && freshness > 0.5) reason = "Новое в подписках";
            else if (sub) reason = "Из ваших подписок";
            else if (cat > 0.6 && v.getCategory() != null) reason = "Вы смотрите «" + catNames.getOrDefault(v.getCategory().getId(), v.getCategory().getName()) + "»";
            else if (tag > 0.5) reason = "Похоже на ваши интересы";
            else if (freshness > 0.8) reason = "Свежее";
            else if (popularity > 0.7) reason = "Популярное";
            else reason = "Для вас";
            scored.add(new ScoredVideo(v, reason, score));
        }
        scored.sort(Comparator.comparingDouble(ScoredVideo::score).reversed());
        List<ScoredVideo> diversified = diversify(scored);
        if (offset >= diversified.size()) return List.of();
        return diversified.subList(offset, Math.min(diversified.size(), offset + limit));
    }

    /** Avoid more than two videos of the same author in any window of five. */
    private List<ScoredVideo> diversify(List<ScoredVideo> list) {
        List<ScoredVideo> result = new ArrayList<>();
        LinkedList<ScoredVideo> pending = new LinkedList<>(list);
        while (!pending.isEmpty()) {
            Iterator<ScoredVideo> it = pending.iterator();
            ScoredVideo chosen = null;
            while (it.hasNext()) {
                ScoredVideo c = it.next();
                if (countAuthorInWindow(result, c.video().getAuthor().getId()) < 2) {
                    chosen = c;
                    it.remove();
                    break;
                }
            }
            if (chosen == null) chosen = pending.removeFirst();
            result.add(chosen);
        }
        return result;
    }

    private int countAuthorInWindow(List<ScoredVideo> result, Long authorId) {
        int n = 0;
        for (int i = Math.max(0, result.size() - 4); i < result.size(); i++) {
            if (result.get(i).video().getAuthor().getId().equals(authorId)) n++;
        }
        return n;
    }

    /** Videos similar to the given one: same category, shared tags, same author, popularity. */
    @Transactional(readOnly = true)
    public List<Video> related(Video base, int limit) {
        Set<Long> baseTags = new HashSet<>();
        for (Tag t : base.getTags()) baseTags.add(t.getId());
        Long baseCat = base.getCategory() == null ? null : base.getCategory().getId();
        LocalDateTime now = LocalDateTime.now();
        List<ScoredVideo> scored = new ArrayList<>();
        for (Video v : videos.findTop500ByVisibilityOrderByCreatedAtDesc(Visibility.PUBLIC)) {
            if (v.getId().equals(base.getId())) continue;
            double s = 0;
            if (baseCat != null && v.getCategory() != null && baseCat.equals(v.getCategory().getId())) s += 3;
            for (Tag t : v.getTags()) if (baseTags.contains(t.getId())) s += 1.5;
            if (v.getAuthor().getId().equals(base.getAuthor().getId())) s += 2;
            s += 0.35 * Math.log10(v.getViews() + 1);
            s += 0.8 * Math.exp(-Duration.between(v.getCreatedAt(), now).toHours() / 24.0 / 20.0);
            scored.add(new ScoredVideo(v, null, s));
        }
        scored.sort(Comparator.comparingDouble(ScoredVideo::score).reversed());
        return scored.stream().limit(limit).map(ScoredVideo::video).toList();
    }

    private static double max(Map<Long, Double> m) {
        double mx = 0;
        for (double d : m.values()) mx = Math.max(mx, d);
        return mx <= 0 ? 1 : mx;
    }
}

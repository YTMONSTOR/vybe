package com.vybe.service;

import com.vybe.dto.ApiDtos.Series;
import com.vybe.entity.ReportStatus;
import com.vybe.entity.Subscription;
import com.vybe.entity.User;
import com.vybe.entity.Video;
import com.vybe.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Watch Statistics for creators and platform statistics for admins. */
@Service
public class StatsService {

    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("dd.MM");

    private final VideoRepository videos;
    private final ViewEventRepository viewEvents;
    private final SubscriptionRepository subscriptions;
    private final UserRepository users;
    private final CommentRepository comments;
    private final ReportRepository reports;

    public StatsService(VideoRepository videos, ViewEventRepository viewEvents, SubscriptionRepository subscriptions,
                        UserRepository users, CommentRepository comments, ReportRepository reports) {
        this.videos = videos;
        this.viewEvents = viewEvents;
        this.subscriptions = subscriptions;
        this.users = users;
        this.comments = comments;
        this.reports = reports;
    }

    public record CreatorTotals(long videos, long views, long likes, long comments, long subscribers,
                                long views30, long viewsPrev30, long subs30) {
        public int viewsTrend() {
            if (viewsPrev30 == 0) return views30 > 0 ? 100 : 0;
            return (int) Math.round((views30 - viewsPrev30) * 100.0 / viewsPrev30);
        }
    }

    @Transactional(readOnly = true)
    public CreatorTotals creatorTotals(User creator) {
        LocalDateTime now = LocalDateTime.now();
        List<LocalDateTime> views60 = viewEvents.viewTimesForAuthor(creator, now.minusDays(60));
        long last30 = views60.stream().filter(t -> t.isAfter(now.minusDays(30))).count();
        long subs30 = subscriptions.findByChannelAndCreatedAtAfter(creator, now.minusDays(30)).size();
        return new CreatorTotals(videos.countByAuthor(creator), videos.sumViewsByAuthor(creator),
                videos.sumLikesByAuthor(creator), videos.sumCommentsByAuthor(creator),
                subscriptions.countByChannel(creator), last30, views60.size() - last30, subs30);
    }

    @Transactional(readOnly = true)
    public Series creatorViews(User creator, int days) {
        return bucket(viewEvents.viewTimesForAuthor(creator, startOf(days)), days);
    }

    @Transactional(readOnly = true)
    public Series creatorSubscribers(User creator, int days) {
        List<LocalDateTime> times = subscriptions.findByChannelAndCreatedAtAfter(creator, startOf(days)).stream()
                .map(Subscription::getCreatedAt).toList();
        return bucket(times, days);
    }

    @Transactional(readOnly = true)
    public Series videoViews(Video video, int days) {
        return bucket(viewEvents.viewTimesForVideo(video, startOf(days)), days);
    }

    public record PlatformTotals(long users, long videos, long views, long comments, long openReports,
                                 long newUsers7, long newVideos7, long views7, long storageBytes) {}

    @Transactional(readOnly = true)
    public PlatformTotals platformTotals() {
        LocalDateTime week = LocalDateTime.now().minusDays(7);
        return new PlatformTotals(users.count(), videos.count(), videos.sumAllViews(), comments.count(),
                reports.countByStatus(ReportStatus.OPEN), users.countByCreatedAtAfter(week),
                videos.countByCreatedAtAfter(week), viewEvents.countByCreatedAtAfter(week), videos.sumStorage());
    }

    @Transactional(readOnly = true)
    public Series platformViews(int days) {
        return bucket(viewEvents.viewTimesSince(startOf(days)), days);
    }

    private LocalDateTime startOf(int days) {
        return LocalDate.now().minusDays(days - 1L).atStartOfDay();
    }

    private Series bucket(Collection<LocalDateTime> times, int days) {
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        long[] counts = new long[days];
        for (LocalDateTime t : times) {
            int idx = (int) (t.toLocalDate().toEpochDay() - start.toEpochDay());
            if (idx >= 0 && idx < days) counts[idx]++;
        }
        List<String> labels = new ArrayList<>(days);
        List<Long> values = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            labels.add(start.plusDays(i).format(LABEL));
            values.add(counts[i]);
        }
        return new Series(labels, values);
    }
}

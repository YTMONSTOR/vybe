package com.vybe.ws;

import com.vybe.entity.User;
import com.vybe.entity.Video;
import com.vybe.entity.Visibility;
import com.vybe.exception.NotFoundException;
import com.vybe.repository.VideoRepository;
import com.vybe.service.Fmt;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.WebSocketSession;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory Watch Together rooms. */
@Service
public class TogetherRoomService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final long EMPTY_ROOM_TTL_MS = 6 * 60 * 60 * 1000L;

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final VideoRepository videos;
    private final Fmt fmt;

    public TogetherRoomService(VideoRepository videos, Fmt fmt) {
        this.videos = videos;
        this.fmt = fmt;
    }

    public record VideoInfo(Long id, String title, String url, String thumbnail, String author, String duration) {}

    public record ChatLine(String name, String text, long at, int hue) {}

    public static class Room {
        final String code;
        final String hostName;
        volatile VideoInfo video;
        volatile boolean playing;
        volatile double time;
        volatile long updatedAt = System.currentTimeMillis();
        volatile long emptySince = System.currentTimeMillis();
        final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
        final Deque<ChatLine> chat = new ArrayDeque<>();

        Room(String code, String hostName, VideoInfo video) {
            this.code = code;
            this.hostName = hostName;
            this.video = video;
        }

        public String getCode() { return code; }
        public String getHostName() { return hostName; }
        public VideoInfo getVideo() { return video; }
        public int getMemberCount() { return sessions.size(); }

        public synchronized double currentTime() {
            if (!playing) return time;
            return time + (System.currentTimeMillis() - updatedAt) / 1000.0;
        }

        public synchronized void update(boolean playing, double time) {
            this.playing = playing;
            this.time = Math.max(0, time);
            this.updatedAt = System.currentTimeMillis();
        }

        public synchronized List<ChatLine> chatSnapshot() {
            return new ArrayList<>(chat);
        }

        public synchronized void addChat(ChatLine line) {
            chat.addLast(line);
            while (chat.size() > 60) chat.removeFirst();
        }
    }

    @Transactional(readOnly = true)
    public Room create(User host, Long videoId) {
        VideoInfo info = describe(videoId);
        String code;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            code = sb.toString();
        } while (rooms.containsKey(code));
        Room room = new Room(code, host.getDisplayName(), info);
        rooms.put(code, room);
        return room;
    }

    public Optional<Room> find(String code) {
        if (code == null) return Optional.empty();
        return Optional.ofNullable(rooms.get(code.toUpperCase(Locale.ROOT)));
    }

    @Transactional(readOnly = true)
    public VideoInfo describe(Long videoId) {
        Video v = videos.findById(videoId).orElseThrow(() -> new NotFoundException("Видео не найдено"));
        if (v.getVisibility() == Visibility.PRIVATE) throw new NotFoundException("Видео недоступно");
        return new VideoInfo(v.getId(), v.getTitle(), v.getVideoUrl(), v.getThumbnailUrl(),
                v.getAuthor().getDisplayName(), fmt.duration(v.getDurationSeconds()));
    }

    @Scheduled(fixedDelay = 15 * 60 * 1000L)
    public void cleanup() {
        long now = System.currentTimeMillis();
        rooms.values().removeIf(r -> r.sessions.isEmpty() && now - r.emptySince > EMPTY_ROOM_TTL_MS);
    }
}

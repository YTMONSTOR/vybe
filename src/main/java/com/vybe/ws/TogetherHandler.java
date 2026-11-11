package com.vybe.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vybe.entity.User;
import com.vybe.repository.UserRepository;
import com.vybe.ws.TogetherRoomService.ChatLine;
import com.vybe.ws.TogetherRoomService.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Watch Together protocol over a plain WebSocket.
 * Every member can play, pause, seek, change the video, chat and send reactions;
 * the server keeps the authoritative playback state and re-syncs newcomers.
 */
@Component
public class TogetherHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TogetherHandler.class);
    private static final Set<String> EMOJI = Set.of("🔥", "😂", "😮", "❤️", "👏", "💜");

    private final TogetherRoomService rooms;
    private final UserRepository users;
    private final ObjectMapper json;
    private final Map<String, Member> members = new ConcurrentHashMap<>();

    private record Member(String id, String name, int hue, Room room, WebSocketSession session) {}

    public TogetherHandler(TogetherRoomService rooms, UserRepository users, ObjectMapper json) {
        this.rooms = rooms;
        this.users = users;
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession raw) throws Exception {
        String code = UriComponentsBuilder.fromUri(Objects.requireNonNull(raw.getUri())).build()
                .getQueryParams().getFirst("room");
        Optional<Room> roomOpt = rooms.find(code);
        if (roomOpt.isEmpty()) {
            raw.close(new CloseStatus(4404, "Room not found"));
            return;
        }
        Room room = roomOpt.get();
        WebSocketSession session = new ConcurrentWebSocketSessionDecorator(raw, 5000, 256 * 1024);
        String name = "Гость " + (100 + new Random().nextInt(900));
        int hue = new Random().nextInt(360);
        Principal p = raw.getPrincipal();
        if (p != null) {
            Optional<User> u = users.findByUsernameIgnoreCase(p.getName());
            if (u.isPresent()) {
                name = u.get().getDisplayName();
                hue = u.get().getHue();
            }
        }
        Member m = new Member(raw.getId(), name, hue, room, session);
        members.put(raw.getId(), m);
        room.sessions.add(session);

        Map<String, Object> welcome = new LinkedHashMap<>();
        welcome.put("type", "welcome");
        welcome.put("you", name);
        welcome.put("host", room.hostName);
        welcome.put("video", room.video);
        welcome.put("playing", room.playing);
        welcome.put("time", room.currentTime());
        welcome.put("chat", room.chatSnapshot());
        send(session, welcome);
        broadcastMembers(room);
        broadcast(room, Map.of("type", "system", "text", name + " присоединился к просмотру"), null);
    }

    @Override
    protected void handleTextMessage(WebSocketSession raw, TextMessage message) throws Exception {
        Member m = members.get(raw.getId());
        if (m == null) return;
        if (message.getPayloadLength() > 8192) return;
        JsonNode node = json.readTree(message.getPayload());
        String type = node.path("type").asText("");
        Room room = m.room();
        switch (type) {
            case "state" -> {
                boolean playing = node.path("playing").asBoolean(false);
                double time = node.path("time").asDouble(0);
                room.update(playing, time);
                Map<String, Object> sync = new LinkedHashMap<>();
                sync.put("type", "sync");
                sync.put("playing", playing);
                sync.put("time", time);
                sync.put("by", m.name());
                sync.put("action", node.path("action").asText(""));
                broadcast(room, sync, m.session());
            }
            case "chat" -> {
                String text = node.path("text").asText("").trim();
                if (text.isEmpty()) return;
                if (text.length() > 500) text = text.substring(0, 500);
                ChatLine line = new ChatLine(m.name(), text, System.currentTimeMillis(), m.hue());
                room.addChat(line);
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("type", "chat");
                out.put("line", line);
                broadcast(room, out, null);
            }
            case "reaction" -> {
                String emoji = node.path("emoji").asText("");
                if (!EMOJI.contains(emoji)) return;
                broadcast(room, Map.of("type", "reaction", "emoji", emoji, "name", m.name()), null);
            }
            case "video" -> {
                long videoId = node.path("videoId").asLong(0);
                try {
                    room.video = rooms.describe(videoId);
                    room.update(false, 0);
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("type", "video");
                    out.put("video", room.video);
                    out.put("by", m.name());
                    broadcast(room, out, null);
                } catch (RuntimeException e) {
                    send(m.session(), Map.of("type", "error", "text", "Видео недоступно"));
                }
            }
            case "sync-request" -> {
                Map<String, Object> sync = new LinkedHashMap<>();
                sync.put("type", "sync");
                sync.put("playing", room.playing);
                sync.put("time", room.currentTime());
                sync.put("by", "");
                sync.put("action", "resync");
                send(m.session(), sync);
            }
            default -> log.debug("Unknown together message type {}", type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession raw, CloseStatus status) {
        Member m = members.remove(raw.getId());
        if (m == null) return;
        Room room = m.room();
        room.sessions.remove(m.session());
        if (room.sessions.isEmpty()) {
            room.emptySince = System.currentTimeMillis();
            room.update(false, room.currentTime());
        } else {
            broadcastMembers(room);
            broadcast(room, Map.of("type", "system", "text", m.name() + " вышел"), null);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Together transport error: {}", exception.getMessage());
    }

    private void broadcastMembers(Room room) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Member mem : members.values()) {
            if (mem.room() == room) list.add(Map.of("name", mem.name(), "hue", mem.hue()));
        }
        broadcast(room, Map.of("type", "members", "members", list), null);
    }

    private void broadcast(Room room, Object payload, WebSocketSession except) {
        String text;
        try {
            text = json.writeValueAsString(payload);
        } catch (IOException e) {
            return;
        }
        for (WebSocketSession s : room.sessions) {
            if (s == except || !s.isOpen()) continue;
            try {
                s.sendMessage(new TextMessage(text));
            } catch (IOException | IllegalStateException e) {
                log.debug("Cannot deliver to {}: {}", s.getId(), e.getMessage());
            }
        }
    }

    private void send(WebSocketSession s, Object payload) {
        try {
            s.sendMessage(new TextMessage(json.writeValueAsString(payload)));
        } catch (IOException | IllegalStateException e) {
            log.debug("Cannot deliver: {}", e.getMessage());
        }
    }
}

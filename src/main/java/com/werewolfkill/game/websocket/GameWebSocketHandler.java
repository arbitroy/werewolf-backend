package com.werewolfkill.game.websocket;

import com.werewolfkill.game.model.Room;
import com.werewolfkill.game.repository.RoomRepository;
import com.werewolfkill.game.session.SessionManager;
import com.werewolfkill.game.session.SessionManager.PlayerInfo;
import com.werewolfkill.game.session.SessionManager.RoomSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.*;
import java.util.stream.Collectors;

@Controller
public class GameWebSocketHandler {

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/room/{roomId}/join")
    public void handleJoinRoom(
            @DestinationVariable String roomId,
            Map<String, String> message,
            StompHeaderAccessor headerAccessor) {

        String playerIdStr = message.get("playerId");
        String username = message.get("username");
        String webSocketSessionId = headerAccessor.getSessionId();

        if (playerIdStr == null || username == null) {
            sendError(webSocketSessionId, "playerId and username required");
            return;
        }

        UUID playerId = UUID.fromString(playerIdStr);
        UUID roomUuid = UUID.fromString(roomId);

        // Verify room exists in database
        Room room = roomRepository.findById(roomUuid).orElse(null);
        if (room == null) {
            sendError(webSocketSessionId, "Room does not exist");
            return;
        }

        // Get or create session
        RoomSession session = sessionManager.getOrCreateSession(roomUuid, room.getName());

        // Check max players
        if (session.getPlayers().size() >= room.getMaxPlayers()) {
            sendError(webSocketSessionId, "Room is full");
            return;
        }

        // Store in WebSocket session attributes for disconnect handling
        headerAccessor.getSessionAttributes().put("roomId", roomId);
        headerAccessor.getSessionAttributes().put("playerId", playerIdStr);
        headerAccessor.getSessionAttributes().put("username", username);

        // Add player to session (SessionManager handles host assignment)
        PlayerInfo player = sessionManager.addPlayer(roomUuid, webSocketSessionId, playerId, username);

        System.out.println("✅ Player " + username + " joined room " + roomId);

        // ✅ ALWAYS broadcast full room state after any change
        broadcastRoomState(roomUuid);
    }

    @MessageMapping("/room/{roomId}/leave")
    public void handleLeaveRoom(
            @DestinationVariable String roomId,
            StompHeaderAccessor headerAccessor) {

        String webSocketSessionId = headerAccessor.getSessionId();
        UUID roomUuid = UUID.fromString(roomId);

        System.out.println("🚪 Player leaving room " + roomId);

        // Remove player (SessionManager handles host reassignment)
        sessionManager.removePlayer(roomUuid, webSocketSessionId);

        // ✅ Broadcast updated room state
        broadcastRoomState(roomUuid);

        // Clear session attributes
        headerAccessor.getSessionAttributes().clear();
    }

    @MessageMapping("/room/{roomId}/heartbeat")
    public void handleHeartbeat(
            @DestinationVariable String roomId,
            StompHeaderAccessor headerAccessor) {

        String webSocketSessionId = headerAccessor.getSessionId();
        UUID roomUuid = UUID.fromString(roomId);
        sessionManager.updateHeartbeat(roomUuid, webSocketSessionId);
    }

    /**
     * ✅ Handle unexpected disconnects (browser close, network issues, etc.)
     * This ensures host reassignment happens even without explicit leave
     */
    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        String roomIdStr = (String) accessor.getSessionAttributes().get("roomId");
        String webSocketSessionId = accessor.getSessionId();
        String username = (String) accessor.getSessionAttributes().get("username");

        if (roomIdStr == null)
            return;

        UUID roomId = UUID.fromString(roomIdStr);

        System.out.println("🔌 WebSocket disconnect for " + username + " in room " + roomId);

        // Remove player (SessionManager handles host reassignment and broadcasts
        // HOST_CHANGED)
        sessionManager.removePlayer(roomId, webSocketSessionId);

        // ✅ Broadcast updated room state to all remaining players
        broadcastRoomState(roomId);
    }

    /**
     * ✅ Broadcasts complete room state to all participants
     * This is the single source of truth for room state
     */
    private void broadcastRoomState(UUID roomId) {
        Optional<RoomSession> sessionOpt = sessionManager.getSession(roomId);
        if (sessionOpt.isEmpty()) {
            System.out.println("⚠️ Cannot broadcast - session not found for room: " + roomId);
            return;
        }

        RoomSession session = sessionOpt.get();

        // Build complete player list with isHost flags
        List<Map<String, Object>> playerList = session.getPlayers().values().stream()
                .map(player -> {
                    Map<String, Object> playerData = new HashMap<>();
                    playerData.put("playerId", player.getPlayerId().toString());
                    playerData.put("username", player.getUsername());
                    // ✅ CRITICAL: Correctly identify host
                    playerData.put("isHost", player.getWebSocketSessionId().equals(session.getHostSessionId()));
                    playerData.put("status", player.getStatus() != null
                            ? player.getStatus().toString()
                            : "ALIVE");
                    playerData.put("role", player.getRole() != null
                            ? player.getRole().toString()
                            : null);
                    return playerData;
                })
                .collect(Collectors.toList());

        // Get host info for additional context
        PlayerInfo host = session.getPlayers().get(session.getHostSessionId());

        // Build comprehensive room state message
        Map<String, Object> message = new HashMap<>();
        message.put("type", "ROOM_STATE_UPDATE");
        message.put("roomId", roomId.toString());
        message.put("roomName", session.getRoomName());
        message.put("players", playerList);
        message.put("hostUsername", host != null ? host.getUsername() : null);
        message.put("playerCount", session.getPlayers().size());
        message.put("currentPhase", session.getCurrentPhase());
        message.put("timestamp", System.currentTimeMillis());

        // Broadcast to all room subscribers
        String destination = "/topic/room/" + roomId.toString();
        messagingTemplate.convertAndSend(destination, message);

        System.out.println("📢 Broadcasted ROOM_STATE_UPDATE: " + playerList.size() +
                " players, host: " + (host != null ? host.getUsername() : "none"));
    }

    private void sendError(String sessionId, String errorMessage) {
        Map<String, Object> error = new HashMap<>();
        error.put("type", "ERROR");
        error.put("message", errorMessage);
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/errors", error);

        System.out.println("❌ Sent error to session " + sessionId + ": " + errorMessage);
    }
}
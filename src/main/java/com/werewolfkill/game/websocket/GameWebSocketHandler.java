package com.werewolfkill.game.websocket;

import com.werewolfkill.game.model.Room;
import com.werewolfkill.game.model.User;
import com.werewolfkill.game.repository.RoomRepository;
import com.werewolfkill.game.repository.UserRepository;
import com.werewolfkill.game.service.WebSocketService;
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
    private UserRepository userRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private WebSocketService webSocketService;

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

        // Verify room exists
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

        // Store in WebSocket session attributes
        headerAccessor.getSessionAttributes().put("roomId", roomId);
        headerAccessor.getSessionAttributes().put("playerId", playerIdStr);
        headerAccessor.getSessionAttributes().put("username", username);

        // Add player to session
        PlayerInfo player = sessionManager.addPlayer(roomUuid, webSocketSessionId, playerId, username);

        // Broadcast updated state
        broadcastRoomState(roomUuid);
    }

    @MessageMapping("/room/{roomId}/leave")
    public void handleLeaveRoom(
            @DestinationVariable String roomId,
            StompHeaderAccessor headerAccessor) {

        String webSocketSessionId = headerAccessor.getSessionId();
        UUID roomUuid = UUID.fromString(roomId);

        sessionManager.removePlayer(roomUuid, webSocketSessionId);
        broadcastRoomState(roomUuid);
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

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        String roomIdStr = (String) accessor.getSessionAttributes().get("roomId");
        String webSocketSessionId = accessor.getSessionId();

        if (roomIdStr == null) return;

        UUID roomId = UUID.fromString(roomIdStr);
        sessionManager.removePlayer(roomId, webSocketSessionId);
        broadcastRoomState(roomId);
    }

    private void broadcastRoomState(UUID roomId) {
        Optional<RoomSession> sessionOpt = sessionManager.getSession(roomId);
        if (sessionOpt.isEmpty()) return;

        RoomSession session = sessionOpt.get();

        List<Map<String, Object>> playerList = session.getPlayers().values().stream()
            .map(player -> {
                Map<String, Object> playerData = new HashMap<>();
                playerData.put("playerId", player.getPlayerId().toString());
                playerData.put("username", player.getUsername());
                playerData.put("isHost", player.getWebSocketSessionId().equals(session.getHostSessionId()));
                playerData.put("status", player.getStatus() != null ? player.getStatus().toString() : "ALIVE");
                playerData.put("role", player.getRole() != null ? player.getRole().toString() : null);
                return playerData;
            })
            .collect(Collectors.toList());

        PlayerInfo host = session.getPlayers().get(session.getHostSessionId());

        Map<String, Object> message = new HashMap<>();
        message.put("type", "ROOM_STATE_UPDATE");
        message.put("roomId", roomId.toString());
        message.put("roomName", session.getRoomName());
        message.put("players", playerList);
        message.put("hostUsername", host != null ? host.getUsername() : null);
        message.put("playerCount", session.getPlayers().size());
        message.put("currentPhase", session.getCurrentPhase());
        message.put("timestamp", System.currentTimeMillis());

        String destination = "/topic/room/" + roomId.toString();
        messagingTemplate.convertAndSend(destination, message);
    }

    private void sendError(String sessionId, String errorMessage) {
        Map<String, Object> error = new HashMap<>();
        error.put("type", "ERROR");
        error.put("message", errorMessage);
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/errors", error);
    }
}
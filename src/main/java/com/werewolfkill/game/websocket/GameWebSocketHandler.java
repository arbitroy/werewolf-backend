package com.werewolfkill.game.websocket;

import com.werewolfkill.game.model.Room;
import com.werewolfkill.game.repository.RoomRepository;
import com.werewolfkill.game.repository.UserRepository;
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

        /**
         * Handle player joining room via WebSocket
         */
        @MessageMapping("/room/{roomId}/join")
        public void handleJoinRoom(
                        @DestinationVariable String roomId,
                        Map<String, String> message,
                        StompHeaderAccessor headerAccessor) {

                System.out.println("═══════════════════════════════════════");
                System.out.println("🎮 WEBSOCKET JOIN REQUEST");
                System.out.println("═══════════════════════════════════════");

                String playerIdStr = message.get("playerId");
                String username = message.get("username");
                String webSocketSessionId = headerAccessor.getSessionId();

                if (playerIdStr == null || username == null) {
                        sendError(webSocketSessionId, "playerId and username required");
                        return;
                }

                UUID playerId = UUID.fromString(playerIdStr);
                UUID roomUuid = UUID.fromString(roomId);

                // 1. Verify room exists in database
                Room room = roomRepository.findById(roomUuid).orElse(null);
                if (room == null) {
                        sendError(webSocketSessionId, "Room does not exist");
                        return;
                }

                // 2. Get or create session
                RoomSession session = sessionManager.getOrCreateSession(roomUuid, room.getName());

                // 3. Check max players
                if (session.getPlayers().size() >= room.getMaxPlayers()) {
                        sendError(webSocketSessionId, "Room is full");
                        return;
                }

                // 4. Store room context in WebSocket session attributes
                headerAccessor.getSessionAttributes().put("roomId", roomId);
                headerAccessor.getSessionAttributes().put("playerId", playerIdStr);
                headerAccessor.getSessionAttributes().put("username", username);

                // 5. Add player to session (in-memory)
                PlayerInfo player = sessionManager.addPlayer(roomUuid, webSocketSessionId, playerId, username);

                // 6. Broadcast updated room state to all players
                broadcastRoomState(roomUuid);

                System.out.println("✅ Player joined: " + username + " (Host: "
                                + player.getWebSocketSessionId().equals(session.getHostSessionId()) + ")");
                System.out.println("═══════════════════════════════════════");
        }

        /**
         * Handle player leaving room
         */
        @MessageMapping("/room/{roomId}/leave")
        public void handleLeaveRoom(
                        @DestinationVariable String roomId,
                        StompHeaderAccessor headerAccessor) {

                String webSocketSessionId = headerAccessor.getSessionId();
                String username = (String) headerAccessor.getSessionAttributes().get("username");
                UUID roomUuid = UUID.fromString(roomId);

                System.out.println("🚪 Player leaving: " + username);

                // Remove from session
                sessionManager.removePlayer(roomUuid, webSocketSessionId);

                // Broadcast updated state
                broadcastRoomState(roomUuid);

                // Clear session attributes
                headerAccessor.getSessionAttributes().clear();
        }

        /**
         * Handle heartbeat ping
         */
        @MessageMapping("/room/{roomId}/heartbeat")
        public void handleHeartbeat(
                        @DestinationVariable String roomId,
                        StompHeaderAccessor headerAccessor) {

                String webSocketSessionId = headerAccessor.getSessionId();
                UUID roomUuid = UUID.fromString(roomId);

                sessionManager.updateHeartbeat(roomUuid, webSocketSessionId);
                // No broadcast needed for heartbeat
        }

        /**
         * Handle WebSocket disconnection (automatic cleanup)
         */
        @EventListener
        public void handleDisconnect(SessionDisconnectEvent event) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

                String roomIdStr = (String) accessor.getSessionAttributes().get("roomId");
                String username = (String) accessor.getSessionAttributes().get("username");
                String webSocketSessionId = accessor.getSessionId();

                if (roomIdStr == null) {
                        return; // Player wasn't in a room
                }

                UUID roomId = UUID.fromString(roomIdStr);

                System.out.println("═══════════════════════════════════════");
                System.out.println("🔌 PLAYER DISCONNECTED");
                System.out.println("Player: " + username);
                System.out.println("Room: " + roomId);
                System.out.println("═══════════════════════════════════════");

                // Remove from session (automatic cleanup!)
                sessionManager.removePlayer(roomId, webSocketSessionId);

                // Broadcast updated state to remaining players
                broadcastRoomState(roomId);
        }

        /**
         * Broadcast current room state to all players
         */
        private void broadcastRoomState(UUID roomId) {
                Optional<RoomSession> sessionOpt = sessionManager.getSession(roomId);
                if (sessionOpt.isEmpty()) {
                        return; // Session was destroyed (no players left)
                }

                RoomSession session = sessionOpt.get();

                // Build player list
                List<Map<String, Object>> playerList = session.getPlayers().values().stream()
                                .map(player -> {
                                        Map<String, Object> playerData = new HashMap<>();
                                        playerData.put("playerId", player.getPlayerId().toString());
                                        playerData.put("username", player.getUsername());
                                        playerData.put("isHost", player.getWebSocketSessionId()
                                                        .equals(session.getHostSessionId()));
                                        playerData.put("status",
                                                        player.getStatus() != null ? player.getStatus().toString()
                                                                        : "ALIVE");
                                        playerData.put("role",
                                                        player.getRole() != null ? player.getRole().toString() : null);
                                        return playerData;
                                })
                                .collect(Collectors.toList());

                // Get host info
                PlayerInfo host = session.getPlayers().get(session.getHostSessionId());

                // Build message
                Map<String, Object> message = new HashMap<>();
                message.put("type", "ROOM_STATE_UPDATE");
                message.put("roomId", roomId.toString());
                message.put("roomName", session.getRoomName());
                message.put("players", playerList);
                message.put("hostUsername", host != null ? host.getUsername() : null);
                message.put("playerCount", session.getPlayers().size());
                message.put("currentPhase", session.getCurrentPhase());
                message.put("timestamp", System.currentTimeMillis());

                // Broadcast to topic
                String destination = "/topic/room/" + roomId.toString();
                messagingTemplate.convertAndSend(destination, message);

                System.out.println("📢 Broadcasted ROOM_STATE_UPDATE: " + playerList.size() + " players");
        }

        /**
         * Send error message to specific player
         */
        private void sendError(String sessionId, String errorMessage) {
                Map<String, Object> error = new HashMap<>();
                error.put("type", "ERROR");
                error.put("message", errorMessage);

                messagingTemplate.convertAndSendToUser(
                                sessionId,
                                "/queue/errors",
                                error);
        }
}
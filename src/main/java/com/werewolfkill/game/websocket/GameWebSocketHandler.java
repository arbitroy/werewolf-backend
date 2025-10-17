package com.werewolfkill.game.websocket;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.context.event.EventListener;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.stereotype.Controller;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.werewolfkill.game.model.PlayerRoom;
import com.werewolfkill.game.model.User;
import com.werewolfkill.game.repository.PlayerRoomRepository;
import com.werewolfkill.game.repository.UserRepository;
import com.werewolfkill.game.service.WebSocketService;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.List;
import java.util.Optional;

@Controller
public class GameWebSocketHandler {

        @Autowired
        private PlayerRoomRepository playerRoomRepository;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private WebSocketService webSocketService;

        @Autowired
        private SimpMessagingTemplate messagingTemplate;

        /**
         * Handle player joining room via WebSocket
         */
        @MessageMapping("/room/{roomId}/join")
        @Transactional
        public void handleJoinRoom(
                        @DestinationVariable String roomId,
                        Map<String, String> message,
                        StompHeaderAccessor headerAccessor) {

                System.out.println("═══════════════════════════════════════");
                System.out.println("🎮 WEBSOCKET JOIN REQUEST");
                System.out.println("═══════════════════════════════════════");

                String playerId = message.get("playerId");
                String username = message.get("username");

                if (playerId == null || username == null) {
                        throw new IllegalArgumentException("playerId and username required");
                }

                UUID playerUuid = UUID.fromString(playerId);
                UUID roomUuid = UUID.fromString(roomId);

                // Store in session for disconnect handling
                headerAccessor.getSessionAttributes().put("playerId", playerId);
                headerAccessor.getSessionAttributes().put("roomId", roomId);
                headerAccessor.getSessionAttributes().put("username", username);

                // Get EXISTING players BEFORE adding new one
                List<PlayerRoom> existingPlayers = playerRoomRepository.findByRoomId(roomUuid);
                System.out.println("🔍 Existing players in room: " + existingPlayers.size());

                // Get or create player in room
                PlayerRoom playerRoom = playerRoomRepository
                                .findByPlayerIdAndRoomId(playerUuid, roomUuid)
                                .orElseGet(() -> {
                                        // NEW PLAYER - Check if they should be host
                                        boolean shouldBeHost = existingPlayers.isEmpty();

                                        System.out.println("🆕 New player joining");
                                        System.out.println("🎯 Should be host: " + shouldBeHost);

                                        PlayerRoom newPlayerRoom = new PlayerRoom();
                                        newPlayerRoom.setPlayerId(playerUuid);
                                        newPlayerRoom.setRoomId(roomUuid);
                                        newPlayerRoom.setIsHost(shouldBeHost);

                                        return playerRoomRepository.save(newPlayerRoom);
                                });

                System.out.println("✅ Player in room: " + username + ", isHost=" + playerRoom.getIsHost());

                // Send full player list to the NEW joiner FIRST
                sendPlayerListToNewJoiner(roomUuid, playerUuid, headerAccessor);

                // Then broadcast the new player to EVERYONE (including self)
                webSocketService.broadcastPlayerJoined(
                                roomUuid,
                                playerUuid,
                                username);

                System.out.println("═══════════════════════════════════════");
        }

        /**
         * Send complete player list to newly joined player
         */
        private void sendPlayerListToNewJoiner(UUID roomId, UUID joinerId, StompHeaderAccessor headerAccessor) {
                List<PlayerRoom> playersInRoom = playerRoomRepository.findByRoomId(roomId);

                List<Map<String, Object>> playerList = playersInRoom.stream()
                                .map(pr -> {
                                        User user = userRepository.findById(pr.getPlayerId()).orElse(null);
                                        Map<String, Object> playerData = new HashMap<>();
                                        playerData.put("playerId", pr.getPlayerId().toString());
                                        playerData.put("username", user != null ? user.getUsername() : "Unknown");
                                        playerData.put("isHost", pr.getIsHost());
                                        return playerData;
                                })
                                .toList();

                Map<String, Object> response = new HashMap<>();
                response.put("type", "PLAYERS_LIST");
                response.put("players", playerList);
                response.put("targetPlayerId", joinerId.toString());

                System.out.println("📋 Broadcasting PLAYERS_LIST: " + playerList.size() + " players");

                String destination = "/topic/room/" + roomId.toString();
                messagingTemplate.convertAndSend(destination, (Object) response);
        }

        /**
         * Handle WebSocket disconnection
         */
        @EventListener
        @Transactional
        public void handleDisconnect(SessionDisconnectEvent event) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

                String playerId = (String) accessor.getSessionAttributes().get("playerId");
                String roomId = (String) accessor.getSessionAttributes().get("roomId");
                String username = (String) accessor.getSessionAttributes().get("username");

                if (playerId == null || roomId == null) {
                        return;
                }

                System.out.println("═══════════════════════════════════════");
                System.out.println("🔌 PLAYER DISCONNECTED");
                System.out.println("Player: " + username + " (" + playerId + ")");
                System.out.println("Room: " + roomId);

                UUID playerUuid = UUID.fromString(playerId);
                UUID roomUuid = UUID.fromString(roomId);

                Optional<PlayerRoom> playerRoomOpt = playerRoomRepository
                                .findByPlayerIdAndRoomId(playerUuid, roomUuid);

                if (playerRoomOpt.isEmpty()) {
                        return;
                }

                PlayerRoom playerRoom = playerRoomOpt.get();
                boolean wasHost = playerRoom.getIsHost();

                // Remove player from room
                playerRoomRepository.delete(playerRoom);
                System.out.println("✅ Player removed from database");

                // Broadcast player left
                webSocketService.broadcastPlayerLeft(roomUuid, playerUuid);

                // If host left, reassign to next player
                if (wasHost) {
                        reassignHost(roomUuid);
                }

                System.out.println("═══════════════════════════════════════");
        }

        /**
         * Reassign host to next player in room
         */
        @Transactional
        private void reassignHost(UUID roomId) {
                System.out.println("👑 HOST LEFT - Reassigning host for room: " + roomId);

                List<PlayerRoom> remainingPlayers = playerRoomRepository.findByRoomId(roomId);

                if (remainingPlayers.isEmpty()) {
                        System.out.println("❌ No players left in room");
                        return;
                }

                // Promote first remaining player to host
                PlayerRoom newHost = remainingPlayers.get(0);
                newHost.setIsHost(true);
                playerRoomRepository.save(newHost);

                User newHostUser = userRepository.findById(newHost.getPlayerId()).orElse(null);
                String newHostUsername = newHostUser != null ? newHostUser.getUsername() : "Unknown";

                System.out.println("👑 NEW HOST: " + newHostUsername);

                // Broadcast host change
                Map<String, Object> hostChangeMessage = new HashMap<>();
                hostChangeMessage.put("type", "HOST_CHANGED");
                hostChangeMessage.put("newHostId", newHost.getPlayerId().toString());
                hostChangeMessage.put("newHostUsername", newHostUsername);

                webSocketService.sendGameUpdate(roomId, hostChangeMessage);
        }
}
package main.java.com.werewolfkill.game.session;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import main.java.com.werewolfkill.game.session.SessionManager.PlayerInfo;
import main.java.com.werewolfkill.game.session.SessionManager.RoomSession;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class HeartbeatMonitor {

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Check for stale connections every 45 seconds
     */
    @Scheduled(fixedRate = 45000)
    public void checkStaleConnections() {
        Instant threshold = Instant.now().minus(60, ChronoUnit.SECONDS);

        sessionManager.getAllSessions().forEach((roomId, session) -> {
            List<String> staleSessionIds = session.getPlayers().values().stream()
                    .filter(player -> player.getLastHeartbeat().isBefore(threshold))
                    .map(SessionManager.PlayerInfo::getWebSocketSessionId)
                    .toList();

            staleSessionIds.forEach(sessionId -> {
                System.out.println("⚠️ Removing stale connection: " + sessionId);
                sessionManager.removePlayer(roomId, sessionId);

                // Broadcast updated state
                broadcastRoomState(roomId);
            });
        });
    }

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
}
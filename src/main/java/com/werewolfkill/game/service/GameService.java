package com.werewolfkill.game.service;

import com.werewolfkill.game.model.Room;
import com.werewolfkill.game.model.enums.Role;
import com.werewolfkill.game.repository.RoomRepository;
import com.werewolfkill.game.session.SessionManager;
import com.werewolfkill.game.session.SessionManager.PlayerInfo;
import com.werewolfkill.game.session.SessionManager.RoomSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class GameService {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private WebSocketService webSocketService;

    @Autowired
    private SessionManager sessionManager;

    /**
     * Start the game for a room
     * - Validates room exists and has enough players
     * - Assigns roles to players
     * - Updates session phase to "NIGHT"
     * - Broadcasts GAME_STARTED event
     */
    @Transactional
    public void startGame(UUID roomId) {
        System.out.println("🎮 Starting game for room: " + roomId);

        // Verify room exists in database
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found in database"));

        // ✅ FIX: Better error handling for missing session
        RoomSession session = sessionManager.getSession(roomId)
                .orElseThrow(() -> {
                    int playerCount = sessionManager.getPlayerCount(roomId);
                    String errorMsg = String.format(
                            "No active WebSocket session for room %s. " +
                                    "Room exists in database but no players have connected via WebSocket yet. " +
                                    "Current player count: %d. Please ensure all players have joined via WebSocket.",
                            roomId, playerCount);
                    System.out.println("❌ " + errorMsg);
                    return new RuntimeException(errorMsg);
                });

        // Check minimum player count
        int playerCount = session.getPlayers().size();
        System.out.println("   Player count: " + playerCount);

        if (playerCount < 3) {
            throw new RuntimeException("Need at least 3 players to start (currently: " + playerCount + ")");
        }

        // Rest of the method remains the same...
        assignRoles(roomId, session);
        session.setCurrentPhase("NIGHT");

        // Broadcast game start
        Map<String, Object> message = new HashMap<>();
        message.put("type", "GAME_STARTED");
        message.put("roomId", roomId.toString());
        message.put("phase", "NIGHT");
        message.put("dayNumber", 0);
        message.put("playerCount", playerCount);
        message.put("timestamp", System.currentTimeMillis());

        webSocketService.sendGameUpdate(roomId, message);
        broadcastRoomState(roomId, session);

        System.out.println("✅ Game started successfully for room: " + roomId);
    }

    /**
     * Assign roles to all players in the session
     * Distribution based on player count:
     * - 3-4 players: 1 Werewolf, 1 Seer, rest Villagers
     * - 5-6 players: 2 Werewolves, 1 Seer, 1 Doctor, rest Villagers
     * - 7-8 players: 2 Werewolves, 1 Seer, 1 Doctor, 1 Hunter, rest Villagers
     */
    private void assignRoles(UUID roomId, RoomSession session) {
        List<PlayerInfo> players = new ArrayList<>(session.getPlayers().values());
        int playerCount = players.size();

        System.out.println("🎭 Assigning roles to " + playerCount + " players");

        // Shuffle players for random role assignment
        Collections.shuffle(players);

        // Determine role distribution based on player count
        int werewolfCount = (playerCount >= 5) ? 2 : 1;

        List<Role> rolesToAssign = new ArrayList<>();

        // Add werewolves
        for (int i = 0; i < werewolfCount; i++) {
            rolesToAssign.add(Role.WEREWOLF);
        }

        // Add seer (always present)
        rolesToAssign.add(Role.SEER);

        // Add doctor for 5+ players
        if (playerCount >= 5) {
            rolesToAssign.add(Role.DOCTOR);
        }

        // Add hunter for 7+ players
        if (playerCount >= 7) {
            rolesToAssign.add(Role.HUNTER);
        }

        // Fill remaining with villagers
        while (rolesToAssign.size() < playerCount) {
            rolesToAssign.add(Role.VILLAGER);
        }

        // Assign roles to players
        for (int i = 0; i < players.size(); i++) {
            PlayerInfo player = players.get(i);
            Role role = rolesToAssign.get(i);
            player.setRole(role);

            System.out.println("   👤 " + player.getUsername() + " → " + role);

            // Send private role assignment to player
            sendRoleAssignment(roomId, player);
        }

        System.out.println("✅ Role assignment complete");
    }

    /**
     * Send private role assignment to a specific player
     */
    private void sendRoleAssignment(UUID roomId, PlayerInfo player) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "ROLE_ASSIGNED");
        message.put("playerId", player.getPlayerId().toString());
        message.put("role", player.getRole().toString());
        message.put("roleDescription", getRoleDescription(player.getRole()));
        message.put("timestamp", System.currentTimeMillis());

        // Send private message to this player only
        webSocketService.sendPrivateMessage(
                player.getWebSocketSessionId(),
                "/queue/role",
                message);

        System.out.println("   📨 Sent role " + player.getRole() + " to " + player.getUsername());
    }

    /**
     * Get role description for the player
     */
    private String getRoleDescription(Role role) {
        return switch (role) {
            case WEREWOLF -> "You are a Werewolf! Each night, you and your pack choose a villager to eliminate.";
            case SEER -> "You are the Seer! Each night, you can learn the true role of one player.";
            case DOCTOR -> "You are the Doctor! Each night, you can protect one player from being eliminated.";
            case HUNTER -> "You are the Hunter! If eliminated, you can take one player down with you.";
            case VILLAGER -> "You are a Villager! Work with others to find and eliminate the werewolves.";
            default -> "Unknown role";
        };
    }

    /**
     * Broadcast current room state to all participants
     */
    private void broadcastRoomState(UUID roomId, RoomSession session) {
        List<Map<String, Object>> playerList = session.getPlayers().values().stream()
                .map(player -> {
                    Map<String, Object> playerData = new HashMap<>();
                    playerData.put("playerId", player.getPlayerId().toString());
                    playerData.put("username", player.getUsername());
                    playerData.put("isHost", player.getWebSocketSessionId().equals(session.getHostSessionId()));
                    playerData.put("status", player.getStatus().toString());
                    // Don't send role in public broadcast!
                    return playerData;
                })
                .toList();

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
        webSocketService.sendGameUpdate(roomId, message);
    }

    /**
     * Handle vote during day phase
     */
    public void handleVote(UUID roomId, UUID voterId, UUID targetId) {
        RoomSession session = sessionManager.getSession(roomId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        // TODO: Implement voting logic
        System.out.println("🗳️ Vote: " + voterId + " → " + targetId);

        // Broadcast vote to all players
        Map<String, Object> message = new HashMap<>();
        message.put("type", "VOTE_CAST");
        message.put("voterId", voterId.toString());
        message.put("targetId", targetId.toString());

        webSocketService.sendGameUpdate(roomId, message);
    }

    /**
     * Handle night action (werewolf kill, seer check, doctor save)
     */
    public void handleNightAction(UUID roomId, UUID actorId, UUID targetId, String action) {
        RoomSession session = sessionManager.getSession(roomId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        // TODO: Implement night action logic
        System.out.println("🌙 Night action: " + action + " by " + actorId + " on " + targetId);

        // Process action and broadcast result
        Map<String, Object> message = new HashMap<>();
        message.put("type", "NIGHT_ACTION_RESULT");
        message.put("action", action);

        webSocketService.sendGameUpdate(roomId, message);
    }
}
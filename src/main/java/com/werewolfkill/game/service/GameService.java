package com.werewolfkill.game.service;

import com.werewolfkill.game.model.Room;
import com.werewolfkill.game.model.enums.PlayerStatus;
import com.werewolfkill.game.model.enums.Role;
import com.werewolfkill.game.repository.RoomRepository;
import com.werewolfkill.game.session.SessionManager;
import com.werewolfkill.game.session.SessionManager.PlayerInfo;
import com.werewolfkill.game.session.SessionManager.RoomSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.function.Function;

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
        session.setDayNumber(0); // ✅ Initialize day counter
        session.clearNightActions(); // ✅ Clear any previous actions

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

    /**
     * Transition from NIGHT phase to DAY phase
     * - Resolves night actions
     * - Announces deaths
     * - Checks win conditions
     */
    public void transitionToDay(UUID roomId) {
        System.out.println("🌅 Transitioning to DAY phase for room: " + roomId);

        RoomSession session = sessionManager.getSession(roomId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        // Resolve night actions
        resolveNightActions(roomId, session);

        // Increment day number
        session.incrementDay();
        session.setCurrentPhase("DAY");
        session.clearNightActions();

        // Check for game over
        String winner = checkWinCondition(session);
        if (winner != null) {
            endGame(roomId, session, winner);
            return;
        }

        // Broadcast day phase started
        Map<String, Object> message = new HashMap<>();
        message.put("type", "PHASE_CHANGE");
        message.put("phase", "DAY");
        message.put("dayNumber", session.getDayNumber());
        message.put("timestamp", System.currentTimeMillis());

        webSocketService.sendGameUpdate(roomId, message);
        broadcastRoomState(roomId, session);

        System.out.println("✅ DAY phase started - Day " + session.getDayNumber());
    }

    /**
     * Transition from DAY phase to VOTING phase
     */
    public void transitionToVoting(UUID roomId) {
        System.out.println("🗳️ Transitioning to VOTING phase for room: " + roomId);

        RoomSession session = sessionManager.getSession(roomId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        session.setCurrentPhase("VOTING");
        session.clearDayVotes();

        // Broadcast voting phase started
        Map<String, Object> message = new HashMap<>();
        message.put("type", "PHASE_CHANGE");
        message.put("phase", "VOTING");
        message.put("dayNumber", session.getDayNumber());
        message.put("timestamp", System.currentTimeMillis());

        webSocketService.sendGameUpdate(roomId, message);
        broadcastRoomState(roomId, session);

        System.out.println("✅ VOTING phase started");
    }

    /**
     * Transition from VOTING phase to NIGHT phase
     * - Counts votes
     * - Eliminates player with most votes
     * - Handles Hunter revenge if applicable
     * - Checks win conditions
     */
    public void transitionToNight(UUID roomId) {
        System.out.println("🌙 Transitioning to NIGHT phase for room: " + roomId);

        RoomSession session = sessionManager.getSession(roomId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        // Count votes and eliminate player
        UUID eliminatedPlayerId = resolveVoting(roomId, session);

        if (eliminatedPlayerId != null) {
            PlayerInfo eliminatedPlayer = session.getPlayers().values().stream()
                    .filter(p -> p.getPlayerId().equals(eliminatedPlayerId))
                    .findFirst()
                    .orElse(null);

            // Check for Hunter revenge
            if (eliminatedPlayer != null && eliminatedPlayer.getRole() == Role.HUNTER) {
                handleHunterRevenge(roomId, session, eliminatedPlayer);
            }
        }

        // Check for game over
        String winner = checkWinCondition(session);
        if (winner != null) {
            endGame(roomId, session, winner);
            return;
        }

        // Move to night phase
        session.setCurrentPhase("NIGHT");
        session.clearNightActions();

        // Broadcast night phase started
        Map<String, Object> message = new HashMap<>();
        message.put("type", "PHASE_CHANGE");
        message.put("phase", "NIGHT");
        message.put("dayNumber", session.getDayNumber());
        message.put("timestamp", System.currentTimeMillis());

        webSocketService.sendGameUpdate(roomId, message);
        broadcastRoomState(roomId, session);

        System.out.println("✅ NIGHT phase started");
    }

    /**
     * Resolve night actions (werewolf kill, doctor save)
     */
    private void resolveNightActions(UUID roomId, RoomSession session) {
        System.out.println("🌙 Resolving night actions...");

        UUID killTarget = session.getWerewolfKillTarget();
        UUID protectTarget = session.getDoctorProtectionTarget();

        // Determine if kill succeeds
        boolean playerDied = false;
        if (killTarget != null) {
            // Doctor saved the target
            if (killTarget.equals(protectTarget)) {
                System.out.println("💊 Doctor saved the target!");
                broadcastMessage(roomId, "NIGHT_RESULT",
                        "The doctor's protection saved a villager tonight!");
            } else {
                // Player dies
                System.out.println("☠️ Player " + killTarget + " was killed by werewolves");
                eliminatePlayer(roomId, session, killTarget, "WEREWOLF_KILL");
                playerDied = true;
            }
        }

        if (!playerDied) {
            broadcastMessage(roomId, "NIGHT_RESULT",
                    "The village survived the night. No one died.");
        }
    }

    /**
     * Resolve voting - eliminate player with most votes
     * Returns the eliminated player's ID, or null if tie/no votes
     */
    private UUID resolveVoting(UUID roomId, RoomSession session) {
        System.out.println("🗳️ Counting votes...");

        Map<UUID, UUID> votes = session.getDayVotes();

        if (votes.isEmpty()) {
            System.out.println("⚠️ No votes cast - no elimination");
            broadcastMessage(roomId, "VOTE_RESULT", "No one was eliminated today.");
            return null;
        }

        // Count votes for each player
        Map<UUID, Long> voteCounts = votes.values().stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        // Find player(s) with most votes
        long maxVotes = voteCounts.values().stream().max(Long::compare).orElse(0L);
        List<UUID> playersWithMaxVotes = voteCounts.entrySet().stream()
                .filter(e -> e.getValue() == maxVotes)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Handle tie
        if (playersWithMaxVotes.size() > 1) {
            System.out.println("⚖️ Vote tie - no elimination");
            broadcastMessage(roomId, "VOTE_RESULT",
                    "The vote was tied. No one was eliminated.");
            return null;
        }

        // Eliminate player with most votes
        UUID eliminatedId = playersWithMaxVotes.get(0);
        eliminatePlayer(roomId, session, eliminatedId, "VOTED_OUT");

        return eliminatedId;
    }

    /**
     * Eliminate a player and broadcast the death
     */
    private void eliminatePlayer(UUID roomId, RoomSession session, UUID playerId, String cause) {
        PlayerInfo player = session.getPlayers().values().stream()
                .filter(p -> p.getPlayerId().equals(playerId))
                .findFirst()
                .orElse(null);

        if (player == null) {
            System.out.println("⚠️ Cannot eliminate player - not found: " + playerId);
            return;
        }

        player.setStatus(PlayerStatus.DEAD);

        System.out.println("☠️ Player eliminated: " + player.getUsername() + " (" + player.getRole() + ")");

        // Broadcast player death
        Map<String, Object> message = new HashMap<>();
        message.put("type", "PLAYER_DIED");
        message.put("playerId", playerId.toString());
        message.put("username", player.getUsername());
        message.put("role", player.getRole().toString());
        message.put("cause", cause);
        message.put("timestamp", System.currentTimeMillis());

        webSocketService.sendGameUpdate(roomId, message);
    }

    /**
     * Handle Hunter revenge mechanism
     */
    private void handleHunterRevenge(UUID roomId, RoomSession session, PlayerInfo hunter) {
        System.out.println("🎯 Hunter revenge triggered: " + hunter.getUsername());

        // TODO: In a real implementation, you'd wait for hunter to choose target
        // For now, we'll just broadcast that hunter CAN take revenge
        // The actual revenge target selection would be handled by a separate API call

        Map<String, Object> message = new HashMap<>();
        message.put("type", "HUNTER_REVENGE");
        message.put("hunterId", hunter.getPlayerId().toString());
        message.put("hunterName", hunter.getUsername());
        message.put("message", "The Hunter has one last shot before dying!");
        message.put("timestamp", System.currentTimeMillis());

        webSocketService.sendGameUpdate(roomId, message);
    }

    /**
     * Check win conditions
     * Returns "WEREWOLVES" if werewolves win
     * Returns "VILLAGERS" if villagers win
     * Returns null if game continues
     */
    private String checkWinCondition(RoomSession session) {
        long aliveWerewolves = session.getPlayers().values().stream()
                .filter(p -> p.getStatus() == PlayerStatus.ALIVE)
                .filter(p -> p.getRole() == Role.WEREWOLF)
                .count();

        long aliveVillagers = session.getPlayers().values().stream()
                .filter(p -> p.getStatus() == PlayerStatus.ALIVE)
                .filter(p -> p.getRole() != Role.WEREWOLF)
                .count();

        System.out.println(
                "📊 Win condition check: " + aliveWerewolves + " werewolves vs " + aliveVillagers + " villagers");

        // Werewolves win if all villagers are dead OR werewolves >= villagers
        if (aliveVillagers == 0 || aliveWerewolves >= aliveVillagers) {
            return "WEREWOLVES";
        }

        // Villagers win if all werewolves are dead
        if (aliveWerewolves == 0) {
            return "VILLAGERS";
        }

        // Game continues
        return null;
    }

    /**
     * End the game and broadcast results
     */
    private void endGame(UUID roomId, RoomSession session, String winner) {
        System.out.println("🎮 Game Over! Winner: " + winner);

        session.setCurrentPhase("GAME_OVER");

        // Build final player results
        List<Map<String, Object>> finalResults = session.getPlayers().values().stream()
                .map(player -> {
                    Map<String, Object> playerResult = new HashMap<>();
                    playerResult.put("playerId", player.getPlayerId().toString());
                    playerResult.put("username", player.getUsername());
                    playerResult.put("role", player.getRole().toString());
                    playerResult.put("survived", player.getStatus() == PlayerStatus.ALIVE);
                    return playerResult;
                })
                .collect(Collectors.toList());

        // Broadcast game over
        Map<String, Object> message = new HashMap<>();
        message.put("type", "GAME_OVER");
        message.put("winner", winner);
        message.put("finalResults", finalResults);
        message.put("totalDays", session.getDayNumber());
        message.put("timestamp", System.currentTimeMillis());

        webSocketService.sendGameUpdate(roomId, message);

        System.out.println("✅ Game ended - " + winner + " win!");
    }

    /**
     * Helper method to broadcast simple messages
     */
    private void broadcastMessage(UUID roomId, String type, String messageText) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", type);
        message.put("message", messageText);
        message.put("timestamp", System.currentTimeMillis());

        webSocketService.sendGameUpdate(roomId, message);
    }
}
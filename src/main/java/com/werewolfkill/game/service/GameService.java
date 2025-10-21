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
        session.setCurrentPhase("STARTING");
        session.setDayNumber(0); // ✅ Initialize day counter
        session.clearNightActions(); // ✅ Clear any previous actions

        // Broadcast game start
        Map<String, Object> message = new HashMap<>();
        message.put("type", "GAME_STARTED");
        message.put("roomId", roomId.toString());
        message.put("phase", "STARTING");
        message.put("dayNumber", 0);
        message.put("playerCount", playerCount);
        message.put("timestamp", System.currentTimeMillis());

        webSocketService.sendGameUpdate(roomId, message);
        broadcastRoomState(roomId, session);

        schedulePhaseTransition(roomId, 5000);

        System.out.println("✅ Game started successfully for room: " + roomId);
    }

    private void schedulePhaseTransition(UUID roomId, long delayMs) {
    new Thread(() -> {
        try {
            Thread.sleep(delayMs);
            transitionToNightFromStarting(roomId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }).start();
}

private void transitionToNightFromStarting(UUID roomId) {
    System.out.println("🌙 Transitioning from STARTING to NIGHT phase");
    
    RoomSession session = sessionManager.getSession(roomId)
            .orElseThrow(() -> new RuntimeException("Session not found"));
    
    session.setCurrentPhase("NIGHT");
    
    Map<String, Object> message = new HashMap<>();
    message.put("type", "PHASE_CHANGE");
    message.put("phase", "NIGHT");
    message.put("dayNumber", session.getDayNumber());
    message.put("timestamp", System.currentTimeMillis());

    webSocketService.sendGameUpdate(roomId, message);
    broadcastRoomState(roomId, session);
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

        webSocketService.sendGameUpdate(roomId, message);

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
     * Handle vote during day/voting phase
     * Players can change their vote until voting ends
     */
    public void handleVote(UUID roomId, UUID voterId, UUID targetId) {
        System.out.println("🗳️ Vote: " + voterId + " → " + targetId);

        RoomSession session = sessionManager.getSession(roomId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        // Validate we're in VOTING phase
        if (!"VOTING".equals(session.getCurrentPhase())) {
            throw new RuntimeException("Voting can only happen during VOTING phase");
        }

        // Find the voter
        PlayerInfo voter = session.getPlayers().values().stream()
                .filter(p -> p.getPlayerId().equals(voterId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Voter not found"));

        // Validate voter is alive
        if (voter.getStatus() != PlayerStatus.ALIVE) {
            throw new RuntimeException("Dead players cannot vote");
        }

        // Find target
        PlayerInfo target = session.getPlayers().values().stream()
                .filter(p -> p.getPlayerId().equals(targetId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Target not found"));

        // Validate target is alive
        if (target.getStatus() != PlayerStatus.ALIVE) {
            throw new RuntimeException("Cannot vote for dead players");
        }

        // Validate not voting for self
        if (voterId.equals(targetId)) {
            throw new RuntimeException("Cannot vote for yourself");
        }

        // Store/update vote
        UUID previousVote = session.getDayVotes().put(voterId, targetId);

        if (previousVote != null) {
            System.out.println("🔄 " + voter.getUsername() + " changed vote from " +
                    previousVote + " to " + targetId);
        } else {
            System.out.println("✅ " + voter.getUsername() + " voted for " + target.getUsername());
        }

        // Broadcast vote (publicly visible to all players)
        Map<String, Object> message = new HashMap<>();
        message.put("type", "VOTE_CAST");
        message.put("voterId", voterId.toString());
        message.put("voterName", voter.getUsername());
        message.put("targetId", targetId.toString());
        message.put("targetName", target.getUsername());
        message.put("timestamp", System.currentTimeMillis());

        webSocketService.sendGameUpdate(roomId, message);

        // Check if all players have voted
        checkVotingComplete(roomId, session);
    }

    /**
     * Check if all alive players have cast their vote
     * If yes, automatically transition to NIGHT phase
     */
    private void checkVotingComplete(UUID roomId, RoomSession session) {
        long alivePlayers = session.getPlayers().values().stream()
                .filter(p -> p.getStatus() == PlayerStatus.ALIVE)
                .count();

        long playersWhoVoted = session.getDayVotes().size();

        System.out.println("🗳️ Voting progress: " + playersWhoVoted + "/" + alivePlayers);

        // Broadcast vote count update
        Map<String, Object> voteCountMessage = new HashMap<>();
        voteCountMessage.put("type", "VOTE_COUNT_UPDATE");
        voteCountMessage.put("votesReceived", playersWhoVoted);
        voteCountMessage.put("totalPlayers", alivePlayers);
        voteCountMessage.put("votingComplete", playersWhoVoted == alivePlayers);
        voteCountMessage.put("timestamp", System.currentTimeMillis());

        webSocketService.sendGameUpdate(roomId, voteCountMessage);

        // If all players voted, transition to night
        if (playersWhoVoted == alivePlayers) {
            System.out.println("✅ All players have voted - transitioning to night");
            session.setAllVotesComplete(true);

            // Small delay before transition (let players see final votes)
            try {
                Thread.sleep(2000); // 2 second delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Auto-transition to night phase
            transitionToNight(roomId);
        }
    }

    /**
     * Allow vote retraction (player removes their vote)
     */
    public void retractVote(UUID roomId, UUID voterId) {
        System.out.println("🗳️ Vote retracted by: " + voterId);

        RoomSession session = sessionManager.getSession(roomId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        // Validate we're in VOTING phase
        if (!"VOTING".equals(session.getCurrentPhase())) {
            throw new RuntimeException("Can only retract votes during VOTING phase");
        }

        // Find the voter
        PlayerInfo voter = session.getPlayers().values().stream()
                .filter(p -> p.getPlayerId().equals(voterId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Voter not found"));

        // Remove vote
        UUID removedTarget = session.getDayVotes().remove(voterId);

        if (removedTarget == null) {
            throw new RuntimeException("No vote to retract");
        }

        System.out.println("✅ " + voter.getUsername() + " retracted their vote");

        // Broadcast vote retraction
        Map<String, Object> message = new HashMap<>();
        message.put("type", "VOTE_RETRACTED");
        message.put("voterId", voterId.toString());
        message.put("voterName", voter.getUsername());
        message.put("timestamp", System.currentTimeMillis());

        webSocketService.sendGameUpdate(roomId, message);

        // Update vote count
        checkVotingComplete(roomId, session);
    }

    /**
     * Get current vote counts (for display purposes)
     */
    public Map<String, Object> getVoteStatus(UUID roomId) {
        RoomSession session = sessionManager.getSession(roomId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        // Count votes for each player
        Map<UUID, Long> voteCounts = session.getDayVotes().values().stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        // Build response with player names
        Map<String, Object> voteStatus = new HashMap<>();

        List<Map<String, Object>> voteCountList = voteCounts.entrySet().stream()
                .map(entry -> {
                    PlayerInfo player = session.getPlayers().values().stream()
                            .filter(p -> p.getPlayerId().equals(entry.getKey()))
                            .findFirst()
                            .orElse(null);

                    Map<String, Object> voteInfo = new HashMap<>();
                    voteInfo.put("playerId", entry.getKey().toString());
                    voteInfo.put("playerName", player != null ? player.getUsername() : "Unknown");
                    voteInfo.put("voteCount", entry.getValue());
                    return voteInfo;
                })
                .sorted((a, b) -> ((Long) b.get("voteCount")).compareTo((Long) a.get("voteCount")))
                .collect(Collectors.toList());

        long alivePlayers = session.getPlayers().values().stream()
                .filter(p -> p.getStatus() == PlayerStatus.ALIVE)
                .count();

        voteStatus.put("voteCounts", voteCountList);
        voteStatus.put("totalVotes", session.getDayVotes().size());
        voteStatus.put("totalPlayers", alivePlayers);
        voteStatus.put("votingComplete", session.getDayVotes().size() == alivePlayers);

        return voteStatus;
    }

    /**
     * Handle night action (werewolf kill, seer check, doctor save)
     * Validates action, stores it, and checks if all actions are complete
     */
    public void handleNightAction(UUID roomId, UUID actorId, UUID targetId, String action) {
        System.out.println("🌙 Night action: " + action + " by " + actorId + " on " + targetId);

        RoomSession session = sessionManager.getSession(roomId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        // Validate we're in NIGHT phase
        if (!"NIGHT".equals(session.getCurrentPhase())) {
            throw new RuntimeException("Actions can only be performed during NIGHT phase");
        }

        // Find the actor
        PlayerInfo actor = session.getPlayers().values().stream()
                .filter(p -> p.getPlayerId().equals(actorId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Actor not found"));

        // Validate actor is alive
        if (actor.getStatus() != PlayerStatus.ALIVE) {
            throw new RuntimeException("Dead players cannot perform actions");
        }

        // Find target
        PlayerInfo target = session.getPlayers().values().stream()
                .filter(p -> p.getPlayerId().equals(targetId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Target not found"));

        // Validate target is alive (except seer can check dead players for info)
        if (target.getStatus() != PlayerStatus.ALIVE && !action.equals("SEER_CHECK")) {
            throw new RuntimeException("Cannot target dead players");
        }

        // Process action based on role
        switch (action) {
            case "WEREWOLF_KILL":
                handleWerewolfKill(roomId, session, actor, targetId);
                break;
            case "SEER_CHECK":
                handleSeerCheck(roomId, session, actor, target);
                break;
            case "DOCTOR_PROTECT":
                handleDoctorProtect(roomId, session, actor, targetId);
                break;
            default:
                throw new RuntimeException("Unknown action: " + action);
        }

        // Check if all night actions are complete
        checkNightActionsComplete(roomId, session);
    }

    /**
     * Handle werewolf kill action
     * Multiple werewolves must agree on target
     */
    private void handleWerewolfKill(UUID roomId, RoomSession session, PlayerInfo actor, UUID targetId) {
        // Validate actor is werewolf
        if (actor.getRole() != Role.WEREWOLF) {
            throw new RuntimeException("Only werewolves can kill");
        }

        System.out.println("🐺 Werewolf " + actor.getUsername() + " targets " + targetId);

        // Store the werewolf's vote for kill target
        session.getNightActions().put(actor.getPlayerId(), targetId);
        session.getPlayersWhoActedTonight().add(actor.getPlayerId());

        // Check if all werewolves have voted
        long totalWerewolves = session.getPlayers().values().stream()
                .filter(p -> p.getStatus() == PlayerStatus.ALIVE)
                .filter(p -> p.getRole() == Role.WEREWOLF)
                .count();

        long werewolvesWhoVoted = session.getNightActions().entrySet().stream()
                .filter(e -> {
                    PlayerInfo p = session.getPlayers().values().stream()
                            .filter(player -> player.getPlayerId().equals(e.getKey()))
                            .findFirst()
                            .orElse(null);
                    return p != null && p.getRole() == Role.WEREWOLF;
                })
                .count();

        System.out.println("🐺 Werewolf votes: " + werewolvesWhoVoted + "/" + totalWerewolves);

        // If all werewolves voted, determine consensus
        if (werewolvesWhoVoted == totalWerewolves) {
            // Count votes for each target
            Map<UUID, Long> killVotes = session.getNightActions().entrySet().stream()
                    .filter(e -> {
                        PlayerInfo p = session.getPlayers().values().stream()
                                .filter(player -> player.getPlayerId().equals(e.getKey()))
                                .findFirst()
                                .orElse(null);
                        return p != null && p.getRole() == Role.WEREWOLF;
                    })
                    .map(Map.Entry::getValue)
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

            // Find most voted target
            UUID finalTarget = killVotes.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);

            session.setWerewolfKillTarget(finalTarget);
            System.out.println("🎯 Werewolves decided to kill: " + finalTarget);
        }

        // Broadcast action confirmation (only to werewolves)
        broadcastWerewolfAction(roomId, session, actor, targetId);
    }

    /**
     * Handle seer check action
     */
    private void handleSeerCheck(UUID roomId, RoomSession session, PlayerInfo actor, PlayerInfo target) {
        // Validate actor is seer
        if (actor.getRole() != Role.SEER) {
            throw new RuntimeException("Only the seer can investigate");
        }

        // Validate seer hasn't acted yet this night
        if (session.getPlayersWhoActedTonight().contains(actor.getPlayerId())) {
            throw new RuntimeException("You have already acted tonight");
        }

        System.out.println("🔮 Seer " + actor.getUsername() + " checks " + target.getUsername());

        session.setSeerCheckTarget(target.getPlayerId());
        session.getPlayersWhoActedTonight().add(actor.getPlayerId());

        // Send result privately to seer
        Map<String, Object> result = new HashMap<>();
        result.put("type", "SEER_RESULT");
        result.put("targetId", target.getPlayerId().toString());
        result.put("targetName", target.getUsername());
        result.put("role", target.getRole().toString());
        result.put("isWerewolf", target.getRole() == Role.WEREWOLF);
        result.put("timestamp", System.currentTimeMillis());

        // Send private message to seer only
        webSocketService.sendPrivateMessage(
                actor.getWebSocketSessionId(),
                "/queue/seer-result",
                result);

        System.out.println("✅ Seer result sent: " + target.getUsername() + " is " + target.getRole());
    }

    /**
     * Handle doctor protect action
     */
    private void handleDoctorProtect(UUID roomId, RoomSession session, PlayerInfo actor, UUID targetId) {
        // Validate actor is doctor
        if (actor.getRole() != Role.DOCTOR) {
            throw new RuntimeException("Only the doctor can protect");
        }

        // Validate doctor hasn't acted yet this night
        if (session.getPlayersWhoActedTonight().contains(actor.getPlayerId())) {
            throw new RuntimeException("You have already acted tonight");
        }

        System.out.println("💊 Doctor " + actor.getUsername() + " protects " + targetId);

        session.setDoctorProtectionTarget(targetId);
        session.getPlayersWhoActedTonight().add(actor.getPlayerId());

        // Broadcast action confirmation (only to doctor)
        Map<String, Object> message = new HashMap<>();
        message.put("type", "ACTION_CONFIRMED");
        message.put("action", "DOCTOR_PROTECT");
        message.put("targetId", targetId.toString());
        message.put("timestamp", System.currentTimeMillis());

        webSocketService.sendPrivateMessage(
                actor.getWebSocketSessionId(),
                "/queue/action-confirm",
                message);

        System.out.println("✅ Doctor protection confirmed");
    }

    /**
     * Check if all required night actions are complete
     * If yes, automatically transition to DAY phase
     */
    private void checkNightActionsComplete(UUID roomId, RoomSession session) {
        // Count how many special roles need to act
        long aliveWerewolves = session.getPlayers().values().stream()
                .filter(p -> p.getStatus() == PlayerStatus.ALIVE)
                .filter(p -> p.getRole() == Role.WEREWOLF)
                .count();

        boolean hasSeer = session.getPlayers().values().stream()
                .anyMatch(p -> p.getStatus() == PlayerStatus.ALIVE && p.getRole() == Role.SEER);

        boolean hasDoctor = session.getPlayers().values().stream()
                .anyMatch(p -> p.getStatus() == PlayerStatus.ALIVE && p.getRole() == Role.DOCTOR);

        // Check if werewolves have decided
        boolean werewolvesDone = session.getWerewolfKillTarget() != null || aliveWerewolves == 0;

        // Check if seer has acted (or doesn't exist)
        boolean seerDone = !hasSeer || session.getSeerCheckTarget() != null;

        // Check if doctor has acted (or doesn't exist)
        boolean doctorDone = !hasDoctor || session.getDoctorProtectionTarget() != null;

        System.out.println("🌙 Night actions check: Werewolves=" + werewolvesDone +
                ", Seer=" + seerDone + ", Doctor=" + doctorDone);

        // If all required actions are done, transition to day
        if (werewolvesDone && seerDone && doctorDone) {
            System.out.println("✅ All night actions complete - transitioning to day");
            session.setAllNightActionsComplete(true);

            // Auto-transition to day phase
            transitionToDay(roomId);
        }
    }

    /**
     * Broadcast werewolf action to all werewolves (they can see each other's votes)
     */
    private void broadcastWerewolfAction(UUID roomId, RoomSession session, PlayerInfo actor, UUID targetId) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "WEREWOLF_VOTE");
        message.put("voterId", actor.getPlayerId().toString());
        message.put("voterName", actor.getUsername());
        message.put("targetId", targetId.toString());
        message.put("timestamp", System.currentTimeMillis());

        // Send to all alive werewolves
        session.getPlayers().values().stream()
                .filter(p -> p.getStatus() == PlayerStatus.ALIVE)
                .filter(p -> p.getRole() == Role.WEREWOLF)
                .forEach(werewolf -> {
                    webSocketService.sendPrivateMessage(
                            werewolf.getWebSocketSessionId(),
                            "/queue/werewolf-vote",
                            message);
                });
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
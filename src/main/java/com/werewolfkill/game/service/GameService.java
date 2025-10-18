package com.werewolfkill.game.service;

import com.werewolfkill.game.model.GameState;
import com.werewolfkill.game.model.Room;
import com.werewolfkill.game.repository.GameStateRepository;
import com.werewolfkill.game.repository.RoomRepository;
import com.werewolfkill.game.session.SessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class GameService {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private GameStateRepository gameStateRepository;

    @Autowired
    private WebSocketService webSocketService;
    
    @Autowired
    private SessionManager sessionManager;

    @Transactional
    public void startGame(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        // ✅ Check player count from SessionManager
        int playerCount = sessionManager.getPlayerCount(roomId);
        if (playerCount < 3) {
            throw new RuntimeException("Need at least 3 players");
        }

        // Create game state
        GameState gameState = new GameState();
        gameState.setRoomId(roomId.toString());
        gameState.setPhase("NIGHT");
        gameState.setDayNumber(0);
        gameState.setIsActive(true);
        gameStateRepository.save(gameState);

        // ✅ Assign roles using SessionManager
        assignRoles(roomId);

        // Broadcast game start
        Map<String, Object> message = new HashMap<>();
        message.put("type", "GAME_STARTED");
        message.put("phase", "NIGHT");
        webSocketService.sendGameUpdate(roomId, message);
    }

    private void assignRoles(UUID roomId) {
        SessionManager.RoomSession session = sessionManager.getSession(roomId)
            .orElseThrow(() -> new RuntimeException("Session not found"));
        
        List<SessionManager.PlayerInfo> players = new ArrayList<>(session.getPlayers().values());
        
        // Simple role assignment logic
        // TODO: Implement proper role distribution
        for (int i = 0; i < players.size(); i++) {
            SessionManager.PlayerInfo player = players.get(i);
            // Assign roles...
        }
    }

    public void handleVote(UUID roomId, UUID voterId, UUID targetId) {
        // Implementation using SessionManager
    }

    public void handleNightAction(UUID roomId, UUID actorId, UUID targetId, String action) {
        // Implementation using SessionManager
    }
}
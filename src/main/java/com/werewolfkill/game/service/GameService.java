package com.werewolfkill.game.service;

import com.werewolfkill.game.model.*;
import com.werewolfkill.game.model.enums.*;
import com.werewolfkill.game.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class GameService {

    @Autowired
    private RoomRepository roomRepository;
    
    @Autowired
    private PlayerRoomRepository playerRoomRepository;
    
    @Autowired
    private GameStateRepository gameStateRepository;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void startGame(UUID roomId) {
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RuntimeException("Room not found"));
        
        if (room.getCurrentPlayers() < 3) {
            throw new RuntimeException("Need at least 3 players");
        }
        
        // Update room status
        room.setStatus(RoomStatus.IN_PROGRESS);
        roomRepository.save(room);
        
        // Assign roles
        assignRoles(roomId);
        
        // Create game state
        GameState gameState = new GameState();
        gameState.setRoomId(roomId);
        gameState.setPhase(GamePhase.NIGHT);
        gameState.setTimeRemaining(60);
        gameState.setLastEvent("Game started! Night falls...");
        gameStateRepository.save(gameState);
        
        // Broadcast to all players
        broadcastGameUpdate(roomId);
    }

    private void assignRoles(UUID roomId) {
        List<PlayerRoom> players = playerRoomRepository.findByRoomId(roomId);
        Collections.shuffle(players);
        
        int playerCount = players.size();
        int werewolfCount = playerCount / 3; // 1/3 are werewolves
        
        for (int i = 0; i < players.size(); i++) {
            PlayerRoom player = players.get(i);
            
            if (i < werewolfCount) {
                player.setRole(Role.WEREWOLF);
            } else if (i == werewolfCount) {
                player.setRole(Role.SEER);
            } else {
                player.setRole(Role.VILLAGER);
            }
            
            playerRoomRepository.save(player);
        }
    }

    @Transactional
    public void handleVote(UUID roomId, UUID voterId, UUID targetId) {
        GameState gameState = gameStateRepository.findByRoomId(roomId)
            .orElseThrow(() -> new RuntimeException("Game state not found"));
        
        if (gameState.getPhase() != GamePhase.VOTING) {
            throw new RuntimeException("Not voting phase");
        }
        
        // TODO: Store votes and count when timer ends
        // For now, just broadcast
        broadcastGameUpdate(roomId);
    }

    @Transactional
    public void handleNightAction(UUID roomId, UUID actorId, UUID targetId, String action) {
        GameState gameState = gameStateRepository.findByRoomId(roomId)
            .orElseThrow(() -> new RuntimeException("Game state not found"));
        
        if (gameState.getPhase() != GamePhase.NIGHT) {
            throw new RuntimeException("Not night phase");
        }
        
        PlayerRoom actor = playerRoomRepository.findByPlayerIdAndRoomId(actorId, roomId)
            .orElseThrow(() -> new RuntimeException("Player not found"));
        
        if (action.equals("KILL") && actor.getRole() == Role.WEREWOLF) {
            // Kill target
            PlayerRoom target = playerRoomRepository.findByPlayerIdAndRoomId(targetId, roomId)
                .orElseThrow(() -> new RuntimeException("Target not found"));
            target.setStatus(PlayerStatus.DEAD);
            playerRoomRepository.save(target);
            
            gameState.setLastEvent("A villager was killed during the night...");
        } else if (action.equals("INVESTIGATE") && actor.getRole() == Role.SEER) {
            // Reveal role to seer only
            PlayerRoom target = playerRoomRepository.findByPlayerIdAndRoomId(targetId, roomId)
                .orElseThrow(() -> new RuntimeException("Target not found"));
            
            // Send private message to seer
            Map<String, Object> seerInfo = new HashMap<>();
            seerInfo.put("targetId", targetId);
            seerInfo.put("role", target.getRole());
            messagingTemplate.convertAndSendToUser(
                actorId.toString(), 
                "/queue/seer-vision", 
                seerInfo
            );
        }
        
        gameStateRepository.save(gameState);
        broadcastGameUpdate(roomId);
    }

    @Transactional
    public void changePhase(UUID roomId, GamePhase newPhase) {
        GameState gameState = gameStateRepository.findByRoomId(roomId)
            .orElseThrow(() -> new RuntimeException("Game state not found"));
        
        gameState.setPhase(newPhase);
        gameState.setTimeRemaining(60);
        
        if (newPhase == GamePhase.DAY) {
            gameState.setLastEvent("The sun rises. Discuss and vote!");
        } else if (newPhase == GamePhase.NIGHT) {
            gameState.setLastEvent("Night falls. Special roles act now.");
        }
        
        gameStateRepository.save(gameState);
        
        // Check win condition
        checkWinCondition(roomId);
        
        broadcastGameUpdate(roomId);
    }

    private void checkWinCondition(UUID roomId) {
        List<PlayerRoom> players = playerRoomRepository.findByRoomId(roomId);
        
        long aliveWerewolves = players.stream()
            .filter(p -> p.getStatus() == PlayerStatus.ALIVE && p.getRole() == Role.WEREWOLF)
            .count();
        
        long aliveVillagers = players.stream()
            .filter(p -> p.getStatus() == PlayerStatus.ALIVE && p.getRole() != Role.WEREWOLF)
            .count();
        
        GameState gameState = gameStateRepository.findByRoomId(roomId).orElseThrow();
        
        if (aliveWerewolves == 0) {
            gameState.setPhase(GamePhase.GAME_OVER);
            gameState.setWinner("VILLAGERS");
            gameState.setLastEvent("All werewolves eliminated! Villagers win!");
        } else if (aliveWerewolves >= aliveVillagers) {
            gameState.setPhase(GamePhase.GAME_OVER);
            gameState.setWinner("WEREWOLVES");
            gameState.setLastEvent("Werewolves outnumber villagers! Werewolves win!");
        }
        
        gameStateRepository.save(gameState);
    }

    private void broadcastGameUpdate(UUID roomId) {
        GameState gameState = gameStateRepository.findByRoomId(roomId).orElseThrow();
        List<PlayerRoom> players = playerRoomRepository.findByRoomId(roomId);
        
        Map<String, Object> update = new HashMap<>();
        update.put("phase", gameState.getPhase());
        update.put("timeRemaining", gameState.getTimeRemaining());
        update.put("lastEvent", gameState.getLastEvent());
        update.put("players", players);
        update.put("winner", gameState.getWinner());
        
        messagingTemplate.convertAndSend("/topic/game/" + roomId, (Object) update);
    }
}
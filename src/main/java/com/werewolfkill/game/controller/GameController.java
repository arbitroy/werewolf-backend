package com.werewolfkill.game.controller;

import com.werewolfkill.game.dto.ApiResponse;
import com.werewolfkill.game.service.GameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/game")
public class GameController {

    @Autowired
    private GameService gameService;

    @PostMapping("/{roomId}/start")
    public ResponseEntity<ApiResponse<String>> startGame(@PathVariable UUID roomId) {
        try {
            gameService.startGame(roomId);
            return ResponseEntity.ok(ApiResponse.success("Game started", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{roomId}/vote")
    public ResponseEntity<ApiResponse<String>> vote(
            @PathVariable UUID roomId,
            @RequestBody Map<String, String> request) {
        try {
            UUID voterId = UUID.fromString(request.get("voterId"));
            UUID targetId = UUID.fromString(request.get("targetId"));

            gameService.handleVote(roomId, voterId, targetId);
            return ResponseEntity.ok(ApiResponse.success("Vote cast", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{roomId}/action")
    public ResponseEntity<ApiResponse<String>> nightAction(
            @PathVariable UUID roomId,
            @RequestBody Map<String, String> request) {
        try {
            UUID actorId = UUID.fromString(request.get("actorId"));
            UUID targetId = UUID.fromString(request.get("targetId"));
            String action = request.get("action");

            gameService.handleNightAction(roomId, actorId, targetId, action);
            return ResponseEntity.ok(ApiResponse.success("Action performed", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Retract a vote during voting phase
     */
    @DeleteMapping("/{roomId}/vote")
    public ResponseEntity<ApiResponse<String>> retractVote(
            @PathVariable UUID roomId,
            @RequestBody Map<String, String> request) {
        try {
            UUID voterId = UUID.fromString(request.get("voterId"));

            gameService.retractVote(roomId, voterId);
            return ResponseEntity.ok(ApiResponse.success("Vote retracted", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Get current vote status/counts
     */
    @GetMapping("/{roomId}/votes")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVoteStatus(
            @PathVariable UUID roomId) {
        try {
            Map<String, Object> voteStatus = gameService.getVoteStatus(roomId);
            return ResponseEntity.ok(ApiResponse.success("Vote status retrieved", voteStatus));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Manual phase transition endpoints (for testing/admin)
     * In production, these would be automatic or restricted to hosts
     */
    @PostMapping("/{roomId}/transition/day")
    public ResponseEntity<ApiResponse<String>> transitionToDay(@PathVariable UUID roomId) {
        try {
            gameService.transitionToDay(roomId);
            return ResponseEntity.ok(ApiResponse.success("Transitioned to DAY", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{roomId}/transition/voting")
    public ResponseEntity<ApiResponse<String>> transitionToVoting(@PathVariable UUID roomId) {
        try {
            gameService.transitionToVoting(roomId);
            return ResponseEntity.ok(ApiResponse.success("Transitioned to VOTING", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{roomId}/transition/night")
    public ResponseEntity<ApiResponse<String>> transitionToNight(@PathVariable UUID roomId) {
        try {
            gameService.transitionToNight(roomId);
            return ResponseEntity.ok(ApiResponse.success("Transitioned to NIGHT", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
}
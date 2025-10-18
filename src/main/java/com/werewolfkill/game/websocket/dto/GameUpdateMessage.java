package com.werewolfkill.game.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameUpdateMessage {
    private String type;
    private String phase;
    private Integer dayNumber;
    private Integer timeRemaining;
    private String lastEvent;
    private String winner;
    private List<Map<String, Object>> players;  // ✅ Changed from List<PlayerRoom>
}
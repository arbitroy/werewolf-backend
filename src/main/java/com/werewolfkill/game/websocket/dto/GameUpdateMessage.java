package com.werewolfkill.game.websocket.dto;

import com.werewolfkill.game.model.PlayerRoom;
import com.werewolfkill.game.model.enums.GamePhase;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameUpdateMessage {
    private UUID roomId;
    private GamePhase phase;
    private Integer timeRemaining;
    private String lastEvent;
    private List<PlayerRoom> players;
    private String winner;
}
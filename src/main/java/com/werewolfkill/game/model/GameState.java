package com.werewolfkill.game.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameState {
    private String roomId;
    private String phase;
    private Integer dayNumber = 0;
    private Boolean isActive = false;
}
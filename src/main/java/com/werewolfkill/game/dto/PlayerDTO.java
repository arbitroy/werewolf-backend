package com.werewolfkill.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerDTO {
    private String playerId;
    private String username;
    private Boolean isHost;
    private String role;
    private String status;
}
package com.werewolfkill.game.dto;

import lombok.Data;

@Data
public class CreateRoomRequest {
    private String name;
    private String hostId;
    private Integer maxPlayers = 8;
}
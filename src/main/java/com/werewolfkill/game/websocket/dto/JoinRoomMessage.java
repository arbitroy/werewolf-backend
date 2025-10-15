package com.werewolfkill.game.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JoinRoomMessage {
    private UUID roomId;
    private UUID playerId;
    private String username;
}

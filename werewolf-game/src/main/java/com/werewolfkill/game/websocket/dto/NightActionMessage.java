package com.werewolfkill.game.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NightActionMessage {
    private UUID roomId;
    private UUID actorId;
    private UUID targetId;
    private String action; // "KILL" or "INVESTIGATE"
}
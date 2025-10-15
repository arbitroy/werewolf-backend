package com.werewolfkill.game.websocket;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class GameWebSocketHandler {

    @MessageMapping("/game.join")
    @SendTo("/topic/game")
    public Map<String, Object> joinGame(Map<String, String> message) {
        return Map.of(
            "type", "PLAYER_JOINED",
            "playerId", message.get("playerId"),
            "roomId", message.get("roomId")
        );
    }
}
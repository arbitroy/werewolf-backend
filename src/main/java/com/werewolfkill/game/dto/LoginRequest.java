package com.werewolfkill.game.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;
}
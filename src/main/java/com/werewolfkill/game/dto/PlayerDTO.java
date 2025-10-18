package com.werewolfkill.game.dto;

public class PlayerDTO {
    private String playerId;
    private String username;
    private Boolean isHost;
    private String role;
    private String status;
    
    public PlayerDTO() {}
    
    // Getters
    public String getPlayerId() { return playerId; }
    public String getUsername() { return username; }
    public Boolean getIsHost() { return isHost; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
    
    // Setters
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public void setUsername(String username) { this.username = username; }
    public void setIsHost(Boolean isHost) { this.isHost = isHost; }
    public void setRole(String role) { this.role = role; }
    public void setStatus(String status) { this.status = status; }
}
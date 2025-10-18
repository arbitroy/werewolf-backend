package com.werewolfkill.game.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rooms")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Room {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false, unique = true)
    private String name;
    
    @Column(nullable = false)
    private UUID createdBy;  // Who created the room
    
    @Column(nullable = false)
    private Integer maxPlayers = 8;
    
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    
    private String gameMode = "CLASSIC";
    private Boolean isPublic = true;
}
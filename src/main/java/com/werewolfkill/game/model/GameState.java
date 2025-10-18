package com.werewolfkill.game.model;

import jakarta.persistence.*;  // ✅ ADDED
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;  // ✅ ADDED

@Entity  // ✅ ADDED
@Table(name = "game_states")  // ✅ ADDED
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameState {
    
    @Id  // ✅ ADDED
    @GeneratedValue(strategy = GenerationType.UUID)  // ✅ ADDED
    private UUID id;  // ✅ ADDED
    
    @Column(nullable = false)  // ✅ ADDED
    private String roomId;
    
    @Column(nullable = false)  // ✅ ADDED
    private String phase;
    
    @Column(nullable = false)  // ✅ ADDED
    private Integer dayNumber = 0;
    
    @Column(nullable = false)  // ✅ ADDED
    private Boolean isActive = false;
}
package com.werewolfkill.game.model;

import com.werewolfkill.game.model.enums.GamePhase;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Entity
@Table(name = "game_states")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameState {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false, unique = true)
    private UUID roomId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GamePhase phase = GamePhase.WAITING;
    
    private Integer timeRemaining = 0;
    
    @Column(length = 500)
    private String lastEvent;
    
    private String winner; // WEREWOLVES or VILLAGERS
}
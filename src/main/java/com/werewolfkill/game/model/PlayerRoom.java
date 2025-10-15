package com.werewolfkill.game.model;

import com.werewolfkill.game.model.enums.PlayerStatus;
import com.werewolfkill.game.model.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Entity
@Table(name = "player_rooms")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerRoom {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private UUID playerId;
    
    @Column(nullable = false)
    private UUID roomId;
    
    @Enumerated(EnumType.STRING)
    private Role role;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlayerStatus status = PlayerStatus.ALIVE;
    
    @Column(nullable = false)
    private Boolean isHost = false;
}

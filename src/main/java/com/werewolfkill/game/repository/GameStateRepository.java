package com.werewolfkill.game.repository;

import com.werewolfkill.game.model.GameState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GameStateRepository extends JpaRepository<GameState, UUID> {
    Optional<GameState> findByRoomId(String roomId);
}
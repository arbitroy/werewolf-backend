package com.werewolfkill.game.repository;

import com.werewolfkill.game.model.PlayerRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlayerRoomRepository extends JpaRepository<PlayerRoom, UUID> {
    List<PlayerRoom> findByRoomId(UUID roomId);
    Optional<PlayerRoom> findByPlayerIdAndRoomId(UUID playerId, UUID roomId);
    void deleteByRoomId(UUID roomId);
}
package com.werewolfkill.game.repository;

import com.werewolfkill.game.model.Room;
import com.werewolfkill.game.model.enums.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {
        Optional<Room> findByName(String name);
}
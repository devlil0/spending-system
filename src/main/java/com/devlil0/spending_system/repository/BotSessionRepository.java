package com.devlil0.spending_system.repository;

import com.devlil0.spending_system.model.BotSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BotSessionRepository extends JpaRepository<BotSessionEntity, Long> {

    Optional<BotSessionEntity> findByJid(String jid);

    Optional<BotSessionEntity> findByPhone(String phone);

}

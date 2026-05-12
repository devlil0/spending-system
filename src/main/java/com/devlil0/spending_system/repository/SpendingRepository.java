package com.devlil0.spending_system.repository;

import com.devlil0.spending_system.model.SpendingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpendingRepository extends JpaRepository<SpendingEntity, Long> {

    List<SpendingEntity> findByJid(String jid);

    List<SpendingEntity> findByJidOrderByCreatedAtAsc(String jid);

    List<SpendingEntity> findByJidAndDescriptionIgnoreCase(String jid, String description);

    void deleteByJid(String jid);

}

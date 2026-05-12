package com.devlil0.spending_system.repository;

import com.devlil0.spending_system.model.SpendingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpendingRepository extends JpaRepository<SpendingEntity, Long> {

    List<SpendingEntity> findByPhone(String phone);

    List<SpendingEntity> findByPhoneOrderByCreatedAtAsc(String phone);

    List<SpendingEntity> findByPhoneAndDescriptionIgnoreCase(String phone, String description);

    void deleteByPhone(String phone);

}

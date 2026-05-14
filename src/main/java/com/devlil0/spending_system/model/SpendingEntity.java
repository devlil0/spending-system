package com.devlil0.spending_system.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.devlil0.spending_system.config.ApplicationTimeZoneConfig;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "spending")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class SpendingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String category;

    private String description;

    @Column(nullable = false)
    private BigDecimal amount;

    private String phone;

    private String jid;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now(ApplicationTimeZoneConfig.SAO_PAULO_ZONE_ID);

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ApplicationTimeZoneConfig.SAO_PAULO_ZONE_ID);
        }
    }

}

package com.vetclinic.clinical.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.vetclinic.clinical.constant.ServiceDetailStatus;
import java.time.Instant;

@Entity
@Table(name = "visit_service_detail_histories")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitServiceDetailHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "visit_service_detail_id", nullable = false)
    private Long visitServiceDetailId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private ServiceDetailStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private ServiceDetailStatus toStatus;

    /** ORDERED, START_PROCESSING, COMPLETED, CANCELLED, POSTPONED, RECALL */
    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "changed_by_user_id", nullable = false)
    private Long changedByUserId;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

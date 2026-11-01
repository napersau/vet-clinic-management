package com.vetclinic.clinical.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity
@Table(name = "imaging_results")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImagingResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "visit_service_detail_id", nullable = false, unique = true)
    private Long visitServiceDetailId;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "conclusion", nullable = false, columnDefinition = "TEXT")
    private String conclusion;

    @Column(name = "performed_by", nullable = false)
    private Long performedBy;

    @CreationTimestamp
    @Column(name = "performed_at", nullable = false, updatable = false)
    private Instant performedAt;
}

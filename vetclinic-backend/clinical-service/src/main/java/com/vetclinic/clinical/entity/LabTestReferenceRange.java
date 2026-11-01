package com.vetclinic.clinical.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;

@Entity
@Table(name = "lab_test_reference_ranges")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabTestReferenceRange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lab_test_item_id", nullable = false)
    private LabTestItem labTestItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "species_id", nullable = false)
    private Species species;

    @Column(name = "min_value", nullable = false, precision = 8, scale = 2)
    private BigDecimal minValue;

    @Column(name = "max_value", nullable = false, precision = 8, scale = 2)
    private BigDecimal maxValue;
}

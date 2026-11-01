package com.vetclinic.billing.entity;

import com.vetclinic.billing.constant.PrescriptionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "prescriptions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Prescription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Khóa ngoại logic -> pet_clinic_clinical_db.pet_visits(id); mỗi đợt khám tối đa một đơn (BR-18). */
    @Column(name = "visit_id", nullable = false, unique = true)
    private Long visitId;

    @Column(name = "doctor_notes", columnDefinition = "TEXT")
    private String doctorNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private PrescriptionStatus status = PrescriptionStatus.PRESCRIBED;

    /** Khóa ngoại logic -> auth.users(id): người phát thuốc. */
    @Column(name = "dispensed_by")
    private Long dispensedBy;

    @Column(name = "dispensed_at")
    private Instant dispensedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "prescription", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<PrescriptionItem> items = new ArrayList<>();

    public void addItem(PrescriptionItem item) {
        item.setPrescription(this);
        items.add(item);
    }
}

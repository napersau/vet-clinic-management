package com.vetclinic.clinical.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Dịch vụ xét nghiệm gồm chỉ số nào (câu hỏi mở #2). Dịch vụ không có dòng nào thì được coi là gồm mọi chỉ số. */
@Entity
@Table(name = "service_lab_test_items")
@IdClass(ServiceLabTestItem.Key.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ServiceLabTestItem {

    public record Key(Long serviceId, Long labTestItemId) implements Serializable {
    }

    @Id
    @Column(name = "service_id")
    private Long serviceId;

    @Id
    @Column(name = "lab_test_item_id")
    private Long labTestItemId;
}

package com.vetclinic.billing.mapper;

import com.vetclinic.billing.dto.response.MedicineResponse;
import com.vetclinic.billing.entity.Medicine;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.WARN)
public interface MedicineMapper {

    @Mapping(target = "lowStock", expression = "java(medicine.getStockQuantity() <= medicine.getMinStockAlert())")
    MedicineResponse toResponse(Medicine medicine);
}

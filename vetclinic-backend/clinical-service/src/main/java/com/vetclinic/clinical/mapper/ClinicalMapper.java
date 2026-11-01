package com.vetclinic.clinical.mapper;

import com.vetclinic.clinical.dto.response.PetResponse;
import com.vetclinic.clinical.dto.response.ServiceResponse;
import com.vetclinic.clinical.dto.response.SpeciesResponse;
import com.vetclinic.clinical.entity.ClinicService;
import com.vetclinic.clinical.entity.Pet;
import com.vetclinic.clinical.entity.Species;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.WARN)
public interface ClinicalMapper {

    SpeciesResponse toResponse(Species species);

    @Mapping(target = "speciesId", source = "species.id")
    @Mapping(target = "speciesCode", source = "species.code")
    @Mapping(target = "speciesName", source = "species.name")
    PetResponse toResponse(Pet pet);

    ServiceResponse toResponse(ClinicService service);
}

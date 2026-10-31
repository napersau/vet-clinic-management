package com.vetclinic.auth.mapper;

import com.vetclinic.auth.dto.response.UserResponse;
import com.vetclinic.auth.dto.response.UserSummaryResponse;
import com.vetclinic.auth.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.WARN)
public interface UserMapper {

    @Mapping(target = "role", source = "role.code")
    UserResponse toResponse(User user);

    @Mapping(target = "role", source = "role.code")
    UserSummaryResponse toSummary(User user);
}

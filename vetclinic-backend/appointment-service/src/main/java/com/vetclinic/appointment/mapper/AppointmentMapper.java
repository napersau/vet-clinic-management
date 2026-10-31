package com.vetclinic.appointment.mapper;

import com.vetclinic.appointment.dto.response.AppointmentHistoryResponse;
import com.vetclinic.appointment.dto.response.AppointmentResponse;
import com.vetclinic.appointment.entity.Appointment;
import com.vetclinic.appointment.entity.AppointmentStatusHistory;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.WARN)
public interface AppointmentMapper {

    // Tên thú cưng / chủ nuôi không nằm trong DB của service này: được gắn sau bởi AppointmentEnricher
    @org.mapstruct.Mapping(target = "petName", ignore = true)
    @org.mapstruct.Mapping(target = "ownerName", ignore = true)
    @org.mapstruct.Mapping(target = "ownerPhone", ignore = true)
    AppointmentResponse toResponse(Appointment appointment);

    AppointmentHistoryResponse toHistoryResponse(AppointmentStatusHistory history);
}

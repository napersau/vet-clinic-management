package com.vetclinic.appointment.service;

import com.vetclinic.appointment.client.AuthClient;
import com.vetclinic.appointment.client.PetClient;
import com.vetclinic.appointment.dto.response.AppointmentResponse;
import com.vetclinic.appointment.dto.response.PetSummaryResponse;
import com.vetclinic.appointment.dto.response.UserSummaryResponse;
import com.vetclinic.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Gắn tên thú cưng, tên và SĐT chủ nuôi vào lịch hẹn bằng MỘT lời gọi theo lô sang clinical và MỘT sang auth (không N+1).
 * Gọi ở tầng controller, ngoài mọi transaction. Chỉ là phần hiển thị: service kia tạm sập thì để null, không làm hỏng danh sách.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentEnricher {

    private final AuthClient authClient;
    private final PetClient petClient;

    public AppointmentResponse enrich(AppointmentResponse appointment) {
        return enrichAll(List.of(appointment)).get(0);
    }

    public List<AppointmentResponse> enrichAll(List<AppointmentResponse> appointments) {
        if (appointments.isEmpty()) {
            return appointments;
        }
        Map<Long, PetSummaryResponse> pets = pets(appointments.stream().map(AppointmentResponse::petId).collect(Collectors.toSet()));
        Map<Long, UserSummaryResponse> owners = owners(appointments.stream().map(AppointmentResponse::ownerId).collect(Collectors.toSet()));
        return appointments.stream().map(a -> {
            PetSummaryResponse pet = pets.get(a.petId());
            UserSummaryResponse owner = owners.get(a.ownerId());
            return a.withNames(pet == null ? null : pet.name(), owner == null ? null : owner.fullName(),
                    owner == null ? null : owner.phoneNumber());
        }).toList();
    }

    private Map<Long, PetSummaryResponse> pets(Set<Long> ids) {
        try {
            ApiResponse<List<PetSummaryResponse>> response = petClient.getPets(List.copyOf(ids));
            List<PetSummaryResponse> list = response == null || response.getData() == null ? List.of() : response.getData();
            return list.stream().collect(Collectors.toMap(PetSummaryResponse::id, Function.identity(), (a, b) -> a));
        } catch (RuntimeException e) {
            log.warn("Không lấy được tên thú cưng từ clinical-service: {}", e.getMessage());
            return Map.of();
        }
    }

    private Map<Long, UserSummaryResponse> owners(Set<Long> ids) {
        try {
            ApiResponse<List<UserSummaryResponse>> response = authClient.getUsers(List.copyOf(ids));
            List<UserSummaryResponse> list = response == null || response.getData() == null ? List.of() : response.getData();
            return list.stream().collect(Collectors.toMap(UserSummaryResponse::id, Function.identity(), (a, b) -> a));
        } catch (RuntimeException e) {
            log.warn("Không lấy được thông tin chủ nuôi từ auth-service: {}", e.getMessage());
            return Map.of();
        }
    }
}

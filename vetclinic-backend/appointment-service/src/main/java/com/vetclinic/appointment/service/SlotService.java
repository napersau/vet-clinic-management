package com.vetclinic.appointment.service;

import com.vetclinic.appointment.config.BookingProperties;
import com.vetclinic.appointment.constant.AppointmentStatus;
import com.vetclinic.appointment.dto.internal.SlotBookedCount;
import com.vetclinic.appointment.dto.response.SlotAvailabilityResponse;
import com.vetclinic.appointment.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tình trạng 14 khung giờ của một ngày: sức chứa = số Bác sĩ ACTIVE, đã đặt = số lịch đang chiếm chỗ.
 */
@Service
@RequiredArgsConstructor
public class SlotService {

    private final BookingProperties properties;
    private final SlotPolicy slotPolicy;
    private final AppointmentRepository appointmentRepository;

    @Transactional(readOnly = true)
    public List<SlotAvailabilityResponse> getSlots(LocalDate date) {
        slotPolicy.assertDateInRange(date);

        int capacity = slotPolicy.activeDoctorCapacity();
        Map<LocalTime, Long> booked = new HashMap<>();
        for (SlotBookedCount row : appointmentRepository.countBookedBySlot(date, AppointmentStatus.OCCUPYING)) {
            booked.put(row.startTime(), row.count());
        }

        return properties.slots().stream().map(start -> {
            int bookedCount = booked.getOrDefault(start, 0L).intValue();
            int remaining = Math.max(capacity - bookedCount, 0);
            boolean past = slotPolicy.isPast(date, start);
            return new SlotAvailabilityResponse(start, slotPolicy.endOf(start), capacity, bookedCount, remaining,
                    !past && remaining > 0, past);
        }).toList();
    }
}

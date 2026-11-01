package com.vetclinic.clinical.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class QueueScheduleTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate DAY = LocalDate.of(2026, 10, 30);

    private static Clock at(String instant) {
        return Clock.fixed(Instant.parse(instant), VN);
    }

    @Test
    void insideTheSlot_isOnTime() {
        // 09:15 giờ VN trong khung 09:00-09:30
        assertThat(QueueSchedule.scheduledAtIfOnTime(DAY, LocalTime.of(9, 0), LocalTime.of(9, 30), at("2026-10-30T02:15:00Z")))
                .isEqualTo(Instant.parse("2026-10-30T02:00:00Z"));
    }

    @Test
    void exactlyAtTheEndOfTheSlot_isStillOnTime_butOneSecondLaterIsLate() {
        assertThat(QueueSchedule.scheduledAtIfOnTime(DAY, LocalTime.of(9, 0), LocalTime.of(9, 30), at("2026-10-30T02:30:00Z")))
                .isNotNull();
        assertThat(QueueSchedule.scheduledAtIfOnTime(DAY, LocalTime.of(9, 0), LocalTime.of(9, 30), at("2026-10-30T02:30:01Z")))
                .isNull();
    }

    @Test
    void earlyArrival_isOnTime() {
        assertThat(QueueSchedule.scheduledAtIfOnTime(DAY, LocalTime.of(14, 0), LocalTime.of(14, 30), at("2026-10-30T02:00:00Z")))
                .isEqualTo(Instant.parse("2026-10-30T07:00:00Z"));
    }

    @Test
    void missingEndTime_fallsBackToAThirtyMinuteSlot() {
        assertThat(QueueSchedule.scheduledAtIfOnTime(DAY, LocalTime.of(9, 0), null, at("2026-10-30T02:29:00Z"))).isNotNull();
        assertThat(QueueSchedule.scheduledAtIfOnTime(DAY, LocalTime.of(9, 0), null, at("2026-10-30T02:31:00Z"))).isNull();
    }

    @Test
    void missingDateOrStart_hasNoSchedule() {
        assertThat(QueueSchedule.scheduledAtIfOnTime(null, LocalTime.of(9, 0), null, at("2026-10-30T02:00:00Z"))).isNull();
        assertThat(QueueSchedule.scheduledAtIfOnTime(DAY, null, null, at("2026-10-30T02:00:00Z"))).isNull();
    }
}

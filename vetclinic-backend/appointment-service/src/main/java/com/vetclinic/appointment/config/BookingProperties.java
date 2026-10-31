package com.vetclinic.appointment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * @param zone                 múi giờ vận hành của phòng khám; ngày giờ hẹn (DATE/TIME) là giờ địa phương
 * @param slotMinutes          độ dài mỗi khung giờ
 * @param slotStartTimes       các giờ bắt đầu khung, dạng "HH:mm"
 * @param noShowGraceMinutes   quá giờ KẾT THÚC khung hẹn thêm bấy nhiêu phút thì coi là vắng mặt (EX-01)
 * @param minCancelHoursBefore chủ nuôi chỉ tự hủy / đổi lịch trước giờ hẹn tối thiểu bấy nhiêu giờ (FR-14)
 * @param maxAdvanceDays       đặt trước tối đa bấy nhiêu ngày
 * @param verifyPet            kiểm tra thú cưng tồn tại và thuộc đúng chủ nuôi qua clinical-service
 * @param slotLockTimeoutSeconds thời gian chờ khóa khung giờ
 */
@ConfigurationProperties(prefix = "vetclinic.booking")
public record BookingProperties(
        @DefaultValue("Asia/Ho_Chi_Minh") String zone,
        @DefaultValue("30") int slotMinutes,
        List<String> slotStartTimes,
        @DefaultValue("30") int noShowGraceMinutes,
        @DefaultValue("2") int minCancelHoursBefore,
        @DefaultValue("60") int maxAdvanceDays,
        @DefaultValue("false") boolean verifyPet,
        @DefaultValue("5") int slotLockTimeoutSeconds) {

    public BookingProperties {
        if (slotStartTimes == null || slotStartTimes.isEmpty()) {
            throw new IllegalStateException("vetclinic.booking.slot-start-times không được để trống");
        }
        if (slotMinutes <= 0) {
            throw new IllegalStateException("vetclinic.booking.slot-minutes phải lớn hơn 0");
        }
        // Lỗi cú pháp giờ / múi giờ phải làm service dừng ngay lúc khởi động, không phải lúc có khách đặt lịch
        ZoneId.of(zone);
        slotStartTimes.forEach(LocalTime::parse);
    }

    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }

    /** Các giờ bắt đầu khung, đã sắp tăng dần. */
    public List<LocalTime> slots() {
        return slotStartTimes.stream().map(LocalTime::parse).sorted().toList();
    }
}

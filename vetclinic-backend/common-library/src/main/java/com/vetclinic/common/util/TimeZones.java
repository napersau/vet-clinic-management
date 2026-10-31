package com.vetclinic.common.util;

import java.util.TimeZone;

/**
 * Các service có CSDL phải chạy với múi giờ mặc định của JVM là UTC.
 *
 * Lý do (đã đo thực tế): Hibernate gửi Instant xuống MySQL dưới dạng giờ UTC nên phiên JDBC buộc phải là UTC
 * (connectionTimeZone=UTC). Nhưng LocalTime (appointments.start_time, end_time) lại được đổi qua java.sql.Time theo múi giờ
 * mặc định của JVM. Nếu JVM ở Asia/Ho_Chi_Minh còn phiên ở UTC thì 09:00 bị ghi thành 01:00 vào DB (múi giờ này là +8:00
 * vào ngày 1970-01-01, ngày Java dùng cho java.sql.Time). API vẫn "đúng" vì đọc ra bị đảo ngược đối xứng, nhưng dữ liệu
 * thô trong DB sai, và sẽ lệch tiếp nếu service chạy ở máy có múi giờ khác (vd. chạy từ IDE).
 *
 * Đặt JVM về UTC thì mọi chỗ khớp nhau. Giờ phòng khám luôn được tính tường minh bằng Clock / zone Asia/Ho_Chi_Minh
 * (BookingProperties.zone), không bao giờ dựa vào múi giờ mặc định của JVM.
 */
public final class TimeZones {

    private TimeZones() {
    }

    /** Gọi ở dòng đầu tiên của main(), trước khi Spring và pool kết nối khởi động. */
    public static void forceUtcDefault() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
}

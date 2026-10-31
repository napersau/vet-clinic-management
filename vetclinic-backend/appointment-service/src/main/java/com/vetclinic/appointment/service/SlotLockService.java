package com.vetclinic.appointment.service;

import com.vetclinic.appointment.config.BookingProperties;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * Khóa tên của MySQL (GET_LOCK) để "đếm chỗ rồi chèn lịch hẹn" là một bước không thể bị chen ngang.
 * Nếu chỉ đếm rồi chèn, hai người đặt cùng lúc cùng thấy "còn 1 chỗ" và cả hai đều vào được.
 *
 * Vì sao khóa nằm trên một kết nối RIÊNG và chỉ đóng sau khi action xong:
 * - GET_LOCK gắn với kết nối, KHÔNG gắn với transaction. Nếu nhả khóa trước khi transaction commit, người kế tiếp
 *   đếm sẽ chưa thấy lịch hẹn vừa chèn (chưa commit) và vẫn vượt sức chứa.
 * - Kết nối mở riêng (không lấy từ pool) và đóng bằng try-with-resources: đóng kết nối là MySQL tự nhả mọi khóa,
 *   nên không thể rò khóa, và không làm cạn pool của transaction.
 * - Là khóa của MySQL nên đúng cả khi chạy nhiều instance appointment-service. Không cần thêm bảng vào schema.
 *
 * Gọi runLocked NGOÀI transaction; action tự mở transaction (TransactionTemplate) và commit xong mới trả về.
 */
@Slf4j
@Component
public class SlotLockService {

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final int timeoutSeconds;

    public SlotLockService(@Value("${spring.datasource.url}") String jdbcUrl,
                           @Value("${spring.datasource.username}") String username,
                           @Value("${spring.datasource.password}") String password,
                           BookingProperties properties) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.timeoutSeconds = properties.slotLockTimeoutSeconds();
    }

    /**
     * Lấy TẤT CẢ các khóa (theo thứ tự tên để hai request lấy cùng tập khóa không bao giờ chờ vòng tròn nhau),
     * chạy action, rồi nhả khóa. Không lấy được khóa trong thời gian chờ thì ném SLOT_BUSY.
     */
    public <T> T runLocked(Collection<String> lockNames, Supplier<T> action) {
        List<String> ordered = lockNames.stream().distinct().sorted().toList();
        try (Connection connection = openDedicatedConnection()) {
            for (String name : ordered) {
                acquire(connection, name);
            }
            return action.get();
        } catch (SQLException e) {
            log.error("Không thể dùng khóa khung giờ của MySQL: {}", e.getMessage());
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    private Connection openDedicatedConnection() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", password);
        properties.setProperty("connectTimeout", "3000");
        // Chờ GET_LOCK tối đa timeoutSeconds, nên socket phải chờ lâu hơn một chút
        properties.setProperty("socketTimeout", String.valueOf((timeoutSeconds + 10) * 1000));
        return DriverManager.getConnection(jdbcUrl, properties);
    }

    private void acquire(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            statement.setString(1, name);
            statement.setInt(2, timeoutSeconds);
            try (ResultSet rs = statement.executeQuery()) {
                // 1 = lấy được; 0 = hết thời gian chờ; NULL = lỗi
                if (!rs.next() || rs.getInt(1) != 1) {
                    log.warn("Hết thời gian chờ khóa '{}'", name);
                    throw new AppException(ErrorCode.SLOT_BUSY);
                }
            }
        }
    }

    // ------------------------------------------------------------------ tên khóa

    /** Khóa theo (ngày, giờ): serialize việc đếm chỗ và chèn lịch của cùng một khung giờ. */
    public static String slotLock(LocalDate date, LocalTime start) {
        return "vetclinic:appt-slot:" + date + "T" + start;
    }

    /** Khóa theo (thú cưng, ngày): chặn một thú cưng được đặt hai lịch trong ngày qua hai khung giờ khác nhau cùng lúc. */
    public static String petDayLock(Long petId, LocalDate date) {
        return "vetclinic:appt-pet:" + petId + ":" + date;
    }
}

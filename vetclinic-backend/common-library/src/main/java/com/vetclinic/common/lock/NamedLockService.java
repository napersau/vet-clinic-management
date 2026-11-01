package com.vetclinic.common.lock;

import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * Khóa tên của MySQL (GET_LOCK) cho các thao tác "kiểm tra rồi ghi" không thể chỉ dựa vào transaction:
 * hai request đồng thời cùng thấy điều kiện thỏa rồi cùng ghi (vd. hai lễ tân cùng phân công một bác sĩ).
 *
 * GET_LOCK gắn với KẾT NỐI chứ không gắn với transaction, nên nếu nhả khóa trước khi commit thì người sau vẫn chưa thấy
 * dữ liệu vừa ghi. Vì vậy khóa nằm trên một kết nối RIÊNG (mở thẳng bằng DriverManager, không lấy từ pool), action tự mở
 * transaction bên trong và commit xong thì kết nối mới đóng. Đóng kết nối là MySQL tự nhả mọi khóa nên không thể rò khóa,
 * và không làm cạn pool của transaction. Đúng cả khi chạy nhiều instance, không cần thêm bảng vào schema.
 *
 * Nơi gọi runLocked KHÔNG được đánh dấu @Transactional. Chỉ nạp ở service có CSDL.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "spring.datasource.url")
public class NamedLockService {

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final int timeoutSeconds;

    public NamedLockService(@Value("${spring.datasource.url}") String jdbcUrl,
                            @Value("${spring.datasource.username}") String username,
                            @Value("${spring.datasource.password}") String password,
                            @Value("${vetclinic.lock.timeout-seconds:5}") int timeoutSeconds) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Lấy TẤT CẢ các khóa (theo thứ tự tên để hai request lấy cùng tập khóa không bao giờ chờ vòng tròn nhau),
     * chạy action, rồi nhả khóa. Không lấy được trong thời gian chờ thì ném RESOURCE_BUSY (409, thử lại là được).
     */
    public <T> T runLocked(Collection<String> lockNames, Supplier<T> action) {
        List<String> ordered = lockNames.stream().distinct().sorted().toList();
        try (Connection connection = openDedicatedConnection()) {
            for (String name : ordered) {
                acquire(connection, name);
            }
            return action.get();
        } catch (SQLException e) {
            log.error("Không thể dùng khóa tên của MySQL: {}", e.getMessage());
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
                    throw new AppException(ErrorCode.RESOURCE_BUSY);
                }
            }
        }
    }
}

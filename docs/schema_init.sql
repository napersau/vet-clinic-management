-- =====================================================================================================================
-- Khởi tạo CSDL cho Hệ thống Quản lý Phòng khám Thú y (MySQL 8.0, 1 container, mỗi service một database logic).
-- Chỉ chạy khi volume MySQL còn trống (mount vào /docker-entrypoint-initdb.d/). Sửa file này thì phải `docker compose down -v`.
-- Script không idempotent (MySQL 8 không có CREATE INDEX IF NOT EXISTS).
--
-- Hiện có: pet_clinic_auth_db (auth-service), pet_clinic_appointment_db (appointment-service).
-- Database của các service khác được thêm vào CUỐI file khi chuyển service đó.
-- Bảng tạo SAU CÙNG phải trùng với bảng mà healthcheck của mysql-server trong docker-compose.yml kiểm tra.
-- =====================================================================================================================

-- Client mysql trong entrypoint Docker không dùng UTF-8: thiếu dòng này thì tiếng Việt trong seed bị mã hóa 2 lần
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------------------------------------------------
-- PHẦN 1. pet_clinic_auth_db (auth-service): roles, users, refresh_tokens
-- ---------------------------------------------------------------------------------------------------------------------
CREATE DATABASE pet_clinic_auth_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE pet_clinic_auth_db;

CREATE TABLE roles (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        ENUM ('ADMIN', 'VETERINARIAN', 'RECEPTIONIST', 'LAB_TECHNICIAN', 'IMAGING_TECHNICIAN', 'PET_OWNER') NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Thứ tự INSERT quyết định id: seed bên dưới dùng role_id = 1 cho ADMIN. Không đổi thứ tự.
INSERT INTO roles (code, name, description) VALUES
    ('ADMIN', 'Quản trị viên', 'Toàn quyền cấu hình hệ thống, quản lý danh mục và xem báo cáo'),
    ('VETERINARIAN', 'Bác sĩ thú y khám chính', 'Khám lâm sàng, chỉ định xét nghiệm, chẩn đoán xác định và kê đơn'),
    ('RECEPTIONIST', 'Lễ tân / Thu ngân', 'Tiếp đón, phân công bác sĩ, thu ngân và xuất thuốc'),
    ('LAB_TECHNICIAN', 'Bác sĩ / KTV Xét nghiệm', 'Tiếp nhận mẫu bệnh phẩm, phân tích huyết học/sinh hóa và nhập chỉ số kết quả'),
    ('IMAGING_TECHNICIAN', 'Bác sĩ / KTV Chẩn đoán hình ảnh', 'Thực hiện siêu âm, chụp X-quang, tải tệp ảnh và kết luận hình thái học'),
    ('PET_OWNER', 'Chủ thú cưng', 'Đặt lịch trực tuyến, theo dõi hồ sơ bệnh án và đơn thuốc');

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    role_id       BIGINT       NOT NULL,
    username      VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    phone_number  VARCHAR(20)  NOT NULL,
    email         VARCHAR(150) NULL,
    address       VARCHAR(255) NULL,
    room_number   VARCHAR(20)  NULL,
    status        ENUM ('ACTIVE', 'INACTIVE', 'LOCKED') NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY username (username),
    UNIQUE KEY phone_number (phone_number),
    KEY idx_users_phone (phone_number),
    KEY idx_users_role (role_id),
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Tài khoản quản trị mặc định: admin / Admin@123 (BCryptPasswordEncoder strength 12, đã kiểm chứng khớp)
INSERT INTO users (role_id, username, password_hash, full_name, phone_number, email, status) VALUES
    (1, 'admin', '$2a$12$AOnPlWYNzcOJ6wE/AhTsQe7.DJhsqPAHJ5xX1VEqHUz7yPZcUq.ge', 'Quản trị viên hệ thống', '0901234567',
     'admin@vetclinic.com', 'ACTIVE');

CREATE TABLE refresh_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    token       VARCHAR(255) NOT NULL,
    expiry_date TIMESTAMP    NOT NULL,
    revoked     TINYINT(1)   NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY token (token),
    KEY idx_refresh_tokens_user (user_id),
    KEY idx_refresh_tokens_token (token),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------------------------------------------------
-- PHẦN 2. pet_clinic_appointment_db (appointment-service): appointments, appointment_status_histories
-- pet_id / owner_id tham chiếu sang database khác (clinical, auth) nên không có khóa ngoại vật lý.
-- start_time / end_time là giờ địa phương VN, lưu nguyên giá trị (09:00 -> 09:00:00); mốc TIMESTAMP lưu UTC.
-- ---------------------------------------------------------------------------------------------------------------------
CREATE DATABASE pet_clinic_appointment_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE pet_clinic_appointment_db;

CREATE TABLE appointments (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    appointment_code VARCHAR(50) NOT NULL,
    pet_id           BIGINT      NOT NULL,
    owner_id         BIGINT      NOT NULL,
    appointment_date DATE        NOT NULL,
    start_time       TIME        NOT NULL,
    end_time         TIME        NOT NULL,
    reason           TEXT        NOT NULL,
    booking_channel  ENUM ('ONLINE', 'PHONE', 'WALK_IN') NOT NULL DEFAULT 'ONLINE',
    status           ENUM ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'CANCELLED', 'NO_SHOW') NOT NULL DEFAULT 'PENDING',
    cancel_reason    TEXT        NULL,
    confirmed_by     BIGINT      NULL,
    confirmed_at     TIMESTAMP   NULL DEFAULT NULL,
    cancelled_by     BIGINT      NULL,
    cancelled_at     TIMESTAMP   NULL DEFAULT NULL,
    created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY appointment_code (appointment_code),
    KEY idx_appointments_date_status (appointment_date, status),
    KEY idx_appointments_owner (owner_id),
    KEY idx_appointments_pet (pet_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- changed_by_user_id = NULL nghĩa là hệ thống (scheduler tự quét NO_SHOW / tự hủy lịch PENDING quá hạn)
CREATE TABLE appointment_status_histories (
    id                 BIGINT    NOT NULL AUTO_INCREMENT,
    appointment_id     BIGINT    NOT NULL,
    from_status        ENUM ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'CANCELLED', 'NO_SHOW') NULL DEFAULT NULL,
    to_status          ENUM ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'CANCELLED', 'NO_SHOW') NOT NULL,
    changed_by_user_id BIGINT    NULL DEFAULT NULL,
    notes              TEXT      NULL,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ash_appointment (appointment_id),
    CONSTRAINT fk_ash_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =====================================================================================================================
-- Khởi tạo CSDL cho Hệ thống Quản lý Phòng khám Thú y (MySQL 8.0, 1 container, mỗi service một database logic).
-- Chỉ chạy khi volume MySQL còn trống (mount vào /docker-entrypoint-initdb.d/). Sửa file này thì phải `docker compose down -v`.
-- Script không idempotent (MySQL 8 không có CREATE INDEX IF NOT EXISTS).
--
-- Hiện có: pet_clinic_auth_db (auth-service), pet_clinic_appointment_db (appointment-service),
-- pet_clinic_clinical_db (clinical-service, phần 1: loài, thú cưng, danh mục dịch vụ / xét nghiệm).
-- Database / bảng của các phần khác được thêm vào CUỐI file khi chuyển phần đó.
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

-- ---------------------------------------------------------------------------------------------------------------------
-- PHẦN 3. pet_clinic_clinical_db (clinical-service) - phần 1: species, services, lab_test_items,
-- lab_test_reference_ranges, service_lab_test_items, pets.
-- 7 bảng của đợt khám (pet_visits, *_histories, visit_service_details, lab_test_results, imaging_*) thêm ở phần 2.
-- Seed dưới đây là DANH MỤC GỐC (loài id 1-2, dịch vụ id 1-7, chỉ số id 1-4): thứ tự INSERT quyết định id và các dòng sau
-- tham chiếu cứng theo id đó. Không đổi thứ tự. Mã EXAM_GEN bắt buộc phải có (bác sĩ bắt đầu khám tự thêm phí này).
-- ---------------------------------------------------------------------------------------------------------------------
CREATE DATABASE pet_clinic_clinical_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE pet_clinic_clinical_db;

CREATE TABLE species (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO species (code, name, description) VALUES
    ('DOG', 'Chó', 'Loài chó (Canis lupus familiaris)'),
    ('CAT', 'Mèo', 'Loài mèo (Felis catus)');

CREATE TABLE services (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    code        VARCHAR(50)   NOT NULL,
    name        VARCHAR(150)  NOT NULL,
    category    ENUM ('EXAMINATION', 'VACCINATION', 'DEWORMING', 'EXTERNAL_PARASITE', 'INJECTION_INFUSION', 'LABORATORY',
                      'IMAGING', 'SURGERY', 'PROCEDURE') NOT NULL,
    price       DECIMAL(12, 2) NOT NULL,
    description TEXT          NULL,
    is_active   TINYINT(1)    NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO services (code, name, category, price, description) VALUES
    ('EXAM_GEN', 'Khám lâm sàng tổng quát', 'EXAMINATION', 100000.00, 'Khám tổng thể, kiểm tra thân nhiệt, cân nặng và triệu chứng bệnh'),
    ('VAC_7B', 'Tiêm phòng Vaccine 7 bệnh (Chó)', 'VACCINATION', 150000.00, 'Công tiêm phòng vaccine phòng 7 bệnh truyền nhiễm'),
    ('VAC_4B', 'Tiêm phòng Vaccine 4 bệnh (Mèo)', 'VACCINATION', 150000.00, 'Công tiêm phòng vaccine cho mèo'),
    ('DEWORM', 'Dịch vụ tẩy giun đường ruột', 'DEWORMING', 50000.00, 'Công kiểm tra và hỗ trợ cho uống thuốc tẩy giun'),
    ('LAB_CBC', 'Xét nghiệm tổng phân tích tế bào máu (CBC)', 'LABORATORY', 250000.00, 'Xét nghiệm 18-24 chỉ số tế bào máu tự động'),
    ('IMG_US_AB', 'Siêu âm ổ bụng tổng quát', 'IMAGING', 200000.00, 'Siêu âm màu kiểm tra các cơ quan nội tạng ổ bụng'),
    ('IMG_XRAY', 'Chụp X-quang kỹ thuật số (1 tư thế)', 'IMAGING', 180000.00, 'Chụp X-quang tim phổi / xương khớp');

CREATE TABLE lab_test_items (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    unit        VARCHAR(30)  NOT NULL,
    description VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO lab_test_items (code, name, unit, description) VALUES
    ('WBC', 'Bạch cầu (White Blood Cells)', '10^9/L', 'Số lượng bạch cầu tổng số'),
    ('RBC', 'Hồng cầu (Red Blood Cells)', '10^12/L', 'Số lượng hồng cầu tổng số'),
    ('HGB', 'Huyết sắc tố (Hemoglobin)', 'g/dL', 'Nồng độ huyết sắc tố'),
    ('PLT', 'Tiểu cầu (Platelets)', '10^9/L', 'Số lượng tiểu cầu');

-- Khoảng tham chiếu theo loài; kết quả xét nghiệm chụp lại (snapshot) khoảng tại thời điểm nhập nên sửa khoảng không đổi kết quả cũ
CREATE TABLE lab_test_reference_ranges (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    lab_test_item_id BIGINT       NOT NULL,
    species_id       BIGINT       NOT NULL,
    min_value        DECIMAL(8, 2) NOT NULL,
    max_value        DECIMAL(8, 2) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ltrr_item_species (lab_test_item_id, species_id),
    KEY idx_ltrr_species (species_id),
    CONSTRAINT fk_ltrr_item FOREIGN KEY (lab_test_item_id) REFERENCES lab_test_items (id),
    CONSTRAINT fk_ltrr_species FOREIGN KEY (species_id) REFERENCES species (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO lab_test_reference_ranges (lab_test_item_id, species_id, min_value, max_value) VALUES
    (1, 1, 6.00, 17.00), (2, 1, 5.50, 8.50), (3, 1, 12.00, 18.00), (4, 1, 200.00, 500.00),   -- chó
    (1, 2, 5.50, 19.50), (2, 2, 5.00, 10.00), (3, 2, 8.00, 15.00), (4, 2, 300.00, 700.00);   -- mèo

-- Dịch vụ xét nghiệm gồm những chỉ số nào. Dịch vụ LABORATORY chưa có dòng nào = gồm mọi chỉ số (tương thích ngược)
CREATE TABLE service_lab_test_items (
    service_id       BIGINT NOT NULL,
    lab_test_item_id BIGINT NOT NULL,
    PRIMARY KEY (service_id, lab_test_item_id),
    KEY fk_slti_item (lab_test_item_id),
    CONSTRAINT fk_slti_item FOREIGN KEY (lab_test_item_id) REFERENCES lab_test_items (id),
    CONSTRAINT fk_slti_service FOREIGN KEY (service_id) REFERENCES services (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO service_lab_test_items (service_id, lab_test_item_id) VALUES (5, 1), (5, 2), (5, 3), (5, 4);   -- LAB_CBC

-- owner_id tham chiếu sang pet_clinic_auth_db.users nên không có khóa ngoại vật lý
CREATE TABLE pets (
    id                          BIGINT       NOT NULL AUTO_INCREMENT,
    owner_id                    BIGINT       NOT NULL,
    species_id                  BIGINT       NOT NULL,
    pet_code                    VARCHAR(50)  NOT NULL,
    name                        VARCHAR(100) NOT NULL,
    breed                       VARCHAR(100) NULL,
    gender                      ENUM ('MALE', 'FEMALE', 'UNKNOWN') NOT NULL DEFAULT 'UNKNOWN',
    date_of_birth               DATE         NULL,
    color                       VARCHAR(100) NULL,
    weight                      DECIMAL(5, 2) NULL,
    is_neutered                 TINYINT(1)   NOT NULL DEFAULT 0,
    identifying_characteristics TEXT         NULL,
    notes                       TEXT         NULL,
    created_at                  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY pet_code (pet_code),
    KEY idx_pets_owner (owner_id),
    KEY idx_pets_code (pet_code),
    KEY idx_pets_species (species_id),
    CONSTRAINT fk_pets_species FOREIGN KEY (species_id) REFERENCES species (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

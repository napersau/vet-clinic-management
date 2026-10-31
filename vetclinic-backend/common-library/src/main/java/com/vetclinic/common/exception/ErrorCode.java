package com.vetclinic.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Danh mục mã lỗi dùng chung toàn hệ thống.
 * Không đổi mã chuỗi đã có (Frontend tham chiếu theo code); mã mới thêm vào cuối nhóm tương ứng.
 */
@Getter
public enum ErrorCode {
    // 1. Hệ thống chung (SYS: 1000 - 1099)
    UNCATEGORIZED_EXCEPTION("SYS_1000", "Lỗi hệ thống không xác định, vui lòng thử lại sau", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_INPUT_DATA("SYS_1001", "Dữ liệu đầu vào không hợp lệ", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("SYS_1002", "Chưa đăng nhập hoặc token đã hết hạn", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("SYS_1003", "Bạn không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN),
    SERVICE_UNAVAILABLE("SYS_1004", "Dịch vụ liên quan hiện không phản hồi, vui lòng thử lại sau", HttpStatus.SERVICE_UNAVAILABLE),
    OPTIMISTIC_LOCK_CONFLICT("SYS_1005", "Dữ liệu vừa được người khác cập nhật, vui lòng tải lại trang", HttpStatus.CONFLICT),
    RESOURCE_NOT_FOUND("SYS_1006", "Không tìm thấy tài nguyên được yêu cầu", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("SYS_1007", "Phương thức HTTP không được hỗ trợ cho đường dẫn này", HttpStatus.METHOD_NOT_ALLOWED),
    RESOURCE_BUSY("SYS_1008", "Hệ thống đang xử lý một yêu cầu khác trên cùng dữ liệu, vui lòng thử lại sau vài giây", HttpStatus.CONFLICT),

    // 2. Người dùng & Xác thực (AUTH: 1100 - 1199)
    USER_NOT_FOUND("AUTH_1101", "Không tìm thấy người dùng", HttpStatus.NOT_FOUND),
    PHONE_NUMBER_ALREADY_EXISTS("AUTH_1102", "Số điện thoại đã được đăng ký trong hệ thống", HttpStatus.CONFLICT),
    INVALID_CREDENTIALS("AUTH_1103", "Tên đăng nhập hoặc mật khẩu không chính xác", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_NOT_FOUND("AUTH_1104", "Không tìm thấy phiên Refresh Token", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_REVOKED("AUTH_1105", "Refresh Token đã bị thu hồi hoặc đã qua sử dụng (Cảnh báo bảo mật)", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_EXPIRED("AUTH_1106", "Refresh Token đã hết hạn, vui lòng đăng nhập lại", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED("AUTH_1107", "Tài khoản đã bị khóa, vui lòng liên hệ quản trị viên", HttpStatus.FORBIDDEN),
    ACCOUNT_INACTIVE("AUTH_1108", "Tài khoản đã ngừng hoạt động, vui lòng liên hệ quản trị viên", HttpStatus.FORBIDDEN),
    USERNAME_ALREADY_EXISTS("AUTH_1109", "Tên đăng nhập đã tồn tại trong hệ thống", HttpStatus.CONFLICT),
    ROLE_NOT_FOUND("AUTH_1110", "Không tìm thấy vai trò", HttpStatus.NOT_FOUND),
    INVALID_CURRENT_PASSWORD("AUTH_1111", "Mật khẩu hiện tại không chính xác", HttpStatus.BAD_REQUEST),
    TOO_MANY_LOGIN_ATTEMPTS("AUTH_1112", "Đăng nhập sai quá nhiều lần, vui lòng thử lại sau ít phút", HttpStatus.TOO_MANY_REQUESTS),
    ROOM_NUMBER_ALREADY_ASSIGNED("AUTH_1113", "Số phòng này đã được gán cho nhân sự khác", HttpStatus.CONFLICT),
    CANNOT_MODIFY_OWN_ACCOUNT("AUTH_1114", "Không thể tự thay đổi trạng thái của chính tài khoản đang đăng nhập", HttpStatus.BAD_REQUEST),

    // 3. Thú cưng & Lịch hẹn (APPT: 1200 - 1299)
    PET_NOT_FOUND("APPT_1201", "Không tìm thấy hồ sơ thú cưng", HttpStatus.NOT_FOUND),
    APPOINTMENT_NOT_FOUND("APPT_1202", "Không tìm thấy lịch hẹn", HttpStatus.NOT_FOUND),
    SLOT_CAPACITY_EXCEEDED("APPT_1203", "Khung giờ khám đã kín chỗ, vui lòng chọn khung giờ khác", HttpStatus.BAD_REQUEST),
    CANNOT_CANCEL_APPOINTMENT("APPT_1204", "Chỉ được phép hủy hẹn trước giờ khám tối thiểu 2 tiếng", HttpStatus.BAD_REQUEST),
    SLOT_BUSY("APPT_1205", "Hệ thống đang xử lý một yêu cầu đặt lịch khác cho khung giờ này, vui lòng thử lại sau vài giây", HttpStatus.CONFLICT),
    INVALID_APPOINTMENT_STATUS("APPT_1206", "Trạng thái lịch hẹn không hợp lệ để thực hiện thao tác này", HttpStatus.BAD_REQUEST),
    DUPLICATE_APPOINTMENT("APPT_1207", "Thú cưng này đã có lịch hẹn khác trong ngày", HttpStatus.CONFLICT),
    INVALID_APPOINTMENT_SLOT("APPT_1208", "Khung giờ đặt hẹn không hợp lệ", HttpStatus.BAD_REQUEST),

    // 4. Khám lâm sàng & Cận lâm sàng (CLIN: 1300 - 1399)
    VISIT_NOT_FOUND("CLIN_1301", "Không tìm thấy đợt khám y tế", HttpStatus.NOT_FOUND),
    DOCTOR_NOT_AVAILABLE("CLIN_1302", "Bác sĩ hiện đang bận ca khám khác", HttpStatus.CONFLICT),
    DOCTOR_PRIORITY_LOCKED("CLIN_1303", "Bác sĩ đang có ca chờ đọc kết quả cận lâm sàng, vui lòng ưu tiên gọi lại ca tái khám", HttpStatus.CONFLICT),
    PARACLINICAL_SERVICE_IN_PROGRESS("CLIN_1304", "Dịch vụ cận lâm sàng đang thực hiện, không thể hủy", HttpStatus.BAD_REQUEST),
    INVALID_STATUS_TRANSITION("CLIN_1305", "Trạng thái ca khám không hợp lệ để thực hiện thao tác này", HttpStatus.BAD_REQUEST),
    VISIT_ALREADY_ACTIVE("CLIN_1306", "Thú cưng này đang có một đợt khám chưa hoàn tất", HttpStatus.CONFLICT),
    APPOINTMENT_NOT_READY_FOR_CHECKIN("CLIN_1307", "Lịch hẹn chưa được xác nhận hoặc không phải lịch của ngày hôm nay", HttpStatus.BAD_REQUEST),
    VISIT_NOT_ASSIGNED_TO_YOU("CLIN_1308", "Đợt khám này không được phân công cho bạn", HttpStatus.FORBIDDEN),
    SERVICE_NOT_FOUND("CLIN_1309", "Không tìm thấy dịch vụ kỹ thuật hoặc dịch vụ đã ngừng sử dụng", HttpStatus.NOT_FOUND),
    SERVICE_DETAIL_NOT_FOUND("CLIN_1310", "Không tìm thấy chỉ định dịch vụ", HttpStatus.NOT_FOUND),
    INVALID_SERVICE_DETAIL_STATUS("CLIN_1311", "Trạng thái chỉ định dịch vụ không hợp lệ để thực hiện thao tác này", HttpStatus.BAD_REQUEST),
    DIAGNOSIS_REQUIRED("CLIN_1312", "Vui lòng nhập kết luận chẩn đoán xác định trước khi chốt đợt khám", HttpStatus.BAD_REQUEST),
    LAB_REFERENCE_NOT_FOUND("CLIN_1313", "Chưa cấu hình khoảng tham chiếu của chỉ số này cho loài của thú cưng", HttpStatus.BAD_REQUEST),
    INVALID_IMAGING_FILE("CLIN_1314", "Tệp ảnh không hợp lệ (chỉ nhận JPEG, PNG hoặc DICOM, tối đa 20MB)", HttpStatus.BAD_REQUEST),
    SPECIES_NOT_FOUND("CLIN_1315", "Không tìm thấy loài thú cưng", HttpStatus.NOT_FOUND),
    INVALID_DOCTOR("CLIN_1316", "Người được chọn không phải bác sĩ đang hoạt động", HttpStatus.BAD_REQUEST),
    LAB_ITEM_NOT_FOUND("CLIN_1317", "Không tìm thấy chỉ số xét nghiệm", HttpStatus.NOT_FOUND),
    CODE_ALREADY_EXISTS("CLIN_1318", "Mã đã tồn tại trong danh mục", HttpStatus.CONFLICT),
    LAB_ITEM_NOT_IN_SERVICE("CLIN_1319", "Chỉ số này không thuộc dịch vụ xét nghiệm được chỉ định", HttpStatus.BAD_REQUEST),

    // 5. Viện phí & Kho dược (BILL/PHARM: 1400 - 1499)
    INVOICE_NOT_FOUND("BILL_1401", "Không tìm thấy hóa đơn viện phí", HttpStatus.NOT_FOUND),
    DISCOUNT_EXCEEDS_TOTAL("BILL_1402", "Số tiền chiết khấu không thể vượt quá tổng viện phí", HttpStatus.BAD_REQUEST),
    INVOICE_ALREADY_PAID("BILL_1403", "Hóa đơn này đã được thanh toán trước đó", HttpStatus.BAD_REQUEST),
    PRESCRIPTION_NOT_FOUND("PHARM_1404", "Không tìm thấy đơn thuốc của đợt khám", HttpStatus.NOT_FOUND),
    MEDICINE_NOT_FOUND("PHARM_1405", "Không tìm thấy thuốc trong danh mục kho", HttpStatus.NOT_FOUND),
    INSUFFICIENT_STOCK("PHARM_1406", "Số lượng tồn kho không đủ để xuất thuốc", HttpStatus.BAD_REQUEST),
    PRESCRIPTION_ALREADY_DISPENSED("PHARM_1407", "Đơn thuốc này đã được xuất kho trước đó", HttpStatus.CONFLICT),
    PRESCRIPTION_ALREADY_EXISTS("PHARM_1408", "Đợt khám này đã có đơn thuốc, hãy cập nhật đơn hiện có", HttpStatus.CONFLICT),
    PRESCRIPTION_LOCKED("PHARM_1409", "Đợt khám không còn ở giai đoạn khám hoặc đã được chốt, không thể thay đổi đơn thuốc", HttpStatus.BAD_REQUEST),
    INVALID_PRESCRIPTION_STATUS("PHARM_1410", "Trạng thái đơn thuốc không hợp lệ để thực hiện thao tác này", HttpStatus.BAD_REQUEST),
    MEDICINE_CODE_ALREADY_EXISTS("PHARM_1411", "Mã thuốc đã tồn tại trong danh mục", HttpStatus.CONFLICT),
    INVALID_STOCK_ADJUSTMENT("PHARM_1412", "Điều chỉnh làm tồn kho âm, không thể thực hiện", HttpStatus.BAD_REQUEST),
    INVOICE_NOT_PAID("BILL_1413", "Hóa đơn chưa được thanh toán, chưa thể xuất thuốc", HttpStatus.BAD_REQUEST),
    INVALID_INVOICE_STATUS("BILL_1414", "Trạng thái hóa đơn không hợp lệ để thực hiện thao tác này", HttpStatus.BAD_REQUEST),
    VISIT_NOT_READY_FOR_INVOICE("BILL_1415", "Đợt khám chưa được bác sĩ chốt sang chờ thanh toán", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public static ErrorCode fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ErrorCode ec : values()) {
            if (ec.code.equals(code)) {
                return ec;
            }
        }
        return null;
    }
}

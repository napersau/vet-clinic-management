// Nhãn tiếng Việt + tông màu cho các enum của backend. tone: green | blue | amber | red | violet | gray
export const VISIT_STATUS = {
  WAITING: { label: 'Chờ khám', tone: 'amber' },
  IN_PROGRESS: { label: 'Đang khám', tone: 'blue' },
  WAITING_RESULTS: { label: 'Chờ kết quả CLS', tone: 'violet' },
  RESULTS_READY: { label: 'Đã có kết quả', tone: 'green' },
  WAITING_PAYMENT: { label: 'Chờ thanh toán', tone: 'amber' },
  COMPLETED: { label: 'Hoàn tất', tone: 'green' },
  CANCELLED: { label: 'Đã hủy', tone: 'gray' },
};
export const APPOINTMENT_STATUS = {
  PENDING: { label: 'Chờ xác nhận', tone: 'amber' },
  CONFIRMED: { label: 'Đã xác nhận', tone: 'blue' },
  CHECKED_IN: { label: 'Đã tiếp nhận', tone: 'green' },
  CANCELLED: { label: 'Đã hủy', tone: 'gray' },
  NO_SHOW: { label: 'Vắng mặt', tone: 'red' },
};
export const BOOKING_CHANNEL = { ONLINE: 'Trực tuyến', PHONE: 'Điện thoại', WALK_IN: 'Tại quầy' };
export const SERVICE_DETAIL_STATUS = {
  PENDING: { label: 'Chờ thực hiện', tone: 'amber' },
  IN_PROGRESS: { label: 'Đang thực hiện', tone: 'blue' },
  COMPLETED: { label: 'Hoàn thành', tone: 'green' },
  CANCELLED: { label: 'Đã hủy', tone: 'gray' },
};
export const QUEUE_STATE = {
  WAITING: { label: 'Đang chờ', tone: 'gray' },
  NEXT_IN_LINE: { label: 'Ưu tiên kế tiếp', tone: 'violet' },
  POSTPONED: { label: 'Đã hoãn', tone: 'amber' },
  IN_PROGRESS: { label: 'Đang thực hiện', tone: 'blue' },
};
export const SERVICE_CATEGORY = {
  EXAMINATION: 'Khám tổng quát', VACCINATION: 'Tiêm phòng', DEWORMING: 'Tẩy giun', EXTERNAL_PARASITE: 'Ký sinh trùng ngoài da',
  INJECTION_INFUSION: 'Tiêm / truyền dịch', LABORATORY: 'Xét nghiệm', IMAGING: 'Chẩn đoán hình ảnh', SURGERY: 'Phẫu thuật', PROCEDURE: 'Thủ thuật',
};
export const DOCTOR_STATUS = {
  AVAILABLE: { label: 'Sẵn sàng', tone: 'green' },
  AVAILABLE_PRIORITY_CALLBACK: { label: 'Khóa ưu tiên tái khám', tone: 'violet' },
  BUSY: { label: 'Đang bận', tone: 'red' },
};
export const PAYMENT_STATUS = {
  PENDING: { label: 'Chờ thanh toán', tone: 'amber' },
  PAID: { label: 'Đã thanh toán', tone: 'green' },
  CANCELLED: { label: 'Đã hủy', tone: 'gray' },
};
export const PRESCRIPTION_STATUS = {
  PRESCRIBED: { label: 'Đã kê đơn', tone: 'blue' },
  DISPENSED: { label: 'Đã phát thuốc', tone: 'green' },
  CANCELLED: { label: 'Đã hủy', tone: 'gray' },
};
export const LAB_EVALUATION = {
  LOW: { label: 'Thấp', tone: 'amber' },
  NORMAL: { label: 'Bình thường', tone: 'green' },
  HIGH: { label: 'Cao', tone: 'red' },
};
export const INVENTORY_TYPE = { IMPORT: 'Nhập kho', EXPORT_PRESCRIPTION: 'Xuất theo đơn', ADJUSTMENT: 'Điều chỉnh' };
export const PET_GENDER = { MALE: 'Đực', FEMALE: 'Cái', UNKNOWN: 'Chưa rõ' };
export const ACCOUNT_STATUS = {
  ACTIVE: { label: 'Đang hoạt động', tone: 'green' },
  LOCKED: { label: 'Đã khóa', tone: 'red' },
  INACTIVE: { label: 'Ngừng hoạt động', tone: 'gray' },
};
export const INVOICE_ITEM_TYPE = { SERVICE: 'Dịch vụ', MEDICINE: 'Thuốc' };

export const labelOf = (map, key) => (map[key]?.label ?? map[key] ?? key ?? '—');
export const toneOf = (map, key) => map[key]?.tone ?? 'gray';

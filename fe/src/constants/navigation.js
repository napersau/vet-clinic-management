// Điều hướng theo vai trò. Mỗi vai trò nhân sự có một "khu vực" riêng (StaffLayout); chủ nuôi dùng cổng /portal.
export const CUSTOMER_LINKS = [
  { to: '/portal/profile', label: 'Thông tin cá nhân', description: 'Xem và quản lý hồ sơ của bạn', icon: 'user' },
  { to: '/portal/pets', label: 'Thú cưng của tôi', description: 'Quản lý thông tin thú cưng', icon: 'pets' },
  { to: '/portal/booking', label: 'Đặt lịch khám', description: 'Chọn ngày giờ cho người bạn nhỏ', icon: 'calendar' },
  { to: '/portal/appointments', label: 'Lịch hẹn của tôi', description: 'Theo dõi, đổi hoặc hủy lịch hẹn', icon: 'calendar' },
  { to: '/portal/medical-history', label: 'Lịch sử khám bệnh', description: 'Kết quả khám, xét nghiệm và đơn thuốc', icon: 'document' },
  { to: '/portal/notifications', label: 'Thông báo', description: 'Cập nhật mới nhất từ phòng khám', icon: 'heart' },
];

const RECEPTION_LINKS = [
  { to: '/reception/queue', label: 'Bảng điều phối', icon: 'users' },
  { to: '/reception/intake', label: 'Tiếp nhận khách', icon: 'calendar' },
  { to: '/reception/appointments', label: 'Lịch hẹn', icon: 'calendar' },
  { to: '/reception/invoices', label: 'Hóa đơn & thanh toán', icon: 'chart' },
  { to: '/reception/dispense', label: 'Phát thuốc', icon: 'medicine' },
  { to: '/reception/customers', label: 'Khách hàng', icon: 'users' },
  { to: '/reception/pets', label: 'Thú cưng', icon: 'heart' },
  { to: '/reception/history', label: 'Tra cứu bệnh án', icon: 'document' },
];

const VET_LINKS = [
  { to: '/vet/today', label: 'Ca khám của tôi', icon: 'heart' },
  { to: '/vet/history', label: 'Tra cứu bệnh án', icon: 'document' },
  { to: '/vet/medicines', label: 'Tra cứu thuốc', icon: 'medicine' },
];

const LAB_LINKS = [{ to: '/lab/queue', label: 'Hàng đợi xét nghiệm', icon: 'activity' }];
const IMAGING_LINKS = [{ to: '/imaging/queue', label: 'Hàng đợi CĐHA', icon: 'document' }];

const ADMIN_LINKS = [
  { to: '/admin/dashboard', label: 'Tổng quan', icon: 'grid' },
  { to: '/admin/users', label: 'Tài khoản & nhân sự', icon: 'users' },
  { to: '/admin/visits', label: 'Đợt khám', icon: 'heart' },
  { to: '/admin/services', label: 'Dịch vụ', icon: 'service' },
  { to: '/admin/lab-config', label: 'Xét nghiệm & tham chiếu', icon: 'activity' },
  { to: '/admin/species', label: 'Loài thú cưng', icon: 'heart' },
  { to: '/admin/medicines', label: 'Thuốc & kho', icon: 'medicine' },
  { to: '/admin/reports', label: 'Báo cáo & thống kê', icon: 'chart' },
];

export const STAFF_AREAS = {
  reception: { role: 'RECEPTIONIST', label: 'lễ tân', accountLabel: 'Nhân viên lễ tân', title: 'Không gian lễ tân', subtitle: 'Tiếp nhận · Lịch hẹn · Thu ngân', brand: 'RECEPTION CONSOLE', caption: 'KHU VỰC LỄ TÂN', home: '/reception/queue', themeKey: 'pawcare_reception_theme_v1', defaultTheme: 'light', links: RECEPTION_LINKS, description: 'Điều phối hàng đợi, tiếp nhận, thu tiền và phát thuốc.' },
  vet: { role: 'VETERINARIAN', label: 'bác sĩ', accountLabel: 'Bác sĩ thú y', title: 'Không gian bác sĩ', subtitle: 'Ca được phân công · Hồ sơ khám', brand: 'VETERINARIAN CONSOLE', caption: 'KHU VỰC BÁC SĨ', home: '/vet/today', themeKey: 'pawcare_vet_theme_v1', defaultTheme: 'light', links: VET_LINKS, description: 'Khám hai pha, chỉ định cận lâm sàng, kê đơn và chốt ca.' },
  lab: { role: 'LAB_TECHNICIAN', label: 'xét nghiệm', accountLabel: 'Kỹ thuật viên xét nghiệm', title: 'Phòng xét nghiệm', subtitle: 'Hàng đợi · Nhập kết quả', brand: 'LAB CONSOLE', caption: 'KHU VỰC XÉT NGHIỆM', home: '/lab/queue', themeKey: 'pawcare_lab_theme_v1', defaultTheme: 'light', links: LAB_LINKS, description: 'Nhận mẫu, nhập chỉ số; hệ thống tự đối chiếu khoảng tham chiếu của loài.' },
  imaging: { role: 'IMAGING_TECHNICIAN', label: 'chẩn đoán hình ảnh', accountLabel: 'Kỹ thuật viên CĐHA', title: 'Phòng chẩn đoán hình ảnh', subtitle: 'Hàng đợi · Tải ảnh kết quả', brand: 'IMAGING CONSOLE', caption: 'KHU VỰC CĐHA', home: '/imaging/queue', themeKey: 'pawcare_imaging_theme_v1', defaultTheme: 'light', links: IMAGING_LINKS, description: 'Thực hiện chụp, mô tả và tải ảnh (JPEG, PNG, DICOM).' },
  admin: { role: 'ADMIN', label: 'quản trị', accountLabel: 'Quản trị viên', title: 'Quản trị hệ thống', subtitle: 'Tài khoản · Danh mục · Kho · Báo cáo', brand: 'ADMIN CONSOLE', caption: 'MENU QUẢN TRỊ', home: '/admin/dashboard', themeKey: 'pawcare_admin_theme_v1', defaultTheme: 'dark', links: ADMIN_LINKS, description: 'Không gian dành riêng cho đội ngũ quản trị phòng khám.' },
};

export const AREA_BY_ROLE = Object.fromEntries(Object.entries(STAFF_AREAS).map(([area, config]) => [config.role, area]));

/** Liên kết hiển thị trong menu tài khoản của từng vai trò. */
export const linksForRole = (role) => (role === 'PET_OWNER' ? CUSTOMER_LINKS.slice(1) : STAFF_AREAS[AREA_BY_ROLE[role]]?.links || []);

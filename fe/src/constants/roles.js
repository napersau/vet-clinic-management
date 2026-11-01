// Mã vai trò khớp bảng roles trong thiết kế cơ sở dữ liệu.
export const ROLES = {
  ADMIN: 'ADMIN',
  VETERINARIAN: 'VETERINARIAN',
  RECEPTIONIST: 'RECEPTIONIST',
  LAB_TECHNICIAN: 'LAB_TECHNICIAN',
  IMAGING_TECHNICIAN: 'IMAGING_TECHNICIAN',
  PET_OWNER: 'PET_OWNER',
};

export const ROLE_LABELS = {
  ADMIN: 'Quản trị viên',
  VETERINARIAN: 'Bác sĩ khám lâm sàng',
  RECEPTIONIST: 'Lễ tân / Thu ngân',
  LAB_TECHNICIAN: 'Kỹ thuật viên xét nghiệm',
  IMAGING_TECHNICIAN: 'Kỹ thuật viên CĐHA',
  PET_OWNER: 'Khách hàng',
};

// Trang đầu tiên của từng vai trò sau khi đăng nhập.
export const ROLE_HOME = {
  ADMIN: '/admin/dashboard',
  RECEPTIONIST: '/reception/queue',
  VETERINARIAN: '/vet/today',
  LAB_TECHNICIAN: '/lab/queue',
  IMAGING_TECHNICIAN: '/imaging/queue',
  PET_OWNER: '/portal/profile',
};

export const getRoleHome = (role) => ROLE_HOME[role] || '/';

// Thông tin hiển thị của từng vai trò (nhãn, màu) cho màn hình quản trị tài khoản.
export const ROLE_INFO = {
  ADMIN: { label: 'Quản trị viên', tone: 'violet' },
  RECEPTIONIST: { label: 'Lễ tân', tone: 'blue' },
  VETERINARIAN: { label: 'Bác sĩ thú y', tone: 'teal' },
  LAB_TECHNICIAN: { label: 'Kỹ thuật viên xét nghiệm', tone: 'green' },
  IMAGING_TECHNICIAN: { label: 'Kỹ thuật viên CĐHA', tone: 'green' },
  PET_OWNER: { label: 'Chủ thú cưng', tone: 'orange' },
};

export const ACCOUNT_STATUSES = {
  ACTIVE: { label: 'Đang hoạt động', tone: 'active' },
  INACTIVE: { label: 'Ngừng hoạt động', tone: 'inactive' },
  LOCKED: { label: 'Bị khóa', tone: 'orange' },
};

// Admin tạo tài khoản nhân sự; chủ thú cưng tự đăng ký hoặc do lễ tân tạo.
export const STAFF_ROLES = ['VETERINARIAN', 'RECEPTIONIST', 'LAB_TECHNICIAN', 'IMAGING_TECHNICIAN'];
// Vai trò có số phòng làm việc cố định (UC-02).
export const ROOM_ROLES = ['VETERINARIAN', 'LAB_TECHNICIAN', 'IMAGING_TECHNICIAN'];

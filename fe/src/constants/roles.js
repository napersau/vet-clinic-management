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

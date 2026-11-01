import { ROLES, getRoleHome } from '../constants/roles';
import { ROUTES } from '../constants/routes';

// Chỉ chấp nhận đường dẫn nội bộ để tránh chuyển hướng ra ngoài sau khi đăng nhập.
export function getSafeReturnPath(path, fallback = '/') {
  const isInternal = typeof path === 'string' && path.startsWith('/') && !path.startsWith('//');
  return isInternal ? path : fallback;
}

// Mỗi khu vực nhân viên chỉ dành cho đúng một vai trò.
const AREA_ROLES = {
  '/admin/': ROLES.ADMIN,
  '/reception/': ROLES.RECEPTIONIST,
  '/vet/': ROLES.VETERINARIAN,
  '/lab/': ROLES.LAB_TECHNICIAN,
  '/imaging/': ROLES.IMAGING_TECHNICIAN,
};

/**
 * Trang đến sau khi đăng nhập: quay lại trang đang xem dở nếu vai trò được phép vào, ngược lại về trang chủ của vai trò.
 * Tránh đưa người dùng vào trang 403 (vd. lễ tân đăng nhập từ liên kết của bác sĩ) hoặc quay lại trang đăng nhập.
 */
export function resolvePostLoginPath(returnTo, role) {
  const home = getRoleHome(role);
  const path = getSafeReturnPath(returnTo, null);
  if (!path || path.startsWith('/auth/')) return home;
  const area = Object.keys(AREA_ROLES).find((prefix) => path.startsWith(prefix));
  if (area) return AREA_ROLES[area] === role ? path : home;
  if (path.startsWith('/portal/') && path !== ROUTES.PROFILE && role !== ROLES.PET_OWNER) return home;
  return path;
}

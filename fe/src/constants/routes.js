export const ROUTES = {
  HOME: '/',
  LOGIN: '/auth/login',
  REGISTER: '/auth/register',
  BOOKING: '/portal/booking',
};

// Liên kết tới một khu vực của trang chủ, ví dụ homeSection('services') => '/#services'.
export const homeSection = (id) => `/#${id}`;

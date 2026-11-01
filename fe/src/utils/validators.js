export const MIN_PASSWORD_LENGTH = 8;

// Khớp ValidationPatterns của auth-service: 8-72 ký tự, có ít nhất một chữ cái và một chữ số.
export const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/;

const PHONE_PATTERN = /^0\d{9}$/;
const PHONE_LIKE_PATTERN = /^[\d\s.-]+$/;

export const normalizePhone = (value) => value.replace(/[\s.-]/g, '');

// Số điện thoại di động Việt Nam gồm đúng 10 chữ số, bắt đầu bằng 0.
export const isValidPhone = (value) => PHONE_PATTERN.test(normalizePhone(value));

// Tên đăng nhập là số điện thoại (chủ nuôi, nhân sự) hoặc một tên riêng như "admin". Số điện thoại được bỏ khoảng trắng,
// tên riêng chỉ cắt khoảng trắng hai đầu (backend phân biệt đúng tên đã lưu).
export const normalizeIdentifier = (value) => {
  const trimmed = value.trim();
  return PHONE_LIKE_PATTERN.test(trimmed) ? normalizePhone(trimmed) : trimmed;
};

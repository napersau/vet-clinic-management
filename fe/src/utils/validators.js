export const MIN_PASSWORD_LENGTH = 8;

const PHONE_PATTERN = /^0\d{9}$/;
const PHONE_LIKE_PATTERN = /^[\d\s.-]+$/;

export const normalizePhone = (value) => value.replace(/[\s.-]/g, '');

// Số điện thoại di động Việt Nam gồm đúng 10 chữ số, bắt đầu bằng 0.
export const isValidPhone = (value) => PHONE_PATTERN.test(normalizePhone(value));

// Tài khoản đăng nhập là email hoặc số điện thoại; số điện thoại được bỏ khoảng trắng trước khi gửi.
export const normalizeIdentifier = (value) => {
  const trimmed = value.trim();
  return PHONE_LIKE_PATTERN.test(trimmed) ? normalizePhone(trimmed) : trimmed.toLowerCase();
};

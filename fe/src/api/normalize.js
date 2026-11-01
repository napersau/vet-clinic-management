// Chuẩn hóa dữ liệu backend cho giao diện: giữ nguyên tên trường của backend, chỉ thêm vài bí danh tiện hiển thị.
// id luôn là chuỗi trong giao diện (dùng cho URL và so sánh).
export function normalizeUser(user) {
  if (!user) return null;
  return {
    ...user,
    id: String(user.id),
    phone: user.phoneNumber || '',
    email: user.email || '',
    address: user.address || '',
    roomNumber: user.roomNumber || '',
  };
}

// Mật khẩu tạm cho tài khoản chủ nuôi do lễ tân tạo (UC-10: hệ thống cấp tài khoản). Có đủ chữ hoa, thường, số, ký hiệu.
export function temporaryPassword() {
  const alphabet = 'ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789';
  const bytes = new Uint32Array(8);
  window.crypto.getRandomValues(bytes);
  return `Pc${Array.from(bytes, (value) => alphabet[value % alphabet.length]).join('')}@${bytes[0] % 90 + 10}`;
}

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

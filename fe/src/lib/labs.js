// Đánh giá chỉ số xét nghiệm ngay lúc kỹ thuật viên nhập, để tô màu trước khi lưu. Quy tắc khớp backend
// (ParaclinicServiceImpl.evaluate): thấp hơn ngưỡng dưới = LOW, cao hơn ngưỡng trên = HIGH, nằm trong khoảng (gồm hai đầu) = NORMAL.
// Kết quả cuối cùng vẫn do backend tính và lưu cùng snapshot khoảng tham chiếu; đây chỉ là gợi ý hiển thị.

export const MAX_LAB_VALUE = 999999.99;

/** Khoảng tham chiếu {minValue, maxValue} (có thể là chuỗi) -> số, hoặc null nếu chưa cấu hình cho loài này. */
function toRange(range) {
  if (!range) return null;
  const min = Number(range.minValue);
  const max = Number(range.maxValue);
  return range.minValue === null || range.minValue === undefined || range.maxValue === null || range.maxValue === undefined || Number.isNaN(min) || Number.isNaN(max) ? null : { min, max };
}

export function evaluateLab(value, range) {
  const bounds = toRange(range);
  if (!bounds || value === null || value === undefined || value === '' || Number.isNaN(Number(value))) return null;
  const number = Number(value);
  if (number < bounds.min) return 'LOW';
  if (number > bounds.max) return 'HIGH';
  return 'NORMAL';
}

/**
 * Đọc giá trị người dùng gõ. Trả { empty: true } khi để trống, { error } khi không hợp lệ, hoặc { value } (số).
 * Backend nhận tối đa 6 chữ số nguyên và 2 chữ số thập phân, không âm.
 */
export function parseLabValue(text) {
  const raw = String(text ?? '').trim().replace(',', '.');
  if (raw === '') return { empty: true };
  if (!/^-?\d+(\.\d+)?$/.test(raw)) return { error: 'Giá trị đo phải là số.' };
  const value = Number(raw);
  if (value < 0) return { error: 'Giá trị đo không được âm.' };
  if (value > MAX_LAB_VALUE) return { error: 'Giá trị đo quá lớn (tối đa 999999,99).' };
  if ((raw.split('.')[1] || '').length > 2) return { error: 'Tối đa 2 chữ số thập phân.' };
  return { value };
}

/** Lớp CSS cho ô nhập theo đánh giá: vàng (thấp), xanh (bình thường), đỏ (cao). */
export const LAB_FIELD_CLASS = { LOW: 'lab-low', NORMAL: 'lab-normal', HIGH: 'lab-high' };

// Định dạng hiển thị. Phòng khám dùng múi giờ Việt Nam; backend trả Instant (UTC) và LocalDate/LocalTime (giờ địa phương).
export const CLINIC_ZONE = 'Asia/Ho_Chi_Minh';

const money = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 });
export const formatMoney = (value) => money.format(Number(value || 0));

export const formatNumber = (value, digits = 0) => Number(value || 0).toLocaleString('vi-VN', { maximumFractionDigits: digits });

// Instant -> "dd/MM/yyyy HH:mm" theo giờ VN
export function formatDateTime(value) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  // ghép từng phần để thứ tự luôn là ngày trước giờ, không phụ thuộc quy ước của trình duyệt
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en-GB', { timeZone: CLINIC_ZONE, day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).formatToParts(date).map((part) => [part.type, part.value]));
  return `${parts.day}/${parts.month}/${parts.year} ${parts.hour}:${parts.minute}`;
}

export function formatTimeOfInstant(value) {
  if (!value) return '—';
  return new Date(value).toLocaleTimeString('vi-VN', { timeZone: CLINIC_ZONE, hour: '2-digit', minute: '2-digit', hour12: false });
}

// LocalDate "2026-10-29" -> "29/10/2026" (không đi qua Date để khỏi lệch múi giờ)
export function formatDate(value) {
  if (!value) return '—';
  const [year, month, day] = String(value).slice(0, 10).split('-');
  return year && month && day ? `${day}/${month}/${year}` : '—';
}
export const formatTime = (value) => (value ? String(value).slice(0, 5) : '—');

// Ngày hôm nay (cộng offset ngày) theo giờ phòng khám, dạng yyyy-MM-dd: dùng cho input type=date và tham số lọc.
export function todayIso(offsetDays = 0) {
  return new Intl.DateTimeFormat('en-CA', { timeZone: CLINIC_ZONE, year: 'numeric', month: '2-digit', day: '2-digit' }).format(Date.now() + offsetDays * 86400000);
}

export function formatAge(dateOfBirth) {
  if (!dateOfBirth) return 'Chưa rõ tuổi';
  const [year, month] = dateOfBirth.split('-').map(Number);
  const [nowYear, nowMonth] = todayIso().split('-').map(Number);
  const months = (nowYear - year) * 12 + (nowMonth - month);
  if (months < 1) return 'Dưới 1 tháng';
  return months < 12 ? `${months} tháng` : `${Math.floor(months / 12)} tuổi${months % 12 ? ` ${months % 12} tháng` : ''}`;
}

export const toNumber = (value) => (value === '' || value === null || value === undefined ? null : Number(value));
export const blankToNull = (value) => (typeof value === 'string' ? (value.trim() === '' ? null : value.trim()) : value ?? null);

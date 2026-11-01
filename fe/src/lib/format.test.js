import { blankToNull, formatAge, formatDate, formatDateTime, formatMoney, formatTime, todayIso, toNumber } from './format';
import { labelOf, toneOf, VISIT_STATUS } from './labels';

describe('định dạng', () => {
  test('tiền VND không có số lẻ', () => {
    expect(formatMoney(250000)).toMatch(/250\.000/);
    expect(formatMoney('1500000.00')).toMatch(/1\.500\.000/);
    expect(formatMoney(null)).toMatch(/^0/);
  });

  test('ngày và giờ địa phương không đi qua múi giờ', () => {
    expect(formatDate('2026-10-29')).toBe('29/10/2026');
    expect(formatTime('09:30:00')).toBe('09:30');
    expect(formatDate(null)).toBe('—');
  });

  test('Instant UTC hiển thị theo giờ Việt Nam (+7)', () => {
    expect(formatDateTime('2026-10-29T02:30:00Z')).toBe('29/10/2026 09:30');
    expect(formatDateTime('2026-10-29T18:00:00Z')).toBe('30/10/2026 01:00');
  });

  test('todayIso trả yyyy-MM-dd và cộng ngày được', () => {
    expect(todayIso()).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(todayIso(1) > todayIso()).toBe(true);
  });

  test('tuổi thú cưng', () => {
    expect(formatAge(null)).toBe('Chưa rõ tuổi');
    expect(formatAge(todayIso())).toBe('Dưới 1 tháng');
  });

  test('chuyển trường nhập thành giá trị gửi API', () => {
    expect(toNumber('')).toBeNull();
    expect(toNumber('12.5')).toBe(12.5);
    expect(blankToNull('  ')).toBeNull();
    expect(blankToNull(' abc ')).toBe('abc');
  });
});

describe('nhãn enum', () => {
  test('trạng thái đợt khám có nhãn tiếng Việt và tông màu', () => {
    expect(labelOf(VISIT_STATUS, 'WAITING_PAYMENT')).toBe('Chờ thanh toán');
    expect(toneOf(VISIT_STATUS, 'COMPLETED')).toBe('green');
  });

  test('giá trị lạ không làm hỏng giao diện', () => {
    expect(labelOf(VISIT_STATUS, 'SOMETHING_NEW')).toBe('SOMETHING_NEW');
    expect(toneOf(VISIT_STATUS, 'SOMETHING_NEW')).toBe('gray');
  });
});

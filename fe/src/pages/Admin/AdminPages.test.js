import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { bodyOf, callsTo, failure, mockApi, pageOf, renderAt, signedInAs, success } from '../../testUtils';

const ME = () => success({ id: 9, fullName: 'Quản trị viên hệ thống', phoneNumber: '0901234567', role: 'ADMIN' });

const DOCTOR_ACCOUNT = { id: 31, username: '0912000001', fullName: 'BS. Nguyễn An', phoneNumber: '0912000001', email: '', role: 'VETERINARIAN', roomNumber: 'P101', status: 'ACTIVE', createdAt: '2026-11-01T02:00:00Z' };
const ADMIN_ACCOUNT = { id: 9, username: 'admin', fullName: 'Quản trị viên hệ thống', phoneNumber: '0901234567', email: 'admin@vetclinic.com', role: 'ADMIN', status: 'ACTIVE', createdAt: '2026-10-01T02:00:00Z' };

const rowOf = (text, selector) => within(screen.getByText(text, selector ? { selector } : undefined).closest('tr'));

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
  window.scrollTo = jest.fn();
  signedInAs('admin');
});
afterEach(() => { delete global.fetch; });

describe('quản lý tài khoản và nhân sự', () => {
  const api = (extra = {}) => mockApi({
    'GET /api/v1/auth/me': ME,
    'GET /api/v1/auth/users': () => success(pageOf([ADMIN_ACCOUNT, DOCTOR_ACCOUNT])),
    ...extra,
  });

  test('liệt kê tài khoản kèm số phòng và nhãn vai trò', async () => {
    api();
    renderAt('/admin/users');

    expect(await screen.findByText('BS. Nguyễn An')).toBeInTheDocument();
    expect(rowOf('BS. Nguyễn An').getByText('Phòng P101')).toBeInTheDocument();
    expect(rowOf('BS. Nguyễn An').getByText('Bác sĩ thú y')).toBeInTheDocument();
  });

  test('không tự khóa hoặc đổi vai trò của chính mình, nhưng khóa được người khác', async () => {
    api();
    renderAt('/admin/users');
    await screen.findByText('BS. Nguyễn An');

    expect(rowOf('admin', 'small').queryByRole('button', { name: 'Khóa' })).not.toBeInTheDocument();
    expect(rowOf('admin', 'small').queryByRole('button', { name: 'Đổi vai trò' })).not.toBeInTheDocument();
    expect(rowOf('BS. Nguyễn An').getByRole('button', { name: 'Khóa' })).toBeInTheDocument();
    expect(rowOf('BS. Nguyễn An').getByRole('button', { name: 'Đổi vai trò' })).toBeInTheDocument();
  });

  test('tạo bác sĩ mới gửi đủ vai trò, số phòng và mật khẩu ban đầu', async () => {
    api({ 'POST /api/v1/auth/users': () => success({ ...DOCTOR_ACCOUNT, id: 40, fullName: 'BS. Lê Chi' }, 201) });
    renderAt('/admin/users');
    userEvent.click(await screen.findByRole('button', { name: '+ Thêm nhân sự' }));

    userEvent.type(screen.getByLabelText('Họ và tên *'), 'BS. Lê Chi');
    userEvent.type(screen.getByLabelText('Số điện thoại *'), '0912000003');
    userEvent.type(screen.getByLabelText(/Số phòng làm việc/), 'P103');
    userEvent.type(screen.getByLabelText(/Mật khẩu ban đầu/), 'Matkhau@1');
    userEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));

    expect(await screen.findByText('Đã tạo tài khoản BS. Lê Chi.')).toBeInTheDocument();
    expect(bodyOf(callsTo('POST /api/v1/auth/users')[0])).toEqual({
      fullName: 'BS. Lê Chi', phoneNumber: '0912000003', email: null, address: null, roomNumber: 'P103', username: null, password: 'Matkhau@1', roleCode: 'VETERINARIAN',
    });
  });

  test('kiểm tra dữ liệu trước khi gọi API: số điện thoại sai, mật khẩu yếu, thiếu số phòng', async () => {
    api({ 'POST /api/v1/auth/users': () => success({}, 201) });
    renderAt('/admin/users');
    userEvent.click(await screen.findByRole('button', { name: '+ Thêm nhân sự' }));

    userEvent.type(screen.getByLabelText('Họ và tên *'), 'BS. Thiếu');
    userEvent.type(screen.getByLabelText('Số điện thoại *'), '123');
    userEvent.type(screen.getByLabelText(/Mật khẩu ban đầu/), 'abc');
    userEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));

    expect(await screen.findByText('Số điện thoại gồm đúng 10 số, bắt đầu bằng 0.')).toBeInTheDocument();
    expect(screen.getByText('Mật khẩu 8–72 ký tự, có cả chữ và số.')).toBeInTheDocument();
    expect(screen.getByText('Nhập số phòng làm việc.')).toBeInTheDocument();
    expect(callsTo('POST /api/v1/auth/users')).toHaveLength(0);
  });

  test('khóa tài khoản gửi trạng thái LOCKED sau khi xác nhận', async () => {
    api({ 'POST /api/v1/auth/users/31/status': () => success({ ...DOCTOR_ACCOUNT, status: 'LOCKED' }) });
    renderAt('/admin/users');
    await screen.findByText('BS. Nguyễn An');

    userEvent.click(rowOf('BS. Nguyễn An').getByRole('button', { name: 'Khóa' }));
    userEvent.click(await screen.findByRole('button', { name: 'Xác nhận' }));

    expect(await screen.findByText(/Đã chuyển tài khoản BS. Nguyễn An sang/)).toBeInTheDocument();
    expect(bodyOf(callsTo('POST /api/v1/auth/users/31/status')[0])).toEqual({ status: 'LOCKED' });
  });
});

describe('giám sát đợt khám và mở lại ca', () => {
  const VISIT = { id: 31, visitCode: 'VIS-2026-00031', petName: 'Lu', speciesName: 'Chó', status: 'WAITING_PAYMENT', emergency: false, checkedInAt: '2026-11-01T02:00:00Z', diagnosis: 'Viêm da' };
  const api = (extra = {}) => mockApi({
    'GET /api/v1/auth/me': ME,
    'GET /api/v1/clinical/pet-visits': () => success(pageOf([VISIT])),
    ...extra,
  });

  test('mặc định lọc các ca chờ thanh toán và có nút Mở lại', async () => {
    api();
    renderAt('/admin/visits');

    expect(await screen.findByText('VIS-2026-00031')).toBeInTheDocument();
    const params = new URL(String(callsTo('GET /api/v1/clinical/pet-visits')[0][0])).searchParams;
    expect(params.get('status')).toBe('WAITING_PAYMENT');
    expect(screen.getByRole('button', { name: 'Mở lại' })).toBeInTheDocument();
  });

  test('mở lại bắt buộc có lý do rồi gửi lý do đã cắt khoảng trắng', async () => {
    api({ 'POST /api/v1/clinical/pet-visits/31/reopen': () => success({ ...VISIT, status: 'IN_PROGRESS' }) });
    renderAt('/admin/visits');
    userEvent.click(await screen.findByRole('button', { name: 'Mở lại' }));

    userEvent.click(screen.getAllByRole('button', { name: 'Mở lại' }).pop());
    expect(await screen.findByText('Nhập lý do mở lại.')).toBeInTheDocument();
    expect(callsTo('POST /api/v1/clinical/pet-visits/31/reopen')).toHaveLength(0);

    userEvent.type(screen.getByLabelText('Lý do *'), '  Nhập sai liều thuốc ');
    userEvent.click(screen.getAllByRole('button', { name: 'Mở lại' }).pop());

    expect(await screen.findByText(/Đã mở lại VIS-2026-00031/)).toBeInTheDocument();
    expect(bodyOf(callsTo('POST /api/v1/clinical/pet-visits/31/reopen')[0])).toEqual({ reason: 'Nhập sai liều thuốc' });
  });

  test('bác sĩ đang bận: hiện lỗi của backend và giữ hộp thoại mở', async () => {
    api({ 'POST /api/v1/clinical/pet-visits/31/reopen': () => failure(409, 'CLIN_1302', 'Bác sĩ hiện đang bận ca khám khác') });
    renderAt('/admin/visits');
    userEvent.click(await screen.findByRole('button', { name: 'Mở lại' }));

    userEvent.type(screen.getByLabelText('Lý do *'), 'Điều chỉnh');
    userEvent.click(screen.getAllByRole('button', { name: 'Mở lại' }).pop());

    expect(await screen.findByText('Bác sĩ hiện đang bận ca khám khác')).toBeInTheDocument();
    expect(screen.getByLabelText('Lý do *')).toBeInTheDocument();
  });
});

describe('thuốc và kho', () => {
  const MEDICINE = { id: 5, code: 'MED_AMOX', name: 'Clavamox 250mg', activeIngredient: 'Amoxicillin', dosageForm: 'Viên nén', unit: 'Viên', salePrice: 15000, stockQuantity: 250, minStockAlert: 20, active: true, lowStock: false };
  const api = (extra = {}) => mockApi({
    'GET /api/v1/auth/me': ME,
    'GET /api/v1/pharmacy/medicines': () => success(pageOf([MEDICINE])),
    ...extra,
  });

  test('nhập kho gửi số lượng nguyên dương và cập nhật thông báo tồn mới', async () => {
    api({ 'POST /api/v1/pharmacy/medicines/5/import': () => success({ ...MEDICINE, stockQuantity: 270 }) });
    renderAt('/admin/medicines');
    await screen.findByText('Clavamox 250mg');

    userEvent.click(screen.getByRole('button', { name: 'Nhập kho' }));
    userEvent.type(screen.getByLabelText('Số lượng nhập *'), '20');
    userEvent.type(screen.getByLabelText('Ghi chú (nhà cung cấp, số lô…)'), 'Lô tháng 11');
    userEvent.click(screen.getAllByRole('button', { name: 'Nhập kho' }).pop());

    expect(await screen.findByText('Đã nhập kho; tồn Clavamox 250mg hiện là 270.')).toBeInTheDocument();
    expect(bodyOf(callsTo('POST /api/v1/pharmacy/medicines/5/import')[0])).toEqual({ quantity: 20, notes: 'Lô tháng 11' });
  });

  test('điều chỉnh tồn kho bắt buộc có lý do', async () => {
    api({ 'POST /api/v1/pharmacy/medicines/5/adjust': () => success({ ...MEDICINE, stockQuantity: 245 }) });
    renderAt('/admin/medicines');
    await screen.findByText('Clavamox 250mg');

    userEvent.click(screen.getByRole('button', { name: 'Điều chỉnh' }));
    userEvent.type(screen.getByLabelText('Chênh lệch (+/−) *'), '-5');
    userEvent.click(screen.getAllByRole('button', { name: 'Điều chỉnh' }).pop());

    expect(await screen.findByText('Điều chỉnh tồn kho bắt buộc có lý do.')).toBeInTheDocument();
    expect(callsTo('POST /api/v1/pharmacy/medicines/5/adjust')).toHaveLength(0);
  });
});

describe('tổng quan và báo cáo', () => {
  test('tổng quan gom ca khám, doanh thu, bác sĩ và thuốc sắp hết của hôm nay', async () => {
    mockApi({
      'GET /api/v1/auth/me': ME,
      'GET /api/v1/clinical/doctors/status': () => success([{ doctorId: 31, fullName: 'BS. Nguyễn An', roomNumber: 'P101', status: 'AVAILABLE' }, { doctorId: 32, fullName: 'BS. Trần Bình', roomNumber: 'P102', status: 'BUSY' }]),
      'GET /api/v1/pharmacy/medicines': () => success(pageOf([{ id: 7, name: 'Vaccine Purevax', stockQuantity: 3, minStockAlert: 10 }], { totalElements: 1 })),
      'GET /api/v1/clinical/reports/operations': () => success({ totalVisits: 7, completedVisits: 4, emergencyVisits: 1, cancelledVisits: 0, routineVisits: 5, paraclinicalVisits: 2, byStatus: {}, doctors: [] }),
      'GET /api/v1/billing/reports/revenue': () => success({ totals: { finalAmount: 500000, invoiceCount: 3 }, periods: [], byStatus: {} }),
      'GET /api/v1/auth/users': () => success(pageOf([], { totalElements: 2 })),
    });
    renderAt('/admin/dashboard');

    expect(await screen.findByText('Xin chào, Quản trị viên hệ thống')).toBeInTheDocument();
    expect(await screen.findByText('Ca khám hôm nay')).toBeInTheDocument();
    expect(screen.getByText('4 hoàn tất · 1 cấp cứu')).toBeInTheDocument();
    expect(screen.getByText('3 hóa đơn đã thu')).toBeInTheDocument();
    expect(screen.getByText('Vaccine Purevax')).toBeInTheDocument();
    expect(callsTo('GET /api/v1/auth/users')).toHaveLength(6);
  });

  test('báo cáo doanh thu mặc định nhóm theo ngày và gửi khoảng ngày', async () => {
    mockApi({
      'GET /api/v1/auth/me': ME,
      'GET /api/v1/billing/reports/revenue': () => success({ totals: { finalAmount: 0, invoiceCount: 0, serviceAmount: 0, medicineAmount: 0, vatAmount: 0, discountAmount: 0 }, periods: [], byStatus: {} }),
    });
    renderAt('/admin/reports');

    await screen.findByText('Doanh thu thực thu');
    const params = new URL(String(callsTo('GET /api/v1/billing/reports/revenue')[0][0])).searchParams;
    expect(params.get('groupBy')).toBe('DAY');
    expect(params.get('fromDate')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(params.get('toDate')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});

describe('phân quyền khu vực quản trị', () => {
  test('lễ tân vào đường dẫn quản trị không thấy màn hình quản trị', async () => {
    localStorage.clear();
    signedInAs('receptionist');
    mockApi({ 'GET /api/v1/auth/me': () => success({ id: 2, fullName: 'Trần Lễ Tân', phoneNumber: '0910000001', role: 'RECEPTIONIST' }) });
    renderAt('/admin/users');

    expect(await screen.findByText('Bạn không có quyền truy cập')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '+ Thêm nhân sự' })).not.toBeInTheDocument();
    expect(callsTo('GET /api/v1/auth/users')).toHaveLength(0);
  });
});

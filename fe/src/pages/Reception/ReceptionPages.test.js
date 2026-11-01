import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { bodyOf, callsTo, failure, mockApi, pageOf, renderAt, signedInAs, success } from '../../testUtils';

const OWNER = { id: 5, fullName: 'Lê Thị Hoa', phoneNumber: '0920000005', email: 'hoa@example.com', role: 'PET_OWNER' };
const PET = { id: 21, petCode: 'PET-2026-00021', name: 'Kitty', speciesName: 'Mèo', ownerId: 5 };
const VISIT = { id: 31, visitCode: 'VIS-20261102-00031', petName: 'Kitty', speciesName: 'Mèo', status: 'WAITING', emergency: false, checkedInAt: '2026-11-02T02:00:00Z', scheduledAt: null, initialSymptoms: 'Bỏ ăn' };

const todayAppointment = {
  id: 7, appointmentCode: 'APT-20261102-00007', petId: 21, petName: 'Kitty', ownerId: 5, ownerName: 'Lê Thị Hoa', ownerPhone: '0920000005',
  appointmentDate: '2026-11-02', startTime: '09:00:00', endTime: '09:30:00', reason: 'Khám định kỳ', status: 'CONFIRMED', bookingChannel: 'ONLINE', createdAt: '2026-11-01T01:00:00Z',
};

const DOCTORS = [
  { doctorId: 101, fullName: 'BS. Phạm An', roomNumber: '1', status: 'AVAILABLE' },
  { doctorId: 102, fullName: 'BS. Võ Bình', roomNumber: '2', status: 'BUSY' },
  { doctorId: 103, fullName: 'BS. Đỗ Chi', roomNumber: '3', status: 'AVAILABLE_PRIORITY_CALLBACK' },
];

const ME = () => success({ id: 2, fullName: 'Trần Lễ Tân', phoneNumber: '0910000001', role: 'RECEPTIONIST' });

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
  window.scrollTo = jest.fn();
  signedInAs('receptionist');
});
afterEach(() => { delete global.fetch; });

describe('tiếp đón: khách có lịch hẹn', () => {
  const api = (extra = {}) => mockApi({
    'GET /api/v1/auth/me': ME,
    'GET /api/v1/appointments': () => success(pageOf([todayAppointment])),
    ...extra,
  });

  test('chỉ lấy lịch đã xác nhận của hôm nay', async () => {
    api();
    renderAt('/reception/intake');
    await screen.findByText('09:00');

    const params = new URL(String(callsTo('GET /api/v1/appointments')[0][0])).searchParams;
    expect(params.get('status')).toBe('CONFIRMED');
    expect(params.get('fromDate')).toBe(params.get('toDate'));
    expect(params.get('fromDate')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  test('check-in gửi appointmentId và cờ cấp cứu rồi hiện đợt khám vừa tạo', async () => {
    api({ 'POST /api/v1/clinical/pet-visits/check-in': () => success({ ...VISIT, emergency: true }, 201) });
    renderAt('/reception/intake');

    userEvent.click(await screen.findByRole('button', { name: 'Chọn' }));
    userEvent.type(screen.getByLabelText('Triệu chứng ban đầu (không bắt buộc)'), 'Bỏ ăn');
    userEvent.click(screen.getByLabelText('Ca cấp cứu (ưu tiên tuyệt đối)'));
    userEvent.click(screen.getByRole('button', { name: 'Tiếp nhận' }));

    expect(await screen.findByText('VIS-20261102-00031')).toBeInTheDocument();
    expect(screen.getByText('Cấp cứu')).toBeInTheDocument();
    expect(bodyOf(callsTo('POST /api/v1/clinical/pet-visits/check-in')[0])).toEqual({ appointmentId: 7, emergency: true, initialSymptoms: 'Bỏ ăn' });
    expect(screen.getByRole('link', { name: /Về bảng điều phối/ })).toHaveAttribute('href', '/reception/queue');
  });

  test('lỗi check-in (lịch đã dùng) hiện thông báo và không chuyển sang màn kết quả', async () => {
    api({ 'POST /api/v1/clinical/pet-visits/check-in': () => failure(409, 'CLIN_1306', 'Thú cưng đang có đợt khám mở') });
    renderAt('/reception/intake');

    userEvent.click(await screen.findByRole('button', { name: 'Chọn' }));
    userEvent.click(screen.getByRole('button', { name: 'Tiếp nhận' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Thú cưng đang có đợt khám mở');
    expect(screen.queryByText('Đã tiếp nhận')).not.toBeInTheDocument();
  });
});

describe('tiếp đón: khách vãng lai', () => {
  const api = (extra = {}) => mockApi({
    'GET /api/v1/auth/me': ME,
    'GET /api/v1/appointments': () => success(pageOf([])),
    'GET /api/v1/auth/users': () => success(pageOf([OWNER])),
    'GET /api/v1/pets': () => success(pageOf([PET])),
    ...extra,
  });

  const openWalkIn = async () => {
    userEvent.click(await screen.findByRole('tab', { name: 'Khách vãng lai' }));
  };

  test('tìm chủ nuôi (chỉ vai trò chủ nuôi), chọn thú cưng rồi check-in bằng petId', async () => {
    api({ 'POST /api/v1/clinical/pet-visits/check-in': () => success(VISIT, 201) });
    renderAt('/reception/intake');
    await openWalkIn();

    userEvent.type(screen.getByLabelText('Tìm chủ nuôi'), 'Hoa');
    userEvent.click(await screen.findByRole('button', { name: 'Chọn' }));
    // Chủ nuôi chỉ có một thú cưng nên được chọn sẵn.
    await waitFor(() => expect(screen.getByLabelText('Thú cưng *')).toHaveValue('21'));
    userEvent.click(screen.getByRole('button', { name: 'Tiếp nhận' }));

    expect(await screen.findByText('VIS-20261102-00031')).toBeInTheDocument();
    expect(bodyOf(callsTo('POST /api/v1/clinical/pet-visits/check-in')[0])).toEqual({ petId: 21, emergency: false, initialSymptoms: null });
    const search = new URL(String(callsTo('GET /api/v1/auth/users')[0][0])).searchParams;
    expect(search.get('role')).toBe('PET_OWNER');
    expect(search.get('keyword')).toBe('Hoa');
  });

  test('chưa chọn chủ nuôi thì không check-in', async () => {
    api({ 'POST /api/v1/clinical/pet-visits/check-in': () => success(VISIT, 201) });
    renderAt('/reception/intake');
    await openWalkIn();

    userEvent.click(screen.getByRole('button', { name: 'Tiếp nhận' }));

    expect(await screen.findByText('Chọn hoặc tạo chủ nuôi.')).toBeInTheDocument();
    expect(callsTo('POST /api/v1/clinical/pet-visits/check-in')).toHaveLength(0);
  });

  test('tạo khách hàng mới: gửi vai trò PET_OWNER kèm mật khẩu tạm và chỉ hiện mật khẩu đó một lần', async () => {
    api({
      'GET /api/v1/auth/users': () => success(pageOf([])),
      'GET /api/v1/pets': () => success(pageOf([])),
      'POST /api/v1/auth/users': ({ body }) => success({ id: 9, username: body.phoneNumber, fullName: body.fullName, phoneNumber: body.phoneNumber, email: body.email }, 201),
    });
    renderAt('/reception/intake');
    await openWalkIn();

    userEvent.click(screen.getByRole('button', { name: '+ Khách hàng mới' }));
    const dialog = await screen.findByRole('dialog');
    userEvent.type(within(dialog).getByLabelText('Họ và tên *'), 'Ngô Văn Khách');
    userEvent.type(within(dialog).getByLabelText(/^Số điện thoại */), '0933333333');
    userEvent.click(within(dialog).getByRole('button', { name: 'Tạo tài khoản' }));

    const shown = await screen.findByTestId('temporary-password');
    const body = bodyOf(callsTo('POST /api/v1/auth/users')[0]);
    expect(body).toMatchObject({ fullName: 'Ngô Văn Khách', phoneNumber: '0933333333', roleCode: 'PET_OWNER' });
    expect(body.password).toMatch(/^(?=.*[A-Za-z])(?=.*\d).{8,72}$/);
    expect(shown).toHaveTextContent(body.password);

    userEvent.click(screen.getByRole('button', { name: 'Đã ghi lại' }));
    expect(screen.queryByTestId('temporary-password')).not.toBeInTheDocument();
    // Sau khi tạo, chủ nuôi mới được chọn sẵn.
    expect(await screen.findByText('Ngô Văn Khách')).toBeInTheDocument();
  });

  test('số điện thoại sai định dạng bị chặn ngay trên biểu mẫu', async () => {
    api({ 'POST /api/v1/auth/users': () => success({}, 201) });
    renderAt('/reception/intake');
    await openWalkIn();

    userEvent.click(screen.getByRole('button', { name: '+ Khách hàng mới' }));
    const dialog = await screen.findByRole('dialog');
    userEvent.type(within(dialog).getByLabelText('Họ và tên *'), 'Ngô Văn Khách');
    userEvent.type(within(dialog).getByLabelText(/^Số điện thoại */), '12345');
    userEvent.click(within(dialog).getByRole('button', { name: 'Tạo tài khoản' }));

    expect(await within(dialog).findByText(/đúng 10 số/)).toBeInTheDocument();
    expect(callsTo('POST /api/v1/auth/users')).toHaveLength(0);
  });
});

describe('điều phối hàng đợi', () => {
  const queueItem = { ...VISIT, doctorId: null, assignedRoom: null };
  const api = (extra = {}) => mockApi({
    'GET /api/v1/auth/me': ME,
    'GET /api/v1/clinical/pet-visits/queue': () => success([queueItem, { ...queueItem, id: 32, visitCode: 'VIS-20261102-00032', petName: 'Rex', emergency: true }]),
    'GET /api/v1/clinical/pet-visits': () => success(pageOf([])),
    'GET /api/v1/clinical/doctors/status': () => success(DOCTORS),
    ...extra,
  });

  test('hiện hàng đợi, trạng thái từng bác sĩ và số ca cấp cứu', async () => {
    api();
    renderAt('/reception/queue');

    expect(await screen.findByText('Kitty')).toBeInTheDocument();
    expect(screen.getByText(/CẤP CỨU/)).toBeInTheDocument();
    expect(screen.getByText('1 ca cấp cứu')).toBeInTheDocument();
    const doctorTable = screen.getByRole('table', { name: 'Trạng thái bác sĩ' });
    expect(within(doctorTable).getByText('Sẵn sàng')).toBeInTheDocument();
    expect(within(doctorTable).getByText('Đang bận')).toBeInTheDocument();
    expect(within(doctorTable).getByText('Khóa ưu tiên tái khám')).toBeInTheDocument();
    // Cảnh báo bác sĩ đang giữ khóa ưu tiên.
    expect(screen.getByText(/BS\. Đỗ Chi/, { selector: 'span' })).toBeInTheDocument();
  });

  test('chỉ bác sĩ sẵn sàng chọn được; bác sĩ bận hoặc đang giữ khóa ưu tiên bị vô hiệu', async () => {
    api();
    renderAt('/reception/queue');
    userEvent.click((await screen.findAllByRole('button', { name: 'Phân công' }))[0]);
    const dialog = await screen.findByRole('dialog');

    expect(within(dialog).getByRole('option', { name: /BS\. Phạm An/ })).toBeEnabled();
    expect(within(dialog).getByRole('option', { name: /BS\. Võ Bình/ })).toBeDisabled();
    expect(within(dialog).getByRole('option', { name: /BS\. Đỗ Chi/ })).toBeDisabled();
  });

  test('phân công gửi doctorId dạng số tới đúng ca khám', async () => {
    api({ 'POST /api/v1/clinical/pet-visits/31/assign': () => success({ ...queueItem, doctorId: 101 }) });
    renderAt('/reception/queue');
    userEvent.click((await screen.findAllByRole('button', { name: 'Phân công' }))[0]);
    const dialog = await screen.findByRole('dialog');

    userEvent.click(within(dialog).getByRole('button', { name: 'Phân công' }));
    expect(await within(dialog).findByText('Chọn bác sĩ phụ trách.')).toBeInTheDocument();
    expect(callsTo('POST /api/v1/clinical/pet-visits/31/assign')).toHaveLength(0);

    userEvent.selectOptions(within(dialog).getByLabelText('Bác sĩ *'), '101');
    userEvent.click(within(dialog).getByRole('button', { name: 'Phân công' }));

    expect(await screen.findByText('Đã phân công bác sĩ cho Kitty (VIS-20261102-00031).')).toBeInTheDocument();
    expect(bodyOf(callsTo('POST /api/v1/clinical/pet-visits/31/assign')[0])).toEqual({ doctorId: 101 });
  });

  test('bác sĩ vừa bận (race giữa hai lễ tân) thì hiện lỗi của máy chủ và giữ hộp thoại', async () => {
    api({ 'POST /api/v1/clinical/pet-visits/31/assign': () => failure(409, 'CLIN_1302', 'Bác sĩ đang bận') });
    renderAt('/reception/queue');
    userEvent.click((await screen.findAllByRole('button', { name: 'Phân công' }))[0]);
    const dialog = await screen.findByRole('dialog');

    userEvent.selectOptions(within(dialog).getByLabelText('Bác sĩ *'), '101');
    userEvent.click(within(dialog).getByRole('button', { name: 'Phân công' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Bác sĩ đang bận');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  test('hủy đợt khám bắt buộc có lý do', async () => {
    api({ 'POST /api/v1/clinical/pet-visits/31/cancel': () => success({ ...queueItem, status: 'CANCELLED' }) });
    renderAt('/reception/queue');
    userEvent.click((await screen.findAllByRole('button', { name: 'Hủy' }))[0]);
    const dialog = await screen.findByRole('dialog');

    userEvent.click(within(dialog).getByRole('button', { name: 'Hủy đợt khám' }));
    expect(await within(dialog).findByText('Nhập lý do hủy.')).toBeInTheDocument();

    userEvent.type(within(dialog).getByLabelText('Lý do *'), 'Khách đổi ý');
    userEvent.click(within(dialog).getByRole('button', { name: 'Hủy đợt khám' }));

    expect(await screen.findByText('Đã hủy đợt khám VIS-20261102-00031.')).toBeInTheDocument();
    expect(bodyOf(callsTo('POST /api/v1/clinical/pet-visits/31/cancel')[0])).toEqual({ reason: 'Khách đổi ý' });
  });
});

describe('duyệt lịch hẹn của lễ tân', () => {
  const pending = { ...todayAppointment, status: 'PENDING' };
  const api = (extra = {}) => mockApi({
    'GET /api/v1/auth/me': ME,
    'GET /api/v1/appointments': () => success(pageOf([pending])),
    ...extra,
  });

  test('tab chờ duyệt lấy lịch PENDING cũ nhất trước', async () => {
    api();
    renderAt('/reception/appointments');
    await screen.findByText('APT-20261102-00007');

    const params = new URL(String(callsTo('GET /api/v1/appointments')[0][0])).searchParams;
    expect(params.get('status')).toBe('PENDING');
    expect(params.get('sortBy')).toBe('createdAt');
    expect(params.get('ascending')).toBe('true');
  });

  test('xác nhận lịch gọi đúng endpoint confirm', async () => {
    api({ 'POST /api/v1/appointments/7/confirm': () => success({ ...pending, status: 'CONFIRMED' }) });
    renderAt('/reception/appointments');

    userEvent.click(await screen.findByRole('button', { name: 'Xác nhận' }));

    expect(await screen.findByText('Đã xác nhận lịch APT-20261102-00007.')).toBeInTheDocument();
    expect(callsTo('POST /api/v1/appointments/7/confirm')).toHaveLength(1);
  });

  test('từ chối lịch bắt buộc có lý do và gửi qua endpoint cancel', async () => {
    api({ 'POST /api/v1/appointments/7/cancel': () => success({ ...pending, status: 'CANCELLED' }) });
    renderAt('/reception/appointments');

    userEvent.click(await screen.findByRole('button', { name: 'Từ chối' }));
    const dialog = await screen.findByRole('dialog');
    userEvent.click(within(dialog).getByRole('button', { name: 'Xác nhận hủy' }));
    expect(await within(dialog).findByText('Nhập lý do.')).toBeInTheDocument();

    userEvent.type(within(dialog).getByLabelText('Lý do *'), 'Hết lịch bác sĩ');
    userEvent.click(within(dialog).getByRole('button', { name: 'Xác nhận hủy' }));

    await waitFor(() => expect(callsTo('POST /api/v1/appointments/7/cancel')).toHaveLength(1));
    expect(bodyOf(callsTo('POST /api/v1/appointments/7/cancel')[0])).toEqual({ reason: 'Hết lịch bác sĩ' });
  });

  test('đặt lịch hộ khách: bắt buộc chọn chủ nuôi, thú cưng, khung giờ và lý do', async () => {
    api({ 'POST /api/v1/appointments': () => success(todayAppointment, 201) });
    renderAt('/reception/appointments');

    userEvent.click(await screen.findByRole('button', { name: '+ Đặt lịch hộ khách' }));
    const dialog = await screen.findByRole('dialog');
    userEvent.click(within(dialog).getByRole('button', { name: 'Tạo lịch hẹn' }));

    expect(await within(dialog).findByText('Chọn chủ nuôi và thú cưng.')).toBeInTheDocument();
    expect(callsTo('POST /api/v1/appointments')).toHaveLength(0);
  });
});

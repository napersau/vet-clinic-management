import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { bodyOf, callsTo, failure, mockApi, pageOf, renderAt, signedInAs, success } from '../../testUtils';

const PET = { id: 11, petCode: 'PET-2026-00011', name: 'Mực', speciesId: 1, speciesCode: 'DOG', speciesName: 'Chó', gender: 'MALE', dateOfBirth: '2022-03-01' };
const SPECIES = [{ id: 1, code: 'DOG', name: 'Chó' }, { id: 2, code: 'CAT', name: 'Mèo' }];

const slot = (time, over = {}) => ({ startTime: `${time}:00`, endTime: `${time.slice(0, 3)}30:00`, capacity: 2, booked: 0, remaining: 2, available: true, past: false, ...over });
const SLOTS = [slot('08:00', { past: true, available: false, remaining: 0 }), slot('08:30', { available: false, remaining: 0, booked: 2 }), slot('09:00', { remaining: 1, booked: 1 }), slot('09:30')];

const APPOINTMENT = {
  id: 7, appointmentCode: 'APT-20261102-00007', petId: 11, petName: 'Mực', ownerId: 1, appointmentDate: '2099-01-01', startTime: '09:00:00', endTime: '09:30:00',
  reason: 'Khám định kỳ', status: 'PENDING', bookingChannel: 'ONLINE', createdAt: '2026-11-01T01:00:00Z',
};

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
  window.scrollTo = jest.fn();
  signedInAs('owner');
});
afterEach(() => { delete global.fetch; });

describe('thú cưng của chủ nuôi', () => {
  const api = (extra = {}) => mockApi({
    'GET /api/v1/auth/me': () => success({ id: 1, fullName: 'Nguyễn Văn An', phoneNumber: '0920000001', role: 'PET_OWNER' }),
    'GET /api/v1/pets': () => success(pageOf([PET])),
    'GET /api/v1/species': () => success(SPECIES),
    ...extra,
  });

  test('liệt kê thú cưng của mình kèm liên kết hồ sơ và đặt lịch', async () => {
    api();
    renderAt('/portal/pets');

    expect(await screen.findByRole('link', { name: 'Mực' })).toHaveAttribute('href', '/portal/pets/11');
    // Header cũng có nút "Đặt lịch" chung; ở đây chỉ kiểm tra nút gắn sẵn thú cưng này.
    expect(screen.getAllByRole('link', { name: 'Đặt lịch' }).map((link) => link.getAttribute('href'))).toContain('/portal/booking?petId=11');
  });

  test('thêm thú cưng gửi loài và tên, không gửi ownerId (backend lấy từ token)', async () => {
    api({ 'POST /api/v1/pets': ({ body }) => success({ ...PET, id: 12, name: body.name }, 201) });
    renderAt('/portal/pets');
    await screen.findByRole('link', { name: 'Mực' });

    userEvent.click(screen.getByRole('button', { name: '+ Thêm thú cưng' }));
    const dialog = await screen.findByRole('dialog');
    await within(dialog).findByRole('option', { name: 'Mèo' });
    userEvent.type(within(dialog).getByLabelText('Tên thú cưng *'), 'Mimi');
    userEvent.selectOptions(within(dialog).getByLabelText('Loài *'), '2');
    userEvent.click(within(dialog).getByRole('button', { name: 'Thêm thú cưng' }));

    await waitFor(() => expect(callsTo('POST /api/v1/pets')).toHaveLength(1));
    const body = bodyOf(callsTo('POST /api/v1/pets')[0]);
    expect(body).toMatchObject({ name: 'Mimi', speciesId: 2, gender: 'UNKNOWN', neutered: false });
    expect(body).not.toHaveProperty('ownerId');
    expect(await screen.findByText('Đã lưu hồ sơ của Mimi.')).toBeInTheDocument();
  });

  test('không gửi khi thiếu tên hoặc loài', async () => {
    api({ 'POST /api/v1/pets': () => success(PET, 201) });
    renderAt('/portal/pets');
    await screen.findByRole('link', { name: 'Mực' });

    userEvent.click(screen.getByRole('button', { name: '+ Thêm thú cưng' }));
    const dialog = await screen.findByRole('dialog');
    userEvent.click(within(dialog).getByRole('button', { name: 'Thêm thú cưng' }));

    expect(await within(dialog).findByText('Nhập tên thú cưng.')).toBeInTheDocument();
    expect(within(dialog).getByText('Chọn loài.')).toBeInTheDocument();
    expect(callsTo('POST /api/v1/pets')).toHaveLength(0);
  });
});

describe('đặt lịch khám trực tuyến', () => {
  const api = (extra = {}) => mockApi({
    'GET /api/v1/auth/me': () => success({ id: 1, fullName: 'Nguyễn Văn An', phoneNumber: '0920000001', role: 'PET_OWNER' }),
    'GET /api/v1/pets': () => success(pageOf([PET])),
    'GET /api/v1/species': () => success(SPECIES),
    'GET /api/v1/appointments/slots': () => success(SLOTS),
    ...extra,
  });

  test('lưới khung giờ khóa khung đã qua và khung hết chỗ, hiện số chỗ còn lại', async () => {
    api();
    renderAt('/portal/booking');

    const group = await screen.findByRole('group', { name: 'Khung giờ khám' });
    expect(within(group).getByRole('button', { name: /08:00/ })).toBeDisabled();
    expect(within(group).getByText('Đã qua')).toBeInTheDocument();
    expect(within(group).getByRole('button', { name: /08:30/ })).toBeDisabled();
    expect(within(group).getByText('Hết chỗ')).toBeInTheDocument();
    expect(within(group).getByRole('button', { name: /09:00/ })).toBeEnabled();
    expect(within(group).getByText('Còn 1/2')).toBeInTheDocument();
    expect(within(group).getByText('Còn 2/2')).toBeInTheDocument();
  });

  test('chặn gửi khi chưa chọn thú cưng, khung giờ và lý do', async () => {
    api({ 'POST /api/v1/appointments': () => success(APPOINTMENT, 201) });
    renderAt('/portal/booking');
    await screen.findByRole('group', { name: 'Khung giờ khám' });

    userEvent.click(screen.getByRole('button', { name: 'Gửi yêu cầu đặt lịch' }));

    expect(await screen.findByText('Chọn thú cưng cần khám.')).toBeInTheDocument();
    expect(screen.getByText('Chọn một khung giờ còn trống.')).toBeInTheDocument();
    expect(screen.getByText('Mô tả ngắn lý do khám hoặc triệu chứng.')).toBeInTheDocument();
    expect(callsTo('POST /api/v1/appointments')).toHaveLength(0);
  });

  test('đặt lịch gửi đúng petId, ngày, giờ, lý do và không gửi ownerId hay kênh đặt', async () => {
    api({ 'POST /api/v1/appointments': () => success(APPOINTMENT, 201) });
    renderAt('/portal/booking?petId=11');
    const group = await screen.findByRole('group', { name: 'Khung giờ khám' });

    userEvent.click(within(group).getByRole('button', { name: /09:00/ }));
    userEvent.type(screen.getByLabelText('Lý do khám / triệu chứng *'), 'Khám định kỳ');
    userEvent.click(screen.getByRole('button', { name: 'Gửi yêu cầu đặt lịch' }));

    expect(await screen.findByText('Đã gửi yêu cầu đặt lịch')).toBeInTheDocument();
    expect(screen.getByText('APT-20261102-00007')).toBeInTheDocument();
    const body = bodyOf(callsTo('POST /api/v1/appointments')[0]);
    expect(body).toEqual({ petId: 11, appointmentDate: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/), startTime: '09:00', reason: 'Khám định kỳ' });
  });

  test('khung giờ vừa bị người khác đặt: hiện lỗi của máy chủ và giữ nguyên biểu mẫu', async () => {
    api({ 'POST /api/v1/appointments': () => failure(409, 'APPT_1203', 'Khung giờ đã hết chỗ') });
    renderAt('/portal/booking?petId=11');
    const group = await screen.findByRole('group', { name: 'Khung giờ khám' });

    userEvent.click(within(group).getByRole('button', { name: /09:30/ }));
    userEvent.type(screen.getByLabelText('Lý do khám / triệu chứng *'), 'Tiêm phòng');
    userEvent.click(screen.getByRole('button', { name: 'Gửi yêu cầu đặt lịch' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Khung giờ đã hết chỗ');
    expect(screen.getByLabelText('Lý do khám / triệu chứng *')).toHaveValue('Tiêm phòng');
  });

  test('chưa có thú cưng thì mời thêm thú cưng trước khi đặt lịch', async () => {
    api({ 'GET /api/v1/pets': () => success(pageOf([])) });
    renderAt('/portal/booking');

    expect(await screen.findByText(/cần có hồ sơ thú cưng trước khi đặt lịch/)).toBeInTheDocument();
    expect(screen.queryByRole('group', { name: 'Khung giờ khám' })).not.toBeInTheDocument();
  });
});

describe('lịch hẹn của tôi', () => {
  const api = (extra = {}) => mockApi({
    'GET /api/v1/auth/me': () => success({ id: 1, fullName: 'Nguyễn Văn An', phoneNumber: '0920000001', role: 'PET_OWNER' }),
    'GET /api/v1/appointments': () => success(pageOf([APPOINTMENT, { ...APPOINTMENT, id: 8, appointmentCode: 'APT-20261102-00008', status: 'CHECKED_IN' }])),
    ...extra,
  });

  test('chỉ lịch chờ xác nhận hoặc đã xác nhận mới có nút đổi/hủy', async () => {
    api();
    renderAt('/portal/appointments');

    const rows = await screen.findAllByRole('row');
    const pending = rows.find((row) => within(row).queryByText('APT-20261102-00007'));
    const checkedIn = rows.find((row) => within(row).queryByText('APT-20261102-00008'));
    expect(within(pending).getByRole('button', { name: 'Hủy' })).toBeInTheDocument();
    expect(within(pending).getByRole('button', { name: 'Đổi lịch' })).toBeInTheDocument();
    expect(within(checkedIn).queryByRole('button', { name: 'Hủy' })).not.toBeInTheDocument();
  });

  test('hủy lịch bắt buộc có lý do rồi gửi lên backend', async () => {
    api({ 'POST /api/v1/appointments/7/cancel': () => success({ ...APPOINTMENT, status: 'CANCELLED' }) });
    renderAt('/portal/appointments');
    userEvent.click(await screen.findByRole('button', { name: 'Hủy' }));
    const dialog = await screen.findByRole('dialog');

    userEvent.click(within(dialog).getByRole('button', { name: 'Xác nhận hủy' }));
    expect(await within(dialog).findByText('Nhập lý do.')).toBeInTheDocument();
    expect(callsTo('POST /api/v1/appointments/7/cancel')).toHaveLength(0);

    userEvent.click(within(dialog).getByRole('button', { name: 'Bận việc đột xuất' }));
    userEvent.click(within(dialog).getByRole('button', { name: 'Xác nhận hủy' }));

    expect(await screen.findByText('Đã hủy lịch APT-20261102-00007.')).toBeInTheDocument();
    expect(bodyOf(callsTo('POST /api/v1/appointments/7/cancel')[0])).toEqual({ reason: 'Bận việc đột xuất' });
  });

  test('lọc theo trạng thái gửi tham số status lên backend', async () => {
    api();
    renderAt('/portal/appointments');
    await screen.findByText('APT-20261102-00007');

    userEvent.click(screen.getByRole('tab', { name: 'Đã hủy' }));

    await waitFor(() => expect(callsTo('GET /api/v1/appointments').some(([url]) => new URL(String(url)).searchParams.get('status') === 'CANCELLED')).toBe(true));
  });
});

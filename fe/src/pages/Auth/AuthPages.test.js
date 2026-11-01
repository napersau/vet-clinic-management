import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { SESSION_KEY } from '../../config';
import { AuthProvider } from '../../hooks';
import AppRoutes from '../../routes';

const OWNER = { id: 1, fullName: 'Nguyễn Văn An', phoneNumber: '0901234567', email: 'an@example.com', role: 'PET_OWNER' };
const RECEPTIONIST = { id: 2, fullName: 'Trần Lễ Tân', phoneNumber: '0910000001', email: '', role: 'RECEPTIONIST' };
const TOKENS = { accessToken: 'access-1', refreshToken: 'refresh-1', tokenType: 'Bearer', expiresIn: 1800 };

const reply = (status, body) => ({ ok: status < 400, status, text: async () => JSON.stringify(body) });
const success = (data, status = 200) => reply(status, { status, errorCode: null, message: 'OK', data });
const failure = (status, errorCode, message) => reply(status, { status, errorCode, message, data: null });

// Giả lập Gateway: khóa là đuôi đường dẫn, giá trị là hàm trả về phản hồi. Đường dẫn không khai báo trả 404.
function mockApi(routes) {
  global.fetch = jest.fn(async (url) => {
    const key = Object.keys(routes).find((suffix) => String(url).endsWith(suffix));
    return key ? routes[key]() : failure(404, 'SYS_1004', 'Không tìm thấy');
  });
}
const callsTo = (suffix) => global.fetch.mock.calls.filter(([url]) => String(url).endsWith(suffix));
const bodyOf = ([, init]) => JSON.parse(init.body);

const renderAt = (path) => render(
  <MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
    <AuthProvider><AppRoutes /></AuthProvider>
  </MemoryRouter>,
);

const signIn = (identifier, password = 'secret123') => {
  userEvent.type(screen.getByLabelText('Tên đăng nhập hoặc số điện thoại'), identifier);
  userEvent.type(screen.getByLabelText('Mật khẩu'), password);
  userEvent.click(screen.getByRole('button', { name: /^Đăng nhập/ }));
};

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
  window.scrollTo = jest.fn();
});
afterEach(() => { delete global.fetch; });

describe('đăng nhập', () => {
  test('gửi username đã chuẩn hóa, lưu phiên theo "ghi nhớ" và chuyển chủ nuôi về cổng khách hàng', async () => {
    mockApi({ '/auth/login': () => success({ ...TOKENS, user: OWNER }) });
    renderAt('/auth/login');

    signIn('0901 234 567');

    expect(await screen.findByRole('heading', { level: 1, name: 'Thông tin cá nhân' })).toBeInTheDocument();
    expect(bodyOf(callsTo('/auth/login')[0])).toEqual({ username: '0901234567', password: 'secret123' });
    // Không tích "Ghi nhớ đăng nhập": phiên chỉ nằm trong sessionStorage.
    expect(JSON.parse(sessionStorage.getItem(SESSION_KEY))).toMatchObject({ accessToken: 'access-1', refreshToken: 'refresh-1' });
    expect(localStorage.getItem(SESSION_KEY)).toBeNull();
  });

  test('tích "Ghi nhớ đăng nhập" thì lưu ở localStorage', async () => {
    mockApi({ '/auth/login': () => success({ ...TOKENS, user: OWNER }) });
    renderAt('/auth/login');

    userEvent.click(screen.getByLabelText('Ghi nhớ đăng nhập'));
    signIn('0901234567');

    await screen.findByRole('heading', { level: 1, name: 'Thông tin cá nhân' });
    expect(localStorage.getItem(SESSION_KEY)).not.toBeNull();
    expect(sessionStorage.getItem(SESSION_KEY)).toBeNull();
  });

  test('tên đăng nhập không phải số điện thoại được giữ nguyên chữ hoa/thường', async () => {
    mockApi({ '/auth/login': () => failure(401, 'AUTH_1103', 'Sai thông tin đăng nhập') });
    renderAt('/auth/login');

    signIn('  Admin  ');

    await screen.findByRole('alert');
    expect(bodyOf(callsTo('/auth/login')[0]).username).toBe('Admin');
  });

  test('sai thông tin hiện lỗi từ máy chủ, không lưu phiên và không gọi refresh', async () => {
    mockApi({ '/auth/login': () => failure(401, 'AUTH_1103', 'Tên đăng nhập hoặc mật khẩu không đúng') });
    renderAt('/auth/login');

    signIn('0901234567', 'sai-mat-khau1');

    expect(await screen.findByRole('alert')).toHaveTextContent('Tên đăng nhập hoặc mật khẩu không đúng');
    expect(localStorage.getItem(SESSION_KEY)).toBeNull();
    expect(sessionStorage.getItem(SESSION_KEY)).toBeNull();
    expect(global.fetch).toHaveBeenCalledTimes(1);
  });

  test('lễ tân đăng nhập vào khu vực lễ tân, thanh bên chỉ có mục của vai trò mình', async () => {
    mockApi({ '/auth/login': () => success({ ...TOKENS, user: RECEPTIONIST }) });
    renderAt('/auth/login');

    signIn('0910000001');

    const sidebar = await screen.findByRole('navigation', { name: 'Điều hướng lễ tân' });
    expect(within(sidebar).getByRole('link', { name: /Bảng điều phối/ })).toBeInTheDocument();
    expect(within(sidebar).getByRole('link', { name: /Tiếp nhận khách/ })).toBeInTheDocument();
    expect(within(sidebar).queryByRole('link', { name: /Tài khoản & nhân sự/ })).not.toBeInTheDocument();
    expect(screen.getByText('Không gian lễ tân')).toBeInTheDocument();
  });

  test('quay lại trang đang xem dở sau khi đăng nhập nếu vai trò được phép vào', async () => {
    mockApi({ '/auth/login': () => success({ ...TOKENS, user: RECEPTIONIST }) });
    renderAt('/reception/invoices');

    // Chưa đăng nhập: bị đưa về trang đăng nhập.
    expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeInTheDocument();
    signIn('0910000001');

    expect(await screen.findByRole('heading', { level: 1, name: 'Hóa đơn & thanh toán' })).toBeInTheDocument();
  });
});

describe('đăng ký', () => {
  const fill = ({ phone = '0901 234 567', password = 'secret123', confirmation = password } = {}) => {
    userEvent.type(screen.getByLabelText('Họ và tên'), '  Nguyễn Văn An ');
    userEvent.type(screen.getByLabelText('Số điện thoại'), phone);
    userEvent.type(screen.getByLabelText('Mật khẩu'), password);
    userEvent.type(screen.getByLabelText('Xác nhận mật khẩu'), confirmation);
    userEvent.click(screen.getByRole('checkbox'));
    userEvent.click(screen.getByRole('button', { name: /^Tạo tài khoản/ }));
  };

  test('chặn gửi khi số điện thoại, mật khẩu hoặc xác nhận không hợp lệ', async () => {
    mockApi({});
    renderAt('/auth/register');

    fill({ phone: '12345', password: 'chuyenchu', confirmation: 'khac-nhau' });

    expect(await screen.findByText(/đúng 10 chữ số/)).toBeInTheDocument();
    expect(screen.getByText(/ít nhất 1 chữ cái và 1 chữ số/)).toBeInTheDocument();
    expect(screen.getByText('Mật khẩu xác nhận chưa khớp.')).toBeInTheDocument();
    expect(global.fetch).not.toHaveBeenCalled();
  });

  test('gửi đúng trường của backend, không gửi vai trò, rồi đăng nhập luôn bằng số điện thoại', async () => {
    mockApi({
      '/auth/register': () => success(OWNER, 201),
      '/auth/login': () => success({ ...TOKENS, user: OWNER }),
    });
    renderAt('/auth/register');

    fill();

    expect(await screen.findByRole('heading', { level: 1, name: 'Thông tin cá nhân' })).toBeInTheDocument();
    expect(bodyOf(callsTo('/auth/register')[0])).toEqual({ fullName: 'Nguyễn Văn An', phoneNumber: '0901234567', password: 'secret123' });
    expect(bodyOf(callsTo('/auth/login')[0])).toEqual({ username: '0901234567', password: 'secret123' });
  });

  test('số điện thoại đã đăng ký hiện thông báo của máy chủ và không đăng nhập', async () => {
    mockApi({ '/auth/register': () => failure(409, 'AUTH_1102', 'Số điện thoại đã được đăng ký') });
    renderAt('/auth/register');

    fill();

    expect(await screen.findByRole('alert')).toHaveTextContent('Số điện thoại đã được đăng ký');
    expect(callsTo('/auth/login')).toHaveLength(0);
    expect(screen.getByRole('button', { name: /^Tạo tài khoản/ })).toBeEnabled();
  });

  test('lỗi từng trường của backend (SYS_1001) hiện đúng ô nhập', async () => {
    mockApi({ '/auth/register': () => reply(400, { status: 400, errorCode: 'SYS_1001', message: 'Dữ liệu đầu vào không hợp lệ', data: { phoneNumber: 'Số điện thoại không hợp lệ' } }) });
    renderAt('/auth/register');

    fill();

    expect(await screen.findByText('Số điện thoại không hợp lệ')).toBeInTheDocument();
  });

  test('nút đăng ký bị vô hiệu cho tới khi đồng ý điều khoản', () => {
    renderAt('/auth/register');
    expect(screen.getByRole('button', { name: /^Tạo tài khoản/ })).toBeDisabled();
  });
});

describe('phiên đăng nhập và phân quyền', () => {
  const seed = (user) => localStorage.setItem(SESSION_KEY, JSON.stringify({ ...TOKENS, user }));

  test('menu tài khoản hiện vai trò, mục theo vai trò; đăng xuất thu hồi refresh token và về trang đăng nhập', async () => {
    seed(OWNER);
    mockApi({ '/auth/me': () => success(OWNER), '/auth/logout': () => success(null) });
    renderAt('/');

    userEvent.click(screen.getByRole('button', { name: 'Mở menu tài khoản' }));
    const menu = within(screen.getByRole('region', { name: 'Tài khoản của bạn' }));
    expect(menu.getByText('Khách hàng')).toBeInTheDocument();
    expect(menu.getByText('an@example.com')).toBeInTheDocument();
    expect(menu.getByRole('link', { name: /Đặt lịch khám/ })).toBeInTheDocument();
    expect(menu.queryByRole('link', { name: /Bảng điều phối/ })).not.toBeInTheDocument();

    userEvent.click(screen.getByRole('button', { name: 'Đăng xuất' }));

    expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeInTheDocument();
    expect(localStorage.getItem(SESSION_KEY)).toBeNull();
    await waitFor(() => expect(callsTo('/auth/logout')).toHaveLength(1));
    expect(bodyOf(callsTo('/auth/logout')[0])).toEqual({ refreshToken: 'refresh-1' });
  });

  test('vào khu vực nhân viên khi chưa đăng nhập thì bị đưa về trang đăng nhập', async () => {
    mockApi({});
    renderAt('/admin/dashboard');
    expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeInTheDocument();
  });

  test('sai vai trò thấy trang 403 kèm liên kết về khu vực của mình', async () => {
    seed(RECEPTIONIST);
    mockApi({ '/auth/me': () => success(RECEPTIONIST) });
    renderAt('/admin/dashboard');

    expect(await screen.findByRole('heading', { name: 'Bạn không có quyền truy cập' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Về khu vực của tôi' })).toHaveAttribute('href', '/reception/queue');
  });

  test('phiên hết hạn (refresh thất bại) đưa người dùng về trang đăng nhập', async () => {
    seed(RECEPTIONIST);
    mockApi({
      '/auth/me': () => failure(401, 'AUTH_1105', 'Hết hạn'),
      '/auth/refresh': () => failure(401, 'AUTH_1106', 'Token đã dùng'),
    });
    renderAt('/reception/queue');

    expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeInTheDocument();
    expect(localStorage.getItem(SESSION_KEY)).toBeNull();
  });
});

test('trang chủ hiển thị đủ các khu vực và có liên kết đăng ký khi chưa đăng nhập', () => {
  renderAt('/');
  ['services', 'patients', 'pricing', 'reviews', 'booking'].forEach((id) => expect(document.getElementById(id)).toBeInTheDocument());
  expect(screen.getByRole('link', { name: 'Đăng ký để đặt lịch' })).toHaveAttribute('href', '/auth/register');
});

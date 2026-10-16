import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../../hooks';
import AppRoutes from '../../routes';

const USER = { id: 1, fullName: 'Nguyễn Văn An', phone: '0901234567', email: 'an@example.com', role: 'PET_OWNER' };

const renderAt = (path) => render(
  <MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
    <AuthProvider><AppRoutes /></AuthProvider>
  </MemoryRouter>,
);

const mockFetch = (status, body) => {
  global.fetch = jest.fn().mockResolvedValue({ ok: status < 400, status, json: async () => body });
};

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
  window.scrollTo = jest.fn();
});

test('đăng nhập thành công lưu phiên và chuyển về trang chủ với tên người dùng trên header', async () => {
  mockFetch(200, { token: 'jwt-token', user: USER });
  renderAt('/auth/login');

  userEvent.type(screen.getByLabelText('Email hoặc số điện thoại'), '0901 234 567');
  userEvent.type(screen.getByLabelText('Mật khẩu'), 'secret123');
  userEvent.click(screen.getByRole('button', { name: /^Đăng nhập/ }));

  expect(await screen.findByRole('button', { name: 'Mở menu tài khoản' })).toBeInTheDocument();
  const [, request] = global.fetch.mock.calls[0];
  expect(JSON.parse(request.body)).toEqual({ identifier: '0901234567', password: 'secret123' });
  expect(JSON.parse(sessionStorage.getItem('pawcare_auth_session')).token).toBe('jwt-token');
  expect(localStorage.getItem('pawcare_auth_session')).toBeNull();
});

test('đăng nhập thất bại hiển thị thông báo lỗi từ máy chủ và không lưu phiên', async () => {
  mockFetch(401, { message: 'Sai tên đăng nhập hoặc mật khẩu.' });
  renderAt('/auth/login');

  userEvent.type(screen.getByLabelText('Email hoặc số điện thoại'), 'an@example.com');
  userEvent.type(screen.getByLabelText('Mật khẩu'), 'wrong-pass');
  userEvent.click(screen.getByRole('button', { name: /^Đăng nhập/ }));

  expect(await screen.findByRole('alert')).toHaveTextContent('Sai tên đăng nhập hoặc mật khẩu.');
  expect(localStorage.getItem('pawcare_auth_session')).toBeNull();
});

test('đăng ký chặn gửi khi số điện thoại hoặc mật khẩu xác nhận không hợp lệ', async () => {
  global.fetch = jest.fn();
  renderAt('/auth/register');

  userEvent.type(screen.getByLabelText('Họ và tên'), 'Nguyễn Văn An');
  userEvent.type(screen.getByLabelText('Số điện thoại'), '12345');
  userEvent.type(screen.getByLabelText('Mật khẩu'), 'secret123');
  userEvent.type(screen.getByLabelText('Xác nhận mật khẩu'), 'khac-nhau');
  userEvent.click(screen.getByRole('checkbox'));
  userEvent.click(screen.getByRole('button', { name: /^Tạo tài khoản/ }));

  expect(await screen.findByText(/đúng 10 chữ số/)).toBeInTheDocument();
  expect(screen.getByText('Mật khẩu xác nhận chưa khớp.')).toBeInTheDocument();
  expect(global.fetch).not.toHaveBeenCalled();
});

test('đăng ký hợp lệ gửi dữ liệu đã chuẩn hóa và đăng nhập luôn', async () => {
  mockFetch(201, { token: 'new-token', user: USER });
  renderAt('/auth/register');

  userEvent.type(screen.getByLabelText('Họ và tên'), '  Nguyễn Văn An ');
  userEvent.type(screen.getByLabelText('Số điện thoại'), '0901 234 567');
  userEvent.type(screen.getByLabelText('Mật khẩu'), 'secret123');
  userEvent.type(screen.getByLabelText('Xác nhận mật khẩu'), 'secret123');
  userEvent.click(screen.getByRole('checkbox'));
  userEvent.click(screen.getByRole('button', { name: /^Tạo tài khoản/ }));

  await waitFor(() => expect(screen.getByRole('button', { name: 'Mở menu tài khoản' })).toBeInTheDocument());
  const [, request] = global.fetch.mock.calls[0];
  expect(JSON.parse(request.body)).toEqual({ fullName: 'Nguyễn Văn An', phone: '0901234567', password: 'secret123' });
});

test('nút đăng ký bị vô hiệu cho tới khi đồng ý điều khoản', () => {
  renderAt('/auth/register');
  expect(screen.getByRole('button', { name: /^Tạo tài khoản/ })).toBeDisabled();
});

test('menu tài khoản hiển thị vai trò và đăng xuất đưa về trang đăng nhập', async () => {
  localStorage.setItem('pawcare_auth_session', JSON.stringify({ token: 'jwt-token', user: USER }));
  renderAt('/');

  userEvent.click(screen.getByRole('button', { name: 'Mở menu tài khoản' }));
  const menu = within(screen.getByRole('region', { name: 'Tài khoản của bạn' }));
  expect(menu.getByText('Khách hàng')).toBeInTheDocument();
  expect(menu.getByText('an@example.com')).toBeInTheDocument();
  userEvent.click(screen.getByRole('button', { name: 'Đăng xuất' }));

  expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeInTheDocument();
  expect(localStorage.getItem('pawcare_auth_session')).toBeNull();
});

test('trang chủ hiển thị đủ các khu vực và ảnh thiếu dùng biểu tượng dự phòng', () => {
  renderAt('/');
  ['services', 'patients', 'pricing', 'reviews', 'booking'].forEach((id) => expect(document.getElementById(id)).toBeInTheDocument());
  expect(screen.getByRole('link', { name: 'Đăng ký để đặt lịch' })).toHaveAttribute('href', '/auth/register');
});

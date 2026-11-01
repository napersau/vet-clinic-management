import { getSafeReturnPath, resolvePostLoginPath } from './navigation';

describe('getSafeReturnPath', () => {
  test('chỉ nhận đường dẫn nội bộ', () => {
    expect(getSafeReturnPath('/portal/pets')).toBe('/portal/pets');
    expect(getSafeReturnPath('//evil.example')).toBe('/');
    expect(getSafeReturnPath('https://evil.example')).toBe('/');
    expect(getSafeReturnPath(undefined, '/x')).toBe('/x');
  });
});

describe('resolvePostLoginPath', () => {
  test('không có trang cũ thì về trang đầu của vai trò', () => {
    expect(resolvePostLoginPath(undefined, 'RECEPTIONIST')).toBe('/reception/queue');
    expect(resolvePostLoginPath(undefined, 'ADMIN')).toBe('/admin/dashboard');
    expect(resolvePostLoginPath(undefined, 'PET_OWNER')).toBe('/portal/profile');
  });

  test('quay lại trang cũ nếu đúng khu vực của vai trò', () => {
    expect(resolvePostLoginPath('/reception/appointments', 'RECEPTIONIST')).toBe('/reception/appointments');
    expect(resolvePostLoginPath('/portal/booking', 'PET_OWNER')).toBe('/portal/booking');
    expect(resolvePostLoginPath('/', 'VETERINARIAN')).toBe('/');
  });

  test('trang của khu vực khác thì về trang đầu của vai trò, không rơi vào 403', () => {
    expect(resolvePostLoginPath('/admin/users', 'RECEPTIONIST')).toBe('/reception/queue');
    expect(resolvePostLoginPath('/vet/today', 'LAB_TECHNICIAN')).toBe('/lab/queue');
    expect(resolvePostLoginPath('/portal/pets', 'ADMIN')).toBe('/admin/dashboard');
  });

  test('nhân sự vẫn quay lại được trang cá nhân dùng chung', () => {
    expect(resolvePostLoginPath('/portal/profile', 'VETERINARIAN')).toBe('/portal/profile');
  });

  test('không chuyển hướng ra ngoài hoặc quay lại trang đăng nhập', () => {
    expect(resolvePostLoginPath('//evil.example', 'PET_OWNER')).toBe('/portal/profile');
    expect(resolvePostLoginPath('/auth/login', 'ADMIN')).toBe('/admin/dashboard');
  });
});

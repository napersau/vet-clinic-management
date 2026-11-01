import { ApiError, SESSION_EXPIRED, mapFieldErrors, request } from './client';
import { clearSession, getSession, setSession } from './session';

const ok = (data) => ({ ok: true, status: 200, text: async () => JSON.stringify({ status: 200, data }) });
const fail = (status, body) => ({ ok: false, status, text: async () => JSON.stringify(body) });

beforeEach(() => {
  clearSession();
  setSession({ accessToken: 'old-access', refreshToken: 'old-refresh', user: { id: 1 } }, true);
  global.fetch = jest.fn();
});
afterEach(() => { delete global.fetch; clearSession(); });

describe('request', () => {
  test('gắn Access Token và trả về data của ApiResponse', async () => {
    fetch.mockResolvedValueOnce(ok({ id: 7 }));
    await expect(request('/api/v1/pets/7')).resolves.toEqual({ id: 7 });
    expect(fetch.mock.calls[0][1].headers.Authorization).toBe('Bearer old-access');
  });

  test('bỏ qua tham số rỗng trên query', async () => {
    fetch.mockResolvedValueOnce(ok([]));
    await request('/api/v1/pets', { query: { keyword: '', status: undefined, page: 0 } });
    expect(fetch.mock.calls[0][0]).toMatch(/\/api\/v1\/pets\?page=0$/);
  });

  test('gặp 401 thì refresh MỘT lần rồi gọi lại với token mới', async () => {
    fetch
      .mockResolvedValueOnce(fail(401, { errorCode: 'AUTH_1105', message: 'hết hạn' }))
      .mockResolvedValueOnce(ok({ accessToken: 'new-access', refreshToken: 'new-refresh' }))
      .mockResolvedValueOnce(ok({ done: true }));
    await expect(request('/api/v1/pets')).resolves.toEqual({ done: true });
    expect(fetch.mock.calls[1][0]).toMatch(/\/auth\/refresh$/);
    expect(fetch.mock.calls[2][1].headers.Authorization).toBe('Bearer new-access');
    expect(getSession().refreshToken).toBe('new-refresh');
  });

  test('nhiều request cùng bị 401 chỉ gọi /refresh một lần (refresh token dùng một lần)', async () => {
    let refreshCalls = 0;
    fetch.mockImplementation(async (url, init) => {
      if (String(url).endsWith('/auth/refresh')) { refreshCalls += 1; await new Promise((resolve) => setTimeout(resolve, 20)); return ok({ accessToken: 'new-access', refreshToken: 'new-refresh' }); }
      return init.headers.Authorization === 'Bearer new-access' ? ok({ fine: true }) : fail(401, { errorCode: 'AUTH_1105', message: 'hết hạn' });
    });
    const results = await Promise.all([request('/a'), request('/b'), request('/c')]);
    expect(results).toEqual([{ fine: true }, { fine: true }, { fine: true }]);
    expect(refreshCalls).toBe(1);
  });

  test('refresh thất bại thì xóa phiên và phát SESSION_EXPIRED', async () => {
    const listener = jest.fn();
    window.addEventListener(SESSION_EXPIRED, listener);
    fetch.mockResolvedValueOnce(fail(401, { errorCode: 'AUTH_1105', message: 'hết hạn' })).mockResolvedValueOnce(fail(401, { errorCode: 'AUTH_1106', message: 'token đã dùng' }));
    await expect(request('/api/v1/pets')).rejects.toBeInstanceOf(ApiError);
    expect(getSession()).toBeNull();
    expect(listener).toHaveBeenCalled();
    window.removeEventListener(SESSION_EXPIRED, listener);
  });

  test('lỗi nghiệp vụ mang mã lỗi và trạng thái HTTP', async () => {
    fetch.mockResolvedValueOnce(fail(409, { errorCode: 'CLIN_1302', message: 'Bác sĩ đang bận' }));
    await expect(request('/x', { method: 'POST', body: {} })).rejects.toMatchObject({ code: 'CLIN_1302', status: 409, message: 'Bác sĩ đang bận' });
  });

  test('lỗi xác thực dữ liệu (SYS_1001) lấy thông báo của trường đầu tiên và giữ lỗi từng trường', async () => {
    fetch.mockResolvedValueOnce(fail(400, { errorCode: 'SYS_1001', message: 'Dữ liệu đầu vào không hợp lệ', data: { phoneNumber: 'Số điện thoại không hợp lệ' } }));
    const problem = await request('/x', { method: 'POST', body: {} }).catch((error) => error);
    expect(problem.message).toBe('Số điện thoại không hợp lệ');
    expect(mapFieldErrors(problem, { phoneNumber: 'phone' })).toEqual({ phone: 'Số điện thoại không hợp lệ' });
  });

  test('mất mạng trả ApiError mã NETWORK', async () => {
    fetch.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    await expect(request('/x')).rejects.toMatchObject({ code: 'NETWORK', status: 0 });
  });

  test('không gắn token và không refresh khi auth = false', async () => {
    fetch.mockResolvedValueOnce(fail(401, { errorCode: 'AUTH_1103', message: 'Sai thông tin đăng nhập' }));
    await expect(request('/api/v1/auth/login', { method: 'POST', body: {}, auth: false })).rejects.toMatchObject({ code: 'AUTH_1103' });
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(fetch.mock.calls[0][1].headers.Authorization).toBeUndefined();
  });
});

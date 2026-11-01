import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { SESSION_KEY } from './config';
import { AuthProvider } from './hooks';
import AppRoutes from './routes';

// Công cụ dùng chung cho test màn hình: giả lập Gateway bằng fetch, dựng cả ứng dụng ở một đường dẫn.

export const USERS = {
  owner: { id: 1, fullName: 'Nguyễn Văn An', phoneNumber: '0920000001', email: 'an@example.com', role: 'PET_OWNER' },
  receptionist: { id: 2, fullName: 'Trần Lễ Tân', phoneNumber: '0910000001', email: '', role: 'RECEPTIONIST' },
};
const TOKENS = { accessToken: 'access-1', refreshToken: 'refresh-1' };

export const pageOf = (items, extra = {}) => ({ items, page: 0, size: 20, totalElements: items.length, totalPages: 1, last: true, ...extra });

const reply = (status, body) => ({ ok: status < 400, status, text: async () => JSON.stringify(body) });
export const success = (data, status = 200) => reply(status, { status, errorCode: null, message: 'OK', data });
export const failure = (status, errorCode, message) => reply(status, { status, errorCode, message, data: null });

/**
 * Giả lập Gateway. Khóa có dạng 'METHOD /đường/dẫn' (không kèm query), giá trị là hàm nhận { url, body } và trả về phản hồi.
 * Đường dẫn không khai báo trả 404 để test lộ ra nếu màn hình gọi nhầm endpoint.
 */
export function mockApi(routes) {
  global.fetch = jest.fn(async (url, init = {}) => {
    const parsed = new URL(String(url));
    const key = `${init.method || 'GET'} ${parsed.pathname}`;
    const handler = routes[key];
    if (!handler) return failure(404, 'SYS_1004', `Chưa giả lập ${key}`);
    return handler({ url: parsed, body: typeof init.body === 'string' ? JSON.parse(init.body) : undefined });
  });
}

export const callsTo = (key) => global.fetch.mock.calls.filter(([url, init = {}]) => `${init.method || 'GET'} ${new URL(String(url)).pathname}` === key);
export const bodyOf = (call) => JSON.parse(call[1].body);

export function signedInAs(role) {
  localStorage.setItem(SESSION_KEY, JSON.stringify({ ...TOKENS, user: USERS[role] }));
  return USERS[role];
}

export const renderAt = (path) => render(
  <MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
    <AuthProvider><AppRoutes /></AuthProvider>
  </MemoryRouter>,
);

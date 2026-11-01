import { API_URL } from '../config';
import { clearSession, getSession, patchSession } from './session';

export const SESSION_EXPIRED = 'vetclinic:session-expired';

/**
 * Lỗi từ backend. code là mã nghiệp vụ (vd. "CLIN_1302"), status là mã HTTP, fieldErrors là lỗi theo từng trường
 * ({ phoneNumber: '...' }) khi backend trả SYS_1001 kèm danh sách trường sai.
 */
export class ApiError extends Error {
  constructor(message, { status = 0, code = '', fieldErrors = {} } = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

function buildUrl(path, query) {
  const url = new URL(`${API_URL}${path}`);
  Object.entries(query || {}).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return;
    if (Array.isArray(value)) value.forEach((item) => url.searchParams.append(key, item));
    else url.searchParams.set(key, value);
  });
  return url.toString();
}

async function parse(response) {
  const text = await response.text();
  if (!text) return {};
  try { return JSON.parse(text); } catch { return { message: text }; }
}

function toError(response, body) {
  const fieldErrors = body.errorCode === 'SYS_1001' && body.data && typeof body.data === 'object' ? body.data : {};
  const detail = Object.values(fieldErrors)[0];
  const message = body.message && body.message !== 'Dữ liệu đầu vào không hợp lệ' ? body.message : (detail || body.message);
  return new ApiError(message || `Không thể kết nối máy chủ (HTTP ${response.status}).`, { status: response.status, code: body.errorCode || '', fieldErrors });
}

// Hai request refresh đồng thời với cùng một token bị backend coi là đánh cắp và đăng xuất mọi thiết bị,
// nên mọi nơi cần token mới phải dùng chung MỘT lời gọi đang chạy.
let refreshing = null;
export function refreshAccessToken() {
  if (!refreshing) {
    const session = getSession();
    if (!session?.refreshToken) return Promise.reject(new ApiError('Phiên đăng nhập đã hết hạn.', { status: 401 }));
    refreshing = fetch(`${API_URL}/api/v1/auth/refresh`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: session.refreshToken }),
    }).then(async (response) => {
      const body = await parse(response);
      if (!response.ok) throw toError(response, body);
      patchSession({ accessToken: body.data.accessToken, refreshToken: body.data.refreshToken });
      return body.data.accessToken;
    }).catch((problem) => {
      clearSession();
      window.dispatchEvent(new Event(SESSION_EXPIRED));
      throw problem;
    }).finally(() => { refreshing = null; });
  }
  return refreshing;
}

async function send(path, { method, body, query, form, auth, token }) {
  const headers = {};
  if (auth && token) headers.Authorization = `Bearer ${token}`;
  if (form) { /* trình duyệt tự đặt boundary cho multipart */ } else if (body !== undefined) headers['Content-Type'] = 'application/json';
  try {
    return await fetch(buildUrl(path, query), { method, headers, body: form || (body !== undefined ? JSON.stringify(body) : undefined) });
  } catch {
    throw new ApiError('Không kết nối được máy chủ. Kiểm tra mạng hoặc thử lại sau.', { status: 0, code: 'NETWORK' });
  }
}

/**
 * Gọi API. Trả về `data` của ApiResponse (hoặc Blob khi as = 'blob'). Tự gắn Access Token; gặp 401 thì refresh MỘT lần rồi
 * gọi lại; refresh thất bại thì xóa phiên và phát SESSION_EXPIRED để giao diện quay về trang đăng nhập.
 */
export async function request(path, { method = 'GET', body, query, form, auth = true, as = 'json' } = {}) {
  let response = await send(path, { method, body, query, form, auth, token: getSession()?.accessToken });
  if (response.status === 401 && auth && getSession()?.refreshToken) {
    const token = await refreshAccessToken();
    response = await send(path, { method, body, query, form, auth, token });
  }
  if (as === 'blob' && response.ok) return response.blob();
  const payload = await parse(response);
  if (!response.ok || payload.errorCode) throw toError(response, payload);
  return payload.data;
}

export const api = {
  get: (path, query, options) => request(path, { ...options, query }),
  post: (path, body, options) => request(path, { ...options, method: 'POST', body: body === undefined ? {} : body }),
  put: (path, body, options) => request(path, { ...options, method: 'PUT', body }),
  upload: (path, form) => request(path, { method: 'POST', form }),
  blob: (path) => request(path, { as: 'blob' }),
};

/** Chuyển tên trường backend sang tên trường của form (vd. phoneNumber -> phone) để hiện lỗi đúng ô nhập. */
export function mapFieldErrors(problem, aliases = {}) {
  const errors = {};
  Object.entries(problem?.fieldErrors || {}).forEach(([key, message]) => { errors[aliases[key] || key] = message; });
  return errors;
}

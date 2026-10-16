import { getAccessToken } from '../utils/storage';

const API_BASE_URL = (process.env.REACT_APP_API_URL || 'http://localhost:8080/api').replace(/\/$/, '');

export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

const STATUS_MESSAGES = {
  0: 'Không thể kết nối máy chủ. Vui lòng thử lại sau.',
  400: 'Thông tin gửi lên chưa hợp lệ.',
  401: 'Thông tin đăng nhập không đúng hoặc phiên đã hết hạn.',
  403: 'Bạn không có quyền thực hiện thao tác này.',
  409: 'Thông tin đã tồn tại trong hệ thống.',
};

export async function request(path, { method = 'GET', body } = {}) {
  const token = getAccessToken();
  let response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers: {
        Accept: 'application/json',
        ...(body ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(STATUS_MESSAGES[0], 0);
  }

  const data = await response.json().catch(() => null);
  if (!response.ok) {
    throw new ApiError(data?.message || STATUS_MESSAGES[response.status] || 'Đã xảy ra lỗi, vui lòng thử lại.', response.status);
  }
  return data;
}

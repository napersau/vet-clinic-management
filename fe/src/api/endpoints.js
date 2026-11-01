import { api, request } from './client';

// Một hàm cho mỗi endpoint của backend (qua Gateway). Không chứa logic nghiệp vụ; tên trường giữ nguyên theo backend.
// Các nhóm endpoint khác (appointments, clinical, billing...) được thêm cùng lúc với màn hình dùng chúng.
const AUTH = '/api/v1/auth';

export const authApi = {
  // Ba endpoint dưới đây là public: không gắn Access Token và không tự refresh khi gặp 401.
  login: (username, password) => request(`${AUTH}/login`, { method: 'POST', body: { username, password }, auth: false }),
  register: (body) => request(`${AUTH}/register`, { method: 'POST', body, auth: false }),
  logout: (refreshToken) => request(`${AUTH}/logout`, { method: 'POST', body: { refreshToken }, auth: false }),
  me: () => api.get(`${AUTH}/me`),
  updateMe: (body) => api.put(`${AUTH}/me`, body),
  changePassword: (body) => api.post(`${AUTH}/change-password`, body),
};

export const usersApi = {
  list: (query) => api.get(`${AUTH}/users`, query),
  doctors: () => api.get(`${AUTH}/users/doctors`),
  get: (id) => api.get(`${AUTH}/users/${id}`),
  create: (body) => api.post(`${AUTH}/users`, body),
  update: (id, body) => api.put(`${AUTH}/users/${id}`, body),
  changeStatus: (id, status) => api.post(`${AUTH}/users/${id}/status`, { status }),
  changeRole: (id, roleCode) => api.post(`${AUTH}/users/${id}/role`, { roleCode }),
  resetPassword: (id, newPassword) => api.post(`${AUTH}/users/${id}/reset-password`, { newPassword }),
};

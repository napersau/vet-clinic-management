import { api, request } from './client';

// Một hàm cho mỗi endpoint của backend (qua Gateway). Không chứa logic nghiệp vụ; tên trường giữ nguyên theo backend.
// Mỗi nhóm endpoint được thêm cùng lúc với màn hình dùng nó (pharmacy, paraclinic, báo cáo... sẽ có ở các đợt sau).
const AUTH = '/api/v1/auth';
const APPT = '/api/v1/appointments';
const CLIN = '/api/v1/clinical';
const VISITS = `${CLIN}/pet-visits`;
const BILL = '/api/v1/billing';

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

export const catalogApi = {
  species: () => api.get('/api/v1/species'),
};

export const petsApi = {
  list: (query) => api.get('/api/v1/pets', query),
  get: (id) => api.get(`/api/v1/pets/${id}`),
  create: (body) => api.post('/api/v1/pets', body),
  update: (id, body) => api.put(`/api/v1/pets/${id}`, body),
  visits: (id) => api.get(`/api/v1/pets/${id}/visits`),
};

export const appointmentsApi = {
  slots: (date) => api.get(`${APPT}/slots`, { date }),
  list: (query) => api.get(APPT, query),
  history: (id) => api.get(`${APPT}/${id}/history`),
  create: (body) => api.post(APPT, body),
  confirm: (id) => api.post(`${APPT}/${id}/confirm`),
  cancel: (id, reason) => api.post(`${APPT}/${id}/cancel`, { reason }),
  reschedule: (id, body) => api.post(`${APPT}/${id}/reschedule`, body),
  noShow: (id) => api.post(`${APPT}/${id}/no-show`),
};

export const visitsApi = {
  checkIn: (body) => api.post(`${VISITS}/check-in`, body),
  queue: () => api.get(`${VISITS}/queue`),
  list: (query) => api.get(VISITS, query),
  history: (id) => api.get(`${VISITS}/${id}/history`),
  assign: (id, doctorId) => api.post(`${VISITS}/${id}/assign`, { doctorId }),
  abandon: (id) => api.post(`${VISITS}/${id}/abandon`),
  cancel: (id, reason) => api.post(`${VISITS}/${id}/cancel`, { reason }),
  doctorsStatus: () => api.get(`${CLIN}/doctors/status`),
};

export const billingApi = {
  // Đường cứu hộ của lễ tân khi sự kiện chốt ca không tạo được hóa đơn (idempotent).
  generateForVisit: (visitId) => api.post(`${BILL}/invoices/from-visit/${visitId}`),
};

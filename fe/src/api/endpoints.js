import { api, request } from './client';

// Một hàm cho mỗi endpoint của backend (qua Gateway). Không chứa logic nghiệp vụ; tên trường giữ nguyên theo backend.
// Mỗi nhóm endpoint được thêm cùng lúc với màn hình dùng nó (kho thuốc của Admin, hóa đơn, báo cáo... sẽ có ở các đợt sau).
const AUTH = '/api/v1/auth';
const APPT = '/api/v1/appointments';
const CLIN = '/api/v1/clinical';
const VISITS = `${CLIN}/pet-visits`;
const PHARM = '/api/v1/pharmacy';
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
  services: (includeInactive = false) => api.get(`${CLIN}/services`, { includeInactive }),
};

export const petsApi = {
  list: (query) => api.get('/api/v1/pets', query),
  get: (id) => api.get(`/api/v1/pets/${id}`),
  create: (body) => api.post('/api/v1/pets', body),
  update: (id, body) => api.put(`/api/v1/pets/${id}`, body),
  visits: (id) => api.get(`/api/v1/pets/${id}/visits`),
  medicalHistory: (id, query) => api.get(`/api/v1/pets/${id}/medical-history`, query),
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
  get: (id) => api.get(`${VISITS}/${id}`),
  history: (id) => api.get(`${VISITS}/${id}/history`),
  assign: (id, doctorId) => api.post(`${VISITS}/${id}/assign`, { doctorId }),
  startExam: (id, body) => api.post(`${VISITS}/${id}/start-exam`, body),
  orderServices: (id, services) => api.post(`${VISITS}/${id}/services`, { services }),
  callBack: (id) => api.post(`${VISITS}/${id}/call-back`),
  postponeCallback: (id) => api.post(`${VISITS}/${id}/postpone-callback`),
  closeVisit: (id, body) => api.post(`${VISITS}/${id}/close-visit`, body),
  abandon: (id) => api.post(`${VISITS}/${id}/abandon`),
  cancel: (id, reason) => api.post(`${VISITS}/${id}/cancel`, { reason }),
  doctorsStatus: () => api.get(`${CLIN}/doctors/status`),
  myVisits: () => api.get(`${CLIN}/doctors/me/visits`),
};

export const billingApi = {
  // Đường cứu hộ của lễ tân khi sự kiện chốt ca không tạo được hóa đơn (idempotent).
  generateForVisit: (visitId) => api.post(`${BILL}/invoices/from-visit/${visitId}`),
};

export const paraclinicApi = {
  labQueue: () => api.get(`${CLIN}/lab-queue`),
  imagingQueue: () => api.get(`${CLIN}/imaging-queue`),
  start: (detailId) => api.post(`${CLIN}/service-details/${detailId}/start`),
  postpone: (detailId, reason) => api.post(`${CLIN}/service-details/${detailId}/postpone`, reason ? { reason } : {}),
  recall: (detailId) => api.post(`${CLIN}/service-details/${detailId}/recall`),
  cancel: (detailId, reason) => api.post(`${CLIN}/service-details/${detailId}/cancel`, { reason }),
  labForm: (detailId) => api.get(`${CLIN}/service-details/${detailId}/lab-form`),
  saveLabResults: (detailId, items) => api.post(`${CLIN}/service-details/${detailId}/lab-results`, { items }),
  saveImagingResult: (detailId, { description, conclusion, files }) => {
    const form = new FormData();
    form.append('description', description);
    form.append('conclusion', conclusion || '');
    files.forEach((file) => form.append('files', file));
    return api.upload(`${CLIN}/service-details/${detailId}/imaging-result`, form);
  },
  // Ảnh CĐHA tải qua API có Authorization (blob), không nhúng <img src> trực tiếp.
  attachment: (attachmentId) => api.blob(`${CLIN}/imaging-attachments/${attachmentId}/file`),
};

export const pharmacyApi = {
  medicines: (query) => api.get(`${PHARM}/medicines`, query),
  prescriptionByVisit: (visitId) => api.get(`${PHARM}/prescriptions/by-visit/${visitId}`),
  createPrescription: (body) => api.post(`${PHARM}/prescriptions`, body),
  updatePrescription: (id, body) => api.put(`${PHARM}/prescriptions/${id}`, body),
  cancelPrescription: (id) => api.post(`${PHARM}/prescriptions/${id}/cancel`),
};

import { api, request } from './client';

// Một hàm cho mỗi endpoint của backend (qua Gateway). Không chứa logic nghiệp vụ; tên trường giữ nguyên theo backend.
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
  createSpecies: (body) => api.post('/api/v1/species', body),
  updateSpecies: (id, body) => api.put(`/api/v1/species/${id}`, body),
  services: (includeInactive = false) => api.get(`${CLIN}/services`, { includeInactive }),
  createService: (body) => api.post(`${CLIN}/services`, body),
  updateService: (id, body) => api.put(`${CLIN}/services/${id}`, body),
  labItems: () => api.get(`${CLIN}/lab-test-items`),
  createLabItem: (body) => api.post(`${CLIN}/lab-test-items`, body),
  updateLabItem: (id, body) => api.put(`${CLIN}/lab-test-items/${id}`, body),
  saveRange: (itemId, speciesId, body) => api.put(`${CLIN}/lab-test-items/${itemId}/ranges/${speciesId}`, body),
  serviceLabItems: (serviceId) => api.get(`${CLIN}/services/${serviceId}/lab-test-items`),
  setServiceLabItems: (serviceId, labTestItemIds) => api.put(`${CLIN}/services/${serviceId}/lab-test-items`, { labTestItemIds }),
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
  get: (id) => api.get(`${APPT}/${id}`),
  history: (id) => api.get(`${APPT}/${id}/history`),
  create: (body) => api.post(APPT, body),
  confirm: (id) => api.post(`${APPT}/${id}/confirm`),
  cancel: (id, reason) => api.post(`${APPT}/${id}/cancel`, { reason }),
  reschedule: (id, body) => api.post(`${APPT}/${id}/reschedule`, body),
  noShow: (id) => api.post(`${APPT}/${id}/no-show`),
  report: (query) => api.get(`${APPT}/reports/summary`, query),
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
  reopen: (id, reason) => api.post(`${VISITS}/${id}/reopen`, { reason }),
  doctorsStatus: () => api.get(`${CLIN}/doctors/status`),
  myVisits: () => api.get(`${CLIN}/doctors/me/visits`),
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
  medicine: (id) => api.get(`${PHARM}/medicines/${id}`),
  createMedicine: (body) => api.post(`${PHARM}/medicines`, body),
  updateMedicine: (id, body) => api.put(`${PHARM}/medicines/${id}`, body),
  importStock: (id, body) => api.post(`${PHARM}/medicines/${id}/import`, body),
  adjustStock: (id, body) => api.post(`${PHARM}/medicines/${id}/adjust`, body),
  transactions: (id, query) => api.get(`${PHARM}/medicines/${id}/transactions`, query),
  prescriptions: (query) => api.get(`${PHARM}/prescriptions`, query),
  prescription: (id) => api.get(`${PHARM}/prescriptions/${id}`),
  prescriptionByVisit: (visitId) => api.get(`${PHARM}/prescriptions/by-visit/${visitId}`),
  createPrescription: (body) => api.post(`${PHARM}/prescriptions`, body),
  updatePrescription: (id, body) => api.put(`${PHARM}/prescriptions/${id}`, body),
  cancelPrescription: (id) => api.post(`${PHARM}/prescriptions/${id}/cancel`),
  dispense: (id) => api.post(`${PHARM}/prescriptions/${id}/dispense`),
};

export const billingApi = {
  invoices: (query) => api.get(`${BILL}/invoices`, query),
  invoice: (id) => api.get(`${BILL}/invoices/${id}`),
  invoiceByVisit: (visitId) => api.get(`${BILL}/invoices/by-visit/${visitId}`),
  setDiscount: (id, discountAmount, notes) => api.put(`${BILL}/invoices/${id}/discount`, { discountAmount, notes }),
  confirmPayment: (id) => api.post(`${BILL}/invoices/${id}/confirm-payment`),
  cancelInvoice: (id, reason) => api.post(`${BILL}/invoices/${id}/cancel`, { reason }),
  generateForVisit: (visitId) => api.post(`${BILL}/invoices/from-visit/${visitId}`),
  revenueReport: (query) => api.get(`${BILL}/reports/revenue`, query),
};

export const reportsApi = {
  operations: (query) => api.get(`${CLIN}/reports/operations`, query),
};

export const notificationsApi = {
  list: (limit = 50) => api.get('/api/v1/notifications', { limit }),
  sendEmail: (body) => api.post('/api/v1/notifications/send-email', body),
};

import { request } from './apiClient';

// Hợp đồng API dự kiến (cần thống nhất với backend):
//   POST /auth/login    { identifier, password }                -> { token, user }
//   POST /auth/register { fullName, phone, email?, password }   -> { token, user }
// user: { id, fullName, phone, email, role } với role thuộc ROLES.
export const authService = {
  login: ({ identifier, password }) => request('/auth/login', { method: 'POST', body: { identifier, password } }),
  register: ({ fullName, phone, email, password }) => request('/auth/register', {
    method: 'POST',
    body: { fullName, phone, email: email || undefined, password },
  }),
};

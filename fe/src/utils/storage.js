export const SESSION_KEY = 'pawcare_auth_session';

function read(storage) {
  try {
    const value = storage.getItem(SESSION_KEY);
    return value ? JSON.parse(value) : null;
  } catch {
    return null;
  }
}

function write(storage, value) {
  try {
    if (value === null) storage.removeItem(SESSION_KEY);
    else storage.setItem(SESSION_KEY, JSON.stringify(value));
  } catch {
    // Trình duyệt chặn lưu trữ (chế độ riêng tư): phiên chỉ tồn tại trong bộ nhớ.
  }
}

export function getAuthSession() {
  return read(localStorage) || read(sessionStorage);
}

export function getAccessToken() {
  return getAuthSession()?.token || null;
}

// remember = true: giữ đăng nhập sau khi đóng trình duyệt; false: chỉ trong tab hiện tại.
export function saveAuthSession(session, remember = true) {
  write(remember ? sessionStorage : localStorage, null);
  write(remember ? localStorage : sessionStorage, session);
}

export function clearAuthSession() {
  write(localStorage, null);
  write(sessionStorage, null);
}

import { SESSION_KEY } from '../config';

// Phiên đăng nhập: { accessToken, refreshToken, user }. "Ghi nhớ đăng nhập" lưu ở localStorage, không thì ở sessionStorage
// (đóng tab là mất phiên). Chỉ giữ ở một nơi tại một thời điểm.
const listeners = new Set();

function read(storage) {
  try {
    const value = storage.getItem(SESSION_KEY);
    return value ? JSON.parse(value) : null;
  } catch {
    return null;
  }
}

export function getSession() {
  return read(localStorage) || read(sessionStorage);
}

export function setSession(session, remember = true) {
  const target = remember ? localStorage : sessionStorage;
  const other = remember ? sessionStorage : localStorage;
  other.removeItem(SESSION_KEY);
  target.setItem(SESSION_KEY, JSON.stringify(session));
  listeners.forEach((listener) => listener(session));
}

/** Cập nhật một phần (token mới sau refresh, hồ sơ mới) nhưng giữ nguyên nơi lưu. */
export function patchSession(patch) {
  const current = getSession();
  if (!current) return null;
  const persistent = read(localStorage) !== null;
  const next = { ...current, ...patch };
  setSession(next, persistent);
  return next;
}

export function clearSession() {
  localStorage.removeItem(SESSION_KEY);
  sessionStorage.removeItem(SESSION_KEY);
  listeners.forEach((listener) => listener(null));
}

export function onSessionChange(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

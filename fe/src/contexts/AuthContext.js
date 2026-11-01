import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { authApi } from '../api/endpoints';
import { SESSION_EXPIRED } from '../api/client';
import { normalizeUser } from '../api/normalize';
import { clearSession, getSession, patchSession, setSession } from '../api/session';

const AuthContext = createContext(null);

const failure = (problem) => ({ success: false, message: problem.message, fieldErrors: problem.fieldErrors || {} });

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => normalizeUser(getSession()?.user));

  // Hết phiên (refresh thất bại) hoặc đăng nhập/đăng xuất ở tab khác: đồng bộ lại với kho phiên.
  useEffect(() => {
    const sync = () => setUser(normalizeUser(getSession()?.user));
    window.addEventListener(SESSION_EXPIRED, sync);
    window.addEventListener('storage', sync);
    return () => { window.removeEventListener(SESSION_EXPIRED, sync); window.removeEventListener('storage', sync); };
  }, []);

  // Phiên lưu từ lần trước: xác nhận với máy chủ (tài khoản có thể đã bị khóa hoặc đổi vai trò).
  useEffect(() => {
    if (!getSession()?.accessToken) return;
    authApi.me().then((fresh) => { patchSession({ user: fresh }); setUser(normalizeUser(fresh)); }).catch(() => {});
  }, []);

  const login = useCallback(async (username, password, remember = true) => {
    try {
      const data = await authApi.login(username.trim(), password);
      setSession({ accessToken: data.accessToken, refreshToken: data.refreshToken, user: data.user }, remember);
      const normalized = normalizeUser(data.user);
      setUser(normalized);
      return { success: true, user: normalized };
    } catch (problem) { return failure(problem); }
  }, []);

  // Đăng ký chỉ tạo tài khoản PET_OWNER (không trả token), nên đăng nhập ngay bằng số điện thoại vừa đăng ký.
  const register = useCallback(async (profile, password) => {
    try { await authApi.register({ ...profile, password }); } catch (problem) { return failure(problem); }
    return login(profile.phoneNumber, password, true);
  }, [login]);

  const updateProfile = useCallback(async (profile) => {
    const fresh = await authApi.updateMe(profile);
    patchSession({ user: fresh });
    setUser(normalizeUser(fresh));
    return normalizeUser(fresh);
  }, []);

  const logout = useCallback(async () => {
    const refreshToken = getSession()?.refreshToken;
    clearSession(); setUser(null);
    if (refreshToken) { try { await authApi.logout(refreshToken); } catch { /* đã thu hồi hoặc mất mạng: phiên cục bộ đã xóa */ } }
  }, []);

  const value = useMemo(() => ({ user, isAuthenticated: Boolean(user), login, register, updateProfile, logout }), [user, login, register, updateProfile, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}

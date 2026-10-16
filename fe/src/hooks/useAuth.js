import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { authService } from '../services/authService';
import { clearAuthSession, getAuthSession, saveAuthSession, SESSION_KEY } from '../utils/storage';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [session, setSession] = useState(getAuthSession);

  // Đồng bộ đăng nhập / đăng xuất giữa các tab.
  useEffect(() => {
    const sync = (event) => {
      if (event.key === null || event.key === SESSION_KEY) setSession(getAuthSession());
    };
    window.addEventListener('storage', sync);
    return () => window.removeEventListener('storage', sync);
  }, []);

  const login = useCallback(async ({ identifier, password, remember }) => {
    const next = await authService.login({ identifier, password });
    saveAuthSession(next, remember);
    setSession(next);
    return next.user;
  }, []);

  const register = useCallback(async (profile) => {
    const next = await authService.register(profile);
    saveAuthSession(next, true);
    setSession(next);
    return next.user;
  }, []);

  const logout = useCallback(() => {
    clearAuthSession();
    setSession(null);
  }, []);

  const value = useMemo(
    () => ({ user: session?.user ?? null, isAuthenticated: Boolean(session?.token), login, register, logout }),
    [session, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}

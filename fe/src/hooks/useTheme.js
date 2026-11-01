import { useEffect, useState } from 'react';

export const DEFAULT_THEME_KEY = 'pawcare_admin_theme_v1';
const validTheme = (value) => value === 'light' || value === 'dark';
// Giao diện sáng/tối của khu vực nhân viên, lưu riêng theo từng khu vực (khóa lưu nằm trong STAFF_AREAS).
export default function useTheme(storageKey = DEFAULT_THEME_KEY, defaultTheme = 'dark') {
  const [theme, setTheme] = useState(() => {
    try { const stored = localStorage.getItem(storageKey); return validTheme(stored) ? stored : defaultTheme; }
    catch { return defaultTheme; }
  });
  useEffect(() => {
    // Cosmetic preference must not prevent accessing the application if storage is unavailable.
    try { localStorage.setItem(storageKey, theme); } catch { /* In-memory preference remains usable. */ }
  }, [theme, storageKey]);
  useEffect(() => {
    const sync = (event) => { if (event.key === storageKey && validTheme(event.newValue)) setTheme(event.newValue); };
    window.addEventListener('storage', sync);
    return () => window.removeEventListener('storage', sync);
  }, [storageKey]);
  return { theme, toggleTheme: () => setTheme((current) => current === 'dark' ? 'light' : 'dark') };
}

import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import AdminIcon from '../admin/AdminIcon';
import PriorityCallbackDialog from '../vet/PriorityCallbackDialog';
import { STAFF_AREAS } from '../../constants/navigation';
import useTheme from '../../hooks/useTheme';
import '../../styles/admin.css';
import '../../styles/reception.css';
import '../../styles/ui.css';

/** Khung chung cho mọi khu vực nhân viên (lễ tân, bác sĩ, kỹ thuật viên, quản trị). */
export default function StaffLayout({ title, children, area = 'reception' }) {
  const config = STAFF_AREAS[area];
  const { user, logout } = useAuth(); const navigate = useNavigate();
  const [open, setOpen] = useState(false); const trigger = useRef(null);
  const { theme, toggleTheme } = useTheme(config.themeKey, config.defaultTheme);
  useEffect(() => {
    if (!open) return undefined;
    const close = (event) => { if (event.key === 'Escape') { setOpen(false); trigger.current?.focus(); } };
    document.addEventListener('keydown', close);
    return () => document.removeEventListener('keydown', close);
  }, [open]);
  const initial = (user.fullName || '?').charAt(0);
  return <div className={`admin-shell reception-shell ${area}-shell`} data-theme={theme} data-testid="admin-shell">
    <a className="admin-skip-link" href={`#${area}-main`}>Đến nội dung chính</a>
    {open && <button type="button" className="admin-sidebar-backdrop" aria-label={`Đóng menu ${config.label}`} onClick={() => setOpen(false)} />}
    <aside className={`admin-sidebar ${open ? 'is-open' : ''}`} id={`${area}-sidebar`}>
      <Link to="/" className="admin-brand"><span><AdminIcon name="shield" /></span><div>PawCare<small>{config.brand}</small></div></Link>
      <span className="admin-nav-caption">{config.caption}</span>
      <nav aria-label={`Điều hướng ${config.label}`}>{config.links.map((item) => <NavLink key={item.to} to={item.to} onClick={() => setOpen(false)}><AdminIcon name={item.icon} /><span>{item.label}</span><AdminIcon name="arrow" className="admin-nav-arrow" /></NavLink>)}</nav>
      <div className="admin-sidebar-note"><span><AdminIcon name="users" />PAWCARE WORKSPACE</span><strong>Chăm sóc tốt hơn.<br />Quản lý đơn giản hơn.</strong><p>{config.description}</p><Link to="/">Về trang chủ <AdminIcon name="arrow" /></Link></div>
      <div className="admin-sidebar-account"><span className="admin-avatar">{initial}</span><div><strong>{config.accountLabel}</strong><small>{user.roomNumber ? `Phòng ${user.roomNumber}` : user.phone}</small></div></div>
    </aside>
    <div className="admin-workspace">
      <header className="admin-topbar">
        <button type="button" ref={trigger} className="admin-menu-button" aria-label={`${open ? 'Đóng' : 'Mở'} menu ${config.label}`} aria-controls={`${area}-sidebar`} aria-expanded={open} onClick={() => setOpen(!open)}><AdminIcon name="menu" /></button>
        <div className="reception-topbar-title"><strong>{config.title}</strong><span>{config.subtitle}</span></div>
        <button type="button" className="admin-icon-button admin-theme-toggle" aria-label={`Chuyển sang giao diện ${theme === 'light' ? 'tối' : 'sáng'}`} onClick={toggleTheme}><AdminIcon name={theme === 'light' ? 'moon' : 'sun'} /></button>
        <div className="admin-account"><Link to="/portal/profile" className="reception-account-link" aria-label="Thông tin cá nhân"><span className="admin-avatar">{initial}</span><span className="reception-account-copy"><strong>{user.fullName}</strong><small>{config.accountLabel}</small></span></Link><button type="button" className="admin-icon-button" aria-label="Đăng xuất" onClick={() => { logout(); navigate('/auth/login', { replace: true }); }}><AdminIcon name="logout" /></button></div>
      </header>
      <main id={`${area}-main`} className="admin-main" tabIndex={-1}><div className="admin-breadcrumb"><Link to={config.home}>{config.label.charAt(0).toUpperCase() + config.label.slice(1)}</Link><AdminIcon name="arrow" /><span>{title}</span></div>{children}</main>
      {area === 'vet' && <PriorityCallbackDialog />}
      <footer className="admin-footer"><span>© 2026 PawCare</span><span>Hệ thống quản lý phòng khám thú y</span></footer>
    </div>
  </div>;
}

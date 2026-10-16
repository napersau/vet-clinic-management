import { useId, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { ROLE_LABELS } from '../../constants/roles';
import { ROUTES } from '../../constants/routes';
import { useAuth } from '../../hooks/useAuth';
import useDismiss from '../../hooks/useDismiss';
import { getInitials } from '../../utils/format';

export default function AccountMenu({ open, onOpenChange }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const root = useRef(null);
  const trigger = useRef(null);
  const panelId = useId();

  useDismiss(root, open, (event) => {
    onOpenChange(false);
    if (event.type === 'keydown') trigger.current?.focus();
  });

  if (!user) return null;
  const initials = getInitials(user.fullName);

  const handleLogout = () => {
    logout();
    onOpenChange(false);
    navigate(ROUTES.LOGIN, { replace: true });
  };

  return (
    <div className="header-account" ref={root}>
      <button type="button" className={`account-trigger ${open ? 'is-open' : ''}`} ref={trigger} aria-label={open ? 'Đóng menu tài khoản' : 'Mở menu tài khoản'} aria-expanded={open} aria-controls={panelId} title={`Tài khoản của ${user.fullName}`} onClick={() => onOpenChange(!open)}>
        <span className="account-avatar" aria-hidden="true">{initials}</span>
        <svg width="13" height="13" viewBox="0 0 16 16" fill="none" aria-hidden="true"><path d="m4 6 4 4 4-4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /></svg>
      </button>
      {open && (
        <div className="account-dropdown" id={panelId} role="region" aria-label="Tài khoản của bạn">
          <div className="account-identity">
            <span className="account-avatar" aria-hidden="true">{initials}</span>
            <div>
              <strong>{user.fullName || 'Người dùng PawCare'}</strong>
              <span>{user.email || user.phone}</span>
              <small>{ROLE_LABELS[user.role] || 'Thành viên'}</small>
            </div>
          </div>
          <div className="account-logout">
            <button type="button" onClick={handleLogout}>
              <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M9 21H4V3h5 M14 8l5 4-5 4 M8 12h11" /></svg>
              Đăng xuất
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

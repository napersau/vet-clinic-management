import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { NAV_ITEMS } from '../../constants/home';
import { ROUTES, homeSection } from '../../constants/routes';
import { useAuth } from '../../hooks/useAuth';
import useDismiss from '../../hooks/useDismiss';
import Brand from '../common/Brand';
import AccountMenu from './AccountMenu';
import './Header.css';

export default function Header() {
  const { user } = useAuth();
  const [menuOpen, setMenuOpen] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);
  const header = useRef(null);
  const menuButton = useRef(null);
  const { pathname, search, hash } = useLocation();

  const closeAll = useCallback(() => { setMenuOpen(false); setAccountOpen(false); }, []);
  const changeAccount = useCallback((open) => { setAccountOpen(open); if (open) setMenuOpen(false); }, []);
  const dismissMenu = useCallback((event) => {
    setMenuOpen(false);
    if (event.type === 'keydown') menuButton.current?.focus();
  }, []);

  useEffect(closeAll, [pathname, search, hash, user?.id, closeAll]);
  useDismiss(header, menuOpen, dismissMenu);

  return (
    <header className="site-header" ref={header}>
      <div className="container nav-wrap">
        <Brand onClick={closeAll} />
        <nav id="site-navigation" className={`main-nav ${menuOpen ? 'is-open' : ''}`} aria-label="Điều hướng chính">
          {NAV_ITEMS.map((item) => <Link to={item.to} key={item.to} onClick={closeAll}>{item.label}</Link>)}
          {!user && <Link className="mobile-auth-link" to={ROUTES.LOGIN} onClick={closeAll}>Đăng nhập</Link>}
        </nav>
        <div className="nav-actions">
          {!user && <Link className="sign-in" to={ROUTES.LOGIN}>Đăng nhập</Link>}
          <Link className="button button-primary button-small header-booking" to={homeSection('booking')} onClick={closeAll}>Đặt lịch <span aria-hidden="true">🐾</span></Link>
          {user && <AccountMenu open={accountOpen} onOpenChange={changeAccount} />}
          <button ref={menuButton} className="menu-toggle" type="button" aria-label={menuOpen ? 'Đóng menu' : 'Mở menu'} aria-expanded={menuOpen} aria-controls="site-navigation" onClick={() => { setMenuOpen((current) => !current); setAccountOpen(false); }}>
            <span /><span /><span />
          </button>
        </div>
      </div>
    </header>
  );
}

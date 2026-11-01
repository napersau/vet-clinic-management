import { Link, NavLink } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { CUSTOMER_LINKS } from '../../constants/navigation';
import { getRoleHome } from '../../constants/roles';

export default function CustomerNavigation() {
  const { user } = useAuth();
  const owner = user?.role === 'PET_OWNER';
  return <div className="customer-navigation"><div className="profile-container">
    <div className="customer-breadcrumb"><Link to="/">Trang chủ</Link><span aria-hidden="true">/</span><span>{owner ? 'Khu vực khách hàng' : 'Khu vực cá nhân'}</span></div>
    <nav className={owner ? 'customer-nav-links' : undefined} aria-label={owner ? 'Khu vực khách hàng' : 'Khu vực cá nhân'}>
      {(owner ? CUSTOMER_LINKS : CUSTOMER_LINKS.slice(0, 1)).map((item) => <NavLink key={item.to} to={item.to}>{item.label}</NavLink>)}
      {user && !owner && <NavLink to={getRoleHome(user.role)}>Về khu vực làm việc</NavLink>}
    </nav>
  </div></div>;
}

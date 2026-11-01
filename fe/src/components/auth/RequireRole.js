import { Link } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { getRoleHome } from '../../constants/roles';
import RequireAuth from './RequireAuth';

/** `role` là một mã vai trò hoặc danh sách mã vai trò được phép vào trang. */
export default function RequireRole({ role, children }) {
  const { user } = useAuth();
  const allowed = Array.isArray(role) ? role : [role];
  return (
    <RequireAuth>
      {user && !allowed.includes(user.role) ? (
        <main className="access-denied">
          <p>403</p><h1>Bạn không có quyền truy cập</h1>
          <span>Trang này dành cho tài khoản có vai trò phù hợp.</span>
          <Link to={getRoleHome(user.role)}>Về khu vực của tôi</Link>
        </main>
      ) : children}
    </RequireAuth>
  );
}

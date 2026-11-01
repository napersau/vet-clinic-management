import { useState } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { AuthField, AuthLayout, PasswordInput } from '../../components';
import { ROUTES } from '../../constants';
import { useAuth } from '../../hooks';
import { normalizeIdentifier, resolvePostLoginPath } from '../../utils';
import './AuthPages.css';

export default function LoginPage() {
  const { login, user } = useAuth();
  const location = useLocation();
  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(false);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Đăng nhập xong (hoặc đã có phiên): về trang đang xem dở nếu vai trò được phép vào, ngược lại về trang chủ của vai trò.
  if (user) return <Navigate to={resolvePostLoginPath(location.state?.from, user.role)} replace />;

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');
    setSubmitting(true);
    const result = await login(normalizeIdentifier(identifier), password, remember);
    if (!result.success) {
      setError(result.message);
      setSubmitting(false);
    }
  };

  return (
    <AuthLayout mode="login">
      <div className="auth-card">
        <div className="auth-heading">
          <span className="auth-heading-icon" aria-hidden="true">👋</span>
          <h2>Đăng nhập</h2>
          <p>Chào mừng bạn quay lại với PawCare.</p>
        </div>

        <form className="auth-form" onSubmit={handleSubmit}>
          <AuthField id="login-identifier" label="Tên đăng nhập hoặc số điện thoại" name="identifier" autoComplete="username" placeholder="Số điện thoại hoặc tên đăng nhập" value={identifier} onChange={(event) => setIdentifier(event.target.value)} required />
          <PasswordInput id="login-password" label="Mật khẩu" name="password" autoComplete="current-password" placeholder="Nhập mật khẩu" value={password} onChange={(event) => setPassword(event.target.value)} required />
          <div className="auth-options">
            <label className="check-option"><input type="checkbox" name="remember" checked={remember} onChange={(event) => setRemember(event.target.checked)} /><span>Ghi nhớ đăng nhập</span></label>
            <span className="auth-hint">Quên mật khẩu? Liên hệ lễ tân để được đặt lại.</span>
          </div>
          {error && <p className="field-error form-error" role="alert">{error}</p>}
          <button className="auth-submit" type="submit" disabled={submitting}>{submitting ? 'Đang đăng nhập…' : <>Đăng nhập <span aria-hidden="true">→</span></>}</button>
        </form>

        <p className="auth-switch">Chưa có tài khoản? <Link to={ROUTES.REGISTER}>Đăng ký ngay</Link></p>
      </div>
    </AuthLayout>
  );
}

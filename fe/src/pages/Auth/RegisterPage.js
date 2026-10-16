import { useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { AuthField, AuthLayout, PasswordInput } from '../../components';
import { ROUTES } from '../../constants';
import { useAuth } from '../../hooks';
import { MIN_PASSWORD_LENGTH, isValidPhone, normalizePhone } from '../../utils';
import './AuthPages.css';

const EMAIL_PATTERN = /^\S+@\S+\.\S+$/;
const INITIAL_VALUES ={ fullName: '', phone: '', email: '', password: '', passwordConfirmation: '', terms: false };

function validate(values) {
  const errors = {};
  if (!values.fullName.trim()) errors.fullName = 'Vui lòng nhập họ và tên.';
  if (!isValidPhone(values.phone)) errors.phone = 'Số điện thoại phải gồm đúng 10 chữ số, bắt đầu bằng 0.';
  if (values.email.trim() && !EMAIL_PATTERN.test(values.email.trim())) errors.email = 'Email chưa đúng định dạng.';
  if (values.password.length < MIN_PASSWORD_LENGTH) errors.password = `Mật khẩu cần tối thiểu ${MIN_PASSWORD_LENGTH} ký tự.`;
  if (values.password !== values.passwordConfirmation) errors.passwordConfirmation = 'Mật khẩu xác nhận chưa khớp.';
  return errors;
}

export default function RegisterPage() {
  const { register, isAuthenticated } = useAuth();
  const [values, setValues] = useState(INITIAL_VALUES);
  const [errors, setErrors] = useState({});
  const [submitError, setSubmitError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  if (isAuthenticated) return <Navigate to={ROUTES.HOME} replace />;

  const bind = (field) => ({
    name: field,
    value: values[field],
    error: errors[field],
    onChange: (event) => setValues((current) => ({ ...current, [field]: event.target.value })),
  });

  const handleSubmit = async (event) => {
    event.preventDefault();
    setSubmitError('');
    const found = validate(values);
    setErrors(found);
    if (Object.keys(found).length > 0) return;

    setSubmitting(true);
    try {
      await register({
        fullName: values.fullName.trim(),
        phone: normalizePhone(values.phone),
        email: values.email.trim().toLowerCase(),
        password: values.password,
      });
    } catch (problem) {
      setSubmitError(problem.message);
      setSubmitting(false);
    }
  };

  return (
    <AuthLayout mode="register">
      <div className="auth-card auth-card-register">
        <div className="auth-heading">
          <span className="auth-heading-icon" aria-hidden="true">🌟</span>
          <h2>Tạo tài khoản</h2>
          <p>Tham gia PawCare để chăm sóc bé dễ dàng hơn.</p>
        </div>

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          <div className="auth-name-grid">
            <AuthField id="register-name" label="Họ và tên" autoComplete="name" placeholder="Nguyễn Văn A" required {...bind('fullName')} />
            <AuthField id="register-phone" label="Số điện thoại" type="tel" autoComplete="tel" placeholder="0901 234 567" required {...bind('phone')} />
          </div>
          <AuthField id="register-email" label="Email (không bắt buộc)" type="email" autoComplete="email" placeholder="you@example.com" {...bind('email')} />
          <PasswordInput id="register-password" label="Mật khẩu" autoComplete="new-password" placeholder={`Tối thiểu ${MIN_PASSWORD_LENGTH} ký tự`} required {...bind('password')} />
          <PasswordInput id="register-confirmation" label="Xác nhận mật khẩu" autoComplete="new-password" placeholder="Nhập lại mật khẩu" required {...bind('passwordConfirmation')} />
          <label className="check-option terms-option">
            <input type="checkbox" name="terms" checked={values.terms} onChange={(event) => setValues((current) => ({ ...current, terms: event.target.checked }))} required />
            <span>Tôi đồng ý với <strong>Điều khoản sử dụng</strong> và <strong>Chính sách bảo mật</strong>.</span>
          </label>
          {submitError && <p className="field-error form-error" role="alert">{submitError}</p>}
          <button className="auth-submit" type="submit" disabled={submitting || !values.terms}>{submitting ? 'Đang tạo tài khoản…' : <>Tạo tài khoản <span aria-hidden="true">→</span></>}</button>
        </form>
        <p className="auth-switch">Đã có tài khoản? <Link to={ROUTES.LOGIN}>Đăng nhập</Link></p>
      </div>
    </AuthLayout>
  );
}

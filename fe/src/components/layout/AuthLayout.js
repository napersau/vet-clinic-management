import { Link } from 'react-router-dom';
import { HERO_IMAGE } from '../../constants/home';
import { ROUTES } from '../../constants/routes';
import Brand from '../common/Brand';
import SafeImage from '../common/SafeImage';
import './AuthLayout.css';

const COPY = {
  login: 'Chào mừng bạn trở lại!',
  register: 'Bắt đầu hành trình chăm sóc',
};

export default function AuthLayout({ children, mode }) {
  return (
    <main className="auth-layout">
      <section className="auth-showcase" aria-label="Giới thiệu PawCare">
        <div className="auth-showcase-inner">
          <Brand />
          <div className="auth-showcase-copy">
            <span className="auth-kicker">🐾 Đồng hành cùng thú cưng</span>
            <h1>{COPY[mode]}</h1>
            <p>Quản lý lịch khám, hồ sơ sức khoẻ và nhắc lịch vaccine của các bé tại một nơi.</p>
          </div>
          <div className="auth-pet-card">
            <SafeImage className="auth-pet-image" src={HERO_IMAGE} alt="Chú chó vui vẻ tại PawCare" />
            <div><strong>Chăm sóc tận tâm</strong><span>Cho mọi người bạn nhỏ</span></div>
          </div>
          <div className="auth-trust"><span><b>✓</b> Hồ sơ bảo mật</span><span><b>✓</b> Đặt lịch nhanh</span><span><b>✓</b> Hỗ trợ 7 ngày</span></div>
        </div>
      </section>
      <section className="auth-content">
        <Link className="auth-back" to={ROUTES.HOME} aria-label="Quay về trang chủ">← <span>Về trang chủ</span></Link>
        <div className="auth-mobile-brand"><Brand /></div>
        {children}
      </section>
    </main>
  );
}

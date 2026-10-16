import { Link } from 'react-router-dom';
import { CLINIC_CONTACT, FOOTER_SERVICES } from '../../constants/home';
import { ROUTES, homeSection } from '../../constants/routes';
import Brand from '../common/Brand';
import './Footer.css';

export default function Footer() {
  return (
    <footer className="site-footer">
      <div className="container footer-grid">
        <div className="footer-brand">
          <Brand />
          <p>Chăm sóc thú y tận tâm để mỗi người bạn nhỏ luôn khoẻ mạnh và hạnh phúc. 🐾</p>
        </div>
        <div>
          <h3>Dịch vụ</h3>
          {FOOTER_SERVICES.map((service) => <Link key={service} to={homeSection('services')}>{service}</Link>)}
        </div>
        <div>
          <h3>PawCare</h3>
          <Link to={ROUTES.HOME}>Về chúng tôi</Link>
          <Link to={homeSection('patients')}>Khách hàng</Link>
          <Link to={homeSection('reviews')}>Đánh giá</Link>
          <Link to={homeSection('booking')}>Liên hệ</Link>
        </div>
        <div>
          <h3>Liên hệ</h3>
          {CLINIC_CONTACT.map((item) => <p key={item.text}>{item.icon} {item.text}</p>)}
        </div>
      </div>
      <div className="container footer-bottom">
        <span>© 2026 PawCare. All rights reserved. 🐾</span>
        <div><Link to={ROUTES.HOME}>Chính sách bảo mật</Link><Link to={ROUTES.HOME}>Điều khoản sử dụng</Link></div>
      </div>
    </footer>
  );
}

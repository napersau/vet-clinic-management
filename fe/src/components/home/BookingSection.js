import { Link } from 'react-router-dom';
import { BOOKING_BENEFITS, BOOKING_STEPS } from '../../constants/home';
import { ROUTES } from '../../constants/routes';
import { useAuth } from '../../hooks/useAuth';

export default function BookingSection() {
  const { isAuthenticated } = useAuth();

  return (
    <section id="booking" className="section section-alt booking-section">
      <div className="container booking-grid">
        <div className="booking-copy">
          <div className="eyebrow"><span aria-hidden="true">🐾</span> Đặt lịch ngay hôm nay</div>
          <h2>Sẵn sàng chăm sóc <span className="gradient-text">bé yêu?</span></h2>
          <p>Chỉ mất vài phút để gửi yêu cầu. Đội ngũ PawCare sẽ liên hệ xác nhận khung giờ phù hợp.</p>
          <ul className="booking-benefits">
            {BOOKING_BENEFITS.map((benefit) => (
              <li key={benefit.title}>
                <span aria-hidden="true">{benefit.icon}</span>
                <div><strong>{benefit.title}</strong><small>{benefit.description}</small></div>
              </li>
            ))}
          </ul>
        </div>
        <div className="booking-form clay-card">
          <h3>Đặt lịch khám</h3>
          <p>Mỗi cuộc hẹn bắt đầu từ hồ sơ của bé.</p>
          <ol className="booking-steps">{BOOKING_STEPS.map((step) => <li key={step}>{step}</li>)}</ol>
          {isAuthenticated
            ? <Link className="button button-primary booking-submit" to={ROUTES.BOOKING}>Bắt đầu đặt lịch <span aria-hidden="true">🐾</span></Link>
            : <Link className="button button-primary booking-submit" to={ROUTES.REGISTER}>Đăng ký để đặt lịch <span aria-hidden="true">🐾</span></Link>}
          <p>Đăng nhập bằng tài khoản khách hàng để lưu và theo dõi lịch hẹn. Yêu cầu mới sẽ ở trạng thái chờ xác nhận.</p>
        </div>
      </div>
    </section>
  );
}

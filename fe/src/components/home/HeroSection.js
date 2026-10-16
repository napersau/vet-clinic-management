import { HERO_IMAGE } from '../../constants/home';
import SafeImage from '../common/SafeImage';

export default function HeroSection() {
  return (
    <section className="hero section-shell">
      <div className="decor decor-one" /><div className="decor decor-two" /><div className="decor decor-three" />
      <div className="container hero-grid">
        <div className="hero-copy">
          <div className="eyebrow"><span aria-hidden="true">🐾</span> Phòng khám thú y được tin yêu</div>
          <h1>Chăm sóc trọn vẹn cho <span className="gradient-text">người bạn nhỏ</span></h1>
          <p>Chăm sóc y khoa tận tâm, quy trình minh bạch và trải nghiệm nhẹ nhàng để mỗi lần đi khám đều thật an tâm.</p>
          <div className="hero-actions">
            <a className="button button-primary" href="#booking"><span className="button-icon" aria-hidden="true">📅</span> Đặt lịch khám</a>
            <a className="button button-secondary" href="#services">Xem dịch vụ</a>
          </div>
          <div className="hero-metrics" aria-label="Thành tích của phòng khám">
            <div><strong>5.000+</strong><span>Thú cưng khoẻ mạnh</span></div>
            <div><strong>4.9</strong><span>Điểm đánh giá ⭐</span></div>
            <div><strong>10+</strong><span>Năm kinh nghiệm</span></div>
          </div>
        </div>
        <div className="hero-visual">
          <div className="clay-card hero-photo"><SafeImage className="hero-photo-image" src={HERO_IMAGE} alt="Chú chó khoẻ mạnh đang được chăm sóc" /></div>
          <div className="floating-card floating-card-top"><span className="floating-icon orange" aria-hidden="true">🩺</span><div><strong>Khám tổng quát</strong><span>Từ 150.000đ</span></div></div>
          <div className="floating-card floating-card-bottom"><span className="floating-icon pink" aria-hidden="true">💉</span><div><strong>Tiêm phòng</strong><span>An toàn & đúng lịch</span></div></div>
          <span className="sparkle sparkle-one" aria-hidden="true">✦</span><span className="sparkle sparkle-two" aria-hidden="true">✦</span>
        </div>
      </div>
    </section>
  );
}

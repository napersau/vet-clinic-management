import { TESTIMONIALS } from '../../constants/home';
import SectionHeading from '../common/SectionHeading';
import SafeImage from '../common/SafeImage';

export default function TestimonialsSection() {
  return (
    <section id="reviews" className="section">
      <div className="container">
        <SectionHeading icon="💛" eyebrow="Khách hàng nói gì" description="Hàng nghìn gia đình đã tin tưởng giao người bạn nhỏ cho PawCare.">
          Niềm tin từ những <span className="gradient-text">người yêu thú cưng</span>
        </SectionHeading>
        <div className="testimonial-grid">
          {TESTIMONIALS.map((item) => (
            <article className="testimonial-card" key={item.name}>
              <div className="stars" aria-label="5 trên 5 sao">★ ★ ★ ★ ★</div>
              <blockquote>“{item.quote}”</blockquote>
              <div className="reviewer">
                <SafeImage className="reviewer-image" src={item.image} fallback="🙂" loading="lazy" />
                <div><strong>{item.name}</strong><span>{item.pet}</span></div>
              </div>
            </article>
          ))}
        </div>
        <div className="review-summary">
          <div><strong>4.9/5</strong><span>Đánh giá trung bình</span></div><div className="review-divider" />
          <div><strong>2.000+</strong><span>Phản hồi tích cực</span></div><div className="review-divider" />
          <div><strong>98%</strong><span>Sẽ quay lại</span></div>
        </div>
      </div>
    </section>
  );
}

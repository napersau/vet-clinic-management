import { CARE_PLANS } from '../../constants/home';
import SectionHeading from '../common/SectionHeading';

export default function PricingSection() {
  return (
    <section id="pricing" className="section section-alt">
      <div className="container">
        <SectionHeading icon="💳" eyebrow="Chi phí minh bạch" description="Mức giá tham khảo rõ ràng, không có chi phí ẩn và luôn được tư vấn trước khi thực hiện.">
          Gói chăm sóc <span className="gradient-text">dễ lựa chọn</span>
        </SectionHeading>
        <div className="pricing-grid">
          {CARE_PLANS.map((plan) => (
            <article className={`price-card ${plan.popular ? 'is-popular' : ''}`} key={plan.name}>
              {plan.popular && <div className="popular-label">ĐƯỢC YÊU THÍCH</div>}
              <h3>{plan.name}</h3>
              <p>{plan.description}</p>
              <div className="price"><strong>{plan.price}</strong><span>/ lần</span></div>
              <ul>{plan.features.map((feature) => <li key={feature}><span>✓</span>{feature}</li>)}</ul>
              <a className={`button ${plan.popular ? 'button-primary' : 'button-secondary'}`} href="#booking">Chọn gói này</a>
            </article>
          ))}
        </div>
        <p className="pricing-disclaimer">* Chi phí có thể thay đổi theo tình trạng thực tế và chỉ định của bác sĩ.</p>
      </div>
    </section>
  );
}

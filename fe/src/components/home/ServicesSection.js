import { SERVICES } from '../../constants/home';
import ArrowIcon from '../common/ArrowIcon';
import SectionHeading from '../common/SectionHeading';

export default function ServicesSection() {
  return (
    <section id="services" className="section section-alt">
      <div className="container">
        <SectionHeading icon="🩺" eyebrow="Dịch vụ của chúng tôi" description="Từ thăm khám định kỳ đến điều trị chuyên sâu, PawCare luôn đồng hành cùng bạn và bé.">
          Mọi điều tốt nhất cho <span className="gradient-text">thú cưng</span>
        </SectionHeading>
        <div className="service-grid">
          {SERVICES.map((service) => (
            <article className="service-card" key={service.title}>
              <span className={`service-icon ${service.tone}`} aria-hidden="true">{service.icon}</span>
              <h3>{service.title}</h3>
              <p>{service.description}</p>
              <div className="card-footer"><strong>{service.price}</strong><a href="#booking">Chọn dịch vụ <ArrowIcon /></a></div>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}

import {
  BookingSection, HeroSection, PetGallerySection, PricingSection, PublicLayout, ServicesSection, TestimonialsSection,
} from '../../components';
import './HomePage.css';

export default function HomePage() {
  return (
    <PublicLayout className="home-page">
      <HeroSection />
      <ServicesSection />
      <PetGallerySection />
      <PricingSection />
      <TestimonialsSection />
      <BookingSection />
    </PublicLayout>
  );
}

import { GALLERY_PETS } from '../../constants/home';
import SectionHeading from '../common/SectionHeading';
import SafeImage from '../common/SafeImage';

export default function PetGallerySection() {
  return (
    <section id="patients" className="section">
      <div className="container">
        <SectionHeading icon="📸" eyebrow="Những vị khách đáng yêu" description="Mỗi bạn nhỏ là một câu chuyện đáng nhớ trong hành trình của PawCare.">
          Nụ cười khoẻ mạnh của <span className="gradient-text">các bé</span>
        </SectionHeading>
        <div className="gallery-grid">
          {GALLERY_PETS.map((pet) => (
            <article className="pet-card" key={pet.name}>
              <SafeImage className="pet-card-image" src={pet.image} alt={`${pet.name} - ${pet.breed}`} loading="lazy" />
              <div className="pet-overlay"><strong>{pet.name}</strong><span>{pet.breed}</span></div>
            </article>
          ))}
        </div>
        <div className="gallery-note">Muốn bé nhà bạn xuất hiện ở đây? <a href="#booking">Đặt lịch ngay 📷</a></div>
      </div>
    </section>
  );
}

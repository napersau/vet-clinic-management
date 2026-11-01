import '../../styles/pets.css';

const SYMBOLS = { DOG: '🐶', CAT: '🐱' };

/** Ảnh đại diện theo loài (hệ thống chưa lưu ảnh thú cưng). `pet` là PetResponse của backend. */
export default function PetAvatar({ pet, className = '' }) {
  const code = (pet.speciesCode || '').toUpperCase();
  return <div className={`customer-pet-avatar species-${code.toLowerCase() || 'other'} ${className}`}>
    <span role="img" aria-label={`${pet.speciesName || 'Thú cưng'} ${pet.name || ''}`.trim()}>{SYMBOLS[code] || '🐾'}</span>
  </div>;
}

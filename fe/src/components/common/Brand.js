import { Link } from 'react-router-dom';
import { ROUTES } from '../../constants/routes';

export default function Brand({ onClick }) {
  return (
    <Link className="brand" to={ROUTES.HOME} onClick={onClick} aria-label="PawCare - Trang chủ">
      <span className="brand-mark" aria-hidden="true">
        <svg viewBox="0 0 24 24">
          <path d="M4.5 9.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Zm15 0a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Zm-12.5 5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Zm10 0a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5ZM12 21c3.5 0 6-2.5 6-6 0-2-1.5-4-3-5s-3-1-3-1-1.5 0-3 1-3 3-3 5c0 3.5 2.5 6 6 6Z" />
        </svg>
      </span>
      <span>Paw<span>Care</span></span>
    </Link>
  );
}

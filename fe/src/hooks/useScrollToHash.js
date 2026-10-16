import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';

// Cuộn tới khu vực có id trùng với hash (/#services); đổi trang thì cuộn về đầu trang.
export default function useScrollToHash() {
  const { pathname, hash } = useLocation();
  useEffect(() => {
    const target = hash ? document.getElementById(decodeURIComponent(hash.slice(1))) : null;
    if (target) target.scrollIntoView();
    else window.scrollTo({ top: 0, behavior: 'instant' });
  }, [pathname, hash]);
}

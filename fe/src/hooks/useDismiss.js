import { useEffect } from 'react';

// Gọi onDismiss khi người dùng bấm / focus ra ngoài `ref` hoặc nhấn Escape, chỉ khi `active` bật.
export default function useDismiss(ref, active, onDismiss) {
  useEffect(() => {
    if (!active) return undefined;
    const outside = (event) => { if (!ref.current?.contains(event.target)) onDismiss(event); };
    const escape = (event) => { if (event.key === 'Escape') onDismiss(event); };
    document.addEventListener('pointerdown', outside);
    document.addEventListener('focusin', outside);
    document.addEventListener('keydown', escape);
    return () => {
      document.removeEventListener('pointerdown', outside);
      document.removeEventListener('focusin', outside);
      document.removeEventListener('keydown', escape);
    };
  }, [ref, active, onDismiss]);
}

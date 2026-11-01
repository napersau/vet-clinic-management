import { useEffect, useRef } from 'react';

/** Gọi `callback` định kỳ khi tab đang hiển thị (dự phòng cho trường hợp mất kết nối thông báo thời gian thực). */
export default function usePolling(callback, intervalMs = 30000) {
  const latest = useRef(callback);
  latest.current = callback;
  useEffect(() => {
    const timer = setInterval(() => { if (document.visibilityState === 'visible') latest.current(); }, intervalMs);
    return () => clearInterval(timer);
  }, [intervalMs]);
}

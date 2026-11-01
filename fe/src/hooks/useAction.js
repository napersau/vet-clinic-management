import { useCallback, useRef, useState } from 'react';

/**
 * Chạy một thao tác ghi (POST/PUT) với trạng thái bận + thông báo. `run(fn, successMessage)` trả về kết quả
 * (hoặc `true` nếu thao tác không trả gì); khi lỗi trả undefined và đưa lỗi vào `notice`.
 * Chặn bấm đúp: đang bận thì bỏ qua lời gọi mới.
 */
export default function useAction() {
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState(null);
  const running = useRef(false);
  const run = useCallback(async (action, success) => {
    if (running.current) return undefined;
    running.current = true; setBusy(true); setNotice(null);
    try {
      const result = await action();
      if (success) setNotice({ type: 'success', text: typeof success === 'function' ? success(result) : success });
      return result === undefined || result === null ? true : result;
    } catch (problem) {
      setNotice({ type: 'error', text: problem.message || 'Thao tác không thành công.' });
      return undefined;
    } finally { running.current = false; setBusy(false); }
  }, []);
  const clear = useCallback(() => setNotice(null), []);
  return { busy, notice, run, setNotice, clear };
}

import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Tải dữ liệu khi vào trang hoặc khi `deps` đổi. Trả { data, loading, error, reload, refresh, setData }.
 * Kết quả của lần gọi cũ (đã bị thay thế) bị bỏ qua để bộ lọc đổi nhanh không hiện dữ liệu lệch.
 * reload() hiện lại màn hình "đang tải"; refresh() tải lại âm thầm, giữ dữ liệu đang hiển thị.
 */
export default function useAsync(load, deps = [], { skip = false } = {}) {
  const [state, setState] = useState({ data: null, loading: !skip, error: '' });
  const ticket = useRef(0);
  const loader = useRef(load);
  loader.current = load;

  const run = useCallback(async (quiet = false) => {
    const mine = ++ticket.current;
    if (!quiet) setState((current) => ({ ...current, loading: true, error: '' }));
    try {
      const data = await loader.current();
      if (mine === ticket.current) setState({ data, loading: false, error: '' });
    } catch (problem) {
      if (mine === ticket.current) setState((current) => ({ data: quiet ? current.data : null, loading: false, error: problem.message || 'Không tải được dữ liệu.' }));
    }
  }, []);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { if (!skip) run(); return () => { ticket.current += 1; }; }, [skip, ...deps]);
  const setData = useCallback((data) => setState((current) => ({ ...current, data })), []);
  const reload = useCallback(() => run(false), [run]);
  const refresh = useCallback(() => run(true), [run]);
  return { ...state, reload, refresh, setData };
}

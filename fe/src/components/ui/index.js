import { useEffect, useRef, useState } from 'react';
import Modal from '../common/Modal';
import { labelOf, toneOf } from '../../lib/labels';
import '../../styles/ui.css';

// Bộ thành phần giao diện dùng chung cho mọi vai trò (khu vực nhân viên dùng khung admin-shell, cổng chủ nuôi dùng customer-shell).

export function PageHead({ eyebrow, title, children, actions }) {
  return <div className="ui-head"><div>{eyebrow && <span className="ui-eyebrow">{eyebrow}</span>}<h1>{title}</h1>{children && <p>{children}</p>}</div>{actions && <div className="ui-head-actions">{actions}</div>}</div>;
}

export function Panel({ title, subtitle, actions, children, flush = false, className = '' }) {
  return <section className={`ui-panel ${className}`}>
    {(title || actions) && <header className="ui-panel-head"><div>{title && <h2>{title}</h2>}{subtitle && <p>{subtitle}</p>}</div>{actions && <div className="ui-head-actions">{actions}</div>}</header>}
    <div className={flush ? 'ui-panel-flush' : 'ui-panel-body'}>{children}</div>
  </section>;
}

/** Hiện trạng thái tải / lỗi / rỗng của một useAsync; có dữ liệu thì gọi children(data). */
export function AsyncView({ state, children, empty, isEmpty }) {
  if (state.loading && state.data === null) return <p className="ui-loading" role="status">Đang tải dữ liệu…</p>;
  if (state.error && state.data === null) return <div className="ui-alert error" role="alert"><span>{state.error}</span><button type="button" onClick={state.reload}>Thử lại</button></div>;
  if (state.data === null) return null;
  if (empty && (isEmpty ? isEmpty(state.data) : (Array.isArray(state.data) && state.data.length === 0))) return <Empty>{empty}</Empty>;
  return children(state.data);
}

export function Empty({ title, children }) {
  return <div className="ui-empty">{title && <strong>{title}</strong>}<p>{children}</p></div>;
}

export function Notice({ notice, onClose }) {
  if (!notice) return null;
  return <div className={`ui-alert ${notice.type}`} role={notice.type === 'error' ? 'alert' : 'status'}><span>{notice.text}</span>{onClose && <button type="button" aria-label="Đóng thông báo" onClick={onClose}>×</button>}</div>;
}

export function Badge({ map, value, tone, children }) {
  return <span className={`ui-badge ${tone || toneOf(map || {}, value)}`}>{children ?? labelOf(map || {}, value)}</span>;
}

export function Button({ variant = 'secondary', busy = false, children, type = 'button', className = '', ...props }) {
  return <button type={type} className={`ui-button ${variant} ${className}`} disabled={busy || props.disabled} {...props}>{busy ? 'Đang xử lý…' : children}</button>;
}

export function Field({ label, error, hint, children, wide = false }) {
  return <label className={`ui-field ${wide ? 'wide' : ''}`}><span>{label}</span>{children}{hint && !error && <small>{hint}</small>}{error && <small className="error">{error}</small>}</label>;
}

export const Input = ({ error, ...props }) => <input className={`ui-input ${error ? 'invalid' : ''}`} {...props} />;
export const Select = ({ children, ...props }) => <select className="ui-input" {...props}>{children}</select>;
export const Textarea = (props) => <textarea className="ui-input" rows={3} {...props} />;

export function Stats({ items }) {
  return <div className="ui-stats">{items.map(([label, value, hint, tone]) => <div className={`ui-stat ${tone || ''}`} key={label}><span>{label}</span><strong>{value}</strong>{hint && <small>{hint}</small>}</div>)}</div>;
}

export function Table({ columns, rows, rowKey = 'id', empty = 'Chưa có dữ liệu.', onRowClick, caption, rowClass }) {
  if (!rows.length) return <Empty>{empty}</Empty>;
  return <div className="ui-table-scroll"><table className="ui-table">
    {caption && <caption className="ui-sr-only">{caption}</caption>}
    <thead><tr>{columns.map((column) => <th key={column.key || column.title} className={column.align === 'right' ? 'right' : undefined}>{column.title}</th>)}</tr></thead>
    <tbody>{rows.map((row, index) => <tr key={typeof rowKey === 'function' ? rowKey(row) : row[rowKey] ?? index} className={[onRowClick ? 'clickable' : '', rowClass ? rowClass(row) : ''].join(' ').trim() || undefined} onClick={onRowClick ? () => onRowClick(row) : undefined}>
      {columns.map((column) => <td key={column.key || column.title} className={column.align === 'right' ? 'right' : undefined}>{column.render ? column.render(row) : row[column.key]}</td>)}</tr>)}</tbody>
  </table></div>;
}

/** Phân trang theo PageResponse của backend (page bắt đầu từ 0). */
export function Pager({ page, onChange }) {
  if (!page || page.totalPages <= 1) return page ? <div className="ui-pager"><span>{page.totalElements} kết quả</span></div> : null;
  return <div className="ui-pager"><span>{page.totalElements} kết quả · Trang {page.page + 1}/{page.totalPages}</span>
    <div><Button disabled={page.page <= 0} onClick={() => onChange(page.page - 1)}>← Trước</Button><Button disabled={page.last} onClick={() => onChange(page.page + 1)}>Sau →</Button></div></div>;
}

export function Facts({ items }) {
  return <dl className="ui-facts">{items.filter(Boolean).map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value === null || value === undefined || value === '' ? '—' : value}</dd></div>)}</dl>;
}

export function Tabs({ tabs, value, onChange }) {
  return <div className="ui-tabs" role="tablist">{tabs.map((tab) => <button type="button" role="tab" aria-selected={value === tab.key} className={value === tab.key ? 'active' : ''} key={tab.key} onClick={() => onChange(tab.key)}>{tab.label}{tab.count !== undefined && <em>{tab.count}</em>}</button>)}</div>;
}

export function SearchBox({ value, onChange, placeholder = 'Tìm kiếm…', label = 'Tìm kiếm' }) {
  return <label className="ui-search"><span className="ui-sr-only">{label}</span><input type="search" value={value} placeholder={placeholder} onChange={(event) => onChange(event.target.value)} /></label>;
}

/** Hộp thoại có form: `onSubmit` trả về Promise; ném lỗi thì hộp thoại giữ nguyên và hiện lỗi. */
export function FormDialog({ title, id, onClose, onSubmit, submitLabel = 'Lưu', danger = false, children, wide = false }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const mounted = useRef(true);
  useEffect(() => () => { mounted.current = false; }, []);
  const submit = async (event) => {
    event.preventDefault();
    if (busy) return;
    setBusy(true); setError('');
    try { await onSubmit(); onClose(); } catch (problem) { if (mounted.current) setError(problem.message || 'Thao tác không thành công.'); } finally { if (mounted.current) setBusy(false); }
  };
  return <Modal title={title} id={id || 'dialog-title'} onClose={onClose} busy={busy} className={wide ? 'ui-dialog-wide' : ''}>
    <form className="ui-form" onSubmit={submit} noValidate>
      <div className="ui-form-body">{children}{error && <div className="ui-alert error" role="alert"><span>{error}</span></div>}</div>
      <div className="ui-form-actions"><Button disabled={busy} onClick={onClose}>Hủy</Button><Button type="submit" variant={danger ? 'danger' : 'primary'} busy={busy}>{submitLabel}</Button></div>
    </form>
  </Modal>;
}

export function ConfirmDialog({ title, children, onClose, onConfirm, confirmLabel = 'Xác nhận', danger = false }) {
  return <FormDialog title={title} onClose={onClose} onSubmit={onConfirm} submitLabel={confirmLabel} danger={danger}><p className="ui-confirm-text">{children}</p></FormDialog>;
}

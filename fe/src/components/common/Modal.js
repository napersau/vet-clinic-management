import { useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { portalRoot } from '../../lib/portal';

export default function Modal({ title, id, children, onClose, busy = false, className = '', variant = 'admin', initialFocusRef }) {
  const dialogRef = useRef(null);
  useEffect(() => {
    const dialog = dialogRef.current;
    const trigger = document.activeElement;
    if (typeof dialog.showModal === 'function') dialog.showModal();
    else dialog.setAttribute('open', '');
    initialFocusRef?.current?.focus();
    return () => {
      if (typeof dialog.close === 'function') dialog.close();
      if (trigger?.isConnected) trigger.focus();
    };
  }, [initialFocusRef]);
  // Dựng trong khung giao diện (xem lib/portal.js): hộp thoại thường được mở từ bên trong một <form> của trang (vd. tạo khách hàng
  // ngay trong biểu mẫu tiếp nhận), mà <form> không được lồng nhau, nhưng vẫn cần kế thừa biến màu của khung.
  return createPortal(
    <dialog className={`${variant === 'customer' ? 'customer-modal' : 'admin-modal'} ${className}`} ref={dialogRef} aria-labelledby={id} onCancel={(event) => { event.preventDefault(); if (!busy) onClose(); }}>
      <header><h2 id={id}>{title}</h2><button type="button" className={variant === 'customer' ? 'customer-dialog-close' : 'admin-icon-button'} aria-label="Đóng hộp thoại" disabled={busy} onClick={onClose}>×</button></header>
      {children}
    </dialog>,
    portalRoot(),
  );
}

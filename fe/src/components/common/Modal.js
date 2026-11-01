import { useEffect, useRef } from 'react';

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
  return (
    <dialog className={`${variant === 'customer' ? 'customer-modal' : 'admin-modal'} ${className}`} ref={dialogRef} aria-labelledby={id} onCancel={(event) => { event.preventDefault(); if (!busy) onClose(); }}>
      <header><h2 id={id}>{title}</h2><button type="button" className={variant === 'customer' ? 'customer-dialog-close' : 'admin-icon-button'} aria-label="Đóng hộp thoại" disabled={busy} onClick={onClose}>×</button></header>
      {children}
    </dialog>
  );
}

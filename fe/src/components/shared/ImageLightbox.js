import { useCallback, useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { portalRoot } from '../../lib/portal';
import { formatScale, stepScale, wheelScale } from '../../lib/zoom';

/**
 * Hộp xem ảnh phóng to: nút +/−, con lăn chuột, phím + − 0, kéo để di chuyển khi đã phóng to, mũi tên trái/phải chuyển ảnh.
 * `images` = [{ url, name }], `index` là ảnh đang xem; `onIndexChange` và `onClose` do nơi gọi giữ.
 */
export default function ImageLightbox({ images, index, onIndexChange, onClose }) {
  const [scale, setScale] = useState(1);
  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const drag = useRef(null);
  const dialog = useRef(null);
  const image = images[index];

  const reset = useCallback(() => { setScale(1); setOffset({ x: 0, y: 0 }); }, []);
  const zoom = useCallback((direction) => setScale((current) => { const next = stepScale(current, direction); if (next <= 1) setOffset({ x: 0, y: 0 }); return next; }), []);
  const go = useCallback((step) => { if (images.length > 1) { reset(); onIndexChange((index + step + images.length) % images.length); } }, [images.length, index, onIndexChange, reset]);

  useEffect(() => {
    const element = dialog.current;
    const trigger = document.activeElement;
    if (typeof element.showModal === 'function') element.showModal(); else element.setAttribute('open', '');
    return () => { if (typeof element.close === 'function') element.close(); if (trigger?.isConnected) trigger.focus(); };
  }, []);

  const onKeyDown = (event) => {
    if (event.key === '+' || event.key === '=') zoom(1);
    else if (event.key === '-') zoom(-1);
    else if (event.key === '0') reset();
    else if (event.key === 'ArrowRight') go(1);
    else if (event.key === 'ArrowLeft') go(-1);
  };
  const onWheel = (event) => setScale((current) => { const next = wheelScale(current, event.deltaY); if (next <= 1) setOffset({ x: 0, y: 0 }); return next; });
  const onPointerDown = (event) => { if (scale > 1) { drag.current = { x: event.clientX - offset.x, y: event.clientY - offset.y }; event.currentTarget.setPointerCapture?.(event.pointerId); } };
  const onPointerMove = (event) => { if (drag.current) setOffset({ x: event.clientX - drag.current.x, y: event.clientY - drag.current.y }); };
  const endDrag = () => { drag.current = null; };

  return createPortal(
    <dialog ref={dialog} className="ui-lightbox" aria-label={`Xem ảnh ${image.name}`} onKeyDown={onKeyDown} onCancel={(event) => { event.preventDefault(); onClose(); }}>
      <header>
        <strong>{image.name}{images.length > 1 ? ` (${index + 1}/${images.length})` : ''}</strong>
        <div className="ui-lightbox-tools">
          <button type="button" className="ui-button small" aria-label="Thu nhỏ" onClick={() => zoom(-1)}>−</button>
          <span aria-live="polite" data-testid="zoom-level">{formatScale(scale)}</span>
          <button type="button" className="ui-button small" aria-label="Phóng to" onClick={() => zoom(1)}>+</button>
          <button type="button" className="ui-button small" onClick={reset}>Vừa khung</button>
          <button type="button" className="ui-button small" aria-label="Đóng" onClick={onClose}>Đóng ×</button>
        </div>
      </header>
      <div className={`ui-lightbox-stage ${scale > 1 ? 'can-pan' : ''}`} onWheel={onWheel} onPointerDown={onPointerDown} onPointerMove={onPointerMove} onPointerUp={endDrag} onPointerCancel={endDrag}>
        {images.length > 1 && <button type="button" className="ui-lightbox-nav prev" aria-label="Ảnh trước" onClick={() => go(-1)}>‹</button>}
        <img src={image.url} alt={`Ảnh kết quả ${image.name}`} draggable={false} style={{ transform: `translate(${offset.x}px, ${offset.y}px) scale(${scale})` }} />
        {images.length > 1 && <button type="button" className="ui-lightbox-nav next" aria-label="Ảnh sau" onClick={() => go(1)}>›</button>}
      </div>
    </dialog>,
    portalRoot(),
  );
}

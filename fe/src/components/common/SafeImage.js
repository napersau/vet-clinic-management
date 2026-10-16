import { useState } from 'react';

// Ảnh có phương án thay thế: nếu tệp ảnh chưa có hoặc lỗi tải thì hiển thị biểu tượng thay vì khung ảnh vỡ.
export default function SafeImage({ src, alt = '', fallback = '🐾', className = '', ...imageProps }) {
  const [failed, setFailed] = useState(false);

  if (!src || failed) {
    return (
      <span className={`safe-image-fallback ${className}`} role={alt ? 'img' : undefined} aria-label={alt || undefined} aria-hidden={alt ? undefined : true}>
        {fallback}
      </span>
    );
  }
  return <img className={className} src={src} alt={alt} onError={() => setFailed(true)} {...imageProps} />;
}

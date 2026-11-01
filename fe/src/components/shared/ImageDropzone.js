import { useEffect, useMemo, useRef, useState } from 'react';
import { addImagingFiles, describeRejected, formatSize, IMAGING_ACCEPT, isPreviewable, MAX_IMAGING_FILES } from '../../lib/imagingFiles';

function Thumb({ file }) {
  const [url, setUrl] = useState('');
  useEffect(() => {
    if (!isPreviewable(file) || typeof URL.createObjectURL !== 'function') return undefined;
    const created = URL.createObjectURL(file);
    setUrl(created);
    return () => URL.revokeObjectURL(created);
  }, [file]);
  const [broken, setBroken] = useState(false);
  if (url && !broken) return <img src={url} alt="" onError={() => setBroken(true)} />;
  return <span aria-hidden="true">{isPreviewable(file) ? 'ẢNH' : 'DICOM'}</span>;
}

/**
 * Vùng kéo thả ảnh kết quả CĐHA (JPEG, PNG, DICOM). Bấm hoặc nhấn Enter/Space để chọn bằng hộp thoại tệp.
 * `files` do trang cha giữ; `onChange(files)` nhận danh sách mới; `onProblem(text)` nhận lý do các tệp bị từ chối.
 */
export default function ImageDropzone({ files, onChange, onProblem = () => {}, disabled = false }) {
  const input = useRef(null);
  const [over, setOver] = useState(false);
  const depth = useRef(0);

  const take = (incoming) => {
    const result = addImagingFiles(files, incoming);
    onChange(result.files);
    onProblem(result.rejected.length ? `Không nhận được ${describeRejected(result.rejected)}.` : '');
  };
  const onDrop = (event) => {
    event.preventDefault();
    depth.current = 0; setOver(false);
    if (!disabled) take(event.dataTransfer?.files || []);
  };
  // dragenter/dragleave bắn cho cả phần tử con: đếm độ sâu để không nhấp nháy khi rê qua chữ bên trong.
  const enter = (event) => { event.preventDefault(); depth.current += 1; setOver(true); };
  const leave = () => { depth.current = Math.max(0, depth.current - 1); if (depth.current === 0) setOver(false); };
  const remove = (index) => { onChange(files.filter((_, position) => position !== index)); onProblem(''); };
  const previews = useMemo(() => files.map((file, index) => ({ file, index })), [files]);

  return <div className="ui-dropzone-wrap">
    <div className={`ui-dropzone ${over ? 'is-over' : ''} ${disabled ? 'is-disabled' : ''}`} role="button" tabIndex={disabled ? -1 : 0} aria-disabled={disabled}
      aria-label="Kéo thả ảnh vào đây hoặc bấm để chọn tệp" onClick={() => !disabled && input.current?.click()}
      onKeyDown={(event) => { if (!disabled && (event.key === 'Enter' || event.key === ' ')) { event.preventDefault(); input.current?.click(); } }}
      onDragEnter={enter} onDragOver={(event) => event.preventDefault()} onDragLeave={leave} onDrop={onDrop}>
      <strong>{over ? 'Thả tệp để thêm' : 'Kéo thả ảnh vào đây hoặc bấm để chọn tệp'}</strong>
      <small>JPEG, PNG hoặc DICOM · tối đa 20 MB mỗi tệp · tối đa {MAX_IMAGING_FILES} tệp</small>
      <input ref={input} type="file" multiple accept={IMAGING_ACCEPT} hidden data-testid="imaging-file-input" disabled={disabled}
        onChange={(event) => { take(event.target.files || []); event.target.value = ''; }} />
    </div>
    {files.length > 0 && <ul className="ui-dropzone-list" aria-label="Tệp đã chọn">{previews.map(({ file, index }) => <li key={`${file.name}-${file.size}-${file.lastModified}`}>
      <div className="ui-dropzone-thumb"><Thumb file={file} /></div>
      <div><strong>{file.name}</strong><small>{formatSize(file.size)}</small></div>
      <button type="button" className="ui-button small danger" aria-label={`Bỏ tệp ${file.name}`} disabled={disabled} onClick={() => remove(index)}>Bỏ</button>
    </li>)}</ul>}
  </div>;
}

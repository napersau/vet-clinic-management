// Kiểm tra tệp ảnh CĐHA phía trình duyệt cho khỏi phải tải lên rồi mới biết bị từ chối. Giới hạn khớp backend
// (ImagingStorage.MAX_FILE_BYTES và ParaclinicServiceImpl.MAX_FILES). Backend vẫn kiểm tra lại nội dung tệp bằng magic-byte,
// nên đây chỉ là lớp chặn sớm theo phần mở rộng và kích thước.

export const MAX_IMAGING_FILES = 10;
export const MAX_IMAGING_FILE_BYTES = 20 * 1024 * 1024;
export const IMAGING_ACCEPT = '.jpg,.jpeg,.png,.dcm,image/jpeg,image/png,application/dicom';

const ALLOWED = /\.(jpe?g|png|dcm)$/i;
const PREVIEWABLE = /\.(jpe?g|png)$/i;

export const isPreviewable = (file) => PREVIEWABLE.test(file.name || '');
export const fileKey = (file) => `${file.name}|${file.size}|${file.lastModified ?? 0}`;

export function formatSize(bytes) {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1).replace('.', ',')} MB`;
  return `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

/**
 * Thêm tệp mới vào danh sách đã chọn. Trả { files, rejected } với rejected = [{ name, reason }]:
 * sai định dạng, rỗng, quá 20 MB, trùng tệp đã chọn, hoặc vượt số tệp tối đa.
 */
export function addImagingFiles(current, incoming) {
  const files = [...current];
  const seen = new Set(files.map(fileKey));
  const rejected = [];
  Array.from(incoming).forEach((file) => {
    if (!ALLOWED.test(file.name || '')) rejected.push({ name: file.name, reason: 'chỉ nhận JPEG, PNG hoặc DICOM' });
    else if (!file.size) rejected.push({ name: file.name, reason: 'tệp rỗng' });
    else if (file.size > MAX_IMAGING_FILE_BYTES) rejected.push({ name: file.name, reason: 'vượt quá 20 MB' });
    else if (seen.has(fileKey(file))) rejected.push({ name: file.name, reason: 'đã chọn rồi' });
    else if (files.length >= MAX_IMAGING_FILES) rejected.push({ name: file.name, reason: `tối đa ${MAX_IMAGING_FILES} tệp mỗi kết quả` });
    else { files.push(file); seen.add(fileKey(file)); }
  });
  return { files, rejected };
}

export const describeRejected = (rejected) => rejected.map((item) => `"${item.name}": ${item.reason}`).join('; ');

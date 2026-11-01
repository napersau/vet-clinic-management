import { useEffect, useState } from 'react';
import { paraclinicApi } from '../../api/endpoints';
import { Badge, Facts, Table } from '../ui';
import ImageLightbox from './ImageLightbox';
import { LAB_EVALUATION, PRESCRIPTION_STATUS, SERVICE_CATEGORY, SERVICE_DETAIL_STATUS, VISIT_STATUS } from '../../lib/labels';
import { formatDateTime, formatMoney, formatNumber } from '../../lib/format';

/**
 * Tải các tệp đính kèm CĐHA qua API có xác thực (không dùng thẻ img trực tiếp vì cần Authorization) và trả về
 * { [id]: { url } | { failed: true } }. URL tạm được giải phóng khi rời trang.
 */
export function useAttachmentUrls(attachments) {
  const [loaded, setLoaded] = useState({});
  const key = attachments.map((file) => file.id).join(',');
  useEffect(() => {
    let cancelled = false; const created = [];
    setLoaded({});
    attachments.forEach((file) => {
      paraclinicApi.attachment(file.id)
        .then((blob) => { const url = URL.createObjectURL(blob); created.push(url); if (cancelled) URL.revokeObjectURL(url); else setLoaded((current) => ({ ...current, [file.id]: { url } })); })
        .catch(() => { if (!cancelled) setLoaded((current) => ({ ...current, [file.id]: { failed: true } })); });
    });
    return () => { cancelled = true; created.forEach((url) => URL.revokeObjectURL(url)); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);
  return loaded;
}

const isPreviewableType = (file) => /^image\/(jpeg|png)$/.test(file.contentType || '');

function AttachmentView({ attachment, state, onOpen }) {
  if (state?.failed) return <span className="ui-muted">Không tải được tệp {attachment.fileName}.</span>;
  if (!state) return <span className="ui-muted">Đang tải {attachment.fileName}…</span>;
  return <figure style={{ margin: 0 }}>
    {isPreviewableType(attachment) && <button type="button" className="ui-image-button" aria-label={`Phóng to ảnh ${attachment.fileName}`} onClick={onOpen}><img className="ui-image" src={state.url} alt={`Ảnh kết quả ${attachment.fileName}`} /></button>}
    <figcaption><a className="ui-link" href={state.url} download={attachment.fileName}>Tải {attachment.fileName}</a> <small className="ui-muted">({formatNumber((attachment.fileSize || 0) / 1024)} KB)</small></figcaption>
  </figure>;
}

export function LabResultsTable({ results }) {
  if (!results?.length) return null;
  return <Table caption="Kết quả xét nghiệm" rowKey="id" columns={[
    { title: 'Chỉ số', render: (row) => <><strong>{row.name}</strong><small>{row.code}</small></> },
    { title: 'Kết quả', render: (row) => <span className={row.evaluation === 'HIGH' ? 'ui-lab-high' : row.evaluation === 'LOW' ? 'ui-lab-low' : ''}>{formatNumber(row.value, 2)} {row.unit}</span> },
    { title: 'Khoảng tham chiếu', render: (row) => (row.minValue !== null && row.minValue !== undefined ? `${formatNumber(row.minValue, 2)} – ${formatNumber(row.maxValue, 2)} ${row.unit}` : '—') },
    { title: 'Đánh giá', render: (row) => <Badge map={LAB_EVALUATION} value={row.evaluation} /> },
  ]} rows={results} />;
}

export function ImagingResults({ results }) {
  const all = (results || []).flatMap((result) => result.attachments || []);
  const loaded = useAttachmentUrls(all);
  const [viewer, setViewer] = useState(null); // vị trí trong danh sách ảnh xem được
  if (!results?.length) return null;
  // Chỉ ảnh JPEG/PNG đã tải xong mới đưa vào hộp xem; DICOM chỉ tải về.
  const gallery = all.filter((file) => isPreviewableType(file) && loaded[file.id]?.url).map((file) => ({ id: file.id, url: loaded[file.id].url, name: file.fileName }));
  return <div className="ui-stack" style={{ gap: 14 }}>{results.map((result) => <div key={result.id}>
    <Facts items={[['Mô tả', result.description], ['Kết luận', result.conclusion], ['Thực hiện lúc', formatDateTime(result.performedAt)]]} />
    <div className="ui-actions" style={{ marginTop: 12, alignItems: 'flex-start' }}>{(result.attachments || []).map((file) => <AttachmentView key={file.id} attachment={file} state={loaded[file.id]} onOpen={() => setViewer(gallery.findIndex((item) => item.id === file.id))} />)}</div>
  </div>)}
    {viewer !== null && viewer >= 0 && gallery[viewer] && <ImageLightbox images={gallery} index={viewer} onIndexChange={setViewer} onClose={() => setViewer(null)} />}
  </div>;
}

export function PrescriptionView({ prescription }) {
  if (!prescription) return <p className="ui-muted">Không có đơn thuốc cho đợt khám này.</p>;
  return <div className="ui-stack" style={{ gap: 12 }}>
    {prescription.status && <div><Badge map={PRESCRIPTION_STATUS} value={prescription.status} /></div>}
    {prescription.doctorNotes && <p style={{ margin: 0 }}><strong>Lời dặn của bác sĩ:</strong> {prescription.doctorNotes}</p>}
    <Table caption="Đơn thuốc" rowKey={(row) => row.medicineId} columns={[
      { title: 'Thuốc', render: (row) => <><strong>{row.medicineName}</strong><small>{row.activeIngredient || row.medicineCode}</small></> },
      { title: 'Số lượng', render: (row) => `${row.quantity} ${row.unit}` },
      { title: 'Cách dùng', render: (row) => <>{row.dosage}, {row.frequency}, {row.durationDays} ngày{row.instructions && <small>{row.instructions}</small>}</> },
    ]} rows={prescription.items || []} />
  </div>;
}

/** Một đợt khám trong bệnh án (MedicalHistoryEntry của backend): chẩn đoán, dịch vụ, xét nghiệm, hình ảnh, đơn thuốc. */
export default function MedicalEntry({ entry }) {
  const { visit, doctorName, prescription } = entry;
  const services = (visit.services || []).filter((item) => item.status !== 'CANCELLED');
  return <article className="ui-panel">
    <header className="ui-panel-head"><div><h2>{visit.visitCode} · {formatDateTime(visit.checkedInAt)}</h2><p>Bác sĩ phụ trách: {doctorName || (visit.doctorId ? `#${visit.doctorId}` : 'Chưa ghi nhận')}{visit.emergency ? ' · Cấp cứu' : ''}</p></div><Badge map={VISIT_STATUS} value={visit.status} /></header>
    <div className="ui-panel-body ui-stack" style={{ gap: 20 }}>
      <Facts items={[['Triệu chứng ban đầu', visit.initialSymptoms], ['Cân nặng', visit.weightAtVisit ? `${formatNumber(visit.weightAtVisit, 2)} kg` : null], ['Thân nhiệt', visit.temperature ? `${formatNumber(visit.temperature, 1)} °C` : null],
        ['Ghi chú lâm sàng', visit.clinicalNotes], ['Chẩn đoán', visit.diagnosis], ['Phác đồ điều trị', visit.treatmentPlan], ['Lời dặn của bác sĩ', visit.doctorAdvice]]} />
      {services.length > 0 && <div><h3>Dịch vụ đã thực hiện</h3><Table caption="Dịch vụ" columns={[
        { title: 'Dịch vụ', render: (row) => <><strong>{row.serviceName}</strong><small>{SERVICE_CATEGORY[row.category]}</small></> },
        { title: 'SL', key: 'quantity' }, { title: 'Thành tiền', align: 'right', render: (row) => formatMoney(row.subtotal) },
        { title: 'Trạng thái', render: (row) => <Badge map={SERVICE_DETAIL_STATUS} value={row.status} /> }]} rows={services} /></div>}
      {visit.labResults?.length > 0 && <div><h3>Kết quả xét nghiệm</h3><LabResultsTable results={visit.labResults} /></div>}
      {visit.imagingResults?.length > 0 && <div><h3>Chẩn đoán hình ảnh</h3><ImagingResults results={visit.imagingResults} /></div>}
      <div><h3>Đơn thuốc</h3><PrescriptionView prescription={prescription} /></div>
    </div>
  </article>;
}

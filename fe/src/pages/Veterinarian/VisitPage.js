import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import StaffLayout from '../../components/layout/StaffLayout';
import { ImagingResults, LabResultsTable } from '../../components/shared/MedicalEntry';
import { AsyncView, Badge, Button, ConfirmDialog, Facts, Field, FormDialog, Input, Notice, PageHead, Panel, Select, Table, Tabs, Textarea } from '../../components/ui';
import { catalogApi, pharmacyApi, visitsApi } from '../../api/endpoints';
import { mapFieldErrors } from '../../api/client';
import { useAuth } from '../../contexts/AuthContext';
import useAction from '../../hooks/useAction';
import useAsync from '../../hooks/useAsync';
import usePolling from '../../hooks/usePolling';
import { blankToNull, formatDateTime, formatMoney, formatNumber } from '../../lib/format';
import { SERVICE_CATEGORY, SERVICE_DETAIL_STATUS, VISIT_STATUS } from '../../lib/labels';

function StartExam({ visit, onDone }) {
  const [form, setForm] = useState({ weightAtVisit: '', temperature: '', clinicalNotes: '' });
  const [errors, setErrors] = useState({});
  const action = useAction();
  const set = (key) => (event) => setForm({ ...form, [key]: event.target.value });
  const submit = async (event) => {
    event.preventDefault();
    const found = {};
    // Giới hạn khớp StartExamRequest của backend: cân nặng 0,1–150 kg, thân nhiệt 35–43 °C.
    const weight = Number(form.weightAtVisit);
    const temperature = Number(form.temperature);
    if (form.weightAtVisit === '' || Number.isNaN(weight)) found.weightAtVisit = 'Nhập cân nặng (kg).';
    else if (weight < 0.1 || weight > 150) found.weightAtVisit = 'Cân nặng hợp lệ từ 0,1 đến 150 kg.';
    if (form.temperature === '' || Number.isNaN(temperature)) found.temperature = 'Nhập thân nhiệt (°C).';
    else if (temperature < 35 || temperature > 43) found.temperature = 'Thân nhiệt hợp lệ từ 35 đến 43 °C.';
    setErrors(found);
    if (Object.keys(found).length) return;
    const result = await action.run(() => visitsApi.startExam(visit.id, { weightAtVisit: Number(form.weightAtVisit), temperature: Number(form.temperature), clinicalNotes: blankToNull(form.clinicalNotes) }).catch((problem) => { setErrors(mapFieldErrors(problem)); throw problem; }));
    if (result) onDone();
  };
  return <Panel title="Bắt đầu khám (giai đoạn 1)" subtitle="Ghi nhận chỉ số sinh tồn. Phí khám tổng quát được tự động thêm vào hóa đơn.">
    <form onSubmit={submit} noValidate className="ui-stack" style={{ gap: 16 }}>
      <Notice notice={action.notice} onClose={action.clear} />
      <div className="ui-grid">
        <Field label="Cân nặng (kg) *" error={errors.weightAtVisit}><Input type="number" step="0.1" min="0" value={form.weightAtVisit} onChange={set('weightAtVisit')} autoFocus /></Field>
        <Field label="Thân nhiệt (°C) *" error={errors.temperature}><Input type="number" step="0.1" value={form.temperature} onChange={set('temperature')} /></Field>
        <Field label="Triệu chứng lâm sàng" wide error={errors.clinicalNotes}><Textarea value={form.clinicalNotes} onChange={set('clinicalNotes')} maxLength={1000} /></Field>
      </div>
      <div><Button type="submit" variant="primary" busy={action.busy}>Bắt đầu khám</Button></div>
    </form>
  </Panel>;
}

function ServicesTab({ visit, onChanged }) {
  const catalog = useAsync(() => catalogApi.services(), []);
  const [picked, setPicked] = useState({});
  const action = useAction();
  const navigate = useNavigate();
  const available = (catalog.data || []).filter((service) => service.code !== 'EXAM_GEN');
  const chosen = Object.entries(picked).filter(([, value]) => value.checked);
  const toggle = (id) => setPicked({ ...picked, [id]: { quantity: 1, notes: '', ...picked[id], checked: !picked[id]?.checked } });
  const patch = (id, key, value) => setPicked({ ...picked, [id]: { quantity: 1, notes: '', checked: true, ...picked[id], [key]: value } });
  const hasParaclinical = chosen.some(([id]) => ['LABORATORY', 'IMAGING'].includes(available.find((service) => String(service.id) === id)?.category));

  const order = async () => {
    const services = chosen.map(([id, value]) => ({ serviceId: Number(id), quantity: Number(value.quantity) || 1, notes: blankToNull(value.notes) }));
    const result = await action.run(() => visitsApi.orderServices(visit.id, services));
    if (result) {
      setPicked({});
      if (result.status === 'WAITING_RESULTS') navigate('/vet/today', { replace: true }); else onChanged();
    }
  };

  return <div className="ui-stack">
    <Panel title="Dịch vụ đã chỉ định" flush>
      <Table caption="Dịch vụ đã chỉ định" empty="Chưa có dịch vụ nào." rows={visit.services} columns={[
        { title: 'Dịch vụ', render: (row) => <><strong>{row.serviceName}</strong><small>{SERVICE_CATEGORY[row.category]}{row.notes ? ` · ${row.notes}` : ''}</small></> },
        { title: 'SL', key: 'quantity' }, { title: 'Thành tiền', align: 'right', render: (row) => formatMoney(row.subtotal) },
        { title: 'Trạng thái', render: (row) => <Badge map={SERVICE_DETAIL_STATUS} value={row.status} /> }]} />
    </Panel>
    <Panel title="Chỉ định thêm dịch vụ" subtitle="Xét nghiệm / chẩn đoán hình ảnh: ca chuyển sang chờ kết quả và bạn được giải phóng để nhận ca khác.">
      <Notice notice={action.notice} onClose={action.clear} />
      <AsyncView state={catalog} empty="Chưa có dịch vụ nào trong danh mục.">
        {() => <div className="ui-stack" style={{ gap: 10 }}>
          {available.map((service) => <div key={service.id} style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) 80px minmax(0, 1fr)', gap: 12, alignItems: 'center' }}>
            <label className="ui-check"><input type="checkbox" checked={Boolean(picked[service.id]?.checked)} onChange={() => toggle(service.id)} /><span><strong>{service.name}</strong> <small className="ui-muted">{SERVICE_CATEGORY[service.category]} · {formatMoney(service.price)}</small></span></label>
            <Input type="number" min="1" max="20" aria-label={`Số lượng ${service.name}`} disabled={!picked[service.id]?.checked} value={picked[service.id]?.quantity ?? 1} onChange={(event) => patch(service.id, 'quantity', event.target.value)} />
            <Input aria-label={`Ghi chú ${service.name}`} placeholder="Ghi chú" disabled={!picked[service.id]?.checked} value={picked[service.id]?.notes ?? ''} onChange={(event) => patch(service.id, 'notes', event.target.value)} maxLength={500} />
          </div>)}
          <div><Button variant="primary" disabled={chosen.length === 0} busy={action.busy} onClick={order}>{hasParaclinical ? 'Chỉ định và chuyển sang chờ kết quả' : 'Chỉ định dịch vụ'}</Button></div>
        </div>}
      </AsyncView>
    </Panel>
  </div>;
}

const blankLine = () => ({ medicineId: '', quantity: 1, dosage: '', frequency: '', durationDays: 3, instructions: '' });

function PrescriptionTab({ visit }) {
  const medicines = useAsync(() => pharmacyApi.medicines({ active: true, size: 100 }), []);
  const existing = useAsync(() => pharmacyApi.prescriptionByVisit(visit.id).catch((problem) => { if (problem.status === 404) return null; throw problem; }), [visit.id]);
  const [lines, setLines] = useState([blankLine()]);
  const [notes, setNotes] = useState('');
  const [loaded, setLoaded] = useState(false);
  const action = useAction();
  const current = existing.data && existing.data.status !== 'CANCELLED' ? existing.data : null;

  useEffect(() => {
    if (existing.data === undefined || existing.loading || loaded) return;
    if (current) {
      setLines(current.items.map((item) => ({ medicineId: String(item.medicineId), quantity: item.quantity, dosage: item.dosage, frequency: item.frequency, durationDays: item.durationDays, instructions: item.instructions || '' })));
      setNotes(current.doctorNotes || '');
    }
    setLoaded(true);
  }, [existing.data, existing.loading, current, loaded]);

  const meds = medicines.data?.items || [];
  const setLine = (index, key, value) => setLines(lines.map((line, position) => (position === index ? { ...line, [key]: value } : line)));
  const save = async () => {
    const items = lines.filter((line) => line.medicineId);
    if (!items.length) { action.setNotice({ type: 'error', text: 'Chọn ít nhất một loại thuốc.' }); return; }
    if (items.some((line) => !line.dosage.trim() || !line.frequency.trim())) { action.setNotice({ type: 'error', text: 'Mỗi thuốc cần có liều dùng và tần suất.' }); return; }
    const payload = { doctorNotes: blankToNull(notes), items: items.map((line) => ({ medicineId: Number(line.medicineId), quantity: Number(line.quantity), dosage: line.dosage.trim(), frequency: line.frequency.trim(), durationDays: Number(line.durationDays), instructions: blankToNull(line.instructions) })) };
    const saved = await action.run(() => (current ? pharmacyApi.updatePrescription(current.id, payload) : pharmacyApi.createPrescription({ ...payload, visitId: visit.id })), 'Đã lưu đơn thuốc. Tồn kho chỉ bị trừ khi quầy phát thuốc.');
    if (saved) existing.refresh();
  };
  const cancel = async () => { if (await action.run(() => pharmacyApi.cancelPrescription(current.id), 'Đã hủy đơn thuốc.')) { setLines([blankLine()]); setNotes(''); existing.refresh(); } };

  return <Panel title="Đơn thuốc" subtitle="Lưu đơn TRƯỚC khi chốt ca. Kê vượt tồn kho sẽ bị từ chối; sau khi chốt ca không sửa được.">
    <Notice notice={action.notice} onClose={action.clear} />
    <AsyncView state={medicines}>{() => <div className="ui-stack" style={{ gap: 14 }}>
      {lines.map((line, index) => <div key={index} className="ui-panel" style={{ marginBottom: 0 }}><div className="ui-panel-body ui-grid">
        <Field label="Thuốc" wide><Select value={line.medicineId} onChange={(event) => setLine(index, 'medicineId', event.target.value)}><option value="">Chọn thuốc</option>
          {meds.map((medicine) => <option key={medicine.id} value={medicine.id}>{medicine.name} — {medicine.unit} — tồn {medicine.stockQuantity} — {formatMoney(medicine.salePrice)}</option>)}</Select></Field>
        <Field label="Số lượng"><Input type="number" min="1" value={line.quantity} onChange={(event) => setLine(index, 'quantity', event.target.value)} /></Field>
        <Field label="Số ngày dùng"><Input type="number" min="1" max="365" value={line.durationDays} onChange={(event) => setLine(index, 'durationDays', event.target.value)} /></Field>
        <Field label="Liều dùng *"><Input value={line.dosage} placeholder="Ví dụ: 1 viên/lần" onChange={(event) => setLine(index, 'dosage', event.target.value)} maxLength={100} /></Field>
        <Field label="Tần suất *"><Input value={line.frequency} placeholder="Ví dụ: 2 lần/ngày" onChange={(event) => setLine(index, 'frequency', event.target.value)} maxLength={100} /></Field>
        <Field label="Hướng dẫn" wide><Input value={line.instructions} onChange={(event) => setLine(index, 'instructions', event.target.value)} maxLength={2000} /></Field>
        {lines.length > 1 && <div><Button className="small danger" onClick={() => setLines(lines.filter((_, position) => position !== index))}>Bỏ dòng này</Button></div>}
      </div></div>)}
      <div><Button onClick={() => setLines([...lines, blankLine()])}>+ Thêm thuốc</Button></div>
      <Field label="Lời dặn chung"><Textarea value={notes} onChange={(event) => setNotes(event.target.value)} maxLength={4000} rows={2} /></Field>
      <div className="ui-actions"><Button variant="primary" busy={action.busy} onClick={save}>{current ? 'Cập nhật đơn thuốc' : 'Lưu đơn thuốc'}</Button>{current && <Button variant="danger" onClick={cancel}>Hủy đơn</Button>}</div>
    </div>}</AsyncView>
  </Panel>;
}

function CloseTab({ visit, onClosed }) {
  const [form, setForm] = useState({ diagnosis: visit.diagnosis || '', treatmentPlan: visit.treatmentPlan || '', doctorAdvice: visit.doctorAdvice || '', clinicalNotes: visit.clinicalNotes || '' });
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState('');
  const set = (key) => (event) => setForm({ ...form, [key]: event.target.value });
  return <Panel title="Chốt ca" subtitle="Sau khi chốt, hồ sơ bị khóa (không sửa chẩn đoán, dịch vụ, đơn thuốc) và hóa đơn được lập tự động cho quầy thu ngân.">
    <div className="ui-stack" style={{ gap: 14 }}>
      {error && <div className="ui-alert error" role="alert"><span>{error}</span></div>}
      <Field label="Chẩn đoán *"><Textarea value={form.diagnosis} onChange={set('diagnosis')} maxLength={2000} autoFocus /></Field>
      <Field label="Phác đồ điều trị"><Textarea value={form.treatmentPlan} onChange={set('treatmentPlan')} maxLength={2000} /></Field>
      <Field label="Lời dặn cho chủ nuôi"><Textarea value={form.doctorAdvice} onChange={set('doctorAdvice')} maxLength={2000} /></Field>
      <Field label="Ghi chú lâm sàng"><Textarea value={form.clinicalNotes} onChange={set('clinicalNotes')} maxLength={2000} rows={2} /></Field>
      <div><Button variant="primary" onClick={() => { if (!form.diagnosis.trim()) setError('Chẩn đoán là bắt buộc để chốt ca.'); else { setError(''); setConfirming(true); } }}>Chốt ca và chuyển thanh toán</Button></div>
    </div>
    {confirming && <ConfirmDialog title="Chốt ca khám" confirmLabel="Chốt ca" onClose={() => setConfirming(false)} onConfirm={async () => { await visitsApi.closeVisit(visit.id, { diagnosis: form.diagnosis.trim(), treatmentPlan: blankToNull(form.treatmentPlan), doctorAdvice: blankToNull(form.doctorAdvice), clinicalNotes: blankToNull(form.clinicalNotes) }); onClosed(); }}>
      Đơn thuốc và dịch vụ sẽ bị khóa. Hãy chắc chắn đã lưu đơn thuốc trước khi chốt.</ConfirmDialog>}
  </Panel>;
}

function CancelVisit({ visit, onClose, onDone }) {
  const [reason, setReason] = useState('');
  return <FormDialog title={`Hủy đợt khám ${visit.visitCode}`} onClose={onClose} submitLabel="Hủy đợt khám" danger onSubmit={async () => { if (!reason.trim()) throw new Error('Nhập lý do hủy.'); await visitsApi.cancel(visit.id, reason.trim()); onDone(); }}>
    <Field label="Lý do *"><Textarea value={reason} onChange={(event) => setReason(event.target.value)} maxLength={500} autoFocus /></Field>
  </FormDialog>;
}

export default function VisitPage() {
  const { visitId } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const visit = useAsync(() => visitsApi.get(visitId), [visitId]);
  const [tab, setTab] = useState('services');
  const [cancelling, setCancelling] = useState(false);
  const [abandoning, setAbandoning] = useState(false);
  const action = useAction();
  usePolling(visit.refresh, 15000);

  return <StaffLayout area="vet" title="Hồ sơ khám">
    <AsyncView state={visit}>
      {(v) => {
        const mine = String(v.doctorId) === String(user.id);
        const hasResults = v.labResults?.length > 0 || v.imagingResults?.length > 0;
        return <>
          <PageHead eyebrow={v.visitCode} title={`${v.petName} (${v.speciesName})`} actions={<><Link className="ui-button" to="/vet/today">← Ca của tôi</Link><Link className="ui-button" to={`/vet/history?petId=${v.petId}`}>Bệnh án trước đây</Link></>}>
            Tiếp nhận {formatDateTime(v.checkedInAt)}{v.emergency ? ' · CẤP CỨU' : ''} · Phòng {v.assignedRoom || '—'}
          </PageHead>
          <Notice notice={action.notice} onClose={action.clear} />
          <Panel title="Tóm tắt" actions={<Badge map={VISIT_STATUS} value={v.status} />}>
            <Facts items={[['Triệu chứng ban đầu', v.initialSymptoms], ['Cân nặng', v.weightAtVisit ? `${formatNumber(v.weightAtVisit, 2)} kg` : null], ['Thân nhiệt', v.temperature ? `${formatNumber(v.temperature, 1)} °C` : null], ['Ghi chú lâm sàng', v.clinicalNotes], ['Chẩn đoán', v.diagnosis]]} />
          </Panel>
          {!mine && v.doctorId && <div className="ui-alert warning"><span>Ca này được phân công cho bác sĩ khác — bạn chỉ xem được.</span></div>}
          {mine && v.status === 'WAITING' && <StartExam visit={v} onDone={visit.refresh} />}
          {mine && v.status === 'IN_PROGRESS' && <>
            <Tabs value={tab} onChange={setTab} tabs={[{ key: 'services', label: 'Chỉ định dịch vụ' }, { key: 'prescription', label: 'Đơn thuốc' }, { key: 'close', label: 'Chốt ca' }]} />
            {tab === 'services' && <ServicesTab visit={v} onChanged={visit.refresh} />}
            {tab === 'prescription' && <PrescriptionTab visit={v} />}
            {tab === 'close' && <CloseTab visit={v} onClosed={() => navigate('/vet/today', { replace: true })} />}
            <div className="ui-actions" style={{ marginTop: 18 }}><Button variant="danger" onClick={() => setCancelling(true)}>Hủy đợt khám</Button></div>
          </>}
          {v.status === 'WAITING_RESULTS' && <div className="ui-alert info"><span>Đang chờ kỹ thuật viên hoàn tất xét nghiệm / chẩn đoán hình ảnh. Bạn sẽ nhận thông báo khi đủ kết quả.</span></div>}
          {mine && v.status === 'RESULTS_READY' && <Panel title="Đã có đủ kết quả" subtitle="Mời thú cưng vào khám tiếp hoặc tạm hoãn để nhận ca khác (bạn vẫn giữ khóa ưu tiên cho tới khi mời).">
            <div className="ui-actions">
              <Button variant="primary" busy={action.busy} onClick={async () => { if (await action.run(() => visitsApi.callBack(v.id), 'Đã mời vào khám tiếp.')) visit.refresh(); }}>Mời vào khám tiếp</Button>
              {!v.callbackPostponed && <Button busy={action.busy} onClick={async () => { if (await action.run(() => visitsApi.postponeCallback(v.id), 'Đã tạm hoãn.')) navigate('/vet/today'); }}>Tạm hoãn</Button>}
              <Button variant="danger" onClick={() => setAbandoning(true)}>Khách bỏ về — đóng ca</Button>
            </div></Panel>}
          {abandoning && <ConfirmDialog title="Đóng ca — khách bỏ về" danger confirmLabel="Đóng ca" onClose={() => setAbandoning(false)} onConfirm={async () => { await visitsApi.abandon(v.id); navigate('/vet/today', { replace: true }); }}>
            Chỉ dùng khi chủ nuôi đã bỏ về và không quay lại đọc kết quả. Hồ sơ bị khóa, kết quả cận lâm sàng được lưu vào bệnh án và chi phí đã phát sinh được lập hóa đơn chờ thu (ghi nợ).</ConfirmDialog>}
          {['WAITING_PAYMENT', 'COMPLETED'].includes(v.status) && <div className="ui-alert info"><span>Hồ sơ đã chốt{v.status === 'COMPLETED' ? ' và hoàn tất' : ', đang chờ thanh toán'}; không thể chỉnh sửa. Quản trị viên có thể mở lại ca chưa thu tiền khi cần.</span></div>}
          {(v.services?.length > 0 && v.status !== 'IN_PROGRESS') && <Panel title="Dịch vụ đã chỉ định" flush><Table caption="Dịch vụ" rows={v.services} columns={[
            { title: 'Dịch vụ', render: (row) => <><strong>{row.serviceName}</strong><small>{SERVICE_CATEGORY[row.category]}</small></> }, { title: 'Trạng thái', render: (row) => <Badge map={SERVICE_DETAIL_STATUS} value={row.status} /> }]} /></Panel>}
          {hasResults && <Panel title="Kết quả cận lâm sàng">
            <div className="ui-stack">{v.labResults?.length > 0 && <LabResultsTable results={v.labResults} />}{v.imagingResults?.length > 0 && <ImagingResults results={v.imagingResults} />}</div>
          </Panel>}
          {cancelling && <CancelVisit visit={v} onClose={() => setCancelling(false)} onDone={() => navigate('/vet/today', { replace: true })} />}
        </>;
      }}
    </AsyncView>
  </StaffLayout>;
}

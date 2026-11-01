import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import StaffLayout from '../../components/layout/StaffLayout';
import ImageDropzone from '../../components/shared/ImageDropzone';
import { ROOM } from './QueuePage';
import { AsyncView, Badge, Button, Facts, Field, Notice, PageHead, Panel, Table, Textarea } from '../../components/ui';
import { paraclinicApi } from '../../api/endpoints';
import useAction from '../../hooks/useAction';
import useAsync from '../../hooks/useAsync';
import { blankToNull, formatNumber } from '../../lib/format';
import { LAB_EVALUATION } from '../../lib/labels';
import { evaluateLab, LAB_FIELD_CLASS, parseLabValue } from '../../lib/labs';

function useQueueItem(kind, detailId) {
  const room = ROOM[kind];
  return useAsync(async () => {
    const item = (await room.load()).find((entry) => String(entry.serviceDetailId) === String(detailId));
    if (!item) throw new Error('Ca này không còn trong hàng đợi (đã hoàn tất, bị hủy hoặc thuộc phòng khác).');
    return item;
  }, [kind, detailId]);
}

function Header({ item, kind }) {
  return <Panel title={`${item.petName} — ${item.serviceName}`}>
    <Facts items={[['Đợt khám', item.visitCode], ['Loài', item.speciesName], ['Ca cấp cứu', item.emergency ? 'Có' : 'Không'], ['Ghi chú của bác sĩ', item.notes]]} />
    <div style={{ marginTop: 14 }}><Link className="ui-button" to={`${ROOM[kind].base}/queue`}>← Về hàng đợi</Link></div>
  </Panel>;
}

const rangeOf = (row) => row.ranges?.[0];

function LabForm({ detailId, onDone }) {
  const form = useAsync(() => paraclinicApi.labForm(detailId), [detailId]);
  const [values, setValues] = useState({});
  const action = useAction();
  const set = (id, key, value) => setValues((current) => ({ ...current, [id]: { ...current[id], [key]: value } }));

  // Tô màu ngay khi gõ: vàng = thấp, xanh = bình thường, đỏ = cao (khoảng tham chiếu theo loài của thú cưng).
  const rows = useMemo(() => form.data || [], [form.data]);
  const parsed = useMemo(() => Object.fromEntries(rows.map((row) => [row.id, parseLabValue(values[row.id]?.value)])), [rows, values]);
  const evaluation = (row) => (parsed[row.id]?.value !== undefined ? evaluateLab(parsed[row.id].value, rangeOf(row)) : null);
  const filled = rows.filter((row) => !parsed[row.id]?.empty);
  const abnormal = rows.filter((row) => ['LOW', 'HIGH'].includes(evaluation(row)));

  const submit = async (event) => {
    event.preventDefault();
    const invalid = rows.find((row) => parsed[row.id]?.error);
    if (invalid) { action.setNotice({ type: 'error', text: `${invalid.name}: ${parsed[invalid.id].error}` }); return; }
    if (!filled.length) { action.setNotice({ type: 'error', text: 'Nhập ít nhất một chỉ số.' }); return; }
    const items = filled.map((row) => ({ labTestItemId: Number(row.id), value: parsed[row.id].value, notes: blankToNull(values[row.id]?.notes || '') }));
    if (await action.run(() => paraclinicApi.saveLabResults(detailId, items))) onDone();
  };

  return <Panel title="Nhập kết quả xét nghiệm" subtitle="Khoảng tham chiếu theo loài của thú cưng. Ô nhập tô vàng khi thấp, xanh khi bình thường, đỏ khi cao; hệ thống đánh giá lại và lưu cùng khoảng tham chiếu khi bạn bấm lưu.">
    <AsyncView state={form} empty="Dịch vụ này chưa có chỉ số xét nghiệm nào được cấu hình.">
      {(list) => <form onSubmit={submit} noValidate className="ui-stack" style={{ gap: 14 }}>
        <Notice notice={action.notice} onClose={action.clear} />
        <Table caption="Chỉ số xét nghiệm" rowKey="id" rows={list} columns={[
          { title: 'Chỉ số', render: (row) => <><strong>{row.name}</strong><small>{row.code}</small></> },
          { title: 'Khoảng tham chiếu', render: (row) => { const range = rangeOf(row); return range ? `${formatNumber(range.minValue, 2)} – ${formatNumber(range.maxValue, 2)} ${row.unit}` : 'Chưa cấu hình cho loài này'; } },
          { title: 'Giá trị đo', render: (row) => {
            const level = evaluation(row);
            return <div className="ui-lab-entry">
              <input className={`ui-input ui-lab-input ${level ? LAB_FIELD_CLASS[level] : ''} ${parsed[row.id]?.error ? 'invalid' : ''}`} inputMode="decimal" aria-label={`Giá trị ${row.name}`} aria-invalid={Boolean(parsed[row.id]?.error)}
                value={values[row.id]?.value ?? ''} onChange={(event) => set(row.id, 'value', event.target.value)} />
              <span className="ui-muted">{row.unit}</span>
              {level && <Badge map={LAB_EVALUATION} value={level} />}
              {parsed[row.id]?.error && <small className="ui-lab-error" role="alert">{parsed[row.id].error}</small>}
            </div>;
          } },
          { title: 'Ghi chú', render: (row) => <input className="ui-input" aria-label={`Ghi chú ${row.name}`} value={values[row.id]?.notes ?? ''} onChange={(event) => set(row.id, 'notes', event.target.value)} maxLength={500} /> }]} />
        <div className="ui-lab-summary" role="status">
          {filled.length === 0 ? 'Chưa nhập chỉ số nào.' : `Đã nhập ${filled.length}/${list.length} chỉ số${abnormal.length ? ` · ${abnormal.length} chỉ số ngoài khoảng tham chiếu` : ' · không có chỉ số bất thường'}.`}
        </div>
        <div><Button type="submit" variant="primary" busy={action.busy}>Lưu kết quả và hoàn tất</Button></div>
      </form>}
    </AsyncView>
  </Panel>;
}

function ImagingForm({ detailId, onDone }) {
  const [description, setDescription] = useState('');
  const [conclusion, setConclusion] = useState('');
  const [files, setFiles] = useState([]);
  const [error, setError] = useState('');
  const action = useAction();
  const submit = async (event) => {
    event.preventDefault();
    // Backend bắt buộc cả mô tả lẫn kết luận (ParaclinicServiceImpl.submitImagingResult).
    if (!description.trim()) { setError('Nhập mô tả kết quả.'); return; }
    if (!conclusion.trim()) { setError('Nhập kết luận.'); return; }
    if (!files.length) { setError('Đính kèm ít nhất một tệp ảnh.'); return; }
    setError('');
    if (await action.run(() => paraclinicApi.saveImagingResult(detailId, { description: description.trim(), conclusion: conclusion.trim(), files }))) onDone();
  };
  return <Panel title="Nhập kết quả chẩn đoán hình ảnh" subtitle="Kéo thả ảnh vào khung hoặc bấm để chọn. Hệ thống kiểm tra nội dung tệp, không chỉ phần mở rộng.">
    <form onSubmit={submit} noValidate className="ui-stack" style={{ gap: 14 }}>
      <Notice notice={action.notice} onClose={action.clear} />
      {error && <div className="ui-alert error" role="alert"><span>{error}</span></div>}
      <Field label="Mô tả *"><Textarea value={description} onChange={(event) => setDescription(event.target.value)} maxLength={4000} /></Field>
      <Field label="Kết luận *"><Textarea value={conclusion} onChange={(event) => setConclusion(event.target.value)} maxLength={2000} rows={2} /></Field>
      <div className="ui-field"><span>Tệp ảnh *</span><ImageDropzone files={files} onChange={setFiles} onProblem={setError} disabled={action.busy} /></div>
      <div><Button type="submit" variant="primary" busy={action.busy}>Lưu kết quả và hoàn tất</Button></div>
    </form>
  </Panel>;
}

export default function EntryPage({ kind }) {
  const { detailId } = useParams();
  const navigate = useNavigate();
  const room = ROOM[kind];
  const item = useQueueItem(kind, detailId);
  const back = () => navigate(`${room.base}/queue`, { replace: true });
  return <StaffLayout area={room.area} title="Nhập kết quả">
    <PageHead eyebrow={room.eyebrow} title="Nhập kết quả" />
    <AsyncView state={item}>
      {(data) => data.queueState !== 'IN_PROGRESS'
        ? <div className="ui-alert warning"><span>Dịch vụ chưa được bắt đầu. Hãy bấm “Bắt đầu” ở hàng đợi trước khi nhập kết quả.</span> <Link className="ui-link" to={`${room.base}/queue`}>Về hàng đợi</Link></div>
        : <><Header item={data} kind={kind} />{kind === 'lab' ? <LabForm detailId={detailId} onDone={back} /> : <ImagingForm detailId={detailId} onDone={back} />}</>}
    </AsyncView>
  </StaffLayout>;
}

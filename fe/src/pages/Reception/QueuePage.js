import { useState } from 'react';
import StaffLayout from '../../components/layout/StaffLayout';
import { AsyncView, Badge, Button, ConfirmDialog, Field, FormDialog, Notice, PageHead, Panel, Stats, Table, Textarea } from '../../components/ui';
import { billingApi, visitsApi } from '../../api/endpoints';
import useAsync from '../../hooks/useAsync';
import useAction from '../../hooks/useAction';
import usePolling from '../../hooks/usePolling';
import { formatDateTime, formatTimeOfInstant } from '../../lib/format';
import { DOCTOR_STATUS, VISIT_STATUS } from '../../lib/labels';

const waitedMinutes = (instant) => Math.max(0, Math.floor((Date.now() - new Date(instant).getTime()) / 60000));
const ACTIVE = ['IN_PROGRESS', 'WAITING_RESULTS', 'RESULTS_READY', 'WAITING_PAYMENT'];

async function loadBoard() {
  const [queue, doctors, ...active] = await Promise.all([visitsApi.queue(), visitsApi.doctorsStatus(), ...ACTIVE.map((status) => visitsApi.list({ status, size: 50 }))]);
  return { queue, doctors, active: active.flatMap((page) => page.items).sort((a, b) => String(a.checkedInAt).localeCompare(String(b.checkedInAt))) };
}

function AssignDialog({ visit, doctors, onClose, onDone }) {
  const [doctorId, setDoctorId] = useState('');
  const ready = doctors.filter((doctor) => doctor.status === 'AVAILABLE' && doctor.doctorId !== visit.doctorId);
  const change = Boolean(visit.doctorId);
  const submit = async () => {
    if (!doctorId) throw new Error('Chọn bác sĩ phụ trách.');
    await visitsApi.assign(visit.id, Number(doctorId));
    onDone(`Đã ${change ? 'đổi bác sĩ' : 'phân công bác sĩ'} cho ${visit.petName} (${visit.visitCode}).`);
  };
  return <FormDialog title={`${change ? 'Đổi bác sĩ' : 'Phân công bác sĩ'} — ${visit.petName}`} onClose={onClose} onSubmit={submit} submitLabel={change ? 'Đổi bác sĩ' : 'Phân công'}>
    <p className="ui-confirm-text">Chỉ bác sĩ <strong>sẵn sàng</strong> mới chọn được. Bác sĩ đang bận hoặc đang giữ khóa ưu tiên tái khám (có ca đã có kết quả chờ mời vào) bị khóa cho tới khi xong.{change ? ' Chỉ đổi được khi bác sĩ cũ chưa bắt đầu khám.' : ''}</p>
    {ready.length === 0 && <div className="ui-alert warning" role="status"><span>Hiện không có bác sĩ nào sẵn sàng. Bác sĩ đang bận hoặc đang giữ khóa ưu tiên tái khám chưa nhận được ca mới từ sảnh.</span></div>}
    <Field label="Bác sĩ *"><select className="ui-input" value={doctorId} onChange={(event) => setDoctorId(event.target.value)} autoFocus>
      <option value="">Chọn bác sĩ</option>
      {doctors.map((doctor) => <option key={doctor.doctorId} value={doctor.doctorId} disabled={doctor.status !== 'AVAILABLE' || doctor.doctorId === visit.doctorId}>{doctor.fullName} — phòng {doctor.roomNumber || '?'} — {doctor.doctorId === visit.doctorId ? 'đang được phân công ca này' : DOCTOR_STATUS[doctor.status].label}</option>)}
    </select></Field>
  </FormDialog>;
}

function CancelVisitDialog({ visit, onClose, onDone }) {
  const [reason, setReason] = useState('');
  const submit = async () => {
    if (!reason.trim()) throw new Error('Nhập lý do hủy.');
    await visitsApi.cancel(visit.id, reason.trim());
    onDone(`Đã hủy đợt khám ${visit.visitCode}.`);
  };
  return <FormDialog title={`Hủy đợt khám ${visit.visitCode}`} onClose={onClose} onSubmit={submit} submitLabel="Hủy đợt khám" danger>
    <Field label="Lý do *"><Textarea value={reason} onChange={(event) => setReason(event.target.value)} maxLength={500} autoFocus /></Field>
  </FormDialog>;
}

function HistoryDialog({ visit, onClose }) {
  const history = useAsync(() => visitsApi.history(visit.id), [visit.id]);
  return <FormDialog title={`Lịch sử ${visit.visitCode}`} onClose={onClose} onSubmit={async () => {}} submitLabel="Đóng">
    <AsyncView state={history} empty="Chưa có lịch sử.">{(items) => <ul className="ui-list">{items.map((item) => <li key={item.id}><div><strong>{item.fromStatus ? `${VISIT_STATUS[item.fromStatus]?.label} → ` : ''}{VISIT_STATUS[item.toStatus]?.label}</strong>{item.notes && <small>{item.notes}</small>}</div><small>{formatDateTime(item.createdAt)}</small></li>)}</ul>}</AsyncView>
  </FormDialog>;
}

export default function QueuePage() {
  const board = useAsync(loadBoard, []);
  const [dialog, setDialog] = useState(null);
  const action = useAction();
  usePolling(board.refresh, 15000);
  const done = (text) => { setDialog(null); action.setNotice({ type: 'success', text }); board.refresh(); };
  const run = (fn, text) => action.run(fn, text).then((result) => { if (result) board.refresh(); });

  const data = board.data;
  const priority = data?.doctors.filter((doctor) => doctor.status === 'AVAILABLE_PRIORITY_CALLBACK') || [];
  return <StaffLayout area="reception" title="Bảng điều phối">
    <PageHead eyebrow="ĐIỀU PHỐI" title="Bảng điều phối hàng đợi" actions={<Button onClick={board.reload}>Làm mới</Button>}>Hàng đợi chờ khám xếp theo thứ tự: cấp cứu → khách đúng giờ hẹn → khách vãng lai và khách đến trễ. Tự cập nhật mỗi 15 giây.</PageHead>
    <Notice notice={action.notice} onClose={action.clear} />
    <AsyncView state={board}>
      {(d) => <>
        <Stats items={[['Đang chờ khám', d.queue.length, `${d.queue.filter((v) => v.emergency).length} ca cấp cứu`, d.queue.some((v) => v.emergency) ? 'red' : ''],
          ['Đang khám / chờ kết quả', d.active.filter((v) => ['IN_PROGRESS', 'WAITING_RESULTS', 'RESULTS_READY'].includes(v.status)).length],
          ['Chờ thanh toán', d.active.filter((v) => v.status === 'WAITING_PAYMENT').length, undefined, 'amber'],
          ['Bác sĩ sẵn sàng', d.doctors.filter((doctor) => doctor.status !== 'BUSY').length, `${d.doctors.length} bác sĩ đang hoạt động`, 'green']]} />
        {priority.length > 0 && <div className="ui-alert info" role="status"><span>Bác sĩ {priority.map((doctor) => doctor.fullName).join(', ')} đang giữ <strong>khóa ưu tiên tái khám</strong>: không phân công ca mới cho tới khi bác sĩ mời ca đã có kết quả.</span></div>}
        <div className="ui-cols">
          <Panel title="Hàng đợi chờ khám" subtitle="Phân công bác sĩ cho ca đứng đầu hàng đợi." flush>
            <Table caption="Hàng đợi chờ khám" empty="Hiện không có bệnh nhân nào trong hàng đợi." rows={d.queue} rowClass={(row) => (row.emergency ? 'emergency' : '')} columns={[
              { title: 'Thú cưng', render: (row) => <><strong>{row.petName}</strong>{row.emergency && <span className="ui-emergency"> · CẤP CỨU</span>}<small>{row.speciesName} · {row.visitCode}</small></> },
              { title: 'Giờ', render: (row) => <>{formatTimeOfInstant(row.checkedInAt)} · chờ {waitedMinutes(row.checkedInAt)} phút<small>{row.scheduledAt ? `Hẹn ${formatTimeOfInstant(row.scheduledAt)}` : 'Vãng lai / đến trễ'}</small></> },
              { title: 'Bác sĩ', render: (row) => (row.doctorId ? <>{d.doctors.find((doctor) => doctor.doctorId === row.doctorId)?.fullName || `#${row.doctorId}`}<small>Phòng {row.assignedRoom || '?'}</small></> : <span className="ui-muted">Chưa phân công</span>) },
              { title: '', align: 'right', render: (row) => <div className="ui-actions" style={{ justifyContent: 'flex-end' }}>
                <Button className={`small ${row.doctorId ? '' : 'primary'}`} onClick={() => setDialog({ type: 'assign', visit: row })}>{row.doctorId ? 'Đổi bác sĩ' : 'Phân công'}</Button>
                <Button className="small" onClick={() => setDialog({ type: 'cancel', visit: row })}>Hủy</Button></div> },
            ]} />
          </Panel>
          <Panel title="Bác sĩ" subtitle="Trạng thái tính theo thời gian thực từ các ca đang xử lý." flush>
            <Table caption="Trạng thái bác sĩ" empty="Chưa có bác sĩ nào đang hoạt động." rows={d.doctors} rowKey="doctorId" columns={[
              { title: 'Bác sĩ', render: (row) => <><strong>{row.fullName}</strong><small>Phòng {row.roomNumber || '?'}</small></> },
              { title: 'Trạng thái', render: (row) => <Badge map={DOCTOR_STATUS} value={row.status} /> }]} />
          </Panel>
        </div>
        <Panel title="Các ca đang xử lý" subtitle="Ca đang khám, chờ kết quả cận lâm sàng, chờ thanh toán." flush>
          <Table caption="Các ca đang xử lý" empty="Không có ca nào đang xử lý." rows={d.active} columns={[
            { title: 'Mã ca', render: (row) => <><strong>{row.visitCode}</strong><small>{formatDateTime(row.checkedInAt)}</small></> },
            { title: 'Thú cưng', render: (row) => <>{row.petName}{row.emergency && <span className="ui-emergency"> · CẤP CỨU</span>}<small>{row.speciesName}</small></> },
            { title: 'Bác sĩ', render: (row) => d.doctors.find((doctor) => doctor.doctorId === row.doctorId)?.fullName || (row.doctorId ? `#${row.doctorId}` : '—') },
            { title: 'Trạng thái', render: (row) => <Badge map={VISIT_STATUS} value={row.status} /> },
            { title: '', align: 'right', render: (row) => <div className="ui-actions" style={{ justifyContent: 'flex-end' }}>
              {row.status === 'WAITING_PAYMENT' && <Button className="small" onClick={() => run(() => billingApi.generateForVisit(row.id), `Đã đảm bảo có hóa đơn cho ${row.visitCode}.`)}>Lập hóa đơn</Button>}
              {row.status === 'RESULTS_READY' && <Button className="small" onClick={() => setDialog({ type: 'abandon', visit: row })}>Khách bỏ về</Button>}
              {row.status === 'WAITING_RESULTS' && <Button className="small" onClick={() => setDialog({ type: 'cancel', visit: row })}>Khách bỏ về / Hủy</Button>}
              <Button className="small" onClick={() => setDialog({ type: 'history', visit: row })}>Lịch sử</Button></div> },
          ]} />
        </Panel>
        {dialog?.type === 'assign' && <AssignDialog visit={dialog.visit} doctors={d.doctors} onClose={() => setDialog(null)} onDone={done} />}
        {dialog?.type === 'cancel' && <CancelVisitDialog visit={dialog.visit} onClose={() => setDialog(null)} onDone={done} />}
        {dialog?.type === 'history' && <HistoryDialog visit={dialog.visit} onClose={() => setDialog(null)} />}
        {dialog?.type === 'abandon' && <ConfirmDialog title="Khách bỏ về" onClose={() => setDialog(null)} confirmLabel="Xác nhận" danger onConfirm={async () => { await visitsApi.abandon(dialog.visit.id); done(`Đã ghi nhận ${dialog.visit.petName} bỏ về; hóa đơn chuyển sang chờ thanh toán (ghi nợ).`); }}>
          Ca {dialog.visit.visitCode} sẽ kết thúc ngay, hóa đơn vẫn được lập ở trạng thái chờ thanh toán để ghi nhận công nợ.</ConfirmDialog>}
      </>}
    </AsyncView>
  </StaffLayout>;
}

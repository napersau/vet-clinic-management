import { useState } from 'react';
import StaffLayout from '../../components/layout/StaffLayout';
import OwnerPetPicker from '../../components/shared/OwnerPetPicker';
import SlotPicker from '../../components/shared/SlotPicker';
import { CancelAppointmentDialog, RescheduleAppointmentDialog } from '../../components/shared/AppointmentDialogs';
import { AsyncView, Badge, Button, ConfirmDialog, Field, FormDialog, Input, Notice, PageHead, Pager, Panel, Select, Table, Tabs } from '../../components/ui';
import { appointmentsApi } from '../../api/endpoints';
import useAsync from '../../hooks/useAsync';
import useDebounced from '../../hooks/useDebounced';
import usePolling from '../../hooks/usePolling';
import { formatDate, formatDateTime, formatTime, todayIso } from '../../lib/format';
import { APPOINTMENT_STATUS, BOOKING_CHANNEL } from '../../lib/labels';

function CreateDialog({ onClose, onDone }) {
  const [picked, setPicked] = useState({ owner: null, pet: null });
  const [channel, setChannel] = useState('PHONE');
  const [date, setDate] = useState(todayIso());
  const [startTime, setStartTime] = useState('');
  const [reason, setReason] = useState('');
  const submit = async () => {
    if (!picked.owner || !picked.pet) throw new Error('Chọn chủ nuôi và thú cưng.');
    if (!startTime) throw new Error('Chọn khung giờ còn trống.');
    if (!reason.trim()) throw new Error('Nhập lý do khám.');
    const created = await appointmentsApi.create({ ownerId: Number(picked.owner.id), petId: Number(picked.pet.id), appointmentDate: date, startTime, reason: reason.trim(), bookingChannel: channel });
    onDone(`Đã tạo lịch ${created.appointmentCode} (đã xác nhận).`);
  };
  return <FormDialog title="Đặt lịch hộ khách" onClose={onClose} onSubmit={submit} submitLabel="Tạo lịch hẹn" wide>
    <OwnerPetPicker value={picked} onChange={setPicked} />
    <div className="ui-grid">
      <Field label="Kênh đặt"><Select value={channel} onChange={(event) => setChannel(event.target.value)}><option value="PHONE">{BOOKING_CHANNEL.PHONE}</option><option value="WALK_IN">{BOOKING_CHANNEL.WALK_IN}</option></Select></Field>
      <Field label="Lý do khám *"><Input value={reason} onChange={(event) => setReason(event.target.value)} maxLength={1000} /></Field>
    </div>
    <SlotPicker date={date} onDateChange={(value) => { setDate(value); setStartTime(''); }} value={startTime} onChange={setStartTime} />
  </FormDialog>;
}

function HistoryDialog({ appointment, onClose }) {
  const history = useAsync(() => appointmentsApi.history(appointment.id), [appointment.id]);
  return <FormDialog title={`Lịch sử ${appointment.appointmentCode}`} onClose={onClose} onSubmit={async () => {}} submitLabel="Đóng">
    <AsyncView state={history} empty="Chưa có lịch sử.">{(items) => <ul className="ui-list">{items.map((item) => <li key={item.id}><div><strong>{item.fromStatus ? `${APPOINTMENT_STATUS[item.fromStatus]?.label} → ` : ''}{APPOINTMENT_STATUS[item.toStatus]?.label}</strong><small>{item.changedByUserId ? `Người thực hiện #${item.changedByUserId}` : 'Hệ thống'}{item.notes ? ` · ${item.notes}` : ''}</small></div><small>{formatDateTime(item.createdAt)}</small></li>)}</ul>}</AsyncView>
  </FormDialog>;
}

export default function AppointmentsPage() {
  const [tab, setTab] = useState('PENDING');
  const [page, setPage] = useState(0);
  const [code, setCode] = useState('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [status, setStatus] = useState('');
  const [dialog, setDialog] = useState(null);
  const [notice, setNotice] = useState(null);
  const search = useDebounced(code);
  const pending = tab === 'PENDING';
  const list = useAsync(() => appointmentsApi.list(pending
    ? { status: 'PENDING', sortBy: 'createdAt', ascending: true, page, size: 20 }
    : { code: search.trim() || undefined, fromDate, toDate, status, page, size: 20 }), [tab, page, search, fromDate, toDate, status]);
  // Yêu cầu đặt lịch mới của khách xuất hiện trong hàng chờ duyệt mà không cần tải lại trang.
  usePolling(list.refresh, 15000);
  const done = (text) => { setNotice({ type: 'success', text }); setDialog(null); list.refresh(); };
  const confirm = async (appointment) => {
    try { await appointmentsApi.confirm(appointment.id); done(`Đã xác nhận lịch ${appointment.appointmentCode}.`); } catch (problem) { setNotice({ type: 'error', text: problem.message }); }
  };

  const actions = (row) => <div className="ui-actions" style={{ justifyContent: 'flex-end' }}>
    {row.status === 'PENDING' && <Button className="small primary" onClick={() => confirm(row)}>Xác nhận</Button>}
    {['PENDING', 'CONFIRMED'].includes(row.status) && <Button className="small" onClick={() => setDialog({ type: 'reschedule', appointment: row })}>Đổi lịch</Button>}
    {['PENDING', 'CONFIRMED'].includes(row.status) && <Button className="small danger" onClick={() => setDialog({ type: 'cancel', appointment: row })}>{row.status === 'PENDING' ? 'Từ chối' : 'Hủy'}</Button>}
    {row.status === 'CONFIRMED' && <Button className="small" onClick={() => setDialog({ type: 'noshow', appointment: row })}>Vắng mặt</Button>}
    <Button className="small" onClick={() => setDialog({ type: 'history', appointment: row })}>Lịch sử</Button></div>;

  return <StaffLayout area="reception" title="Lịch hẹn">
    <PageHead eyebrow="LỊCH HẸN" title="Quản lý lịch hẹn" actions={<Button variant="primary" onClick={() => setDialog({ type: 'create' })}>+ Đặt lịch hộ khách</Button>}>
      Duyệt yêu cầu đặt lịch trực tuyến theo thứ tự gửi, đặt lịch hộ khách gọi điện hoặc đến quầy, đổi lịch và ghi nhận khách vắng mặt.
    </PageHead>
    <Notice notice={notice} onClose={() => setNotice(null)} />
    <Tabs tabs={[{ key: 'PENDING', label: 'Chờ duyệt', count: pending ? list.data?.totalElements : undefined }, { key: 'ALL', label: 'Tất cả lịch hẹn' }]} value={tab} onChange={(key) => { setTab(key); setPage(0); }} />
    {!pending && <div className="ui-toolbar">
      <Input aria-label="Mã lịch hẹn" placeholder="Mã lịch (APT-…)" value={code} onChange={(event) => { setCode(event.target.value); setPage(0); }} />
      <Input type="date" aria-label="Từ ngày" value={fromDate} onChange={(event) => { setFromDate(event.target.value); setPage(0); }} />
      <Input type="date" aria-label="Đến ngày" value={toDate} onChange={(event) => { setToDate(event.target.value); setPage(0); }} />
      <Select aria-label="Trạng thái" value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}><option value="">Mọi trạng thái</option>{Object.entries(APPOINTMENT_STATUS).map(([key, value]) => <option key={key} value={key}>{value.label}</option>)}</Select>
    </div>}
    <Panel flush>
      <AsyncView state={list} isEmpty={(data) => data.items.length === 0} empty={pending ? 'Không có yêu cầu nào đang chờ duyệt.' : 'Không có lịch hẹn phù hợp bộ lọc.'}>
        {(data) => <>
          <Table caption="Danh sách lịch hẹn" rows={data.items} columns={[
            { title: 'Mã lịch', render: (row) => <><strong>{row.appointmentCode}</strong><small>{BOOKING_CHANNEL[row.bookingChannel]} · gửi {formatDateTime(row.createdAt)}</small></> },
            { title: 'Thời gian', render: (row) => <>{formatDate(row.appointmentDate)}<small>{formatTime(row.startTime)} – {formatTime(row.endTime)}</small></> },
            { title: 'Khách hàng', render: (row) => <>{row.ownerName || `#${row.ownerId}`}<small>{row.ownerPhone}</small></> },
            { title: 'Thú cưng', render: (row) => row.petName || `#${row.petId}` },
            { title: 'Lý do', render: (row) => <>{row.reason}{row.cancelReason && <small>Lý do hủy: {row.cancelReason}</small>}</> },
            { title: 'Trạng thái', render: (row) => <Badge map={APPOINTMENT_STATUS} value={row.status} /> },
            { title: '', align: 'right', render: actions }]} />
          <Pager page={data} onChange={setPage} />
        </>}
      </AsyncView>
    </Panel>
    {dialog?.type === 'create' && <CreateDialog onClose={() => setDialog(null)} onDone={done} />}
    {dialog?.type === 'cancel' && <CancelAppointmentDialog staff appointment={dialog.appointment} onClose={() => setDialog(null)} onDone={done} />}
    {dialog?.type === 'reschedule' && <RescheduleAppointmentDialog appointment={dialog.appointment} onClose={() => setDialog(null)} onDone={done} />}
    {dialog?.type === 'history' && <HistoryDialog appointment={dialog.appointment} onClose={() => setDialog(null)} />}
    {dialog?.type === 'noshow' && <ConfirmDialog title="Ghi nhận khách vắng mặt" danger confirmLabel="Ghi nhận" onClose={() => setDialog(null)} onConfirm={async () => { await appointmentsApi.noShow(dialog.appointment.id); done(`Đã ghi nhận ${dialog.appointment.appointmentCode} vắng mặt.`); }}>
      Lịch {dialog.appointment.appointmentCode} ({dialog.appointment.petName}) sẽ chuyển sang “Vắng mặt” và trả lại chỗ trống. Hệ thống cũng tự quét lịch quá giờ 30 phút.</ConfirmDialog>}
  </StaffLayout>;
}

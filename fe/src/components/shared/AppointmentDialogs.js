import { useState } from 'react';
import { appointmentsApi } from '../../api/endpoints';
import { Button, Field, FormDialog, Textarea } from '../ui';

import SlotPicker from './SlotPicker';
import { formatDate, formatTime, todayIso } from '../../lib/format';

const OWNER_REASONS = ['Bận việc đột xuất', 'Thú cưng đã khỏi', 'Đổi cơ sở khác'];
/** Hủy lịch (chủ nuôi) hoặc từ chối/hủy (lễ tân): backend bắt buộc có lý do. */
export function CancelAppointmentDialog({ appointment, staff = false, onClose, onDone }) {
  const [reason, setReason] = useState('');
  const submit = async () => {
    if (!reason.trim()) throw new Error('Nhập lý do.');
    await appointmentsApi.cancel(appointment.id, reason.trim());
    onDone(`Đã ${staff ? 'hủy' : 'hủy'} lịch ${appointment.appointmentCode}.`);
  };
  return <FormDialog title={`${staff ? 'Từ chối / hủy' : 'Hủy'} lịch ${appointment.appointmentCode}`} onClose={onClose} onSubmit={submit} submitLabel="Xác nhận hủy" danger>
    <p className="ui-confirm-text">Lịch {formatDate(appointment.appointmentDate)} lúc {formatTime(appointment.startTime)}{appointment.petName ? ` của ${appointment.petName}` : ''} sẽ bị hủy và trả lại chỗ trống.</p>
    {!staff && <div className="ui-actions">{OWNER_REASONS.map((text) => <Button key={text} className="small" onClick={() => setReason(text)}>{text}</Button>)}</div>}
    <Field label="Lý do *"><Textarea value={reason} onChange={(event) => setReason(event.target.value)} maxLength={500} autoFocus /></Field>
  </FormDialog>;
}

export function RescheduleAppointmentDialog({ appointment, onClose, onDone }) {
  const [date, setDate] = useState(appointment.appointmentDate >= todayIso() ? appointment.appointmentDate : todayIso());
  const [startTime, setStartTime] = useState('');
  const [reason, setReason] = useState('');
  const submit = async () => {
    if (!date || !startTime) throw new Error('Chọn ngày và khung giờ mới.');
    await appointmentsApi.reschedule(appointment.id, { newDate: date, newStartTime: startTime, reason: reason.trim() || null });
    onDone(`Đã đổi lịch ${appointment.appointmentCode} sang ${formatDate(date)} lúc ${startTime}.`);
  };
  return <FormDialog title={`Đổi lịch ${appointment.appointmentCode}`} onClose={onClose} onSubmit={submit} submitLabel="Đổi lịch" wide>
    <p className="ui-confirm-text">Lịch hiện tại: {formatDate(appointment.appointmentDate)} lúc {formatTime(appointment.startTime)}. Trạng thái lịch được giữ nguyên sau khi đổi.</p>
    <SlotPicker date={date} onDateChange={(value) => { setDate(value); setStartTime(''); }} value={startTime} onChange={setStartTime} />
    <Field label="Lý do đổi lịch (không bắt buộc)"><Textarea value={reason} onChange={(event) => setReason(event.target.value)} maxLength={500} rows={2} /></Field>
  </FormDialog>;
}

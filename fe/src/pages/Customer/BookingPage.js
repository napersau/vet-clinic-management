import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import CustomerLayout from '../../components/layout/CustomerLayout';
import SlotPicker from '../../components/shared/SlotPicker';
import PetFormDialog from '../../components/shared/PetFormDialog';
import { AsyncView, Badge, Button, Facts, Field, Notice, PageHead, Panel, Select, Textarea } from '../../components/ui';
import { appointmentsApi, petsApi } from '../../api/endpoints';
import { mapFieldErrors } from '../../api/client';
import useAction from '../../hooks/useAction';
import useAsync from '../../hooks/useAsync';
import { formatDate, todayIso } from '../../lib/format';
import { APPOINTMENT_STATUS } from '../../lib/labels';

const SUGGESTIONS = ['Khám tổng quát định kỳ', 'Tiêm phòng', 'Tẩy giun', 'Bỏ ăn, mệt mỏi', 'Nôn hoặc tiêu chảy', 'Ngứa, rụng lông, bệnh ngoài da'];

export default function BookingPage() {
  const [params] = useSearchParams();
  const pets = useAsync(() => petsApi.list({ size: 100 }), []);
  const [petId, setPetId] = useState(params.get('petId') || '');
  const [date, setDate] = useState(todayIso(1));
  const [startTime, setStartTime] = useState('');
  const [reason, setReason] = useState('');
  const [errors, setErrors] = useState({});
  const [created, setCreated] = useState(null);
  const [addingPet, setAddingPet] = useState(false);
  const action = useAction();
  const items = pets.data?.items || [];
  const selectedPet = items.find((pet) => String(pet.id) === String(petId));

  const submit = async (event) => {
    event.preventDefault();
    const found = {};
    if (!petId) found.petId = 'Chọn thú cưng cần khám.';
    if (!date) found.date = 'Chọn ngày khám.';
    if (!startTime) found.startTime = 'Chọn một khung giờ còn trống.';
    if (!reason.trim()) found.reason = 'Mô tả ngắn lý do khám hoặc triệu chứng.';
    setErrors(found);
    if (Object.keys(found).length) return;
    const result = await action.run(() => appointmentsApi.create({ petId: Number(petId), appointmentDate: date, startTime, reason: reason.trim() }).catch((problem) => { setErrors(mapFieldErrors(problem, { appointmentDate: 'date' })); throw problem; }));
    if (result) { setCreated(result); setStartTime(''); setReason(''); }
  };

  if (created) return <CustomerLayout>
    <PageHead eyebrow="ĐẶT LỊCH" title="Đã gửi yêu cầu đặt lịch">Phòng khám sẽ xác nhận lịch hẹn; bạn nhận thông báo (và email nếu đã khai báo) ngay khi lịch được duyệt.</PageHead>
    <Panel title={created.appointmentCode} actions={<Badge map={APPOINTMENT_STATUS} value={created.status} />}>
      <Facts items={[['Thú cưng', selectedPet?.name || created.petName], ['Ngày khám', formatDate(created.appointmentDate)], ['Khung giờ', `${created.startTime.slice(0, 5)} – ${created.endTime.slice(0, 5)}`], ['Lý do khám', created.reason]]} />
      <div className="ui-actions" style={{ marginTop: 20 }}><Link className="ui-button primary" to="/portal/appointments">Xem lịch hẹn của tôi</Link><Button onClick={() => setCreated(null)}>Đặt thêm lịch khác</Button></div>
    </Panel>
  </CustomerLayout>;

  return <CustomerLayout>
    <PageHead eyebrow="ĐẶT LỊCH" title="Đặt lịch khám">Chọn bé, ngày và khung giờ còn trống. Bác sĩ sẽ được phòng khám phân công khi bé đến.</PageHead>
    <AsyncView state={pets} isEmpty={(data) => data.items.length === 0}
      empty={<>Bạn cần có hồ sơ thú cưng trước khi đặt lịch. <button type="button" className="ui-link" onClick={() => setAddingPet(true)}>Thêm thú cưng mới</button></>}>
      {() => <form onSubmit={submit} noValidate className="ui-cols">
        <Panel title="1. Thú cưng và lý do khám">
          <div className="ui-stack" style={{ gap: 16 }}>
            <Notice notice={action.notice} onClose={action.clear} />
            <Field label="Thú cưng *" error={errors.petId}><Select value={petId} onChange={(event) => setPetId(event.target.value)}><option value="">Chọn thú cưng</option>{items.map((pet) => <option key={pet.id} value={pet.id}>{pet.name} ({pet.speciesName})</option>)}</Select></Field>
            <div><Button className="small" onClick={() => setAddingPet(true)}>+ Thêm thú cưng mới</Button></div>
            <Field label="Lý do khám / triệu chứng *" error={errors.reason}><Textarea value={reason} onChange={(event) => setReason(event.target.value)} maxLength={1000} placeholder="Ví dụ: bé bỏ ăn 2 ngày, nôn nhiều lần…" /></Field>
            <div className="ui-actions">{SUGGESTIONS.map((text) => <Button key={text} className="small" onClick={() => setReason(text)}>{text}</Button>)}</div>
          </div>
        </Panel>
        <Panel title="2. Ngày và khung giờ">
          <SlotPicker date={date} onDateChange={(value) => { setDate(value); setStartTime(''); }} value={startTime} onChange={setStartTime} error={errors} />
          <div style={{ marginTop: 20 }}><Button type="submit" variant="primary" busy={action.busy}>Gửi yêu cầu đặt lịch</Button></div>
          <p className="ui-muted" style={{ fontSize: 12, marginBottom: 0 }}>Mỗi bé chỉ có một lịch trong ngày. Có thể hủy hoặc đổi lịch trước giờ hẹn ít nhất 2 giờ.</p>
        </Panel>
      </form>}
    </AsyncView>
    {addingPet && <PetFormDialog onClose={() => setAddingPet(false)} onSaved={(saved) => { pets.refresh(); setPetId(String(saved.id)); }} />}
  </CustomerLayout>;
}

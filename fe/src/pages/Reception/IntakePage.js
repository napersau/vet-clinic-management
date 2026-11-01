import { useState } from 'react';
import { Link } from 'react-router-dom';
import StaffLayout from '../../components/layout/StaffLayout';
import OwnerPetPicker from '../../components/shared/OwnerPetPicker';
import { AsyncView, Badge, Button, Facts, Field, Notice, PageHead, Panel, Table, Tabs, Textarea } from '../../components/ui';
import { appointmentsApi, visitsApi } from '../../api/endpoints';
import useAction from '../../hooks/useAction';
import useAsync from '../../hooks/useAsync';
import { formatTime, todayIso } from '../../lib/format';

function CheckedIn({ visit, onAnother }) {
  return <Panel title="Đã tiếp nhận" actions={<Badge tone={visit.emergency ? 'red' : 'green'}>{visit.emergency ? 'Cấp cứu' : 'Chờ khám'}</Badge>}>
    <Facts items={[['Mã đợt khám', visit.visitCode], ['Thú cưng', `${visit.petName} (${visit.speciesName})`], ['Giờ hẹn', visit.scheduledAt ? 'Đúng khung giờ hẹn' : 'Khách vãng lai hoặc đến trễ — xếp theo giờ đến'], ['Triệu chứng', visit.initialSymptoms]]} />
    <div className="ui-actions" style={{ marginTop: 18 }}><Link className="ui-button primary" to="/reception/queue">Về bảng điều phối để phân công bác sĩ</Link><Button onClick={onAnother}>Tiếp nhận khách khác</Button></div>
  </Panel>;
}

function Appointments({ onDone }) {
  const list = useAsync(() => appointmentsApi.list({ status: 'CONFIRMED', fromDate: todayIso(), toDate: todayIso(), size: 50 }), []);
  const [selected, setSelected] = useState(null);
  const [emergency, setEmergency] = useState(false);
  const [symptoms, setSymptoms] = useState('');
  const action = useAction();
  const checkIn = async () => {
    const visit = await action.run(() => visitsApi.checkIn({ appointmentId: selected.id, emergency, initialSymptoms: symptoms.trim() || null }));
    if (visit && visit !== true) { onDone(visit); list.refresh(); }
  };
  return <div className="ui-cols">
    <Panel title="Lịch hẹn đã xác nhận hôm nay" subtitle="Chọn lịch của khách vừa đến để tiếp nhận." flush>
      <AsyncView state={list} isEmpty={(data) => data.items.length === 0} empty="Không còn lịch hẹn đã xác nhận nào chưa tiếp nhận hôm nay. Lịch chờ duyệt cần được xác nhận ở mục Lịch hẹn. Khách đến trễ sau khi lịch đã bị ghi nhận vắng mặt: tiếp nhận ở tab Khách vãng lai (lịch giữ nguyên trạng thái vắng mặt).">
        {(data) => <Table caption="Lịch hẹn hôm nay" onRowClick={setSelected} rows={data.items} columns={[
          { title: 'Giờ', render: (row) => <strong>{formatTime(row.startTime)}</strong> },
          { title: 'Thú cưng / chủ nuôi', render: (row) => <>{row.petName || `#${row.petId}`}<small>{row.ownerName} · {row.ownerPhone}</small></> },
          { title: '', align: 'right', render: (row) => <Button className={`small ${selected?.id === row.id ? 'primary' : ''}`} onClick={() => setSelected(row)}>{selected?.id === row.id ? 'Đã chọn' : 'Chọn'}</Button> }]} />}
      </AsyncView>
    </Panel>
    <Panel title="Thông tin tiếp nhận">
      {!selected ? <p className="ui-muted">Chọn một lịch hẹn ở bên trái.</p> : <div className="ui-stack" style={{ gap: 14 }}>
        <Notice notice={action.notice} onClose={action.clear} />
        <Facts items={[['Lịch hẹn', selected.appointmentCode], ['Thú cưng', selected.petName], ['Chủ nuôi', `${selected.ownerName} · ${selected.ownerPhone}`], ['Lý do đặt lịch', selected.reason]]} />
        <Field label="Triệu chứng ban đầu (không bắt buộc)"><Textarea value={symptoms} onChange={(event) => setSymptoms(event.target.value)} maxLength={2000} rows={2} /></Field>
        <label className="ui-check"><input type="checkbox" checked={emergency} onChange={(event) => setEmergency(event.target.checked)} />Ca cấp cứu (ưu tiên tuyệt đối)</label>
        <div><Button variant="primary" busy={action.busy} onClick={checkIn}>Tiếp nhận</Button></div>
      </div>}
    </Panel>
  </div>;
}

function WalkIn({ onDone }) {
  const [picked, setPicked] = useState({ owner: null, pet: null });
  const [emergency, setEmergency] = useState(false);
  const [symptoms, setSymptoms] = useState('');
  const [errors, setErrors] = useState({});
  const action = useAction();
  const submit = async (event) => {
    event.preventDefault();
    const found = {};
    if (!picked.owner) found.owner = 'Chọn hoặc tạo chủ nuôi.';
    else if (!picked.pet) found.pet = 'Chọn hoặc thêm thú cưng.';
    setErrors(found);
    if (Object.keys(found).length) return;
    const visit = await action.run(() => visitsApi.checkIn({ petId: picked.pet.id, emergency, initialSymptoms: symptoms.trim() || null }));
    if (visit && visit !== true) onDone(visit);
  };
  return <Panel title="Khách vãng lai" subtitle="Không cần đặt lịch trước. Khách chưa có hồ sơ có thể tạo ngay tại quầy.">
    <form onSubmit={submit} noValidate className="ui-stack" style={{ gap: 16 }}>
      <Notice notice={action.notice} onClose={action.clear} />
      <OwnerPetPicker value={picked} onChange={setPicked} errors={errors} />
      <Field label="Triệu chứng ban đầu (không bắt buộc)"><Textarea value={symptoms} onChange={(event) => setSymptoms(event.target.value)} maxLength={2000} rows={2} /></Field>
      <label className="ui-check"><input type="checkbox" checked={emergency} onChange={(event) => setEmergency(event.target.checked)} />Ca cấp cứu (ưu tiên tuyệt đối, bác sĩ được báo ngay)</label>
      <div><Button type="submit" variant="primary" busy={action.busy}>Tiếp nhận</Button></div>
    </form>
  </Panel>;
}

export default function IntakePage() {
  const [tab, setTab] = useState('appointment');
  const [visit, setVisit] = useState(null);
  return <StaffLayout area="reception" title="Tiếp nhận khách">
    <PageHead eyebrow="TIẾP NHẬN" title="Tiếp nhận khách hàng">Tiếp nhận khách có lịch hẹn hoặc khách vãng lai. Mỗi thú cưng chỉ có một đợt khám đang mở tại một thời điểm.</PageHead>
    {visit ? <CheckedIn visit={visit} onAnother={() => setVisit(null)} /> : <>
      <Tabs tabs={[{ key: 'appointment', label: 'Có lịch hẹn hôm nay' }, { key: 'walkin', label: 'Khách vãng lai' }]} value={tab} onChange={setTab} />
      {tab === 'appointment' ? <Appointments onDone={setVisit} /> : <WalkIn onDone={setVisit} />}
    </>}
  </StaffLayout>;
}


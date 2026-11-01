import { useState } from 'react';
import { Link } from 'react-router-dom';
import CustomerLayout from '../../components/layout/CustomerLayout';
import { CancelAppointmentDialog, RescheduleAppointmentDialog } from '../../components/shared/AppointmentDialogs';
import { AsyncView, Badge, Button, Notice, PageHead, Pager, Panel, Table, Tabs } from '../../components/ui';
import { appointmentsApi } from '../../api/endpoints';
import useAsync from '../../hooks/useAsync';
import { formatDate, formatTime } from '../../lib/format';
import { APPOINTMENT_STATUS, BOOKING_CHANNEL } from '../../lib/labels';

const TABS = [{ key: '', label: 'Tất cả' }, { key: 'PENDING', label: 'Chờ xác nhận' }, { key: 'CONFIRMED', label: 'Đã xác nhận' }, { key: 'CHECKED_IN', label: 'Đã tiếp nhận' }, { key: 'CANCELLED', label: 'Đã hủy' }, { key: 'NO_SHOW', label: 'Vắng mặt' }];

export default function AppointmentsPage() {
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [dialog, setDialog] = useState(null); // { type: 'cancel' | 'reschedule', appointment }
  const [notice, setNotice] = useState(null);
  const list = useAsync(() => appointmentsApi.list({ status, page, size: 10 }), [status, page]);
  const done = (text) => { setNotice({ type: 'success', text }); setDialog(null); list.refresh(); };

  return <CustomerLayout>
    <PageHead eyebrow="LỊCH HẸN" title="Lịch hẹn của tôi" actions={<Link className="ui-button primary" to="/portal/booking">+ Đặt lịch mới</Link>}>
      Theo dõi trạng thái, đổi hoặc hủy lịch hẹn. Chỉ đổi/hủy được trước giờ hẹn ít nhất 2 giờ.
    </PageHead>
    <Notice notice={notice} onClose={() => setNotice(null)} />
    <Tabs tabs={TABS} value={status} onChange={(key) => { setStatus(key); setPage(0); }} />
    <Panel flush>
      <AsyncView state={list} isEmpty={(data) => data.items.length === 0} empty="Không có lịch hẹn nào trong mục này.">
        {(data) => <>
          <Table caption="Lịch hẹn của tôi" columns={[
            { title: 'Mã lịch', render: (row) => <><strong>{row.appointmentCode}</strong><small>{BOOKING_CHANNEL[row.bookingChannel]}</small></> },
            { title: 'Thú cưng', render: (row) => row.petName || `#${row.petId}` },
            { title: 'Thời gian', render: (row) => <>{formatDate(row.appointmentDate)}<small>{formatTime(row.startTime)} – {formatTime(row.endTime)}</small></> },
            { title: 'Lý do', render: (row) => <>{row.reason}{row.cancelReason && <small>Lý do hủy: {row.cancelReason}</small>}</> },
            { title: 'Trạng thái', render: (row) => <Badge map={APPOINTMENT_STATUS} value={row.status} /> },
            { title: '', align: 'right', render: (row) => ['PENDING', 'CONFIRMED'].includes(row.status) && <div className="ui-actions" style={{ justifyContent: 'flex-end' }}>
              <Button className="small" onClick={() => setDialog({ type: 'reschedule', appointment: row })}>Đổi lịch</Button>
              <Button className="small danger" onClick={() => setDialog({ type: 'cancel', appointment: row })}>Hủy</Button></div> },
          ]} rows={data.items} />
          <Pager page={data} onChange={setPage} />
        </>}
      </AsyncView>
    </Panel>
    {dialog?.type === 'cancel' && <CancelAppointmentDialog appointment={dialog.appointment} onClose={() => setDialog(null)} onDone={done} />}
    {dialog?.type === 'reschedule' && <RescheduleAppointmentDialog appointment={dialog.appointment} onClose={() => setDialog(null)} onDone={done} />}
  </CustomerLayout>;
}

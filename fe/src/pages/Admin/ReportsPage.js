import { useState } from 'react';
import StaffLayout from '../../components/layout/StaffLayout';
import { AsyncView, Button, Field, Input, PageHead, Panel, Select, Stats, Table, Tabs } from '../../components/ui';
import { appointmentsApi, billingApi, reportsApi } from '../../api/endpoints';
import useAsync from '../../hooks/useAsync';
import { formatDate, formatMoney, formatNumber, todayIso } from '../../lib/format';
import { APPOINTMENT_STATUS, BOOKING_CHANNEL, PAYMENT_STATUS, VISIT_STATUS } from '../../lib/labels';

const percent = (value) => `${formatNumber((value || 0) * 100, 1)}%`;
const MAX_DAYS = 366;

function Bars({ rows, label, value, format }) {
  const max = Math.max(...rows.map((row) => Number(row[value])), 1);
  return <div className="ui-stack" style={{ gap: 6 }} role="img" aria-label="Biểu đồ cột">
    {rows.map((row) => <div key={row[label]} style={{ display: 'grid', gridTemplateColumns: '110px 1fr 120px', gap: 10, alignItems: 'center', fontSize: 12 }}>
      <span className="ui-muted">{row[label]}</span>
      <div style={{ background: 'var(--ui-raised)', borderRadius: 6, height: 14 }}><div style={{ width: `${(Number(row[value]) / max) * 100}%`, height: '100%', borderRadius: 6, background: 'var(--ui-brand)' }} /></div>
      <strong style={{ textAlign: 'right', color: 'var(--ui-text)' }}>{format(row[value])}</strong>
    </div>)}
  </div>;
}

function Revenue({ range, groupBy }) {
  const report = useAsync(() => billingApi.revenueReport({ ...range, groupBy }), [range.fromDate, range.toDate, groupBy]);
  return <AsyncView state={report}>{(r) => <>
    <Stats items={[['Doanh thu thực thu', formatMoney(r.totals.finalAmount), `${r.totals.invoiceCount} hóa đơn đã thanh toán`, 'green'], ['Tiền dịch vụ', formatMoney(r.totals.serviceAmount)], ['Tiền thuốc', formatMoney(r.totals.medicineAmount)], ['VAT', formatMoney(r.totals.vatAmount)], ['Chiết khấu', formatMoney(r.totals.discountAmount)]]} />
    <Panel title="Doanh thu theo kỳ" subtitle="Tính theo ngày thu tiền (giờ Việt Nam), chỉ hóa đơn đã thanh toán.">
      <Bars rows={r.periods} label="label" value="finalAmount" format={formatMoney} />
    </Panel>
    <div className="ui-cols">
      <Panel title="Chi tiết theo kỳ" flush><Table caption="Doanh thu theo kỳ" rowKey="label" rows={r.periods} columns={[
        { title: 'Kỳ', key: 'label' }, { title: 'HĐ', key: 'invoiceCount' }, { title: 'Dịch vụ', align: 'right', render: (row) => formatMoney(row.serviceAmount) },
        { title: 'Thuốc', align: 'right', render: (row) => formatMoney(row.medicineAmount) }, { title: 'Thực thu', align: 'right', render: (row) => <strong>{formatMoney(row.finalAmount)}</strong> }]} /></Panel>
      <Panel title="Hóa đơn lập trong kỳ theo trạng thái" subtitle="“Chờ thanh toán” là công nợ chưa thu." flush><Table caption="Trạng thái hóa đơn" rowKey="status" rows={Object.entries(r.byStatus).map(([status, total]) => ({ status, ...total }))} columns={[
        { title: 'Trạng thái', render: (row) => PAYMENT_STATUS[row.status]?.label || row.status }, { title: 'Số HĐ', key: 'invoiceCount' }, { title: 'Giá trị', align: 'right', render: (row) => formatMoney(row.finalAmount) }]} /></Panel>
    </div>
  </>}</AsyncView>;
}

function Operations({ range }) {
  const report = useAsync(() => reportsApi.operations(range), [range.fromDate, range.toDate]);
  return <AsyncView state={report}>{(r) => <>
    <Stats items={[['Tổng số ca', r.totalVisits], ['Hoàn tất', r.completedVisits, undefined, 'green'], ['Đã hủy', r.cancelledVisits], ['Cấp cứu', r.emergencyVisits, undefined, r.emergencyVisits ? 'red' : ''], ['Ca thường', r.routineVisits], ['Ca có cận lâm sàng', r.paraclinicalVisits]]} />
    <div className="ui-cols">
      <Panel title="Ca khám theo trạng thái" flush><Table caption="Ca theo trạng thái" rowKey="status" rows={Object.entries(r.byStatus).map(([status, count]) => ({ status, count }))} columns={[{ title: 'Trạng thái', render: (row) => VISIT_STATUS[row.status]?.label || row.status }, { title: 'Số ca', align: 'right', key: 'count' }]} /></Panel>
      <Panel title="Khối lượng theo bác sĩ" flush><Table caption="Bác sĩ" rowKey="doctorId" rows={r.doctors} columns={[{ title: 'Bác sĩ', render: (row) => row.doctorName || `#${row.doctorId}` }, { title: 'Được giao', align: 'right', key: 'assignedVisits' }, { title: 'Hoàn tất', align: 'right', key: 'completedVisits' }]} empty="Chưa có ca nào được giao." /></Panel>
    </div>
  </>}</AsyncView>;
}

function Appointments({ range }) {
  const report = useAsync(() => appointmentsApi.report(range), [range.fromDate, range.toDate]);
  return <AsyncView state={report}>{(r) => <>
    <Stats items={[['Tổng lịch hẹn', r.totalAppointments], ['Tỉ lệ vắng mặt', percent(r.noShowRate), 'Vắng ÷ (đã tiếp nhận + vắng)', r.noShowRate > 0.2 ? 'red' : ''], ['Tỉ lệ hủy', percent(r.cancellationRate), 'Hủy ÷ tổng lịch hẹn']]} />
    <div className="ui-cols">
      <Panel title="Theo trạng thái" flush><Table caption="Lịch hẹn theo trạng thái" rowKey="key" rows={Object.entries(r.byStatus).map(([key, count]) => ({ key, count }))} columns={[{ title: 'Trạng thái', render: (row) => APPOINTMENT_STATUS[row.key]?.label || row.key }, { title: 'Số lịch', align: 'right', key: 'count' }]} /></Panel>
      <Panel title="Theo kênh đặt" flush><Table caption="Lịch hẹn theo kênh" rowKey="key" rows={Object.entries(r.byChannel).map(([key, count]) => ({ key, count }))} columns={[{ title: 'Kênh', render: (row) => BOOKING_CHANNEL[row.key] || row.key }, { title: 'Số lịch', align: 'right', key: 'count' }]} /></Panel>
    </div>
  </>}</AsyncView>;
}

export default function ReportsPage() {
  const [tab, setTab] = useState('revenue');
  const [from, setFrom] = useState(todayIso(-29));
  const [to, setTo] = useState(todayIso());
  const [groupBy, setGroupBy] = useState('DAY');
  const days = (new Date(to) - new Date(from)) / 86400000 + 1;
  const invalid = !from || !to || from > to ? 'Khoảng ngày không hợp lệ: ngày bắt đầu phải trước hoặc bằng ngày kết thúc.' : days > MAX_DAYS ? `Khoảng báo cáo tối đa ${MAX_DAYS} ngày.` : '';
  const [applied, setApplied] = useState({ fromDate: from, toDate: to });
  return <StaffLayout area="admin" title="Báo cáo & thống kê">
    <PageHead eyebrow="BÁO CÁO" title="Báo cáo & thống kê">Doanh thu, vận hành phòng khám và hiệu quả đặt lịch theo khoảng ngày (giờ Việt Nam). Mặc định 30 ngày gần nhất.</PageHead>
    <Panel>
      <div className="ui-toolbar" style={{ margin: 0, alignItems: 'flex-end' }}>
        <Field label="Từ ngày"><Input type="date" value={from} max={to} onChange={(event) => setFrom(event.target.value)} /></Field>
        <Field label="Đến ngày"><Input type="date" value={to} min={from} onChange={(event) => setTo(event.target.value)} /></Field>
        {tab === 'revenue' && <Field label="Nhóm theo"><Select value={groupBy} onChange={(event) => setGroupBy(event.target.value)}><option value="DAY">Ngày</option><option value="WEEK">Tuần</option><option value="MONTH">Tháng</option><option value="QUARTER">Quý</option></Select></Field>}
        <Button variant="primary" disabled={Boolean(invalid)} onClick={() => setApplied({ fromDate: from, toDate: to })}>Xem báo cáo</Button>
      </div>
      {invalid && <div className="ui-alert error" role="alert" style={{ marginTop: 12 }}><span>{invalid}</span></div>}
      <p className="ui-muted" style={{ fontSize: 12, marginBottom: 0 }}>Đang xem: {formatDate(applied.fromDate)} – {formatDate(applied.toDate)}</p>
    </Panel>
    <Tabs tabs={[{ key: 'revenue', label: 'Doanh thu' }, { key: 'operations', label: 'Vận hành' }, { key: 'appointments', label: 'Lịch hẹn' }]} value={tab} onChange={setTab} />
    {tab === 'revenue' && <Revenue range={applied} groupBy={groupBy} />}
    {tab === 'operations' && <Operations range={applied} />}
    {tab === 'appointments' && <Appointments range={applied} />}
  </StaffLayout>;
}

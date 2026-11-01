import { Link } from 'react-router-dom';
import StaffLayout from '../../components/layout/StaffLayout';
import { AsyncView, Badge, PageHead, Panel, Stats, Table } from '../../components/ui';
import { billingApi, pharmacyApi, reportsApi, usersApi, visitsApi } from '../../api/endpoints';
import { ROLE_INFO } from '../../constants/roles';
import { useAuth } from '../../contexts/AuthContext';
import useAsync from '../../hooks/useAsync';
import { formatMoney, todayIso } from '../../lib/format';

const ROLE_KEYS = Object.keys(ROLE_INFO);

async function loadOverview() {
  const today = todayIso();
  const [doctors, lowStock, operations, revenue, ...counts] = await Promise.all([
    visitsApi.doctorsStatus(), pharmacyApi.medicines({ lowStock: true, active: true, size: 5 }),
    reportsApi.operations({ fromDate: today, toDate: today }), billingApi.revenueReport({ fromDate: today, toDate: today, groupBy: 'DAY' }),
    ...ROLE_KEYS.map((role) => usersApi.list({ role, size: 1 })),
  ]);
  return { doctors, lowStock, operations, revenue, counts: Object.fromEntries(ROLE_KEYS.map((role, index) => [role, counts[index].totalElements])) };
}

export default function DashboardPage() {
  const { user } = useAuth();
  const overview = useAsync(loadOverview, []);
  return <StaffLayout area="admin" title="Tổng quan">
    <PageHead eyebrow="PAWCARE ADMIN" title={`Xin chào, ${user.fullName}`}>Tình hình phòng khám hôm nay và các cảnh báo cần xử lý.</PageHead>
    <AsyncView state={overview}>
      {(d) => <>
        <Stats items={[['Ca khám hôm nay', d.operations.totalVisits, `${d.operations.completedVisits} hoàn tất · ${d.operations.emergencyVisits} cấp cứu`], ['Doanh thu hôm nay', formatMoney(d.revenue.totals.finalAmount), `${d.revenue.totals.invoiceCount} hóa đơn đã thu`, 'green'],
          ['Bác sĩ đang hoạt động', d.doctors.length, `Sức chứa đặt lịch: ${d.doctors.length} lịch / khung giờ`], ['Thuốc sắp hết', d.lowStock.totalElements, 'Tồn kho ≤ ngưỡng cảnh báo', d.lowStock.totalElements ? 'red' : '']]} />
        <div className="ui-cols">
          <Panel title="Cảnh báo tồn kho" actions={<Link className="ui-link" to="/admin/medicines?stock=low">Xem kho →</Link>} flush>
            <Table caption="Thuốc sắp hết" empty="Chưa có thuốc nào chạm ngưỡng cảnh báo." rows={d.lowStock.items} columns={[
              { title: 'Thuốc', render: (row) => <strong>{row.name}</strong> }, { title: 'Tồn', align: 'right', render: (row) => <Badge tone="red">{row.stockQuantity} / ngưỡng {row.minStockAlert}</Badge> }]} />
          </Panel>
          <Panel title="Tài khoản theo vai trò" actions={<Link className="ui-link" to="/admin/users">Quản lý →</Link>} flush>
            <Table caption="Tài khoản theo vai trò" rowKey="role" rows={ROLE_KEYS.map((role) => ({ role, count: d.counts[role] }))} columns={[{ title: 'Vai trò', render: (row) => ROLE_INFO[row.role].label }, { title: 'Số tài khoản', align: 'right', key: 'count' }]} />
          </Panel>
        </div>
      </>}
    </AsyncView>
  </StaffLayout>;
}

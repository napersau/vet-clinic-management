import StaffLayout from '../../components/layout/StaffLayout';
import HistoryLookup from '../../components/shared/HistoryLookup';
import { PageHead } from '../../components/ui';

export default function HistoryPage() {
  return <StaffLayout area="vet" title="Tra cứu bệnh án">
    <PageHead eyebrow="BỆNH ÁN" title="Tra cứu bệnh án">Xem toàn bộ lịch sử khám, kết quả xét nghiệm, hình ảnh và đơn thuốc trước đây của một thú cưng.</PageHead>
    <HistoryLookup />
  </StaffLayout>;
}

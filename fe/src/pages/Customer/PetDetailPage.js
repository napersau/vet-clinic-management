import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import CustomerLayout from '../../components/layout/CustomerLayout';
import PetAvatar from '../../components/customer/PetAvatar';
import PetFormDialog from '../../components/shared/PetFormDialog';
import { AsyncView, Badge, Button, Facts, PageHead, Panel, Table } from '../../components/ui';
import { petsApi } from '../../api/endpoints';
import useAsync from '../../hooks/useAsync';
import { formatAge, formatDate, formatDateTime, formatNumber } from '../../lib/format';
import { PET_GENDER, VISIT_STATUS } from '../../lib/labels';

export default function PetDetailPage() {
  const { petId } = useParams();
  const [editing, setEditing] = useState(false);
  const pet = useAsync(() => petsApi.get(petId), [petId]);
  const visits = useAsync(() => petsApi.visits(petId), [petId]);
  return <CustomerLayout>
    <AsyncView state={pet}>
      {(data) => <>
        <PageHead eyebrow={`MÃ ${data.petCode}`} title={data.name} actions={<><Button onClick={() => setEditing(true)}>Sửa hồ sơ</Button><Link className="ui-button primary" to={`/portal/booking?petId=${data.id}`}>Đặt lịch khám</Link></>}>
          {data.speciesName}{data.breed ? ` · ${data.breed}` : ''} · {formatAge(data.dateOfBirth)}
        </PageHead>
        <Panel title="Thông tin chung">
          <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap' }}>
            <PetAvatar pet={data} className="pet-detail-avatar" />
            <div style={{ flex: 1, minWidth: 260 }}>
              <Facts items={[['Giới tính', PET_GENDER[data.gender]], ['Ngày sinh', formatDate(data.dateOfBirth)], ['Cân nặng', data.weight ? `${formatNumber(data.weight, 2)} kg` : null], ['Màu lông', data.color],
                ['Triệt sản', data.neutered ? 'Đã triệt sản' : 'Chưa'], ['Đặc điểm nhận dạng', data.identifyingCharacteristics], ['Ghi chú / dị ứng', data.notes]]} />
            </div>
          </div>
        </Panel>
        <Panel title="Các đợt khám" subtitle="Chi tiết kết quả và đơn thuốc nằm trong Lịch sử khám bệnh." actions={<Link className="ui-button" to={`/portal/medical-history?petId=${data.id}`}>Xem lịch sử khám</Link>} flush>
          <AsyncView state={visits} empty="Bé chưa có đợt khám nào.">
            {(items) => <Table caption="Các đợt khám" columns={[
              { title: 'Mã đợt khám', render: (row) => <strong>{row.visitCode}</strong> },
              { title: 'Tiếp nhận', render: (row) => formatDateTime(row.checkedInAt) },
              { title: 'Chẩn đoán', render: (row) => row.diagnosis || '—' },
              { title: 'Trạng thái', render: (row) => <Badge map={VISIT_STATUS} value={row.status} /> }]} rows={items} />}
          </AsyncView>
        </Panel>
        {editing && <PetFormDialog pet={data} onClose={() => setEditing(false)} onSaved={() => pet.refresh()} />}
      </>}
    </AsyncView>
  </CustomerLayout>;
}

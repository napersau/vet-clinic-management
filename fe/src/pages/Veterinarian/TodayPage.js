import { Link } from 'react-router-dom';
import StaffLayout from '../../components/layout/StaffLayout';
import { AsyncView, Badge, Button, Empty, Notice, PageHead, Panel, Stats } from '../../components/ui';
import { visitsApi } from '../../api/endpoints';
import useAsync from '../../hooks/useAsync';
import useAction from '../../hooks/useAction';
import usePolling from '../../hooks/usePolling';
import { formatTimeOfInstant } from '../../lib/format';
import { VISIT_STATUS } from '../../lib/labels';

const SECTIONS = [
  { key: 'RESULTS_READY', title: 'Ca đã có kết quả — mời vào khám tiếp', hint: 'Ưu tiên tuyệt đối: bạn đang giữ khóa ưu tiên tái khám cho tới khi mời hoặc tạm hoãn.' },
  { key: 'IN_PROGRESS', title: 'Đang khám', hint: 'Tiếp tục thăm khám, chỉ định, kê đơn và chốt ca.' },
  { key: 'WAITING', title: 'Được phân công — chờ khám', hint: 'Lễ tân đã phân công; bấm Bắt đầu khám khi thú cưng vào phòng.' },
];

export default function TodayPage() {
  const visits = useAsync(() => visitsApi.myVisits(), []);
  const action = useAction();
  usePolling(visits.refresh, 15000);
  const call = async (visit, fn, text) => { if (await action.run(() => fn(visit.id), text)) visits.refresh(); };

  return <StaffLayout area="vet" title="Ca khám của tôi">
    <PageHead eyebrow="BÁC SĨ" title="Ca khám của tôi" actions={<Button onClick={visits.reload}>Làm mới</Button>}>Các ca lễ tân đã phân công cho bạn. Ca đang chờ kết quả cận lâm sàng không hiện ở đây (bạn được giải phóng để nhận ca khác) và quay lại khi đủ kết quả.</PageHead>
    <Notice notice={action.notice} onClose={action.clear} />
    <AsyncView state={visits}>
      {(items) => {
        const by = (key) => items.filter((visit) => visit.status === key);
        return <>
          <Stats items={[['Đã có kết quả', by('RESULTS_READY').length, 'Cần mời vào khám tiếp', by('RESULTS_READY').length ? 'amber' : ''], ['Đang khám', by('IN_PROGRESS').length], ['Chờ khám', by('WAITING').length]]} />
          {items.length === 0 && <Panel><Empty title="Chưa có ca nào">Khi lễ tân phân công ca cho bạn, ca sẽ hiện ở đây (trang tự cập nhật mỗi 15 giây).</Empty></Panel>}
          {SECTIONS.map((section) => by(section.key).length > 0 && <Panel key={section.key} title={section.title} subtitle={section.hint}>
            <ul className="ui-list">{by(section.key).map((visit) => <li key={visit.id}>
              <div><strong>{visit.petName}</strong> <small style={{ display: 'inline' }}>({visit.speciesName})</small> {visit.emergency && <span className="ui-emergency">CẤP CỨU</span>}
                <small>{visit.visitCode} · tiếp nhận {formatTimeOfInstant(visit.checkedInAt)}{visit.initialSymptoms ? ` · ${visit.initialSymptoms}` : ''}</small></div>
              <div className="ui-actions"><Badge map={VISIT_STATUS} value={visit.status} />
                {visit.status === 'RESULTS_READY' && <>
                  <Button className="small primary" onClick={() => call(visit, visitsApi.callBack, `Đã mời ${visit.petName} vào khám tiếp.`)}>Mời vào khám</Button>
                  {!visit.callbackPostponed && <Button className="small" onClick={() => call(visit, visitsApi.postponeCallback, 'Đã tạm hoãn; bạn có thể nhận ca khác.')}>Tạm hoãn</Button>}</>}
                <Link className="ui-button small" to={`/vet/visits/${visit.id}`}>Mở hồ sơ</Link></div>
            </li>)}</ul>
          </Panel>)}
        </>;
      }}
    </AsyncView>
  </StaffLayout>;
}

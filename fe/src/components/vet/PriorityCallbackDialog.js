import { useState } from 'react';
import { Link, useMatch, useNavigate } from 'react-router-dom';
import Modal from '../common/Modal';
import { Badge, Button, Notice } from '../ui';
import { visitsApi } from '../../api/endpoints';
import { useAuth } from '../../contexts/AuthContext';
import useAction from '../../hooks/useAction';
import useAsync from '../../hooks/useAsync';
import usePolling from '../../hooks/usePolling';
import { formatTimeOfInstant } from '../../lib/format';
import { VISIT_STATUS } from '../../lib/labels';

// Ca đã bị đóng hộp thoại bằng "Xem sau": nhớ trong phiên làm việc (theo bác sĩ + ca) để không hiện lại mỗi lần chuyển trang.
// Ca nào vẫn ở RESULTS_READY sẽ luôn nằm ở đầu trang "Ca khám của tôi" nên bác sĩ không bỏ sót.
const dismissed = new Set();

const POLL_MS = 15000;

/**
 * Hộp thoại ưu tiên ca tái khám (UC-30): khi một ca của bác sĩ đã có đủ kết quả cận lâm sàng (RESULTS_READY) và chưa tạm hoãn,
 * bác sĩ đang giữ khóa ưu tiên và phải mời thú cưng vào khám tiếp hoặc tạm hoãn. Gắn trong khung khu vực bác sĩ nên hiện ở mọi trang.
 * Dùng polling; khi có WebSocket thông báo (notification-service) chỉ cần gọi thêm refresh khi nhận sự kiện doctor.priority_callback.
 */
export default function PriorityCallbackDialog() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const onVisitPage = useMatch('/vet/visits/:visitId');
  const visits = useAsync(() => visitsApi.myVisits(), []);
  const action = useAction();
  const [, rerender] = useState(0);
  usePolling(visits.refresh, POLL_MS);

  const keyOf = (visit) => `${user.id}:${visit.id}`;
  // Trang hồ sơ của chính ca đó đã có sẵn các nút mời / tạm hoãn nên không chồng thêm hộp thoại.
  const pending = (visits.data || []).filter((visit) => visit.status === 'RESULTS_READY' && !visit.callbackPostponed
    && !dismissed.has(keyOf(visit)) && String(onVisitPage?.params.visitId) !== String(visit.id));
  const current = pending[0];
  if (!current) return null;

  const later = () => { dismissed.add(keyOf(current)); action.clear(); rerender((count) => count + 1); };
  const invite = async () => {
    if (await action.run(() => visitsApi.callBack(current.id))) { visits.refresh(); navigate(`/vet/visits/${current.id}`); }
  };
  const postpone = async () => {
    if (await action.run(() => visitsApi.postponeCallback(current.id), `Đã tạm hoãn ${current.petName}; bạn có thể nhận ca khác.`)) visits.refresh();
  };

  return <Modal key={current.id} title="Ca đã có kết quả — cần ưu tiên" id="priority-callback-title" busy={action.busy} onClose={later}>
    <div className="ui-form-body" role="alertdialog" aria-labelledby="priority-callback-title">
      <p className="ui-confirm-text">
        <strong>{current.petName}</strong> ({current.speciesName}) đã có đủ kết quả xét nghiệm / chẩn đoán hình ảnh.
        Bạn đang giữ <strong>khóa ưu tiên tái khám</strong>: lễ tân chưa thể giao ca mới cho bạn cho tới khi bạn mời bệnh nhân này vào hoặc tạm hoãn.
      </p>
      <div className="ui-actions">
        <Badge map={VISIT_STATUS} value={current.status} />
        {current.emergency && <span className="ui-emergency">CẤP CỨU</span>}
        <small className="ui-muted">{current.visitCode} · tiếp nhận {formatTimeOfInstant(current.checkedInAt)}</small>
      </div>
      {pending.length > 1 && <p className="ui-muted" style={{ margin: 0 }}>Còn {pending.length - 1} ca khác cũng đang chờ bạn.</p>}
      <Notice notice={action.notice} onClose={action.clear} />
    </div>
    <div className="ui-form-actions">
      <Link className="ui-button secondary" to={`/vet/visits/${current.id}`} onClick={later}>Xem hồ sơ</Link>
      <Button onClick={later} disabled={action.busy}>Xem sau</Button>
      <Button onClick={postpone} disabled={action.busy}>Tạm hoãn</Button>
      <Button variant="primary" busy={action.busy} onClick={invite}>Mời vào khám tiếp</Button>
    </div>
  </Modal>;
}

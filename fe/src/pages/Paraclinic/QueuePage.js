import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import StaffLayout from '../../components/layout/StaffLayout';
import { AsyncView, Badge, Button, Field, FormDialog, Notice, PageHead, Panel, Stats, Table, Tabs, Textarea } from '../../components/ui';
import { paraclinicApi } from '../../api/endpoints';
import useAsync from '../../hooks/useAsync';
import useAction from '../../hooks/useAction';
import usePolling from '../../hooks/usePolling';
import { formatTimeOfInstant } from '../../lib/format';
import { QUEUE_STATE } from '../../lib/labels';

export const ROOM = {
  lab: { area: 'lab', title: 'Hàng đợi xét nghiệm', eyebrow: 'PHÒNG XÉT NGHIỆM', base: '/lab', load: paraclinicApi.labQueue, other: 'chẩn đoán hình ảnh' },
  imaging: { area: 'imaging', title: 'Hàng đợi chẩn đoán hình ảnh', eyebrow: 'PHÒNG CĐHA', base: '/imaging', load: paraclinicApi.imagingQueue, other: 'xét nghiệm' },
};

/** otherRoomStatus của backend có dạng LAB_IN_PROGRESS, IMAGING_PENDING, LAB_DONE... (FR-36: huy hiệu liên phòng). */
export function otherRoomText(value, otherRoom) {
  if (!value) return null;
  const state = value.endsWith('IN_PROGRESS') ? 'đang được thực hiện' : value.endsWith('PENDING') ? 'đang chờ' : value.endsWith('DONE') ? 'đã xong' : null;
  return state ? `Phòng ${otherRoom}: ${state}` : null;
}

function ReasonDialog({ title, optional, item, onClose, onSubmit, danger }) {
  const [reason, setReason] = useState('');
  return <FormDialog title={`${title} — ${item.petName}`} onClose={onClose} danger={danger} submitLabel={title}
    onSubmit={async () => { if (!optional && !reason.trim()) throw new Error('Nhập lý do.'); await onSubmit(reason.trim()); }}>
    <Field label={optional ? 'Lý do (không bắt buộc)' : 'Lý do *'}><Textarea value={reason} onChange={(event) => setReason(event.target.value)} maxLength={500} rows={2} autoFocus /></Field>
  </FormDialog>;
}

export default function QueuePage({ kind }) {
  const room = ROOM[kind];
  const navigate = useNavigate();
  const queue = useAsync(() => room.load(), [kind]);
  const [dialog, setDialog] = useState(null);
  const [tab, setTab] = useState('queue');
  const action = useAction();
  usePolling(queue.refresh, 15000);
  const done = (text) => { setDialog(null); action.setNotice({ type: 'success', text }); queue.refresh(); };
  const start = async (item) => { if (await action.run(() => paraclinicApi.start(item.serviceDetailId))) navigate(`${room.base}/entry/${item.serviceDetailId}`); };
  const recall = async (item) => { if (await action.run(() => paraclinicApi.recall(item.serviceDetailId), `Đã ghim ${item.petName} làm ca ưu tiên kế tiếp.`)) queue.refresh(); };

  return <StaffLayout area={room.area} title={room.title}>
    <PageHead eyebrow={room.eyebrow} title={room.title} actions={<Button onClick={queue.reload}>Làm mới</Button>}>Ca cấp cứu lên đầu; ca vừa gọi lại được ưu tiên kế tiếp; ca đã hoãn nằm cuối. Bạn chỉ thấy ca thuộc phòng của mình.</PageHead>
    <Notice notice={action.notice} onClose={action.clear} />
    <AsyncView state={queue}>
      {(items) => <>
        <Stats items={[['Đang thực hiện', items.filter((i) => i.queueState === 'IN_PROGRESS').length], ['Đang chờ', items.filter((i) => ['WAITING', 'NEXT_IN_LINE'].includes(i.queueState)).length], ['Đã hoãn', items.filter((i) => i.queueState === 'POSTPONED').length, undefined, 'amber'], ['Cấp cứu', items.filter((i) => i.emergency).length, undefined, items.some((i) => i.emergency) ? 'red' : '']]} />
        <Tabs value={tab} onChange={setTab} tabs={[{ key: 'queue', label: 'Hàng đợi', count: items.filter((i) => i.queueState !== 'POSTPONED').length }, { key: 'postponed', label: 'Ca tạm hoãn do vắng mặt', count: items.filter((i) => i.queueState === 'POSTPONED').length }]} />
        <Panel flush>
          <Table caption={room.title} empty={tab === 'queue' ? 'Hiện không có ca nào chờ ở phòng này.' : 'Không có ca nào đang tạm hoãn.'} rows={items.filter((i) => (tab === 'postponed') === (i.queueState === 'POSTPONED'))} rowKey="serviceDetailId" rowClass={(row) => (row.emergency ? 'emergency' : '')} columns={[
            { title: 'Thú cưng', render: (row) => <><strong>{row.petName}</strong>{row.emergency && <span className="ui-emergency"> · CẤP CỨU</span>}<small>{row.speciesName} · {row.visitCode}</small></> },
            { title: 'Dịch vụ', render: (row) => <>{row.serviceName}{row.notes && <small>{row.notes}</small>}</> },
            { title: 'Chỉ định lúc', render: (row) => formatTimeOfInstant(row.orderedAt) },
            { title: 'Trạng thái', render: (row) => <><Badge map={QUEUE_STATE} value={row.queueState} />{otherRoomText(row.otherRoomStatus, room.other) && <small>{otherRoomText(row.otherRoomStatus, room.other)}</small>}</> },
            { title: '', align: 'right', render: (row) => <div className="ui-actions" style={{ justifyContent: 'flex-end' }}>
              {row.queueState === 'IN_PROGRESS' ? <Link className="ui-button small primary" to={`${room.base}/entry/${row.serviceDetailId}`}>Nhập kết quả</Link> : <Button className="small primary" onClick={() => start(row)}>Bắt đầu</Button>}
              {row.queueState === 'POSTPONED' && <Button className="small" onClick={() => recall(row)}>Gọi lại</Button>}
              {row.queueState !== 'IN_PROGRESS' && row.queueState !== 'POSTPONED' && <Button className="small" onClick={() => setDialog({ type: 'postpone', item: row })}>Hoãn</Button>}
              <Button className="small danger" onClick={() => setDialog({ type: 'cancel', item: row })}>Hủy</Button></div> }]} />
        </Panel>
        {dialog?.type === 'postpone' && <ReasonDialog title="Hoãn" optional item={dialog.item} onClose={() => setDialog(null)} onSubmit={async (reason) => { await paraclinicApi.postpone(dialog.item.serviceDetailId, reason); done(`Đã hoãn ${dialog.item.petName}.`); }} />}
        {dialog?.type === 'cancel' && <ReasonDialog title="Hủy dịch vụ" danger item={dialog.item} onClose={() => setDialog(null)} onSubmit={async (reason) => { await paraclinicApi.cancel(dialog.item.serviceDetailId, reason); done(`Đã hủy dịch vụ của ${dialog.item.petName}.`); }} />}
      </>}
    </AsyncView>
  </StaffLayout>;
}

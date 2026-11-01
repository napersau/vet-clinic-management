import { useState } from 'react';
import StaffLayout from '../../components/layout/StaffLayout';
import { AsyncView, Badge, Button, Field, FormDialog, Input, Notice, PageHead, Panel, Select, Table, Textarea } from '../../components/ui';
import { catalogApi } from '../../api/endpoints';
import { mapFieldErrors } from '../../api/client';
import useAsync from '../../hooks/useAsync';
import { blankToNull, formatMoney } from '../../lib/format';
import { SERVICE_CATEGORY } from '../../lib/labels';

function ServiceDialog({ service, onClose, onSaved }) {
  const editing = Boolean(service);
  const [form, setForm] = useState({ code: service?.code || '', name: service?.name || '', category: service?.category || 'EXAMINATION', price: service?.price ?? '', description: service?.description || '', active: service?.active ?? true });
  const [errors, setErrors] = useState({});
  const set = (key) => (event) => setForm({ ...form, [key]: event.target.type === 'checkbox' ? event.target.checked : event.target.value });
  const submit = async () => {
    const found = {};
    if (!editing && !form.code.trim()) found.code = 'Nhập mã dịch vụ.';
    if (!form.name.trim()) found.name = 'Nhập tên dịch vụ.';
    if (form.price === '' || Number(form.price) < 0) found.price = 'Đơn giá không được âm.';
    setErrors(found);
    if (Object.keys(found).length) throw new Error('Vui lòng kiểm tra lại các trường được đánh dấu.');
    try {
      const saved = editing
        ? await catalogApi.updateService(service.id, { name: form.name.trim(), price: Number(form.price), description: blankToNull(form.description), active: form.active })
        : await catalogApi.createService({ code: form.code.trim().toUpperCase(), name: form.name.trim(), category: form.category, price: Number(form.price), description: blankToNull(form.description) });
      onSaved(saved);
    } catch (problem) { setErrors(mapFieldErrors(problem)); throw problem; }
  };
  return <FormDialog title={editing ? `Sửa dịch vụ ${service.name}` : 'Thêm dịch vụ'} id="service-form-title" onClose={onClose} onSubmit={submit} submitLabel={editing ? 'Lưu thay đổi' : 'Thêm dịch vụ'}>
    <div className="ui-grid">
      <Field label="Mã dịch vụ *" error={errors.code} hint={editing ? 'Mã không đổi được.' : 'Ví dụ: VAC_RABIES'}><Input value={form.code} onChange={set('code')} disabled={editing} maxLength={50} /></Field>
      <Field label="Nhóm dịch vụ *" hint={editing ? 'Nhóm không đổi được.' : undefined}><Select value={form.category} onChange={set('category')} disabled={editing}>{Object.entries(SERVICE_CATEGORY).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</Select></Field>
      <Field label="Tên dịch vụ *" error={errors.name} wide><Input value={form.name} onChange={set('name')} maxLength={150} /></Field>
      <Field label="Đơn giá (VND) *" error={errors.price}><Input type="number" min="0" step="1000" value={form.price} onChange={set('price')} /></Field>
      {editing && <label className="ui-check"><input type="checkbox" checked={form.active} onChange={set('active')} />Đang cung cấp</label>}
      <Field label="Mô tả" wide><Textarea value={form.description} onChange={set('description')} rows={2} /></Field>
    </div>
    {editing && <p className="ui-muted" style={{ fontSize: 12 }}>Đổi đơn giá chỉ áp dụng cho chỉ định mới; chỉ định cũ giữ đơn giá đã chốt. Dịch vụ đã phát sinh giao dịch chỉ ngừng cung cấp, không xóa.</p>}
  </FormDialog>;
}

export default function ServicesPage() {
  const [inactive, setInactive] = useState(true);
  const list = useAsync(() => catalogApi.services(inactive), [inactive]);
  const [dialog, setDialog] = useState(null);
  const [notice, setNotice] = useState(null);
  return <StaffLayout area="admin" title="Dịch vụ">
    <PageHead eyebrow="DANH MỤC" title="Dịch vụ" actions={<Button variant="primary" onClick={() => setDialog({})}>+ Thêm dịch vụ</Button>}>Danh mục dịch vụ khám chữa bệnh và đơn giá. Dịch vụ xét nghiệm được gắn chỉ số ở mục “Xét nghiệm & tham chiếu”.</PageHead>
    <Notice notice={notice} onClose={() => setNotice(null)} />
    <label className="ui-check" style={{ marginBottom: 14 }}><input type="checkbox" checked={inactive} onChange={(event) => setInactive(event.target.checked)} />Hiện cả dịch vụ đã ngừng cung cấp</label>
    <Panel flush>
      <AsyncView state={list} empty="Chưa có dịch vụ nào.">
        {(items) => <Table caption="Dịch vụ" rows={items} columns={[
          { title: 'Dịch vụ', render: (row) => <><strong>{row.name}</strong><small>{row.code}</small></> },
          { title: 'Nhóm', render: (row) => SERVICE_CATEGORY[row.category] },
          { title: 'Đơn giá', align: 'right', render: (row) => formatMoney(row.price) },
          { title: 'Trạng thái', render: (row) => <Badge tone={row.active ? 'green' : 'gray'}>{row.active ? 'Đang cung cấp' : 'Ngừng'}</Badge> },
          { title: '', align: 'right', render: (row) => <Button className="small" onClick={() => setDialog({ service: row })}>Sửa</Button> }]} />}
      </AsyncView>
    </Panel>
    {dialog && <ServiceDialog service={dialog.service} onClose={() => setDialog(null)} onSaved={(saved) => { setNotice({ type: 'success', text: `Đã lưu dịch vụ ${saved.name}.` }); list.refresh(); }} />}
  </StaffLayout>;
}

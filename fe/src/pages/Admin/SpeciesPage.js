import { useState } from 'react';
import StaffLayout from '../../components/layout/StaffLayout';
import { AsyncView, Button, Field, FormDialog, Input, Notice, PageHead, Panel, Table } from '../../components/ui';
import { catalogApi } from '../../api/endpoints';
import { mapFieldErrors } from '../../api/client';
import useAsync from '../../hooks/useAsync';
import { blankToNull } from '../../lib/format';

function SpeciesDialog({ species, onClose, onSaved }) {
  const editing = Boolean(species);
  const [form, setForm] = useState({ code: species?.code || '', name: species?.name || '', description: species?.description || '' });
  const [errors, setErrors] = useState({});
  const set = (key) => (event) => setForm({ ...form, [key]: event.target.value });
  const submit = async () => {
    const found = {};
    if (!form.code.trim()) found.code = 'Nhập mã loài.';
    if (!form.name.trim()) found.name = 'Nhập tên loài.';
    setErrors(found);
    if (Object.keys(found).length) throw new Error('Vui lòng kiểm tra lại các trường được đánh dấu.');
    const body = { code: form.code.trim().toUpperCase(), name: form.name.trim(), description: blankToNull(form.description) };
    try { onSaved(editing ? await catalogApi.updateSpecies(species.id, body) : await catalogApi.createSpecies(body)); } catch (problem) { setErrors(mapFieldErrors(problem)); throw problem; }
  };
  return <FormDialog title={editing ? `Sửa loài ${species.name}` : 'Thêm loài thú cưng'} onClose={onClose} onSubmit={submit} submitLabel={editing ? 'Lưu thay đổi' : 'Thêm loài'}>
    <Field label="Mã loài *" error={errors.code} hint="Ví dụ: DOG, CAT, RABBIT."><Input value={form.code} onChange={set('code')} maxLength={50} /></Field>
    <Field label="Tên loài *" error={errors.name}><Input value={form.name} onChange={set('name')} maxLength={100} /></Field>
    <Field label="Mô tả" error={errors.description}><Input value={form.description} onChange={set('description')} maxLength={255} /></Field>
  </FormDialog>;
}

export default function SpeciesPage() {
  const list = useAsync(() => catalogApi.species(), []);
  const [dialog, setDialog] = useState(null);
  const [notice, setNotice] = useState(null);
  return <StaffLayout area="admin" title="Loài thú cưng">
    <PageHead eyebrow="DANH MỤC" title="Loài thú cưng" actions={<Button variant="primary" onClick={() => setDialog({})}>+ Thêm loài</Button>}>Loài quyết định khoảng tham chiếu xét nghiệm. Thêm loài mới rồi cấu hình khoảng tham chiếu ở mục “Xét nghiệm & tham chiếu”. Loài đã có thú cưng không bị xóa.</PageHead>
    <Notice notice={notice} onClose={() => setNotice(null)} />
    <Panel flush>
      <AsyncView state={list} empty="Chưa có loài nào.">
        {(items) => <Table caption="Loài thú cưng" rows={items} columns={[
          { title: 'Mã', render: (row) => <span className="ui-mono">{row.code}</span> }, { title: 'Tên loài', render: (row) => <strong>{row.name}</strong> }, { title: 'Mô tả', render: (row) => row.description || '—' },
          { title: '', align: 'right', render: (row) => <Button className="small" onClick={() => setDialog({ species: row })}>Sửa</Button> }]} />}
      </AsyncView>
    </Panel>
    {dialog && <SpeciesDialog species={dialog.species} onClose={() => setDialog(null)} onSaved={(saved) => { setNotice({ type: 'success', text: `Đã lưu loài ${saved.name}.` }); list.refresh(); }} />}
  </StaffLayout>;
}

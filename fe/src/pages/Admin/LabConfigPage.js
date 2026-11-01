import { useEffect, useState } from 'react';
import StaffLayout from '../../components/layout/StaffLayout';
import { AsyncView, Button, Field, FormDialog, Input, Notice, PageHead, Panel, Select, Table, Tabs } from '../../components/ui';
import { catalogApi } from '../../api/endpoints';
import { mapFieldErrors } from '../../api/client';
import useAction from '../../hooks/useAction';
import useAsync from '../../hooks/useAsync';
import { blankToNull, formatNumber } from '../../lib/format';

function ItemDialog({ item, species, onClose, onSaved }) {
  const editing = Boolean(item);
  const [form, setForm] = useState({ code: item?.code || '', name: item?.name || '', unit: item?.unit || '', description: item?.description || '' });
  const [ranges, setRanges] = useState(() => Object.fromEntries(species.map((s) => { const r = item?.ranges?.find((x) => x.speciesId === s.id); return [s.id, { min: r?.minValue ?? '', max: r?.maxValue ?? '' }]; })));
  const [errors, setErrors] = useState({});
  const set = (key) => (event) => setForm({ ...form, [key]: event.target.value });
  const submit = async () => {
    const found = {};
    if (!editing && !form.code.trim()) found.code = 'Nhập mã chỉ số.';
    if (!form.name.trim()) found.name = 'Nhập tên chỉ số.';
    if (!form.unit.trim()) found.unit = 'Nhập đơn vị.';
    const filled = species.filter((s) => ranges[s.id].min !== '' || ranges[s.id].max !== '');
    const bad = filled.find((s) => ranges[s.id].min === '' || ranges[s.id].max === '' || Number(ranges[s.id].min) >= Number(ranges[s.id].max));
    if (bad) found.ranges = `Khoảng tham chiếu của loài ${bad.name} cần có cả hai đầu và giá trị nhỏ nhất phải bé hơn lớn nhất.`;
    setErrors(found);
    if (Object.keys(found).length) throw new Error(found.ranges || 'Vui lòng kiểm tra lại các trường được đánh dấu.');
    try {
      if (editing) {
        let saved = await catalogApi.updateLabItem(item.id, { name: form.name.trim(), unit: form.unit.trim(), description: blankToNull(form.description) });
        for (const s of filled) saved = await catalogApi.saveRange(item.id, s.id, { minValue: Number(ranges[s.id].min), maxValue: Number(ranges[s.id].max) });
        onSaved(saved);
      } else {
        onSaved(await catalogApi.createLabItem({ code: form.code.trim().toUpperCase(), name: form.name.trim(), unit: form.unit.trim(), description: blankToNull(form.description), ranges: filled.map((s) => ({ speciesId: s.id, minValue: Number(ranges[s.id].min), maxValue: Number(ranges[s.id].max) })) }));
      }
    } catch (problem) { setErrors(mapFieldErrors(problem)); throw problem; }
  };
  return <FormDialog title={editing ? `Sửa chỉ số ${item.name}` : 'Thêm chỉ số xét nghiệm'} id="lab-item-title" onClose={onClose} onSubmit={submit} wide submitLabel={editing ? 'Lưu thay đổi' : 'Thêm chỉ số'}>
    <div className="ui-grid">
      <Field label="Mã chỉ số *" error={errors.code} hint={editing ? 'Mã không đổi được.' : 'Ví dụ: WBC'}><Input value={form.code} onChange={set('code')} disabled={editing} maxLength={50} /></Field>
      <Field label="Đơn vị *" error={errors.unit}><Input value={form.unit} onChange={set('unit')} maxLength={30} /></Field>
      <Field label="Tên chỉ số *" error={errors.name} wide><Input value={form.name} onChange={set('name')} maxLength={100} /></Field>
    </div>
    <h3 style={{ margin: '4px 0 0', fontSize: 14 }}>Khoảng tham chiếu theo loài</h3>
    <p className="ui-muted" style={{ margin: 0, fontSize: 12 }}>Kết quả đã nhập trước đây giữ nguyên khoảng tham chiếu lúc đó. Để trống nếu chưa có khoảng cho loài.</p>
    {species.map((s) => <div key={s.id} className="ui-grid" style={{ alignItems: 'end' }}>
      <Field label={`${s.name} — nhỏ nhất`}><Input type="number" step="0.01" min="0" value={ranges[s.id].min} onChange={(event) => setRanges({ ...ranges, [s.id]: { ...ranges[s.id], min: event.target.value } })} /></Field>
      <Field label={`${s.name} — lớn nhất`}><Input type="number" step="0.01" min="0" value={ranges[s.id].max} onChange={(event) => setRanges({ ...ranges, [s.id]: { ...ranges[s.id], max: event.target.value } })} /></Field>
    </div>)}
  </FormDialog>;
}

function Items({ items, species, onChanged }) {
  const [dialog, setDialog] = useState(null);
  const [notice, setNotice] = useState(null);
  return <>
    <Notice notice={notice} onClose={() => setNotice(null)} />
    <div className="ui-toolbar"><Button variant="primary" onClick={() => setDialog({})}>+ Thêm chỉ số</Button></div>
    <Panel flush>
      <Table caption="Chỉ số xét nghiệm" empty="Chưa có chỉ số nào." rows={items} columns={[
        { title: 'Chỉ số', render: (row) => <><strong>{row.name}</strong><small>{row.code} · {row.unit}</small></> },
        ...species.map((s) => ({ title: `Tham chiếu ${s.name}`, render: (row) => { const r = row.ranges.find((x) => x.speciesId === s.id); return r ? `${formatNumber(r.minValue, 2)} – ${formatNumber(r.maxValue, 2)}` : <span className="ui-muted">Chưa có</span>; } })),
        { title: '', align: 'right', render: (row) => <Button className="small" onClick={() => setDialog({ item: row })}>Sửa</Button> }]} />
    </Panel>
    {dialog && <ItemDialog item={dialog.item} species={species} onClose={() => setDialog(null)} onSaved={(saved) => { setNotice({ type: 'success', text: `Đã lưu chỉ số ${saved.name}.` }); onChanged(); }} />}
  </>;
}

function ServiceMapping({ items }) {
  const services = useAsync(() => catalogApi.services(), []);
  const labServices = (services.data || []).filter((service) => service.category === 'LABORATORY');
  const [serviceId, setServiceId] = useState('');
  const mapping = useAsync(() => catalogApi.serviceLabItems(serviceId), [serviceId], { skip: !serviceId });
  const [selected, setSelected] = useState([]);
  const action = useAction();
  useEffect(() => { if (mapping.data) setSelected(mapping.data.map((item) => item.id)); }, [mapping.data]);
  const toggle = (id) => setSelected(selected.includes(id) ? selected.filter((x) => x !== id) : [...selected, id]);
  return <Panel title="Chỉ số theo dịch vụ xét nghiệm" subtitle="Dịch vụ chưa chọn chỉ số nào sẽ gồm TẤT CẢ chỉ số. Chọn một số chỉ số để giới hạn biểu mẫu nhập của kỹ thuật viên.">
    <div className="ui-stack" style={{ gap: 14 }}>
      <Notice notice={action.notice} onClose={action.clear} />
      <Field label="Dịch vụ xét nghiệm"><Select value={serviceId} onChange={(event) => setServiceId(event.target.value)}><option value="">Chọn dịch vụ</option>{labServices.map((service) => <option key={service.id} value={service.id}>{service.name}</option>)}</Select></Field>
      {serviceId && <AsyncView state={mapping}>{() => <>
        <div className="ui-grid">{items.map((item) => <label className="ui-check" key={item.id}><input type="checkbox" checked={selected.includes(item.id)} onChange={() => toggle(item.id)} />{item.name} <span className="ui-muted">({item.code})</span></label>)}</div>
        <div><Button variant="primary" busy={action.busy} onClick={async () => { if (await action.run(() => catalogApi.setServiceLabItems(serviceId, selected), selected.length ? 'Đã lưu danh sách chỉ số của dịch vụ.' : 'Đã lưu: dịch vụ gồm tất cả chỉ số.')) mapping.refresh(); }}>Lưu</Button></div></>}</AsyncView>}
    </div>
  </Panel>;
}

export default function LabConfigPage() {
  const [tab, setTab] = useState('items');
  const items = useAsync(() => catalogApi.labItems(), []);
  const species = useAsync(() => catalogApi.species(), []);
  return <StaffLayout area="admin" title="Xét nghiệm & tham chiếu">
    <PageHead eyebrow="DANH MỤC" title="Xét nghiệm & khoảng tham chiếu">Cấu hình chỉ số xét nghiệm, khoảng tham chiếu cho từng loài và chỉ số thuộc từng dịch vụ xét nghiệm. Chỉ số đã có kết quả không xóa được.</PageHead>
    <Tabs tabs={[{ key: 'items', label: 'Chỉ số & khoảng tham chiếu' }, { key: 'mapping', label: 'Chỉ số theo dịch vụ' }]} value={tab} onChange={setTab} />
    <AsyncView state={items}>{(itemList) => <AsyncView state={species}>{(speciesList) => (tab === 'items' ? <Items items={itemList} species={speciesList} onChanged={items.refresh} /> : <ServiceMapping items={itemList} />)}</AsyncView>}</AsyncView>
  </StaffLayout>;
}

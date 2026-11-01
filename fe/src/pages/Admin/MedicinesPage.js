import { useState } from 'react';
import StaffLayout from '../../components/layout/StaffLayout';
import { AsyncView, Badge, Button, Field, FormDialog, Input, Notice, PageHead, Pager, Panel, SearchBox, Select, Table } from '../../components/ui';
import { pharmacyApi } from '../../api/endpoints';
import { mapFieldErrors } from '../../api/client';
import useAsync from '../../hooks/useAsync';
import useDebounced from '../../hooks/useDebounced';
import { blankToNull, formatDateTime, formatMoney } from '../../lib/format';
import { INVENTORY_TYPE } from '../../lib/labels';

function MedicineDialog({ medicine, onClose, onSaved }) {
  const editing = Boolean(medicine);
  const [form, setForm] = useState({ code: medicine?.code || '', name: medicine?.name || '', activeIngredient: medicine?.activeIngredient || '', dosageForm: medicine?.dosageForm || '', unit: medicine?.unit || '', salePrice: medicine?.salePrice ?? '', initialStock: 0, minStockAlert: medicine?.minStockAlert ?? 10, active: medicine?.active ?? true });
  const [errors, setErrors] = useState({});
  const set = (key) => (event) => setForm({ ...form, [key]: event.target.type === 'checkbox' ? event.target.checked : event.target.value });
  const submit = async () => {
    const found = {};
    if (!editing && !form.code.trim()) found.code = 'Nhập mã thuốc.';
    if (!form.name.trim()) found.name = 'Nhập tên thuốc.';
    if (!form.dosageForm.trim()) found.dosageForm = 'Nhập dạng bào chế.';
    if (!form.unit.trim()) found.unit = 'Nhập đơn vị tính.';
    if (form.salePrice === '' || Number(form.salePrice) < 0) found.salePrice = 'Giá bán không được âm.';
    setErrors(found);
    if (Object.keys(found).length) throw new Error('Vui lòng kiểm tra lại các trường được đánh dấu.');
    const common = { name: form.name.trim(), activeIngredient: blankToNull(form.activeIngredient), dosageForm: form.dosageForm.trim(), unit: form.unit.trim(), salePrice: Number(form.salePrice), minStockAlert: Number(form.minStockAlert) };
    try {
      onSaved(editing ? await pharmacyApi.updateMedicine(medicine.id, { ...common, active: form.active }) : await pharmacyApi.createMedicine({ ...common, code: form.code.trim().toUpperCase(), initialStock: Number(form.initialStock) }));
    } catch (problem) { setErrors(mapFieldErrors(problem)); throw problem; }
  };
  return <FormDialog title={editing ? `Sửa thuốc ${medicine.name}` : 'Thêm thuốc'} id="medicine-form-title" onClose={onClose} onSubmit={submit} submitLabel={editing ? 'Lưu thay đổi' : 'Thêm thuốc'} wide>
    <div className="ui-grid">
      <Field label="Mã thuốc *" error={errors.code} hint={editing ? 'Mã không đổi được.' : undefined}><Input value={form.code} onChange={set('code')} disabled={editing} maxLength={50} /></Field>
      <Field label="Tên thuốc *" error={errors.name}><Input value={form.name} onChange={set('name')} maxLength={150} /></Field>
      <Field label="Hoạt chất" error={errors.activeIngredient}><Input value={form.activeIngredient} onChange={set('activeIngredient')} maxLength={150} /></Field>
      <Field label="Dạng bào chế *" error={errors.dosageForm}><Input value={form.dosageForm} onChange={set('dosageForm')} maxLength={50} placeholder="Viên nén, siro, ống tiêm…" /></Field>
      <Field label="Đơn vị tính *" error={errors.unit}><Input value={form.unit} onChange={set('unit')} maxLength={30} placeholder="viên, chai, ống…" /></Field>
      <Field label="Giá bán (VND) *" error={errors.salePrice}><Input type="number" min="0" step="500" value={form.salePrice} onChange={set('salePrice')} /></Field>
      <Field label="Ngưỡng cảnh báo tồn kho" error={errors.minStockAlert}><Input type="number" min="0" value={form.minStockAlert} onChange={set('minStockAlert')} /></Field>
      {!editing && <Field label="Tồn kho ban đầu" error={errors.initialStock}><Input type="number" min="0" value={form.initialStock} onChange={set('initialStock')} /></Field>}
      {editing && <label className="ui-check"><input type="checkbox" checked={form.active} onChange={set('active')} />Đang kinh doanh</label>}
    </div>
    {editing && <p className="ui-muted" style={{ fontSize: 12 }}>Tồn kho không sửa ở đây — dùng Nhập kho hoặc Điều chỉnh để có nhật ký.</p>}
  </FormDialog>;
}

function StockDialog({ medicine, mode, onClose, onSaved }) {
  const [amount, setAmount] = useState('');
  const [notes, setNotes] = useState('');
  const submit = async () => {
    const value = Number(amount);
    if (!Number.isInteger(value) || value === 0 || (mode === 'import' && value < 0)) throw new Error(mode === 'import' ? 'Số lượng nhập phải là số nguyên dương.' : 'Nhập số nguyên khác 0 (âm = hao hụt).');
    if (mode === 'adjust' && !notes.trim()) throw new Error('Điều chỉnh tồn kho bắt buộc có lý do.');
    onSaved(mode === 'import' ? await pharmacyApi.importStock(medicine.id, { quantity: value, notes: blankToNull(notes) }) : await pharmacyApi.adjustStock(medicine.id, { delta: value, reason: notes.trim() }));
  };
  return <FormDialog title={`${mode === 'import' ? 'Nhập kho' : 'Điều chỉnh tồn kho'} — ${medicine.name}`} onClose={onClose} onSubmit={submit} submitLabel={mode === 'import' ? 'Nhập kho' : 'Điều chỉnh'}>
    <p className="ui-confirm-text">Tồn hiện tại: <strong>{medicine.stockQuantity} {medicine.unit}</strong>.{mode === 'adjust' && ' Nhập số âm cho hao hụt; kho không được âm.'}</p>
    <Field label={mode === 'import' ? 'Số lượng nhập *' : 'Chênh lệch (+/−) *'}><Input type="number" step="1" value={amount} onChange={(event) => setAmount(event.target.value)} autoFocus /></Field>
    <Field label={mode === 'import' ? 'Ghi chú (nhà cung cấp, số lô…)' : 'Lý do *'}><Input value={notes} onChange={(event) => setNotes(event.target.value)} maxLength={255} /></Field>
  </FormDialog>;
}

function TransactionsDialog({ medicine, onClose }) {
  const [page, setPage] = useState(0);
  const list = useAsync(() => pharmacyApi.transactions(medicine.id, { page, size: 10 }), [medicine.id, page]);
  return <FormDialog title={`Nhật ký kho — ${medicine.name}`} onClose={onClose} onSubmit={async () => {}} submitLabel="Đóng" wide>
    <AsyncView state={list} isEmpty={(data) => data.items.length === 0} empty="Chưa có giao dịch kho nào.">{(data) => <>
      <Table caption="Nhật ký kho" rows={data.items} columns={[
        { title: 'Thời gian', render: (row) => formatDateTime(row.createdAt) }, { title: 'Loại', render: (row) => INVENTORY_TYPE[row.transactionType] },
        { title: 'Số lượng', align: 'right', render: (row) => (row.transactionType === 'ADJUSTMENT' && row.quantity > 0 ? `+${row.quantity}` : row.quantity) },
        { title: 'Tồn sau', align: 'right', key: 'balanceAfter' }, { title: 'Ghi chú', render: (row) => row.notes || '—' }]} />
      <Pager page={data} onChange={setPage} /></>}</AsyncView>
  </FormDialog>;
}

export default function MedicinesPage() {
  const [keyword, setKeyword] = useState('');
  const [active, setActive] = useState('');
  const [lowStock, setLowStock] = useState(new URLSearchParams(window.location.search).get('stock') === 'low');
  const [page, setPage] = useState(0);
  const [dialog, setDialog] = useState(null);
  const [notice, setNotice] = useState(null);
  const search = useDebounced(keyword);
  const list = useAsync(() => pharmacyApi.medicines({ keyword: search.trim() || undefined, active: active === '' ? undefined : active === 'true', lowStock, page, size: 15 }), [search, active, lowStock, page]);
  const done = (text) => (saved) => { setDialog(null); setNotice({ type: 'success', text: typeof text === 'function' ? text(saved) : text }); list.refresh(); };

  return <StaffLayout area="admin" title="Thuốc & kho">
    <PageHead eyebrow="KHO THUỐC" title="Thuốc & kho" actions={<Button variant="primary" onClick={() => setDialog({ type: 'edit' })}>+ Thêm thuốc</Button>}>Quản lý danh mục thuốc, giá bán và tồn kho. Mọi lần nhập, điều chỉnh, xuất theo đơn đều được ghi nhật ký; tồn kho chạm ngưỡng sẽ báo cho quản trị viên.</PageHead>
    <Notice notice={notice} onClose={() => setNotice(null)} />
    <div className="ui-toolbar">
      <SearchBox value={keyword} onChange={(value) => { setKeyword(value); setPage(0); }} placeholder="Mã, tên hoặc hoạt chất" label="Tìm thuốc" />
      <Select aria-label="Trạng thái kinh doanh" value={active} onChange={(event) => { setActive(event.target.value); setPage(0); }}><option value="">Mọi trạng thái</option><option value="true">Đang kinh doanh</option><option value="false">Ngừng kinh doanh</option></Select>
      <label className="ui-check"><input type="checkbox" checked={lowStock} onChange={(event) => { setLowStock(event.target.checked); setPage(0); }} />Chỉ thuốc sắp hết</label>
    </div>
    <Panel flush>
      <AsyncView state={list} isEmpty={(data) => data.items.length === 0} empty="Không có thuốc phù hợp.">
        {(data) => <>
          <Table caption="Thuốc" rows={data.items} columns={[
            { title: 'Thuốc', render: (row) => <><strong>{row.name}</strong><small>{row.code} · {row.activeIngredient || '—'}</small></> },
            { title: 'Dạng / đơn vị', render: (row) => `${row.dosageForm} (${row.unit})` },
            { title: 'Giá bán', align: 'right', render: (row) => formatMoney(row.salePrice) },
            { title: 'Tồn kho', align: 'right', render: (row) => <>{row.stockQuantity}<small>Ngưỡng {row.minStockAlert}</small></> },
            { title: 'Trạng thái', render: (row) => <>{row.active ? <Badge tone="green">Đang bán</Badge> : <Badge tone="gray">Ngừng</Badge>} {row.lowStock && <Badge tone="amber">Sắp hết</Badge>}</> },
            { title: '', align: 'right', render: (row) => <div className="ui-actions" style={{ justifyContent: 'flex-end' }}>
              <Button className="small primary" onClick={() => setDialog({ type: 'import', medicine: row })}>Nhập kho</Button>
              <Button className="small" onClick={() => setDialog({ type: 'adjust', medicine: row })}>Điều chỉnh</Button>
              <Button className="small" onClick={() => setDialog({ type: 'edit', medicine: row })}>Sửa</Button>
              <Button className="small" onClick={() => setDialog({ type: 'log', medicine: row })}>Nhật ký</Button></div> }]} />
          <Pager page={data} onChange={setPage} />
        </>}
      </AsyncView>
    </Panel>
    {dialog?.type === 'edit' && <MedicineDialog medicine={dialog.medicine} onClose={() => setDialog(null)} onSaved={done((saved) => `Đã lưu thuốc ${saved.name}.`)} />}
    {dialog?.type === 'import' && <StockDialog mode="import" medicine={dialog.medicine} onClose={() => setDialog(null)} onSaved={done((saved) => `Đã nhập kho; tồn ${saved.name} hiện là ${saved.stockQuantity}.`)} />}
    {dialog?.type === 'adjust' && <StockDialog mode="adjust" medicine={dialog.medicine} onClose={() => setDialog(null)} onSaved={done((saved) => `Đã điều chỉnh; tồn ${saved.name} hiện là ${saved.stockQuantity}.`)} />}
    {dialog?.type === 'log' && <TransactionsDialog medicine={dialog.medicine} onClose={() => setDialog(null)} />}
  </StaffLayout>;
}

import { useState } from 'react';
import { catalogApi, petsApi } from '../../api/endpoints';
import { mapFieldErrors } from '../../api/client';
import useAsync from '../../hooks/useAsync';
import { Field, FormDialog, Input, Select, Textarea } from '../ui';
import { PET_GENDER } from '../../lib/labels';
import { blankToNull, toNumber } from '../../lib/format';

const empty = { name: '', speciesId: '', breed: '', gender: 'UNKNOWN', dateOfBirth: '', color: '', weight: '', neutered: false, identifyingCharacteristics: '', notes: '' };

/**
 * Thêm hoặc sửa hồ sơ thú cưng. `pet` (PetResponse) có thì là sửa — loài không đổi được sau khi tạo (backend từ chối).
 * `ownerId` chỉ cần khi lễ tân tạo hộ; chủ nuôi tự tạo thì backend lấy từ token.
 */
export default function PetFormDialog({ pet, ownerId, onClose, onSaved }) {
  const species = useAsync(() => catalogApi.species(), []);
  const [form, setForm] = useState(pet ? { ...empty, ...Object.fromEntries(Object.keys(empty).map((key) => [key, pet[key] ?? empty[key]])), weight: pet.weight ?? '' } : empty);
  const [errors, setErrors] = useState({});
  const set = (key) => (event) => setForm({ ...form, [key]: event.target.type === 'checkbox' ? event.target.checked : event.target.value });

  const submit = async () => {
    const found = {};
    if (!form.name.trim()) found.name = 'Nhập tên thú cưng.';
    if (!pet && !form.speciesId) found.speciesId = 'Chọn loài.';
    if (form.weight !== '' && !(Number(form.weight) > 0)) found.weight = 'Cân nặng phải lớn hơn 0.';
    setErrors(found);
    if (Object.keys(found).length) throw new Error('Vui lòng kiểm tra lại các trường được đánh dấu.');
    const body = {
      name: form.name.trim(), breed: blankToNull(form.breed), gender: form.gender, dateOfBirth: blankToNull(form.dateOfBirth), color: blankToNull(form.color),
      weight: toNumber(form.weight), neutered: Boolean(form.neutered), identifyingCharacteristics: blankToNull(form.identifyingCharacteristics), notes: blankToNull(form.notes),
    };
    try {
      const saved = pet ? await petsApi.update(pet.id, body) : await petsApi.create({ ...body, speciesId: Number(form.speciesId), ...(ownerId ? { ownerId: Number(ownerId) } : {}) });
      onSaved(saved);
    } catch (problem) {
      setErrors(mapFieldErrors(problem));
      throw problem;
    }
  };

  return <FormDialog title={pet ? `Sửa hồ sơ ${pet.name}` : 'Thêm thú cưng'} id="pet-form-title" onClose={onClose} onSubmit={submit} submitLabel={pet ? 'Lưu thay đổi' : 'Thêm thú cưng'} wide>
    <div className="ui-grid">
      <Field label="Tên thú cưng *" error={errors.name}><Input value={form.name} onChange={set('name')} maxLength={100} autoFocus /></Field>
      <Field label="Loài *" error={errors.speciesId} hint={pet ? 'Không đổi được loài sau khi tạo hồ sơ.' : undefined}>
        <Select value={form.speciesId || pet?.speciesId || ''} onChange={set('speciesId')} disabled={Boolean(pet)}><option value="">{species.loading ? 'Đang tải…' : 'Chọn loài'}</option>{(species.data || []).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</Select>
      </Field>
      <Field label="Giống" error={errors.breed}><Input value={form.breed} onChange={set('breed')} maxLength={100} /></Field>
      <Field label="Giới tính"><Select value={form.gender} onChange={set('gender')}>{Object.entries(PET_GENDER).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</Select></Field>
      <Field label="Ngày sinh" error={errors.dateOfBirth}><Input type="date" value={form.dateOfBirth} onChange={set('dateOfBirth')} max={new Date().toISOString().slice(0, 10)} /></Field>
      <Field label="Cân nặng (kg)" error={errors.weight}><Input type="number" step="0.01" min="0" value={form.weight} onChange={set('weight')} /></Field>
      <Field label="Màu lông" error={errors.color}><Input value={form.color} onChange={set('color')} maxLength={100} /></Field>
      <label className="ui-check"><input type="checkbox" checked={Boolean(form.neutered)} onChange={set('neutered')} />Đã triệt sản</label>
      <Field label="Đặc điểm nhận dạng" wide error={errors.identifyingCharacteristics}><Textarea value={form.identifyingCharacteristics} onChange={set('identifyingCharacteristics')} maxLength={1000} /></Field>
      <Field label="Ghi chú (dị ứng, bệnh nền…)" wide error={errors.notes}><Textarea value={form.notes} onChange={set('notes')} maxLength={2000} /></Field>
    </div>
  </FormDialog>;
}

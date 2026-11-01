import { useEffect, useMemo, useState } from 'react';
import { petsApi, usersApi } from '../../api/endpoints';
import useAsync from '../../hooks/useAsync';
import useDebounced from '../../hooks/useDebounced';
import { Button, Field, Select, SearchBox } from '../ui';
import CustomerFormDialog from './CustomerFormDialog';
import PetFormDialog from './PetFormDialog';

/**
 * Chọn chủ nuôi (tìm theo tên / SĐT) rồi chọn thú cưng của họ; có thể tạo mới ngay tại chỗ.
 * onChange({ owner, pet }) — pet là null cho tới khi chọn. `value.owner` / `value.pet` do trang cha giữ.
 */
export default function OwnerPetPicker({ value, onChange, errors = {} }) {
  const [keyword, setKeyword] = useState('');
  const [dialog, setDialog] = useState(null); // 'customer' | 'pet'
  const search = useDebounced(keyword);
  const owners = useAsync(() => usersApi.list({ role: 'PET_OWNER', keyword: search, size: 6 }), [search], { skip: search.trim().length < 2 });
  const ownerId = value.owner?.id;
  const pets = useAsync(() => petsApi.list({ ownerId, size: 100 }), [ownerId], { skip: !ownerId });
  const petItems = useMemo(() => pets.data?.items || [], [pets.data]);

  // Chủ nuôi chỉ có một thú cưng thì chọn luôn
  useEffect(() => {
    if (ownerId && !value.pet && petItems.length === 1) onChange({ owner: value.owner, pet: petItems[0] });
  }, [ownerId, petItems, value.pet, value.owner, onChange]);

  const found = search.trim().length >= 2 ? owners.data?.items || [] : [];
  return <div className="ui-stack" style={{ gap: 14 }}>
    {!value.owner ? <>
      <div style={{ display: 'flex', gap: 10, alignItems: 'flex-end', flexWrap: 'wrap' }}>
        <div style={{ flex: 1, minWidth: 220 }}><div className="ui-field"><span>Chủ nuôi *</span><SearchBox value={keyword} onChange={setKeyword} placeholder="Nhập tên hoặc số điện thoại (từ 2 ký tự)" label="Tìm chủ nuôi" />{errors.owner && <small className="error">{errors.owner}</small>}</div></div>
        <Button onClick={() => setDialog('customer')}>+ Khách hàng mới</Button>
      </div>
      {owners.loading && <p className="ui-muted">Đang tìm…</p>}
      {search.trim().length >= 2 && !owners.loading && found.length === 0 && <p className="ui-muted">Không tìm thấy khách hàng. Hãy tạo khách hàng mới.</p>}
      {found.length > 0 && <ul className="ui-list">{found.map((owner) => <li key={owner.id}><div><strong>{owner.fullName}</strong><small>{owner.phoneNumber}{owner.email ? ` · ${owner.email}` : ''}</small></div><Button className="small primary" onClick={() => onChange({ owner, pet: null })}>Chọn</Button></li>)}</ul>}
    </> : <div className="ui-list"><li style={{ listStyle: 'none', padding: '12px 16px', border: '1px solid var(--ui-border)', borderRadius: 12, display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
      <div><strong>{value.owner.fullName}</strong><small style={{ display: 'block' }} className="ui-muted">{value.owner.phoneNumber}</small></div>
      <Button className="small" onClick={() => { setKeyword(''); onChange({ owner: null, pet: null }); }}>Đổi chủ nuôi</Button></li></div>}
    {value.owner && <div style={{ display: 'flex', gap: 10, alignItems: 'flex-end', flexWrap: 'wrap' }}>
      <div style={{ flex: 1, minWidth: 220 }}><Field label="Thú cưng *" error={errors.pet}>
        <Select value={value.pet?.id || ''} onChange={(event) => onChange({ owner: value.owner, pet: petItems.find((pet) => String(pet.id) === event.target.value) || null })}>
          <option value="">{pets.loading ? 'Đang tải…' : petItems.length ? 'Chọn thú cưng' : 'Chưa có thú cưng — hãy thêm mới'}</option>
          {petItems.map((pet) => <option key={pet.id} value={pet.id}>{pet.name} ({pet.speciesName}, {pet.petCode})</option>)}
        </Select></Field></div>
      <Button onClick={() => setDialog('pet')}>+ Thêm thú cưng</Button>
    </div>}
    {dialog === 'customer' && <CustomerFormDialog onClose={() => setDialog(null)} onSaved={(saved) => onChange({ owner: { id: saved.id, fullName: saved.fullName, phoneNumber: saved.phoneNumber, email: saved.email }, pet: null })} />}
    {dialog === 'pet' && <PetFormDialog ownerId={value.owner.id} onClose={() => setDialog(null)} onSaved={(saved) => { pets.refresh(); onChange({ owner: value.owner, pet: saved }); }} />}
  </div>;
}

import { useState } from 'react';
import { Link } from 'react-router-dom';
import CustomerLayout from '../../components/layout/CustomerLayout';
import PetAvatar from '../../components/customer/PetAvatar';
import PetFormDialog from '../../components/shared/PetFormDialog';
import { AsyncView, Button, Notice, PageHead, Pager, SearchBox } from '../../components/ui';
import { petsApi } from '../../api/endpoints';
import useAsync from '../../hooks/useAsync';
import useDebounced from '../../hooks/useDebounced';
import { formatAge } from '../../lib/format';
import { PET_GENDER } from '../../lib/labels';

export default function PetsPage() {
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState(null); // null | 'new' | pet
  const [notice, setNotice] = useState(null);
  const search = useDebounced(keyword);
  const pets = useAsync(() => petsApi.list({ keyword: search, page, size: 12 }), [search, page]);

  return <CustomerLayout>
    <PageHead eyebrow="THÚ CƯNG" title="Thú cưng của tôi" actions={<Button variant="primary" onClick={() => setEditing('new')}>+ Thêm thú cưng</Button>}>
      Lưu hồ sơ từng bé để đặt lịch nhanh và theo dõi toàn bộ lịch sử khám.
    </PageHead>
    <Notice notice={notice} onClose={() => setNotice(null)} />
    <div className="ui-toolbar"><SearchBox value={keyword} onChange={(value) => { setKeyword(value); setPage(0); }} placeholder="Tìm theo tên hoặc mã thú cưng" label="Tìm thú cưng" /></div>
    <AsyncView state={pets} isEmpty={(data) => data.items.length === 0} empty="Bạn chưa có hồ sơ thú cưng nào. Hãy thêm bé đầu tiên để đặt lịch khám.">
      {(data) => <>
        <div className="ui-stack" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))' }}>
          {data.items.map((pet) => <article className="ui-panel" key={pet.id} style={{ marginBottom: 0 }}>
            <div className="ui-panel-body" style={{ display: 'flex', gap: 16, alignItems: 'center' }}>
              <PetAvatar pet={pet} className="pet-card-avatar" />
              <div style={{ minWidth: 0 }}>
                <h2 style={{ margin: '0 0 4px', fontSize: 18 }}><Link className="ui-link" to={`/portal/pets/${pet.id}`}>{pet.name}</Link></h2>
                <p className="ui-muted" style={{ margin: 0, fontSize: 13 }}>{pet.speciesName}{pet.breed ? ` · ${pet.breed}` : ''} · {PET_GENDER[pet.gender] || ''}</p>
                <p className="ui-muted" style={{ margin: '2px 0 8px', fontSize: 12 }}>{formatAge(pet.dateOfBirth)} · Mã {pet.petCode}</p>
                <div className="ui-actions"><Link className="ui-button small" to={`/portal/pets/${pet.id}`}>Hồ sơ</Link><Button className="small" onClick={() => setEditing(pet)}>Sửa</Button><Link className="ui-button small primary" to={`/portal/booking?petId=${pet.id}`}>Đặt lịch</Link></div>
              </div>
            </div>
          </article>)}
        </div>
        <Pager page={data} onChange={setPage} />
      </>}
    </AsyncView>
    {editing && <PetFormDialog pet={editing === 'new' ? null : editing} onClose={() => setEditing(null)} onSaved={(saved) => { setNotice({ type: 'success', text: `Đã lưu hồ sơ của ${saved.name}.` }); pets.refresh(); }} />}
  </CustomerLayout>;
}

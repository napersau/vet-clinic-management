import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import MedicalEntry from './MedicalEntry';
import { AsyncView, Button, Panel, Pager, SearchBox, Table } from '../ui';
import { petsApi } from '../../api/endpoints';
import useAsync from '../../hooks/useAsync';
import useDebounced from '../../hooks/useDebounced';
import { formatAge } from '../../lib/format';

function Timeline({ petId }) {
  const [page, setPage] = useState(0);
  const pet = useAsync(() => petsApi.get(petId), [petId]);
  const history = useAsync(() => petsApi.medicalHistory(petId, { page, size: 5 }), [petId, page]);
  return <>
    <AsyncView state={pet}>{(data) => <p className="ui-muted">Bệnh án của <strong>{data.name}</strong> ({data.speciesName}{data.breed ? `, ${data.breed}` : ''}, {formatAge(data.dateOfBirth)}) · Mã {data.petCode}{data.notes ? ` · Lưu ý: ${data.notes}` : ''}</p>}</AsyncView>
    <AsyncView state={history} isEmpty={(data) => data.items.length === 0} empty="Thú cưng này chưa có đợt khám nào.">
      {(data) => <>{data.items.map((entry) => <MedicalEntry key={entry.visit.id} entry={entry} />)}<Pager page={data} onChange={setPage} /></>}
    </AsyncView>
  </>;
}

/** Tra cứu bệnh án trọn đời (UC-38/39) cho lễ tân, bác sĩ, quản trị: tìm thú cưng theo tên/mã rồi xem timeline. */
export default function HistoryLookup() {
  const [params, setParams] = useSearchParams();
  const petId = params.get('petId');
  const [keyword, setKeyword] = useState('');
  const search = useDebounced(keyword);
  const pets = useAsync(() => petsApi.list({ keyword: search, size: 8 }), [search], { skip: search.trim().length < 2 });
  const found = search.trim().length >= 2 ? pets.data?.items || [] : [];
  return <>
    <Panel title="Tìm thú cưng" subtitle="Nhập tên hoặc mã thú cưng (từ 2 ký tự).">
      <SearchBox value={keyword} onChange={setKeyword} placeholder="Ví dụ: Milo hoặc PET-2026-00001" label="Tìm thú cưng" />
      {pets.loading && <p className="ui-muted">Đang tìm…</p>}
      {found.length > 0 && <div style={{ marginTop: 12 }}><Table caption="Kết quả tìm kiếm" rows={found} columns={[
        { title: 'Thú cưng', render: (row) => <><strong>{row.name}</strong><small>{row.petCode}</small></> },
        { title: 'Loài', render: (row) => `${row.speciesName}${row.breed ? ` · ${row.breed}` : ''}` },
        { title: '', align: 'right', render: (row) => <Button className="small primary" onClick={() => setParams({ petId: row.id })}>Xem bệnh án</Button> }]} /></div>}
      {search.trim().length >= 2 && !pets.loading && found.length === 0 && <p className="ui-muted">Không tìm thấy thú cưng nào.</p>}
    </Panel>
    {petId && <Timeline key={petId} petId={petId} />}
  </>;
}

import { useState } from 'react';
import StaffLayout from '../../components/layout/StaffLayout';
import { AsyncView, Badge, PageHead, Pager, Panel, SearchBox, Table } from '../../components/ui';
import { pharmacyApi } from '../../api/endpoints';
import useAsync from '../../hooks/useAsync';
import useDebounced from '../../hooks/useDebounced';
import { formatMoney } from '../../lib/format';

export default function MedicinesPage() {
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const search = useDebounced(keyword);
  const list = useAsync(() => pharmacyApi.medicines({ keyword: search.trim() || undefined, page, size: 20 }), [search, page]);
  return <StaffLayout area="vet" title="Tra cứu thuốc">
    <PageHead eyebrow="THUỐC" title="Tra cứu thuốc">Xem thuốc đang kinh doanh, giá và tồn kho hiện tại trước khi kê đơn.</PageHead>
    <div className="ui-toolbar"><SearchBox value={keyword} onChange={(value) => { setKeyword(value); setPage(0); }} placeholder="Mã, tên hoặc hoạt chất" label="Tìm thuốc" /></div>
    <Panel flush>
      <AsyncView state={list} isEmpty={(data) => data.items.length === 0} empty="Không tìm thấy thuốc nào.">
        {(data) => <>
          <Table caption="Danh mục thuốc" rows={data.items} columns={[
            { title: 'Thuốc', render: (row) => <><strong>{row.name}</strong><small>{row.code} · {row.activeIngredient || 'Chưa ghi hoạt chất'}</small></> },
            { title: 'Dạng bào chế', render: (row) => `${row.dosageForm} (${row.unit})` },
            { title: 'Giá bán', align: 'right', render: (row) => formatMoney(row.salePrice) },
            { title: 'Tồn kho', align: 'right', render: (row) => <>{row.stockQuantity} {row.lowStock && <Badge tone="amber">Sắp hết</Badge>}</> }]} />
          <Pager page={data} onChange={setPage} />
        </>}
      </AsyncView>
    </Panel>
  </StaffLayout>;
}

import { useState } from 'react';
import StaffLayout from '../../components/layout/StaffLayout';
import Modal from '../../components/common/Modal';
import { AsyncView, Badge, Button, Field, FormDialog, Input, Notice, PageHead, Pager, Panel, SearchBox, Select, Table } from '../../components/ui';
import { usersApi } from '../../api/endpoints';
import { mapFieldErrors } from '../../api/client';
import { temporaryPassword } from '../../api/normalize';
import { ACCOUNT_STATUSES, ROLE_INFO, ROOM_ROLES, STAFF_ROLES } from '../../constants/roles';
import { useAuth } from '../../contexts/AuthContext';
import useAction from '../../hooks/useAction';
import useAsync from '../../hooks/useAsync';
import useDebounced from '../../hooks/useDebounced';
import { blankToNull, formatDateTime } from '../../lib/format';
import { ACCOUNT_STATUS } from '../../lib/labels';

const PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/;

function UserDialog({ user, onClose, onSaved }) {
  const editing = Boolean(user);
  const [form, setForm] = useState({ username: '', password: '', fullName: user?.fullName || '', phoneNumber: user?.phoneNumber || '', email: user?.email || '', address: user?.address || '', roomNumber: user?.roomNumber || '', roleCode: user?.role || 'VETERINARIAN' });
  const [errors, setErrors] = useState({});
  const set = (key) => (event) => setForm({ ...form, [key]: event.target.value });
  const needsRoom = ROOM_ROLES.includes(editing ? user.role : form.roleCode);

  const submit = async () => {
    const found = {};
    if (!form.fullName.trim()) found.fullName = 'Nhập họ tên.';
    if (!/^0\d{9}$/.test(form.phoneNumber.trim())) found.phoneNumber = 'Số điện thoại gồm đúng 10 số, bắt đầu bằng 0.';
    if (!editing && !PASSWORD_RULE.test(form.password)) found.password = 'Mật khẩu 8–72 ký tự, có cả chữ và số.';
    if (needsRoom && !form.roomNumber.trim()) found.roomNumber = 'Nhập số phòng làm việc.';
    setErrors(found);
    if (Object.keys(found).length) throw new Error('Vui lòng kiểm tra lại các trường được đánh dấu.');
    const common = { fullName: form.fullName.trim(), phoneNumber: form.phoneNumber.trim(), email: blankToNull(form.email), address: blankToNull(form.address), roomNumber: needsRoom ? form.roomNumber.trim() : (editing ? '' : null) };
    try {
      const saved = editing ? await usersApi.update(user.id, common) : await usersApi.create({ ...common, username: blankToNull(form.username), password: form.password, roleCode: form.roleCode });
      onSaved(saved);
    } catch (problem) { setErrors(mapFieldErrors(problem)); throw problem; }
  };

  return <FormDialog title={editing ? `Sửa tài khoản ${user.fullName}` : 'Thêm tài khoản nhân sự'} id="user-form-title" onClose={onClose} onSubmit={submit} submitLabel={editing ? 'Lưu thay đổi' : 'Tạo tài khoản'} wide>
    <div className="ui-grid">
      {!editing && <Field label="Vai trò *"><Select value={form.roleCode} onChange={set('roleCode')}>{STAFF_ROLES.map((role) => <option key={role} value={role}>{ROLE_INFO[role].label}</option>)}</Select></Field>}
      {!editing && <Field label="Tên đăng nhập" error={errors.username} hint="Để trống sẽ dùng số điện thoại."><Input value={form.username} onChange={set('username')} maxLength={100} autoComplete="off" /></Field>}
      <Field label="Họ và tên *" error={errors.fullName}><Input value={form.fullName} onChange={set('fullName')} maxLength={150} /></Field>
      <Field label="Số điện thoại *" error={errors.phoneNumber}><Input inputMode="tel" value={form.phoneNumber} onChange={set('phoneNumber')} /></Field>
      <Field label="Email" error={errors.email}><Input type="email" value={form.email} onChange={set('email')} /></Field>
      <Field label="Địa chỉ" error={errors.address}><Input value={form.address} onChange={set('address')} maxLength={255} /></Field>
      {needsRoom && <Field label="Số phòng làm việc *" error={errors.roomNumber} hint="Mỗi phòng chỉ có một người phụ trách."><Input value={form.roomNumber} onChange={set('roomNumber')} /></Field>}
      {!editing && <Field label="Mật khẩu ban đầu *" error={errors.password} hint="Gợi ý: bấm “Tạo ngẫu nhiên” rồi chuyển cho nhân viên."><div style={{ display: 'flex', gap: 8 }}><Input value={form.password} onChange={set('password')} autoComplete="new-password" /><Button onClick={() => setForm({ ...form, password: temporaryPassword() })}>Tạo ngẫu nhiên</Button></div></Field>}
    </div>
  </FormDialog>;
}

function RoleDialog({ user, onClose, onSaved }) {
  const [role, setRole] = useState(user.role);
  return <FormDialog title={`Đổi vai trò — ${user.fullName}`} onClose={onClose} submitLabel="Đổi vai trò"
    onSubmit={async () => { if (role === user.role) throw new Error('Chọn vai trò khác với vai trò hiện tại.'); await usersApi.changeRole(user.id, role); onSaved(`Đã đổi vai trò của ${user.fullName} sang ${ROLE_INFO[role].label}.`); }}>
    <p className="ui-confirm-text">Mọi phiên đăng nhập của tài khoản này bị đăng xuất để áp dụng vai trò mới. Số phòng bị xóa nếu vai trò mới không có phòng cố định.</p>
    <Field label="Vai trò mới"><Select value={role} onChange={(event) => setRole(event.target.value)}>{STAFF_ROLES.map((item) => <option key={item} value={item}>{ROLE_INFO[item].label}</option>)}</Select></Field>
  </FormDialog>;
}

function StatusDialog({ user, status, onClose, onSaved }) {
  const label = ACCOUNT_STATUSES[status].label;
  return <FormDialog title={`Chuyển sang “${label}”`} onClose={onClose} danger={status !== 'ACTIVE'} submitLabel="Xác nhận"
    onSubmit={async () => { await usersApi.changeStatus(user.id, status); onSaved(`Đã chuyển tài khoản ${user.fullName} sang “${label}”.`); }}>
    <p className="ui-confirm-text">{status === 'ACTIVE' ? `Tài khoản ${user.fullName} sẽ đăng nhập lại được.` : `Tài khoản ${user.fullName} sẽ không đăng nhập được và mọi phiên hiện tại bị thu hồi. Bác sĩ bị khóa làm giảm sức chứa đặt lịch ngay lập tức.`}</p>
  </FormDialog>;
}

function ResetDialog({ user, onClose }) {
  const [password] = useState(temporaryPassword);
  const [done, setDone] = useState(false);
  const action = useAction();
  return <Modal title={`Đặt lại mật khẩu — ${user.fullName}`} id="admin-reset-title" onClose={onClose} busy={action.busy}>
    <div className="ui-form-body">
      {done ? <div className="ui-alert success"><span>Đã đặt mật khẩu tạm. Mọi phiên đăng nhập cũ đã bị đăng xuất.</span></div> : <p className="ui-confirm-text">Đặt mật khẩu tạm cho tài khoản này và chuyển cho người dùng; họ nên đổi lại ngay sau khi đăng nhập.</p>}
      <Notice notice={action.notice} />
      <Field label="Mật khẩu tạm"><Input readOnly value={password} className="ui-mono" data-testid="temporary-password" onFocus={(event) => event.target.select()} /></Field>
    </div>
    <div className="ui-form-actions"><Button onClick={onClose}>{done ? 'Đóng' : 'Hủy'}</Button>{!done && <Button variant="primary" busy={action.busy} onClick={async () => { if (await action.run(() => usersApi.resetPassword(user.id, password))) setDone(true); }}>Đặt mật khẩu tạm</Button>}</div>
  </Modal>;
}

export default function UsersPage() {
  const { user: me } = useAuth();
  const [role, setRole] = useState('');
  const [status, setStatus] = useState('');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [dialog, setDialog] = useState(null);
  const [notice, setNotice] = useState(null);
  const search = useDebounced(keyword);
  const list = useAsync(() => usersApi.list({ role, status, keyword: search.trim() || undefined, page, size: 15 }), [role, status, search, page]);
  const done = (text) => { setDialog(null); setNotice({ type: 'success', text }); list.refresh(); };
  const reset = (setter) => (event) => { setter(event.target.value); setPage(0); };

  return <StaffLayout area="admin" title="Tài khoản & nhân sự">
    <PageHead eyebrow="QUẢN TRỊ" title="Tài khoản & nhân sự" actions={<Button variant="primary" onClick={() => setDialog({ type: 'create' })}>+ Thêm nhân sự</Button>}>
      Tạo tài khoản bác sĩ, lễ tân, kỹ thuật viên; gán số phòng, đổi vai trò, khóa/mở khóa và đặt lại mật khẩu. Chủ nuôi tự đăng ký hoặc do lễ tân tạo.
    </PageHead>
    <Notice notice={notice} onClose={() => setNotice(null)} />
    <div className="ui-toolbar">
      <SearchBox value={keyword} onChange={(value) => { setKeyword(value); setPage(0); }} placeholder="Tên, số điện thoại hoặc tên đăng nhập" label="Tìm tài khoản" />
      <Select aria-label="Vai trò" value={role} onChange={reset(setRole)}><option value="">Mọi vai trò</option>{Object.entries(ROLE_INFO).map(([key, value]) => <option key={key} value={key}>{value.label}</option>)}</Select>
      <Select aria-label="Trạng thái" value={status} onChange={reset(setStatus)}><option value="">Mọi trạng thái</option>{Object.entries(ACCOUNT_STATUSES).map(([key, value]) => <option key={key} value={key}>{value.label}</option>)}</Select>
    </div>
    <Panel flush>
      <AsyncView state={list} isEmpty={(data) => data.items.length === 0} empty="Không có tài khoản phù hợp.">
        {(data) => <>
          <Table caption="Tài khoản" rows={data.items} columns={[
            { title: 'Người dùng', render: (row) => <><strong>{row.fullName}</strong><small>{row.username}</small></> },
            { title: 'Liên hệ', render: (row) => <>{row.phoneNumber}<small>{row.email || '—'}</small></> },
            { title: 'Vai trò', render: (row) => <>{ROLE_INFO[row.role]?.label}{row.roomNumber && <small>Phòng {row.roomNumber}</small>}</> },
            { title: 'Tạo lúc', render: (row) => formatDateTime(row.createdAt) },
            { title: 'Trạng thái', render: (row) => <Badge map={ACCOUNT_STATUS} value={row.status} /> },
            { title: '', align: 'right', render: (row) => {
              const staff = STAFF_ROLES.includes(row.role); const self = String(row.id) === String(me.id);
              return <div className="ui-actions" style={{ justifyContent: 'flex-end' }}>
                {staff && <Button className="small" onClick={() => setDialog({ type: 'edit', user: row })}>Sửa</Button>}
                {staff && !self && <Button className="small" onClick={() => setDialog({ type: 'role', user: row })}>Đổi vai trò</Button>}
                {!self && (row.status === 'ACTIVE'
                  ? <Button className="small danger" onClick={() => setDialog({ type: 'status', user: row, status: 'LOCKED' })}>Khóa</Button>
                  : <Button className="small" onClick={() => setDialog({ type: 'status', user: row, status: 'ACTIVE' })}>Mở khóa</Button>)}
                {!self && <Button className="small" onClick={() => setDialog({ type: 'reset', user: row })}>Đặt lại MK</Button>}
              </div>; } }]} />
          <Pager page={data} onChange={setPage} />
        </>}
      </AsyncView>
    </Panel>
    {dialog?.type === 'create' && <UserDialog onClose={() => setDialog(null)} onSaved={(saved) => done(`Đã tạo tài khoản ${saved.fullName}.`)} />}
    {dialog?.type === 'edit' && <UserDialog user={dialog.user} onClose={() => setDialog(null)} onSaved={(saved) => done(`Đã cập nhật ${saved.fullName}.`)} />}
    {dialog?.type === 'role' && <RoleDialog user={dialog.user} onClose={() => setDialog(null)} onSaved={done} />}
    {dialog?.type === 'status' && <StatusDialog user={dialog.user} status={dialog.status} onClose={() => setDialog(null)} onSaved={done} />}
    {dialog?.type === 'reset' && <ResetDialog user={dialog.user} onClose={() => setDialog(null)} />}
  </StaffLayout>;
}

import { useState } from 'react';
import CustomerLayout from '../../components/layout/CustomerLayout';
import { PageHead, Panel, Field, Input, Button, Notice } from '../../components/ui';
import { authApi } from '../../api/endpoints';
import { mapFieldErrors } from '../../api/client';
import { useAuth } from '../../contexts/AuthContext';
import useAction from '../../hooks/useAction';
import { ROLE_LABELS } from '../../constants/roles';
import { blankToNull } from '../../lib/format';

const PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/;

function ProfileForm() {
  const { user, updateProfile } = useAuth();
  const [form, setForm] = useState({ fullName: user.fullName || '', phoneNumber: user.phoneNumber || '', email: user.email || '', address: user.address || '' });
  const [errors, setErrors] = useState({});
  const action = useAction();
  const set = (key) => (event) => setForm({ ...form, [key]: event.target.value });
  const submit = async (event) => {
    event.preventDefault();
    const found = {};
    if (!form.fullName.trim()) found.fullName = 'Nhập họ tên.';
    if (!/^0\d{9}$/.test(form.phoneNumber.trim())) found.phoneNumber = 'Số điện thoại gồm đúng 10 số, bắt đầu bằng 0.';
    setErrors(found);
    if (Object.keys(found).length) return;
    const saved = await action.run(() => updateProfile({ fullName: form.fullName.trim(), phoneNumber: form.phoneNumber.trim(), email: blankToNull(form.email), address: blankToNull(form.address) }).catch((problem) => { setErrors(mapFieldErrors(problem)); throw problem; }), 'Đã cập nhật thông tin cá nhân.');
    if (saved) setErrors({});
  };
  return <Panel title="Thông tin cá nhân" subtitle={`Vai trò: ${ROLE_LABELS[user.role] || user.role}${user.roomNumber ? ` · Phòng ${user.roomNumber}` : ''}`}>
    <form onSubmit={submit} noValidate className="ui-stack" style={{ gap: 16 }}>
      <Notice notice={action.notice} onClose={action.clear} />
      <div className="ui-grid">
        <Field label="Họ và tên *" error={errors.fullName}><Input value={form.fullName} onChange={set('fullName')} maxLength={150} /></Field>
        <Field label="Số điện thoại *" error={errors.phoneNumber} hint={user.role === 'PET_OWNER' ? 'Đổi số điện thoại không đổi tên đăng nhập hiện tại.' : undefined}><Input inputMode="tel" value={form.phoneNumber} onChange={set('phoneNumber')} /></Field>
        <Field label="Email" error={errors.email} hint="Dùng để nhận thư xác nhận lịch hẹn và thanh toán."><Input type="email" value={form.email} onChange={set('email')} /></Field>
        <Field label="Địa chỉ" error={errors.address}><Input value={form.address} onChange={set('address')} maxLength={255} /></Field>
      </div>
      <div><Button type="submit" variant="primary" busy={action.busy}>Lưu thay đổi</Button></div>
    </form>
  </Panel>;
}

function PasswordForm() {
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirm: '' });
  const [errors, setErrors] = useState({});
  const action = useAction();
  const set = (key) => (event) => setForm({ ...form, [key]: event.target.value });
  const submit = async (event) => {
    event.preventDefault();
    const found = {};
    if (!form.currentPassword) found.currentPassword = 'Nhập mật khẩu hiện tại.';
    if (!PASSWORD_RULE.test(form.newPassword)) found.newPassword = 'Mật khẩu mới 8–72 ký tự, có cả chữ và số.';
    if (form.confirm !== form.newPassword) found.confirm = 'Mật khẩu nhập lại chưa khớp.';
    setErrors(found);
    if (Object.keys(found).length) return;
    const done = await action.run(() => authApi.changePassword({ currentPassword: form.currentPassword, newPassword: form.newPassword }), 'Đã đổi mật khẩu. Các phiên đăng nhập khác sẽ phải đăng nhập lại.');
    if (done) setForm({ currentPassword: '', newPassword: '', confirm: '' });
  };
  return <Panel title="Đổi mật khẩu" subtitle="Sau khi đổi, mọi phiên đăng nhập khác bị đăng xuất.">
    <form onSubmit={submit} noValidate className="ui-stack" style={{ gap: 16 }}>
      <Notice notice={action.notice} onClose={action.clear} />
      <div className="ui-grid">
        <Field label="Mật khẩu hiện tại" error={errors.currentPassword} wide><Input type="password" autoComplete="current-password" value={form.currentPassword} onChange={set('currentPassword')} /></Field>
        <Field label="Mật khẩu mới" error={errors.newPassword}><Input type="password" autoComplete="new-password" value={form.newPassword} onChange={set('newPassword')} /></Field>
        <Field label="Nhập lại mật khẩu mới" error={errors.confirm}><Input type="password" autoComplete="new-password" value={form.confirm} onChange={set('confirm')} /></Field>
      </div>
      <div><Button type="submit" variant="primary" busy={action.busy}>Đổi mật khẩu</Button></div>
    </form>
  </Panel>;
}

export default function ProfilePage() {
  return <CustomerLayout>
    <PageHead eyebrow="TÀI KHOẢN" title="Thông tin cá nhân">Cập nhật thông tin liên hệ để phòng khám gửi nhắc lịch và xác nhận đúng nơi.</PageHead>
    <ProfileForm />
    <PasswordForm />
  </CustomerLayout>;
}

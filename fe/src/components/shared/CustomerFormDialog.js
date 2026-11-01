import { useRef, useState } from 'react';
import { usersApi } from '../../api/endpoints';
import { mapFieldErrors } from '../../api/client';
import { temporaryPassword } from '../../api/normalize';
import { Button, Field, FormDialog, Input } from '../ui';
import Modal from '../common/Modal';
import { blankToNull } from '../../lib/format';

/**
 * Lễ tân tạo tài khoản chủ nuôi (UC-10). Hệ thống cấp mật khẩu tạm, hiển thị MỘT lần để đưa cho khách;
 * tên đăng nhập mặc định là số điện thoại. Có `customer` thì là sửa thông tin liên hệ.
 */
export default function CustomerFormDialog({ customer, onClose, onSaved }) {
  const [form, setForm] = useState({ fullName: customer?.fullName || '', phoneNumber: customer?.phoneNumber || '', email: customer?.email || '', address: customer?.address || '' });
  const [errors, setErrors] = useState({});
  const [issued, setIssued] = useState(null);
  const justIssued = useRef(false);
  const set = (key) => (event) => setForm({ ...form, [key]: event.target.value });

  const submit = async () => {
    const found = {};
    if (!form.fullName.trim()) found.fullName = 'Nhập họ tên khách hàng.';
    if (!/^0\d{9}$/.test(form.phoneNumber.trim())) found.phoneNumber = 'Số điện thoại gồm đúng 10 số, bắt đầu bằng 0.';
    setErrors(found);
    if (Object.keys(found).length) throw new Error('Vui lòng kiểm tra lại các trường được đánh dấu.');
    const body = { fullName: form.fullName.trim(), phoneNumber: form.phoneNumber.trim(), email: blankToNull(form.email), address: blankToNull(form.address) };
    try {
      if (customer) {
        const saved = await usersApi.update(customer.id, body);
        onSaved(saved);
      } else {
        const password = temporaryPassword();
        const saved = await usersApi.create({ ...body, password, roleCode: 'PET_OWNER' });
        justIssued.current = true;
        setIssued({ user: saved, password });
        onSaved(saved, { keepOpen: true });
      }
    } catch (problem) { setErrors(mapFieldErrors(problem)); throw problem; }
  };

  // FormDialog tự đóng hộp thoại sau khi lưu thành công; riêng lần tạo mới thì phải giữ lại để hiện mật khẩu tạm (chỉ có một lần).
  const closeForm = () => { if (!justIssued.current) onClose(); };

  if (issued) return <Modal title="Đã tạo tài khoản khách hàng" id="customer-issued-title" onClose={onClose}>
    <div className="ui-form-body">
      <p className="ui-confirm-text">Tài khoản của <strong>{issued.user.fullName}</strong> đã sẵn sàng. Mật khẩu tạm chỉ hiện một lần — hãy đưa cho khách và nhắc đổi mật khẩu sau khi đăng nhập.</p>
      <div className="ui-facts"><div><dt>Tên đăng nhập</dt><dd className="ui-mono">{issued.user.username}</dd></div><div><dt>Mật khẩu tạm</dt><dd className="ui-mono" data-testid="temporary-password">{issued.password}</dd></div></div>
    </div>
    <div className="ui-form-actions"><Button variant="primary" onClick={onClose}>Đã ghi lại</Button></div>
  </Modal>;

  return <FormDialog title={customer ? 'Sửa thông tin khách hàng' : 'Thêm khách hàng mới'} id="customer-form-title" onClose={closeForm} onSubmit={submit} submitLabel={customer ? 'Lưu thay đổi' : 'Tạo tài khoản'}>
    <div className="ui-grid">
      <Field label="Họ và tên *" error={errors.fullName}><Input value={form.fullName} onChange={set('fullName')} maxLength={150} autoFocus /></Field>
      <Field label="Số điện thoại *" error={errors.phoneNumber} hint={customer ? undefined : 'Dùng làm tên đăng nhập.'}><Input inputMode="tel" value={form.phoneNumber} onChange={set('phoneNumber')} /></Field>
      <Field label="Email" error={errors.email}><Input type="email" value={form.email} onChange={set('email')} /></Field>
      <Field label="Địa chỉ" error={errors.address}><Input value={form.address} onChange={set('address')} maxLength={255} /></Field>
    </div>
  </FormDialog>;
}

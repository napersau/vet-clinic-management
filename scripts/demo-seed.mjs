// Tạo tài khoản và dữ liệu mẫu cho buổi demo, đi qua API Gateway. Chạy lại nhiều lần vẫn an toàn (bỏ qua cái đã có).
// Cách chạy:  node scripts/demo-seed.mjs [địa chỉ Gateway, mặc định http://localhost:8080]
// Cần: hệ thống đã chạy (docker compose up) và tài khoản admin / Admin@123 còn nguyên.
const GATEWAY = process.argv[2] || 'http://localhost:8080';
const API = `${GATEWAY}/api/v1`;
const PASSWORD = 'Demo@1234';

async function call(path, { method = 'GET', token, body } = {}) {
  const response = await fetch(`${API}${path}`, {
    method,
    headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  let json; try { json = JSON.parse(text); } catch { json = { raw: text }; }
  return { status: response.status, ...json };
}

const login = async (username, password) => (await call('/auth/login', { method: 'POST', body: { username, password } })).data?.accessToken;

const admin = await login('admin', 'Admin@123');
if (!admin) { console.error('Không đăng nhập được bằng admin / Admin@123. Hệ thống đã chạy chưa, hoặc đã đổi mật khẩu admin?'); process.exit(1); }

const STAFF = [
  { fullName: 'Lễ tân Hoa', phoneNumber: '0911000001', roleCode: 'RECEPTIONIST' },
  { fullName: 'BS. Nguyễn An', phoneNumber: '0912000001', roleCode: 'VETERINARIAN', roomNumber: 'P101' },
  { fullName: 'BS. Trần Bình', phoneNumber: '0912000002', roleCode: 'VETERINARIAN', roomNumber: 'P102' },
  { fullName: 'BS. Lê Chi', phoneNumber: '0912000003', roleCode: 'VETERINARIAN', roomNumber: 'P103' },
  { fullName: 'KTV Xét nghiệm Dũng', phoneNumber: '0913000001', roleCode: 'LAB_TECHNICIAN', roomNumber: 'XN01' },
  { fullName: 'KTV Hình ảnh Em', phoneNumber: '0915000001', roleCode: 'IMAGING_TECHNICIAN', roomNumber: 'CDHA01' },
];
const OWNERS = [
  { fullName: 'Nguyễn Văn Khách', phoneNumber: '0981000001' },
  { fullName: 'Trần Thị Mai', phoneNumber: '0982000002' },
];
const PETS = { '0981000001': [{ name: 'Lu', speciesId: 1, breed: 'Poodle' }, { name: 'Mimi', speciesId: 2, breed: 'Mèo Anh lông ngắn' }], '0982000002': [{ name: 'Bông', speciesId: 1, breed: 'Corgi' }] };

const report = [];
const note = (label, result, detail = '') => { report.push(`${result} ${label}${detail ? ` (${detail})` : ''}`); };

for (const staff of STAFF) {
  const created = await call('/auth/users', { method: 'POST', token: admin, body: { password: PASSWORD, ...staff } });
  note(`${staff.roleCode}: ${staff.fullName} / ${staff.phoneNumber}`, created.status === 201 ? '[tạo mới]' : created.errorCode === 'AUTH_1102' ? '[đã có]' : '[LỖI]', created.status === 201 || created.errorCode === 'AUTH_1102' ? '' : `${created.status} ${created.message || ''}`);
}
for (const owner of OWNERS) {
  const created = await call('/auth/register', { method: 'POST', body: { ...owner, password: PASSWORD } });
  note(`PET_OWNER: ${owner.fullName} / ${owner.phoneNumber}`, created.status === 201 ? '[tạo mới]' : created.errorCode === 'AUTH_1102' ? '[đã có]' : '[LỖI]', created.status === 201 || created.errorCode === 'AUTH_1102' ? '' : `${created.status} ${created.message || ''}`);
}

const ownerTokens = {};
for (const owner of OWNERS) ownerTokens[owner.phoneNumber] = await login(owner.phoneNumber, PASSWORD);
for (const [phone, pets] of Object.entries(PETS)) {
  const token = ownerTokens[phone];
  if (!token) { note(`Thú cưng của ${phone}`, '[LỖI]', 'không đăng nhập được chủ nuôi'); continue; }
  const existing = (await call('/pets?size=100', { token })).data?.items?.map((pet) => pet.name) || [];
  for (const pet of pets) {
    if (existing.includes(pet.name)) { note(`Thú cưng ${pet.name} (chủ ${phone})`, '[đã có]'); continue; }
    const created = await call('/pets', { method: 'POST', token, body: { ...pet, gender: 'UNKNOWN', weight: 8 } });
    note(`Thú cưng ${pet.name} (chủ ${phone})`, created.status === 201 ? '[tạo mới]' : '[LỖI]', created.status === 201 ? '' : `${created.status} ${created.message || ''}`);
  }
}

// Lịch hẹn mẫu hôm nay (lễ tân đặt hộ, xác nhận ngay) ở khung giờ còn trống đầu tiên. Buổi tối khung giờ đã qua hết nên sẽ bỏ qua.
const receptionist = await login('0911000001', PASSWORD);
const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date());
const slot = (await call(`/appointments/slots?date=${today}`, { token: receptionist })).data?.find((item) => item.available && !item.past);
const lu = (await call('/pets?size=100', { token: ownerTokens['0981000001'] })).data?.items?.find((pet) => pet.name === 'Lu');
if (slot && lu) {
  const already = (await call(`/appointments?petId=${lu.id}&fromDate=${today}&toDate=${today}`, { token: receptionist })).data?.items?.length;
  if (already) note('Lịch hẹn hôm nay của Lu', '[đã có]');
  else {
    const booked = await call('/appointments', { method: 'POST', token: receptionist, body: { petId: lu.id, ownerId: lu.ownerId, appointmentDate: today, startTime: slot.startTime, reason: 'Khám định kỳ, biếng ăn', bookingChannel: 'PHONE' } });
    note(`Lịch hẹn hôm nay ${slot.startTime} cho Lu`, booked.status === 201 ? '[tạo mới]' : '[LỖI]', booked.status === 201 ? '' : `${booked.status} ${booked.message || ''}`);
  }
} else note('Lịch hẹn hôm nay', '[bỏ qua]', 'không còn khung giờ trống trong ngày; demo bằng khách vãng lai');

console.log(`\n${report.join('\n')}\n`);
console.log(`Mật khẩu chung của các tài khoản demo: ${PASSWORD}`);
console.log('Đăng nhập bằng SỐ ĐIỆN THOẠI. Quản trị: admin / Admin@123');
process.exit(report.some((line) => line.startsWith('[LỖI]')) ? 1 : 0);

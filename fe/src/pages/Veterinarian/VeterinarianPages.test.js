import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { bodyOf, callsTo, failure, fileReply, mockApi, pageOf, renderAt, signedInAs, success } from '../../testUtils';

const ME = () => success({ id: 3, fullName: 'BS. Phạm An', phoneNumber: '0910000002', role: 'VETERINARIAN' });

// Mỗi test dùng id ca khác nhau vì hộp thoại ưu tiên nhớ ca đã "Xem sau" trong phiên (biến cấp module).
const mine = (over = {}) => ({ id: 41, visitCode: 'VIS-2026-00041', petId: 21, petName: 'Kitty', speciesName: 'Mèo', status: 'WAITING', emergency: false, checkedInAt: '2026-11-02T02:00:00Z', initialSymptoms: 'Bỏ ăn', callbackPostponed: false, ...over });
const visitDetail = (over = {}) => ({ ...mine(), doctorId: 3, assignedRoom: '1', services: [], labResults: [], imagingResults: [], diagnosis: null, ...over });

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
  window.scrollTo = jest.fn();
  signedInAs('veterinarian');
  URL.createObjectURL = jest.fn(() => 'blob:mock-image');
  URL.revokeObjectURL = jest.fn();
});
afterEach(() => { delete global.fetch; });

describe('danh sách ca của bác sĩ', () => {
  test('ca đã có kết quả nằm đầu; mời vào khám gọi call-back, ca đã tạm hoãn không còn nút tạm hoãn', async () => {
    mockApi({
      'GET /api/v1/auth/me': ME,
      'GET /api/v1/clinical/doctors/me/visits': () => success([mine({ id: 51, visitCode: 'VIS-2026-00051', petName: 'Rex', status: 'RESULTS_READY' }), mine({ id: 52, visitCode: 'VIS-2026-00052', petName: 'Bông', status: 'RESULTS_READY', callbackPostponed: true }), mine({ id: 53, visitCode: 'VIS-2026-00053', petName: 'Lu', status: 'WAITING' })]),
      'POST /api/v1/clinical/pet-visits/51/call-back': () => success({}),
      'POST /api/v1/clinical/pet-visits/52/postpone-callback': () => success({}),
    });
    renderAt('/vet/today');

    // Hộp thoại ưu tiên hiện cho ca đầu tiên chưa tạm hoãn; đóng lại để thao tác trên danh sách.
    userEvent.click(await screen.findByRole('button', { name: 'Xem sau' }));
    const readySection = (await screen.findByText('Ca đã có kết quả — mời vào khám tiếp')).closest('section');
    const rows = within(readySection).getAllByRole('listitem');
    expect(within(rows[0]).getByText('Rex')).toBeInTheDocument();
    expect(within(rows[0]).getByRole('button', { name: 'Tạm hoãn' })).toBeInTheDocument();
    expect(within(rows[1]).getByText('Bông')).toBeInTheDocument();
    expect(within(rows[1]).queryByRole('button', { name: 'Tạm hoãn' })).not.toBeInTheDocument();

    userEvent.click(within(rows[0]).getByRole('button', { name: 'Mời vào khám' }));
    expect(await screen.findByText('Đã mời Rex vào khám tiếp.')).toBeInTheDocument();
    expect(callsTo('POST /api/v1/clinical/pet-visits/51/call-back')).toHaveLength(1);
  });
});

describe('hộp thoại ưu tiên ca tái khám', () => {
  const api = (visits, extra = {}) => mockApi({
    'GET /api/v1/auth/me': ME,
    'GET /api/v1/clinical/doctors/me/visits': () => success(visits),
    'GET /api/v1/clinical/pet-visits/61': () => success(visitDetail({ id: 61, status: 'IN_PROGRESS' })),
    ...extra,
  });

  test('hiện trên mọi trang của bác sĩ khi có ca RESULTS_READY chưa tạm hoãn', async () => {
    api([mine({ id: 61, petName: 'Mực', status: 'RESULTS_READY' })]);
    renderAt('/vet/medicines');

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(/Mực/)).toBeInTheDocument();
    expect(within(dialog).getByText(/khóa ưu tiên tái khám/)).toBeInTheDocument();
  });

  test('"Mời vào khám tiếp" gọi call-back rồi mở hồ sơ ca đó', async () => {
    api([mine({ id: 61, petName: 'Mực', status: 'RESULTS_READY' })], { 'POST /api/v1/clinical/pet-visits/61/call-back': () => success({}) });
    renderAt('/vet/medicines');

    userEvent.click(await screen.findByRole('button', { name: 'Mời vào khám tiếp' }));

    await waitFor(() => expect(callsTo('POST /api/v1/clinical/pet-visits/61/call-back')).toHaveLength(1));
    expect(await screen.findByRole('heading', { level: 1, name: /Mực|Kitty/ })).toBeInTheDocument();
  });

  test('lỗi từ máy chủ (bác sĩ đang bận ca khác) hiện ngay trong hộp thoại và giữ hộp thoại', async () => {
    api([mine({ id: 62, petName: 'Mực', status: 'RESULTS_READY' })], { 'POST /api/v1/clinical/pet-visits/62/call-back': () => failure(409, 'CLIN_1302', 'Bác sĩ đang bận') });
    renderAt('/vet/medicines');

    userEvent.click(await screen.findByRole('button', { name: 'Mời vào khám tiếp' }));

    expect(await within(screen.getByRole('dialog')).findByText('Bác sĩ đang bận')).toBeInTheDocument();
  });

  test('"Tạm hoãn" gọi postpone-callback', async () => {
    api([mine({ id: 63, petName: 'Mực', status: 'RESULTS_READY' })], { 'POST /api/v1/clinical/pet-visits/63/postpone-callback': () => success({}) });
    renderAt('/vet/medicines');

    userEvent.click(await screen.findByRole('button', { name: 'Tạm hoãn' }));

    await waitFor(() => expect(callsTo('POST /api/v1/clinical/pet-visits/63/postpone-callback')).toHaveLength(1));
  });

  test('không hiện khi ca đã tạm hoãn, và không hiện cho vai trò khác', async () => {
    api([mine({ id: 64, status: 'RESULTS_READY', callbackPostponed: true })]);
    renderAt('/vet/medicines');
    await screen.findByRole('heading', { level: 1, name: 'Tra cứu thuốc' });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  test('"Xem sau" ẩn hộp thoại và không hiện lại khi chuyển trang trong cùng phiên', async () => {
    api([mine({ id: 65, petName: 'Mực', status: 'RESULTS_READY' })]);
    renderAt('/vet/medicines');

    userEvent.click(await screen.findByRole('button', { name: 'Xem sau' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(callsTo('POST /api/v1/clinical/pet-visits/65/call-back')).toHaveLength(0);
  });
});

describe('khám giai đoạn 1: bắt đầu khám', () => {
  const api = (extra = {}) => mockApi({
    'GET /api/v1/auth/me': ME,
    'GET /api/v1/clinical/doctors/me/visits': () => success([]),
    'GET /api/v1/clinical/pet-visits/41': () => success(visitDetail({ status: 'WAITING' })),
    ...extra,
  });

  test('chặn cân nặng và thân nhiệt ngoài giới hạn của backend (0,1–150 kg, 35–43 °C)', async () => {
    api({ 'POST /api/v1/clinical/pet-visits/41/start-exam': () => success(visitDetail({ status: 'IN_PROGRESS' })) });
    renderAt('/vet/visits/41');
    await screen.findByText('Bắt đầu khám (giai đoạn 1)');

    userEvent.type(screen.getByLabelText(/Cân nặng/), '200');
    userEvent.type(screen.getByLabelText(/Thân nhiệt/), '44');
    userEvent.click(screen.getByRole('button', { name: 'Bắt đầu khám' }));

    expect(await screen.findByText('Cân nặng hợp lệ từ 0,1 đến 150 kg.')).toBeInTheDocument();
    expect(screen.getByText('Thân nhiệt hợp lệ từ 35 đến 43 °C.')).toBeInTheDocument();
    expect(callsTo('POST /api/v1/clinical/pet-visits/41/start-exam')).toHaveLength(0);
  });

  test('bắt đầu khám gửi cân nặng, thân nhiệt dạng số và ghi chú', async () => {
    api({ 'POST /api/v1/clinical/pet-visits/41/start-exam': () => success(visitDetail({ status: 'IN_PROGRESS' })) });
    renderAt('/vet/visits/41');
    await screen.findByText('Bắt đầu khám (giai đoạn 1)');

    userEvent.type(screen.getByLabelText(/Cân nặng/), '4.2');
    userEvent.type(screen.getByLabelText(/Thân nhiệt/), '39.1');
    userEvent.type(screen.getByLabelText(/Triệu chứng lâm sàng/), 'Mệt mỏi');
    userEvent.click(screen.getByRole('button', { name: 'Bắt đầu khám' }));

    await waitFor(() => expect(callsTo('POST /api/v1/clinical/pet-visits/41/start-exam')).toHaveLength(1));
    expect(bodyOf(callsTo('POST /api/v1/clinical/pet-visits/41/start-exam')[0])).toEqual({ weightAtVisit: 4.2, temperature: 39.1, clinicalNotes: 'Mệt mỏi' });
  });

  test('ca của bác sĩ khác chỉ được xem, không có biểu mẫu bắt đầu khám', async () => {
    api({ 'GET /api/v1/clinical/pet-visits/41': () => success(visitDetail({ status: 'WAITING', doctorId: 99 })) });
    renderAt('/vet/visits/41');

    expect(await screen.findByText(/phân công cho bác sĩ khác/)).toBeInTheDocument();
    expect(screen.queryByText('Bắt đầu khám (giai đoạn 1)')).not.toBeInTheDocument();
  });
});

describe('chỉ định, đơn thuốc và chốt ca', () => {
  const MEDICINES = pageOf([{ id: 8, code: 'AMOX', name: 'Amoxicillin', unit: 'viên', stockQuantity: 100, salePrice: 5000 }]);
  const SERVICES = [
    { id: 1, code: 'EXAM_GEN', name: 'Khám tổng quát', category: 'EXAMINATION', price: 100000 },
    { id: 2, code: 'LAB_CBC', name: 'Xét nghiệm máu', category: 'LABORATORY', price: 250000 },
    { id: 3, code: 'VAC', name: 'Tiêm phòng', category: 'VACCINATION', price: 180000 },
  ];
  const api = (extra = {}) => mockApi({
    'GET /api/v1/auth/me': ME,
    'GET /api/v1/clinical/doctors/me/visits': () => success([]),
    'GET /api/v1/clinical/pet-visits/41': () => success(visitDetail({ status: 'IN_PROGRESS' })),
    'GET /api/v1/clinical/services': () => success(SERVICES),
    'GET /api/v1/pharmacy/medicines': () => MEDICINES && success(MEDICINES),
    'GET /api/v1/pharmacy/prescriptions/by-visit/41': () => failure(404, 'PHARM_1404', 'Không có đơn thuốc'),
    ...extra,
  });

  test('chỉ định xét nghiệm gửi danh sách dịch vụ rồi trở về danh sách ca (bác sĩ được giải phóng)', async () => {
    api({ 'POST /api/v1/clinical/pet-visits/41/services': () => success(visitDetail({ status: 'WAITING_RESULTS' })) });
    renderAt('/vet/visits/41');

    userEvent.click(await screen.findByRole('checkbox', { name: /Xét nghiệm máu/ }));
    expect(screen.queryByLabelText(/Khám tổng quát/)).not.toBeInTheDocument(); // EXAM_GEN do hệ thống tự thêm
    expect(screen.getByRole('button', { name: 'Chỉ định và chuyển sang chờ kết quả' })).toBeEnabled();
    userEvent.click(screen.getByRole('button', { name: 'Chỉ định và chuyển sang chờ kết quả' }));

    await waitFor(() => expect(callsTo('POST /api/v1/clinical/pet-visits/41/services')).toHaveLength(1));
    expect(bodyOf(callsTo('POST /api/v1/clinical/pet-visits/41/services')[0])).toEqual({ services: [{ serviceId: 2, quantity: 1, notes: null }] });
    expect(await screen.findByRole('heading', { level: 1, name: 'Ca khám của tôi' })).toBeInTheDocument();
  });

  test('đơn thuốc bắt buộc có liều dùng và tần suất, lưu gửi kèm visitId', async () => {
    api({ 'POST /api/v1/pharmacy/prescriptions': () => success({ id: 1, visitId: 41, status: 'PRESCRIBED', items: [] }, 201) });
    renderAt('/vet/visits/41');
    userEvent.click(await screen.findByRole('tab', { name: 'Đơn thuốc' }));
    await screen.findByRole('option', { name: /Amoxicillin/ });

    userEvent.selectOptions(screen.getByLabelText('Thuốc'), '8');
    userEvent.click(screen.getByRole('button', { name: 'Lưu đơn thuốc' }));
    expect(await screen.findByText('Mỗi thuốc cần có liều dùng và tần suất.')).toBeInTheDocument();
    expect(callsTo('POST /api/v1/pharmacy/prescriptions')).toHaveLength(0);

    userEvent.type(screen.getByLabelText('Liều dùng *'), '1 viên/lần');
    userEvent.type(screen.getByLabelText('Tần suất *'), '2 lần/ngày');
    userEvent.click(screen.getByRole('button', { name: 'Lưu đơn thuốc' }));

    await waitFor(() => expect(callsTo('POST /api/v1/pharmacy/prescriptions')).toHaveLength(1));
    expect(bodyOf(callsTo('POST /api/v1/pharmacy/prescriptions')[0])).toEqual({
      visitId: 41, doctorNotes: null, items: [{ medicineId: 8, quantity: 1, dosage: '1 viên/lần', frequency: '2 lần/ngày', durationDays: 3, instructions: null }],
    });
  });

  test('kê vượt tồn kho bị từ chối và hiện lỗi của máy chủ', async () => {
    api({ 'POST /api/v1/pharmacy/prescriptions': () => failure(409, 'PHARM_1406', 'Thuốc Amoxicillin không đủ tồn kho') });
    renderAt('/vet/visits/41');
    userEvent.click(await screen.findByRole('tab', { name: 'Đơn thuốc' }));
    await screen.findByRole('option', { name: /Amoxicillin/ });
    userEvent.selectOptions(screen.getByLabelText('Thuốc'), '8');
    userEvent.type(screen.getByLabelText('Liều dùng *'), '1 viên');
    userEvent.type(screen.getByLabelText('Tần suất *'), '2 lần/ngày');
    userEvent.click(screen.getByRole('button', { name: 'Lưu đơn thuốc' }));

    expect(await screen.findByText('Thuốc Amoxicillin không đủ tồn kho')).toBeInTheDocument();
  });

  test('chốt ca bắt buộc có chẩn đoán, có hộp xác nhận rồi gửi close-visit', async () => {
    api({ 'POST /api/v1/clinical/pet-visits/41/close-visit': () => success(visitDetail({ status: 'WAITING_PAYMENT' })) });
    renderAt('/vet/visits/41');
    userEvent.click(await screen.findByRole('tab', { name: 'Chốt ca' }));

    userEvent.click(screen.getByRole('button', { name: 'Chốt ca và chuyển thanh toán' }));
    expect(await screen.findByText('Chẩn đoán là bắt buộc để chốt ca.')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    userEvent.type(screen.getByLabelText('Chẩn đoán *'), 'Viêm dạ dày ruột');
    userEvent.type(screen.getByLabelText('Phác đồ điều trị'), 'Truyền dịch');
    userEvent.click(screen.getByRole('button', { name: 'Chốt ca và chuyển thanh toán' }));
    const dialog = await screen.findByRole('dialog');
    userEvent.click(within(dialog).getByRole('button', { name: 'Chốt ca' }));

    await waitFor(() => expect(callsTo('POST /api/v1/clinical/pet-visits/41/close-visit')).toHaveLength(1));
    expect(bodyOf(callsTo('POST /api/v1/clinical/pet-visits/41/close-visit')[0])).toEqual({ diagnosis: 'Viêm dạ dày ruột', treatmentPlan: 'Truyền dịch', doctorAdvice: null, clinicalNotes: null });
    expect(await screen.findByRole('heading', { level: 1, name: 'Ca khám của tôi' })).toBeInTheDocument();
  });
});

describe('khám giai đoạn 2: ca đã có kết quả', () => {
  const RESULT_VISIT = visitDetail({
    id: 71, visitCode: 'VIS-2026-00071', status: 'RESULTS_READY', petName: 'Mực',
    services: [{ id: 1, serviceName: 'Xét nghiệm máu', category: 'LABORATORY', status: 'COMPLETED' }],
    labResults: [
      { id: 1, code: 'WBC', name: 'Bạch cầu', unit: 'K/µL', value: 22, minValue: 6, maxValue: 17, evaluation: 'HIGH' },
      { id: 2, code: 'HGB', name: 'Huyết sắc tố', unit: 'g/dL', value: 4, minValue: 5, maxValue: 9, evaluation: 'LOW' },
      { id: 3, code: 'PLT', name: 'Tiểu cầu', unit: 'K/µL', value: 300, minValue: 200, maxValue: 500, evaluation: 'NORMAL' },
    ],
    imagingResults: [{ id: 9, description: 'Phổi có vùng mờ', conclusion: 'Nghi viêm phổi', performedAt: '2026-11-02T03:00:00Z', attachments: [
      { id: 201, fileName: 'xquang-1.png', fileSize: 2048, contentType: 'image/png' },
      { id: 202, fileName: 'xquang-2.png', fileSize: 4096, contentType: 'image/png' },
      { id: 203, fileName: 'scan.dcm', fileSize: 8192, contentType: 'application/dicom' }] }],
  });
  const api = (extra = {}) => mockApi({
    'GET /api/v1/auth/me': ME,
    'GET /api/v1/clinical/doctors/me/visits': () => success([]),
    'GET /api/v1/clinical/pet-visits/71': () => success(RESULT_VISIT),
    'GET /api/v1/clinical/imaging-attachments/201/file': () => fileReply(),
    'GET /api/v1/clinical/imaging-attachments/202/file': () => fileReply(),
    'GET /api/v1/clinical/imaging-attachments/203/file': () => fileReply('application/dicom'),
    ...extra,
  });

  test('kết quả xét nghiệm có huy hiệu Cao / Thấp / Bình thường theo đánh giá của backend', async () => {
    api();
    renderAt('/vet/visits/71');

    const table = await screen.findByRole('table', { name: 'Kết quả xét nghiệm' });
    const rowOf = (name) => within(table).getByText(name).closest('tr');
    expect(within(rowOf('Bạch cầu')).getByText('Cao')).toBeInTheDocument();
    expect(within(rowOf('Huyết sắc tố')).getByText('Thấp')).toBeInTheDocument();
    expect(within(rowOf('Tiểu cầu')).getByText('Bình thường')).toBeInTheDocument();
  });

  test('mời vào khám tiếp và tạm hoãn gọi đúng endpoint', async () => {
    api({ 'POST /api/v1/clinical/pet-visits/71/call-back': () => success({}) });
    renderAt('/vet/visits/71');

    userEvent.click(await screen.findByRole('button', { name: 'Mời vào khám tiếp' }));

    expect(await screen.findByText('Đã mời vào khám tiếp.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Tạm hoãn' })).toBeInTheDocument();
  });

  test('ảnh CĐHA tải qua API có Authorization; bấm vào ảnh mở hộp xem phóng to, thu phóng và chuyển ảnh', async () => {
    api();
    renderAt('/vet/visits/71');

    userEvent.click(await screen.findByRole('button', { name: 'Phóng to ảnh xquang-1.png' }));
    const viewer = await screen.findByRole('dialog', { name: 'Xem ảnh xquang-1.png' });
    const attachmentCall = callsTo('GET /api/v1/clinical/imaging-attachments/201/file')[0];
    expect(attachmentCall[1].headers.Authorization).toBe('Bearer access-1');

    expect(within(viewer).getByTestId('zoom-level')).toHaveTextContent('100%');
    userEvent.click(within(viewer).getByRole('button', { name: 'Phóng to' }));
    expect(within(viewer).getByTestId('zoom-level')).toHaveTextContent('125%');
    userEvent.click(within(viewer).getByRole('button', { name: 'Thu nhỏ' }));
    userEvent.click(within(viewer).getByRole('button', { name: 'Thu nhỏ' }));
    expect(within(viewer).getByTestId('zoom-level')).toHaveTextContent('80%');
    fireEvent.wheel(within(viewer).getByRole('img').parentElement, { deltaY: -100 });
    expect(within(viewer).getByTestId('zoom-level')).toHaveTextContent('100%');
    userEvent.click(within(viewer).getByRole('button', { name: 'Vừa khung' }));
    expect(within(viewer).getByTestId('zoom-level')).toHaveTextContent('100%');

    // Hai ảnh xem được (tệp DICOM chỉ tải về): có điều hướng giữa chúng.
    expect(within(viewer).getByText(/\(1\/2\)/)).toBeInTheDocument();
    userEvent.click(within(viewer).getByRole('button', { name: 'Ảnh sau' }));
    expect(within(viewer).getByText(/xquang-2\.png \(2\/2\)/)).toBeInTheDocument();

    userEvent.click(within(viewer).getByRole('button', { name: 'Đóng' }));
    await waitFor(() => expect(screen.queryByRole('dialog', { name: /Xem ảnh/ })).not.toBeInTheDocument());
  });

  test('tệp DICOM chỉ có liên kết tải về, không có ảnh xem trước', async () => {
    api();
    renderAt('/vet/visits/71');

    expect(await screen.findByRole('link', { name: 'Tải scan.dcm' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Phóng to ảnh scan.dcm' })).not.toBeInTheDocument();
  });
});

describe('bệnh án và tra cứu thuốc', () => {
  test('tìm thú cưng rồi xem bệnh án trọn đời: chẩn đoán, đơn thuốc, bác sĩ phụ trách', async () => {
    mockApi({
      'GET /api/v1/auth/me': ME,
      'GET /api/v1/clinical/doctors/me/visits': () => success([]),
      'GET /api/v1/pets': () => success(pageOf([{ id: 21, name: 'Kitty', petCode: 'PET-2026-00021', speciesName: 'Mèo' }])),
      'GET /api/v1/pets/21': () => success({ id: 21, name: 'Kitty', petCode: 'PET-2026-00021', speciesName: 'Mèo', breed: 'Anh lông ngắn', dateOfBirth: '2022-03-01', notes: 'Dị ứng penicillin' }),
      'GET /api/v1/pets/21/medical-history': () => success(pageOf([{
        visit: { id: 5, visitCode: 'VIS-2026-00005', status: 'COMPLETED', checkedInAt: '2026-10-01T02:00:00Z', doctorId: 3, diagnosis: 'Viêm tai ngoài', services: [], labResults: [], imagingResults: [] },
        doctorName: 'BS. Phạm An',
        prescription: { status: 'DISPENSED', doctorNotes: 'Nhỏ tai 5 ngày', items: [{ medicineId: 8, medicineName: 'Otic drops', medicineCode: 'OTIC', unit: 'lọ', quantity: 1, dosage: '3 giọt', frequency: '2 lần/ngày', durationDays: 5 }] },
      }])),
    });
    renderAt('/vet/history');

    userEvent.type(await screen.findByLabelText('Tìm thú cưng'), 'Kit');
    userEvent.click(await screen.findByRole('button', { name: 'Xem bệnh án' }));

    expect(await screen.findByText('Viêm tai ngoài')).toBeInTheDocument();
    expect(screen.getByText(/Bác sĩ phụ trách: BS\. Phạm An/)).toBeInTheDocument();
    expect(screen.getByText('Otic drops')).toBeInTheDocument();
    expect(screen.getByText(/Lưu ý: Dị ứng penicillin/)).toBeInTheDocument();
  });

  test('tra cứu thuốc gửi từ khóa tìm kiếm', async () => {
    mockApi({
      'GET /api/v1/auth/me': ME,
      'GET /api/v1/clinical/doctors/me/visits': () => success([]),
      'GET /api/v1/pharmacy/medicines': () => success(pageOf([{ id: 8, code: 'AMOX', name: 'Amoxicillin', activeIngredient: 'Amoxicillin', dosageForm: 'Viên nén', unit: 'viên', salePrice: 5000, stockQuantity: 3, lowStock: true }])),
    });
    renderAt('/vet/medicines');

    expect(await screen.findByText('Amoxicillin', { selector: 'strong' })).toBeInTheDocument();
    expect(screen.getByText('Sắp hết')).toBeInTheDocument();
    userEvent.type(screen.getByLabelText('Tìm thuốc'), 'amo');
    await waitFor(() => expect(callsTo('GET /api/v1/pharmacy/medicines').some(([url]) => new URL(String(url)).searchParams.get('keyword') === 'amo')).toBe(true));
  });
});

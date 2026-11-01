import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { bodyOf, callsTo, failure, mockApi, renderAt, signedInAs, success } from '../../testUtils';

const LAB_ME = () => success({ id: 4, fullName: 'KTV Xét Nghiệm', phoneNumber: '0910000004', role: 'LAB_TECHNICIAN' });
const IMG_ME = () => success({ id: 5, fullName: 'KTV CĐHA', phoneNumber: '0910000005', role: 'IMAGING_TECHNICIAN' });

const item = (over = {}) => ({
  serviceDetailId: 301, visitId: 31, visitCode: 'VIS-2026-00031', petId: 21, petName: 'Kitty', speciesName: 'Mèo', serviceId: 2, serviceName: 'Xét nghiệm máu',
  category: 'LABORATORY', emergency: false, status: 'PENDING', queueState: 'WAITING', otherRoomStatus: null, notes: null, orderedAt: '2026-11-02T02:00:00Z', ...over,
});

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
  window.scrollTo = jest.fn();
  URL.createObjectURL = jest.fn(() => 'blob:preview');
  URL.revokeObjectURL = jest.fn();
});
afterEach(() => { delete global.fetch; });

describe('hàng đợi xét nghiệm', () => {
  const queue = [
    item({ serviceDetailId: 301, petName: 'Kitty' }),
    item({ serviceDetailId: 302, petName: 'Rex', emergency: true, visitCode: 'VIS-2026-00032', otherRoomStatus: 'IMAGING_IN_PROGRESS' }),
    item({ serviceDetailId: 303, petName: 'Bông', queueState: 'POSTPONED', visitCode: 'VIS-2026-00033' }),
    item({ serviceDetailId: 304, petName: 'Lu', queueState: 'IN_PROGRESS', status: 'IN_PROGRESS', visitCode: 'VIS-2026-00034' }),
  ];
  const api = (extra = {}) => mockApi({ 'GET /api/v1/auth/me': LAB_ME, 'GET /api/v1/clinical/lab-queue': () => success(queue), ...extra });

  beforeEach(() => signedInAs('lab'));

  test('ca cấp cứu được đánh dấu đỏ, ca liên phòng có huy hiệu, ca hoãn nằm riêng một tab', async () => {
    api();
    renderAt('/lab/queue');

    const table = await screen.findByRole('table', { name: 'Hàng đợi xét nghiệm' });
    const emergencyRow = within(table).getByText('Rex').closest('tr');
    expect(emergencyRow).toHaveClass('emergency');
    expect(within(emergencyRow).getByText(/CẤP CỨU/)).toBeInTheDocument();
    expect(within(emergencyRow).getByText('Phòng chẩn đoán hình ảnh: đang được thực hiện')).toBeInTheDocument();
    expect(within(table).getByText('Kitty').closest('tr')).not.toHaveClass('emergency');
    expect(within(table).queryByText('Bông')).not.toBeInTheDocument();

    userEvent.click(screen.getByRole('tab', { name: /Ca tạm hoãn/ }));
    expect(within(screen.getByRole('table', { name: 'Hàng đợi xét nghiệm' })).getByText('Bông')).toBeInTheDocument();
  });

  test('"Bắt đầu" gọi start rồi mở form nhập; ca đang thực hiện có nút nhập kết quả thẳng', async () => {
    api({
      'POST /api/v1/clinical/service-details/301/start': () => success({}),
      'GET /api/v1/clinical/service-details/301/lab-form': () => success([]),
    });
    renderAt('/lab/queue');

    const luRow = (await screen.findByText('Lu')).closest('tr');
    expect(within(luRow).getByRole('link', { name: 'Nhập kết quả' })).toHaveAttribute('href', '/lab/entry/304');
    userEvent.click(within(screen.getByText('Kitty').closest('tr')).getByRole('button', { name: 'Bắt đầu' }));

    await waitFor(() => expect(callsTo('POST /api/v1/clinical/service-details/301/start')).toHaveLength(1));
    expect(await screen.findByRole('heading', { level: 1, name: 'Nhập kết quả' })).toBeInTheDocument();
  });

  test('hủy dịch vụ bắt buộc có lý do, hoãn thì không bắt buộc', async () => {
    api({ 'POST /api/v1/clinical/service-details/301/cancel': () => success({}), 'POST /api/v1/clinical/service-details/301/postpone': () => success({}) });
    renderAt('/lab/queue');
    const row = (await screen.findByText('Kitty')).closest('tr');

    userEvent.click(within(row).getByRole('button', { name: 'Hủy' }));
    let dialog = await screen.findByRole('dialog');
    userEvent.click(within(dialog).getByRole('button', { name: 'Hủy dịch vụ' }));
    expect(await within(dialog).findByText('Nhập lý do.')).toBeInTheDocument();
    userEvent.type(within(dialog).getByLabelText('Lý do *'), 'Mẫu bị đông');
    userEvent.click(within(dialog).getByRole('button', { name: 'Hủy dịch vụ' }));
    await waitFor(() => expect(callsTo('POST /api/v1/clinical/service-details/301/cancel')).toHaveLength(1));
    expect(bodyOf(callsTo('POST /api/v1/clinical/service-details/301/cancel')[0])).toEqual({ reason: 'Mẫu bị đông' });

    userEvent.click(within((await screen.findByText('Kitty')).closest('tr')).getByRole('button', { name: 'Hoãn' }));
    dialog = await screen.findByRole('dialog');
    userEvent.click(within(dialog).getByRole('button', { name: 'Hoãn' }));
    await waitFor(() => expect(callsTo('POST /api/v1/clinical/service-details/301/postpone')).toHaveLength(1));
    expect(bodyOf(callsTo('POST /api/v1/clinical/service-details/301/postpone')[0])).toEqual({});
  });

  test('kỹ thuật viên xét nghiệm không vào được hàng đợi CĐHA', async () => {
    api();
    renderAt('/imaging/queue');
    expect(await screen.findByRole('heading', { name: 'Bạn không có quyền truy cập' })).toBeInTheDocument();
  });
});

describe('form nhập chỉ số xét nghiệm tô màu', () => {
  const FORM = [
    { id: 11, code: 'WBC', name: 'Bạch cầu', unit: 'K/µL', ranges: [{ speciesId: 2, minValue: 5.5, maxValue: 19.5 }] },
    { id: 12, code: 'HGB', name: 'Huyết sắc tố', unit: 'g/dL', ranges: [{ speciesId: 2, minValue: 8, maxValue: 15 }] },
    { id: 13, code: 'PLT', name: 'Tiểu cầu', unit: 'K/µL', ranges: [{ speciesId: 2, minValue: 200, maxValue: 500 }] },
    { id: 14, code: 'NEW', name: 'Chỉ số mới', unit: 'u', ranges: [] },
  ];
  const api = (extra = {}) => mockApi({
    'GET /api/v1/auth/me': LAB_ME,
    'GET /api/v1/clinical/lab-queue': () => success([item({ queueState: 'IN_PROGRESS', status: 'IN_PROGRESS' })]),
    'GET /api/v1/clinical/service-details/301/lab-form': () => success(FORM),
    ...extra,
  });
  const field = (name) => screen.getByLabelText(`Giá trị ${name}`);

  beforeEach(() => signedInAs('lab'));

  test('ô nhập tô vàng khi thấp, xanh khi bình thường, đỏ khi cao, kèm huy hiệu và tổng kết', async () => {
    api();
    renderAt('/lab/entry/301');
    await screen.findByLabelText('Giá trị Bạch cầu');

    userEvent.type(field('Bạch cầu'), '3');
    userEvent.type(field('Huyết sắc tố'), '12');
    userEvent.type(field('Tiểu cầu'), '900');

    expect(field('Bạch cầu')).toHaveClass('lab-low');
    expect(field('Huyết sắc tố')).toHaveClass('lab-normal');
    expect(field('Tiểu cầu')).toHaveClass('lab-high');
    const table = screen.getByRole('table', { name: 'Chỉ số xét nghiệm' });
    expect(within(within(table).getByText('Bạch cầu').closest('tr')).getByText('Thấp')).toBeInTheDocument();
    expect(within(within(table).getByText('Huyết sắc tố').closest('tr')).getByText('Bình thường')).toBeInTheDocument();
    expect(within(within(table).getByText('Tiểu cầu').closest('tr')).getByText('Cao')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Đã nhập 3/4 chỉ số · 2 chỉ số ngoài khoảng tham chiếu');
  });

  test('hai đầu khoảng tham chiếu vẫn là bình thường; đổi giá trị thì màu đổi theo', async () => {
    api();
    renderAt('/lab/entry/301');
    await screen.findByLabelText('Giá trị Bạch cầu');

    userEvent.type(field('Bạch cầu'), '5.5');
    expect(field('Bạch cầu')).toHaveClass('lab-normal');
    userEvent.clear(field('Bạch cầu'));
    userEvent.type(field('Bạch cầu'), '19,51');
    expect(field('Bạch cầu')).toHaveClass('lab-high');
    userEvent.clear(field('Bạch cầu'));
    expect(field('Bạch cầu')).not.toHaveClass('lab-high');
  });

  test('chỉ số chưa có khoảng tham chiếu cho loài này không bị tô màu', async () => {
    api();
    renderAt('/lab/entry/301');
    await screen.findByLabelText('Giá trị Bạch cầu');

    expect(screen.getByText('Chưa cấu hình cho loài này')).toBeInTheDocument();
    userEvent.type(field('Chỉ số mới'), '10');
    expect(field('Chỉ số mới')).not.toHaveClass('lab-low');
    expect(field('Chỉ số mới')).not.toHaveClass('lab-normal');
    expect(field('Chỉ số mới')).not.toHaveClass('lab-high');
  });

  test('lưu gửi các chỉ số đã nhập dạng số (bỏ qua chỉ số để trống) rồi về hàng đợi', async () => {
    api({ 'POST /api/v1/clinical/service-details/301/lab-results': () => success({}) });
    renderAt('/lab/entry/301');
    await screen.findByLabelText('Giá trị Bạch cầu');

    userEvent.type(field('Bạch cầu'), '7,25');
    userEvent.type(screen.getByLabelText('Ghi chú Bạch cầu'), 'Mẫu tốt');
    userEvent.type(field('Tiểu cầu'), '300');
    userEvent.click(screen.getByRole('button', { name: 'Lưu kết quả và hoàn tất' }));

    await waitFor(() => expect(callsTo('POST /api/v1/clinical/service-details/301/lab-results')).toHaveLength(1));
    expect(bodyOf(callsTo('POST /api/v1/clinical/service-details/301/lab-results')[0])).toEqual({
      items: [{ labTestItemId: 11, value: 7.25, notes: 'Mẫu tốt' }, { labTestItemId: 13, value: 300, notes: null }],
    });
    expect(await screen.findByRole('heading', { level: 1, name: 'Hàng đợi xét nghiệm' })).toBeInTheDocument();
  });

  test('giá trị không hợp lệ hoặc không nhập gì thì không gửi', async () => {
    api({ 'POST /api/v1/clinical/service-details/301/lab-results': () => success({}) });
    renderAt('/lab/entry/301');
    await screen.findByLabelText('Giá trị Bạch cầu');

    userEvent.click(screen.getByRole('button', { name: 'Lưu kết quả và hoàn tất' }));
    expect(await screen.findByText('Nhập ít nhất một chỉ số.')).toBeInTheDocument();

    userEvent.type(field('Bạch cầu'), '1.234');
    expect(screen.getByText('Tối đa 2 chữ số thập phân.')).toBeInTheDocument();
    userEvent.click(screen.getByRole('button', { name: 'Lưu kết quả và hoàn tất' }));
    expect(await screen.findByText('Bạch cầu: Tối đa 2 chữ số thập phân.')).toBeInTheDocument();
    expect(callsTo('POST /api/v1/clinical/service-details/301/lab-results')).toHaveLength(0);
  });

  test('chỉ số ngoài dịch vụ bị backend từ chối thì hiện lỗi và ở lại form', async () => {
    api({ 'POST /api/v1/clinical/service-details/301/lab-results': () => failure(400, 'CLIN_1319', 'Chỉ số không thuộc dịch vụ này') });
    renderAt('/lab/entry/301');
    await screen.findByLabelText('Giá trị Bạch cầu');

    userEvent.type(field('Bạch cầu'), '7');
    userEvent.click(screen.getByRole('button', { name: 'Lưu kết quả và hoàn tất' }));

    expect(await screen.findByText('Chỉ số không thuộc dịch vụ này')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'Nhập kết quả' })).toBeInTheDocument();
  });

  test('chưa bấm "Bắt đầu" thì chưa nhập được kết quả', async () => {
    api({ 'GET /api/v1/clinical/lab-queue': () => success([item({ queueState: 'WAITING' })]) });
    renderAt('/lab/entry/301');

    expect(await screen.findByText(/chưa được bắt đầu/)).toBeInTheDocument();
    expect(screen.queryByText('Nhập kết quả xét nghiệm')).not.toBeInTheDocument();
  });
});

describe('hàng đợi và nhập kết quả chẩn đoán hình ảnh', () => {
  const imagingItem = item({ serviceDetailId: 401, serviceName: 'Chụp X-quang', category: 'IMAGING', queueState: 'IN_PROGRESS', status: 'IN_PROGRESS', emergency: true });
  const api = (extra = {}) => mockApi({
    'GET /api/v1/auth/me': IMG_ME,
    'GET /api/v1/clinical/imaging-queue': () => success([imagingItem, item({ serviceDetailId: 402, petName: 'Rex', category: 'IMAGING', serviceName: 'Siêu âm' })]),
    ...extra,
  });
  const png = (name = 'xquang.png', size = 2048) => new File([new Uint8Array(size)], name, { type: 'image/png' });
  const dropOn = (files) => fireEvent.drop(screen.getByRole('button', { name: /Kéo thả ảnh vào đây/ }), { dataTransfer: { files } });

  beforeEach(() => signedInAs('imaging'));

  test('hàng đợi chỉ hiện ca của phòng CĐHA, ca cấp cứu đánh dấu đỏ', async () => {
    api();
    renderAt('/imaging/queue');

    const table = await screen.findByRole('table', { name: 'Hàng đợi chẩn đoán hình ảnh' });
    expect(within(table).getByText('Kitty').closest('tr')).toHaveClass('emergency');
    expect(within(table).getByRole('link', { name: 'Nhập kết quả' })).toHaveAttribute('href', '/imaging/entry/401');
  });

  test('kéo thả ảnh vào vùng nhận tệp hợp lệ, hiện tên, dung lượng, và cho bỏ từng tệp', async () => {
    api();
    renderAt('/imaging/entry/401');
    await screen.findByText('Nhập kết quả chẩn đoán hình ảnh');

    dropOn([png('a.png', 2048), png('b.jpg', 3 * 1024 * 1024)]);

    const list = await screen.findByRole('list', { name: 'Tệp đã chọn' });
    expect(within(list).getByText('a.png')).toBeInTheDocument();
    expect(within(list).getByText('2 KB')).toBeInTheDocument();
    expect(within(list).getByText('3,0 MB')).toBeInTheDocument();
    userEvent.click(within(list).getByRole('button', { name: 'Bỏ tệp a.png' }));
    expect(within(screen.getByRole('list', { name: 'Tệp đã chọn' })).queryByText('a.png')).not.toBeInTheDocument();
  });

  test('tệp sai định dạng hoặc quá 20 MB bị từ chối ngay kèm lý do, tệp hợp lệ còn lại vẫn được nhận', async () => {
    api();
    renderAt('/imaging/entry/401');
    await screen.findByText('Nhập kết quả chẩn đoán hình ảnh');

    const huge = png('lon.png', 10);
    Object.defineProperty(huge, 'size', { value: 21 * 1024 * 1024 });
    dropOn([new File(['x'], 'bao-cao.pdf', { type: 'application/pdf' }), huge, png('ok.png')]);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('"bao-cao.pdf": chỉ nhận JPEG, PNG hoặc DICOM');
    expect(alert).toHaveTextContent('"lon.png": vượt quá 20 MB');
    expect(within(screen.getByRole('list', { name: 'Tệp đã chọn' })).getByText('ok.png')).toBeInTheDocument();
  });

  test('chọn tệp bằng hộp thoại tệp cũng đi qua cùng bước kiểm tra', async () => {
    api();
    renderAt('/imaging/entry/401');
    await screen.findByText('Nhập kết quả chẩn đoán hình ảnh');

    fireEvent.change(screen.getByTestId('imaging-file-input'), { target: { files: [png('scan.dcm', 500)] } });

    expect(within(await screen.findByRole('list', { name: 'Tệp đã chọn' })).getByText('scan.dcm')).toBeInTheDocument();
  });

  test('bắt buộc có mô tả, kết luận và ít nhất một tệp trước khi gửi', async () => {
    api({ 'POST /api/v1/clinical/service-details/401/imaging-result': () => success({}) });
    renderAt('/imaging/entry/401');
    await screen.findByText('Nhập kết quả chẩn đoán hình ảnh');

    userEvent.click(screen.getByRole('button', { name: 'Lưu kết quả và hoàn tất' }));
    expect(await screen.findByText('Nhập mô tả kết quả.')).toBeInTheDocument();

    userEvent.type(screen.getByLabelText('Mô tả *'), 'Phổi sạch');
    userEvent.click(screen.getByRole('button', { name: 'Lưu kết quả và hoàn tất' }));
    expect(await screen.findByText('Nhập kết luận.')).toBeInTheDocument();

    userEvent.type(screen.getByLabelText('Kết luận *'), 'Bình thường');
    userEvent.click(screen.getByRole('button', { name: 'Lưu kết quả và hoàn tất' }));
    expect(await screen.findByText('Đính kèm ít nhất một tệp ảnh.')).toBeInTheDocument();
    expect(callsTo('POST /api/v1/clinical/service-details/401/imaging-result')).toHaveLength(0);
  });

  test('gửi multipart gồm mô tả, kết luận và từng tệp trong phần "files", không tự đặt Content-Type', async () => {
    let received;
    api({ 'POST /api/v1/clinical/service-details/401/imaging-result': ({ form }) => { received = form; return success({}); } });
    renderAt('/imaging/entry/401');
    await screen.findByText('Nhập kết quả chẩn đoán hình ảnh');

    userEvent.type(screen.getByLabelText('Mô tả *'), 'Phổi sạch');
    userEvent.type(screen.getByLabelText('Kết luận *'), 'Bình thường');
    dropOn([png('a.png'), png('b.png')]);
    await screen.findByRole('list', { name: 'Tệp đã chọn' });
    userEvent.click(screen.getByRole('button', { name: 'Lưu kết quả và hoàn tất' }));

    await waitFor(() => expect(received).toBeDefined());
    expect(received.get('description')).toBe('Phổi sạch');
    expect(received.get('conclusion')).toBe('Bình thường');
    expect(received.getAll('files').map((file) => file.name)).toEqual(['a.png', 'b.png']);
    // Trình duyệt tự đặt boundary cho multipart: không được ghi đè Content-Type.
    expect(callsTo('POST /api/v1/clinical/service-details/401/imaging-result')[0][1].headers['Content-Type']).toBeUndefined();
    expect(await screen.findByRole('heading', { level: 1, name: 'Hàng đợi chẩn đoán hình ảnh' })).toBeInTheDocument();
  });

  test('backend từ chối nội dung tệp (magic-byte) thì hiện lỗi và giữ nguyên các tệp đã chọn', async () => {
    api({ 'POST /api/v1/clinical/service-details/401/imaging-result': () => failure(400, 'CLIN_1320', 'Nội dung tệp không phải ảnh hợp lệ') });
    renderAt('/imaging/entry/401');
    await screen.findByText('Nhập kết quả chẩn đoán hình ảnh');

    userEvent.type(screen.getByLabelText('Mô tả *'), 'Phổi sạch');
    userEvent.type(screen.getByLabelText('Kết luận *'), 'Bình thường');
    dropOn([png('gia-mao.png')]);
    await screen.findByRole('list', { name: 'Tệp đã chọn' });
    userEvent.click(screen.getByRole('button', { name: 'Lưu kết quả và hoàn tất' }));

    expect(await screen.findByText('Nội dung tệp không phải ảnh hợp lệ')).toBeInTheDocument();
    expect(within(screen.getByRole('list', { name: 'Tệp đã chọn' })).getByText('gia-mao.png')).toBeInTheDocument();
  });
});

import { addImagingFiles, describeRejected, formatSize, MAX_IMAGING_FILES, MAX_IMAGING_FILE_BYTES } from './imagingFiles';
import { evaluateLab, parseLabValue } from './labs';
import { MAX_SCALE, MIN_SCALE, stepScale, wheelScale } from './zoom';

describe('evaluateLab (khớp quy tắc của backend)', () => {
  const range = { minValue: 6, maxValue: 17 };

  test('thấp hơn ngưỡng dưới là LOW, cao hơn ngưỡng trên là HIGH', () => {
    expect(evaluateLab(5.99, range)).toBe('LOW');
    expect(evaluateLab(17.01, range)).toBe('HIGH');
  });

  test('hai đầu của khoảng vẫn là NORMAL', () => {
    expect(evaluateLab(6, range)).toBe('NORMAL');
    expect(evaluateLab(17, range)).toBe('NORMAL');
    expect(evaluateLab('11.5', range)).toBe('NORMAL');
  });

  test('khoảng tham chiếu của backend có thể là chuỗi số thập phân', () => {
    expect(evaluateLab(3, { minValue: '3.50', maxValue: '5.00' })).toBe('LOW');
  });

  test('chưa có khoảng cho loài này hoặc chưa nhập giá trị thì không đánh giá', () => {
    expect(evaluateLab(10, undefined)).toBeNull();
    expect(evaluateLab(10, { minValue: null, maxValue: null })).toBeNull();
    expect(evaluateLab('', range)).toBeNull();
    expect(evaluateLab('abc', range)).toBeNull();
  });
});

describe('parseLabValue', () => {
  test('để trống không phải lỗi', () => {
    expect(parseLabValue('')).toEqual({ empty: true });
    expect(parseLabValue('   ')).toEqual({ empty: true });
  });

  test('nhận số, chấp nhận dấu phẩy thập phân kiểu Việt Nam', () => {
    expect(parseLabValue('12')).toEqual({ value: 12 });
    expect(parseLabValue('7,25')).toEqual({ value: 7.25 });
    expect(parseLabValue(' 0.5 ')).toEqual({ value: 0.5 });
  });

  test('từ chối chữ, số âm, quá lớn và quá hai chữ số thập phân (như backend)', () => {
    expect(parseLabValue('abc').error).toMatch(/phải là số/);
    expect(parseLabValue('-1').error).toMatch(/không được âm/);
    expect(parseLabValue('1000000').error).toMatch(/quá lớn/);
    expect(parseLabValue('1.234').error).toMatch(/2 chữ số thập phân/);
    expect(parseLabValue('999999.99')).toEqual({ value: 999999.99 });
  });
});

describe('addImagingFiles', () => {
  const file = (name, size = 1000, lastModified = 1) => ({ name, size, lastModified });

  test('nhận JPEG, PNG và DICOM', () => {
    const { files, rejected } = addImagingFiles([], [file('a.jpg'), file('b.PNG'), file('c.dcm'), file('d.jpeg')]);
    expect(files).toHaveLength(4);
    expect(rejected).toEqual([]);
  });

  test('từ chối sai định dạng, tệp rỗng và tệp quá 20 MB, kèm lý do', () => {
    const { files, rejected } = addImagingFiles([], [file('x.pdf'), file('y.png', 0), file('z.png', MAX_IMAGING_FILE_BYTES + 1), file('ok.png')]);
    expect(files.map((item) => item.name)).toEqual(['ok.png']);
    expect(rejected.map((item) => item.reason)).toEqual(['chỉ nhận JPEG, PNG hoặc DICOM', 'tệp rỗng', 'vượt quá 20 MB']);
    expect(describeRejected(rejected)).toContain('"x.pdf": chỉ nhận JPEG, PNG hoặc DICOM');
  });

  test('đúng 20 MB vẫn được nhận', () => {
    expect(addImagingFiles([], [file('edge.png', MAX_IMAGING_FILE_BYTES)]).files).toHaveLength(1);
  });

  test('bỏ qua tệp trùng và không vượt số tệp tối đa', () => {
    const first = addImagingFiles([], [file('a.png')]).files;
    expect(addImagingFiles(first, [file('a.png')]).rejected[0].reason).toBe('đã chọn rồi');

    const many = Array.from({ length: MAX_IMAGING_FILES + 2 }, (_, index) => file(`f${index}.png`, 1000 + index));
    const result = addImagingFiles([], many);
    expect(result.files).toHaveLength(MAX_IMAGING_FILES);
    expect(result.rejected).toHaveLength(2);
  });

  test('không làm thay đổi danh sách cũ', () => {
    const current = [file('a.png')];
    addImagingFiles(current, [file('b.png')]);
    expect(current).toHaveLength(1);
  });

  test('định dạng kích thước dễ đọc', () => {
    expect(formatSize(500)).toBe('1 KB');
    expect(formatSize(2048)).toBe('2 KB');
    expect(formatSize(5 * 1024 * 1024)).toBe('5,0 MB');
  });
});

describe('thu phóng ảnh', () => {
  test('phóng to rồi thu nhỏ quay về đúng mức cũ', () => {
    expect(stepScale(stepScale(1, 1), -1)).toBe(1);
    expect(stepScale(stepScale(stepScale(1, 1), 1), -1)).toBe(1.25);
  });

  test('giới hạn trong khoảng cho phép', () => {
    let scale = 1;
    for (let i = 0; i < 40; i += 1) scale = stepScale(scale, 1);
    expect(scale).toBe(MAX_SCALE);
    for (let i = 0; i < 80; i += 1) scale = stepScale(scale, -1);
    expect(scale).toBe(MIN_SCALE);
  });

  test('cuộn chuột lên phóng to, cuộn xuống thu nhỏ', () => {
    expect(wheelScale(1, -100)).toBeGreaterThan(1);
    expect(wheelScale(1, 100)).toBeLessThan(1);
  });
});

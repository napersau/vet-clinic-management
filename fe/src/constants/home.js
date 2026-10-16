import { homeSection } from './routes';

const LANDING_IMAGES = '/images/landing';

export const NAV_ITEMS = [
  { to: homeSection('services'), label: 'Dịch vụ' },
  { to: homeSection('patients'), label: 'Khách hàng' },
  { to: homeSection('pricing'), label: 'Bảng giá' },
  { to: homeSection('reviews'), label: 'Đánh giá' },
];

export const HERO_IMAGE = `${LANDING_IMAGES}/hero-dog.jpg`;

export const SERVICES = [
  { icon: '🩺', tone: 'orange', title: 'Khám tổng quát', description: 'Kiểm tra sức khỏe toàn diện, tư vấn dinh dưỡng và lập kế hoạch chăm sóc phù hợp.', price: 'Từ 150.000đ' },
  { icon: '💉', tone: 'pink', title: 'Tiêm phòng', description: 'Lịch tiêm khoa học, vaccine chính hãng và nhắc lịch tự động cho từng bé.', price: 'Từ 180.000đ' },
  { icon: '🧪', tone: 'yellow', title: 'Xét nghiệm', description: 'Xét nghiệm máu, sinh hoá và chẩn đoán nhanh với quy trình an toàn.', price: 'Từ 250.000đ' },
  { icon: '🩻', tone: 'purple', title: 'Chẩn đoán hình ảnh', description: 'Siêu âm, X-quang hiện đại giúp bác sĩ đánh giá chính xác và nhanh chóng.', price: 'Từ 300.000đ' },
  { icon: '⚕️', tone: 'blue', title: 'Điều trị chuyên sâu', description: 'Phác đồ cá nhân hoá, theo dõi sát sao và cập nhật hồ sơ sức khoẻ liên tục.', price: 'Theo chỉ định' },
  { icon: '🏥', tone: 'green', title: 'Chăm sóc nội trú', description: 'Khu lưu trú sạch sẽ, giám sát 24/7 và cập nhật tình trạng cho gia đình.', price: 'Từ 350.000đ' },
];

export const GALLERY_PETS = [
  { image: `${LANDING_IMAGES}/pet-max.jpg`, name: 'Max', breed: 'Golden Retriever' },
  { image: `${LANDING_IMAGES}/pet-bella.jpg`, name: 'Bella', breed: 'Beagle' },
  { image: `${LANDING_IMAGES}/pet-charlie.jpg`, name: 'Charlie', breed: 'Border Collie' },
  { image: `${LANDING_IMAGES}/pet-luna.jpg`, name: 'Luna', breed: 'Husky' },
  { image: `${LANDING_IMAGES}/pet-cooper.jpg`, name: 'Cooper', breed: 'Labrador' },
  { image: `${LANDING_IMAGES}/pet-daisy.jpg`, name: 'Daisy', breed: 'Poodle' },
];

export const CARE_PLANS = [
  { name: 'Khám cơ bản', description: 'Kiểm tra sức khoẻ định kỳ', price: '150K', features: ['Khám lâm sàng', 'Tư vấn dinh dưỡng', 'Theo dõi cân nặng', 'Hồ sơ sức khoẻ'] },
  { name: 'Chăm sóc toàn diện', description: 'Gói được gia đình lựa chọn nhiều nhất', price: '499K', popular: true, features: ['Tất cả quyền lợi cơ bản', 'Xét nghiệm máu', 'Tư vấn vaccine', 'Nhắc lịch tái khám'] },
  { name: 'Khám chuyên sâu', description: 'Sàng lọc chuyên sâu theo độ tuổi', price: '899K', features: ['Tất cả quyền lợi toàn diện', 'Siêu âm hoặc X-quang', 'Tư vấn bác sĩ chuyên khoa', 'Ưu tiên khung giờ khám'] },
];

export const TESTIMONIALS = [
  { quote: 'Bác sĩ rất nhẹ nhàng và giải thích kỹ từng bước. Bé Bô vốn sợ người lạ mà lần này lại hợp tác bất ngờ!', name: 'Nguyễn Ngọc Lan', pet: 'Mẹ của Bô • Poodle', image: `${LANDING_IMAGES}/customer-lan.jpg` },
  { quote: 'Mình đặt lịch online rất nhanh, đến nơi không phải chờ lâu. Hồ sơ sức khoẻ của bé được lưu rất tiện.', name: 'Trần Quang Minh', pet: 'Ba của Mít • Mèo Anh lông ngắn', image: `${LANDING_IMAGES}/customer-minh.jpg` },
  { quote: 'Không gian sạch, nhân viên thân thiện và chi phí minh bạch. Mình nhận được nhắc lịch vaccine đúng lúc.', name: 'Lê Thu Thảo', pet: 'Mẹ của Moon • Corgi', image: `${LANDING_IMAGES}/customer-thao.jpg` },
];

export const BOOKING_BENEFITS = [
  { icon: '⚡', title: 'Linh hoạt trong ngày', description: 'Nhiều khung giờ để bạn lựa chọn' },
  { icon: '📱', title: 'Cập nhật liên tục', description: 'Theo dõi lịch và hồ sơ ngay trên portal' },
  { icon: '💳', title: 'Chi phí rõ ràng', description: 'Xác nhận trước khi thực hiện dịch vụ' },
];

export const BOOKING_STEPS = [
  'Chọn thú cưng thuộc tài khoản của bạn.',
  'Chọn dịch vụ và mô tả lý do khám.',
  'Chọn ngày và khung giờ còn trống.',
  'Kiểm tra thông tin rồi gửi yêu cầu.',
];

export const FOOTER_SERVICES = ['Khám tổng quát', 'Tiêm phòng', 'Xét nghiệm', 'Chăm sóc nội trú'];

export const CLINIC_CONTACT = [
  { icon: '📍', text: '123 Nguyễn Văn Linh, TP.HCM' },
  { icon: '📞', text: '1900 6868' },
  { icon: '✉️', text: 'hello@pawcare.vn' },
  { icon: '🕒', text: '07:30 – 20:30, mỗi ngày' },
];

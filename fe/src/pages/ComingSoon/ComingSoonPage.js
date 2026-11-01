import { Empty, PageHead, Panel } from '../../components/ui';

// Chỗ giữ cho màn hình chưa làm: menu theo vai trò đã có đủ mục, bấm vào chưa có nghiệp vụ thì vào trang này.
export default function ComingSoonPage({ title }) {
  return <>
    <PageHead eyebrow="ĐANG XÂY DỰNG" title={title}>Màn hình này sẽ được nối với API trong các đợt cập nhật tiếp theo.</PageHead>
    <Panel><Empty title="Chưa có nội dung">Bạn có thể quay lại trang chủ của khu vực qua menu bên trái.</Empty></Panel>
  </>;
}

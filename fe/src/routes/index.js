import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireAuth, RequireRole } from '../components';
import CustomerLayout from '../components/layout/CustomerLayout';
import StaffLayout from '../components/layout/StaffLayout';
import { CUSTOMER_LINKS, ROUTES, STAFF_AREAS } from '../constants';
import { useScrollToHash } from '../hooks';
import { ComingSoonPage, HomePage, LoginPage, ProfilePage, RegisterPage } from '../pages';

// Màn hình đã làm xong, theo đường dẫn. Mỗi trang tự dựng khung (StaffLayout / CustomerLayout) bên trong.
// Đường dẫn nào có trong menu (constants/navigation) mà chưa có ở đây thì hiện ComingSoonPage trong đúng khung của vai trò.
const IMPLEMENTED = {};

const guarded = (role, path, fallback) => {
  const Page = IMPLEMENTED[path];
  return <RequireRole role={role}>{Page ? <Page /> : fallback}</RequireRole>;
};

export default function AppRoutes() {
  useScrollToHash();

  return (
    <Routes>
      <Route path={ROUTES.HOME} element={<HomePage />} />
      <Route path={ROUTES.LOGIN} element={<LoginPage />} />
      <Route path={ROUTES.REGISTER} element={<RegisterPage />} />
      <Route path={ROUTES.PROFILE} element={<RequireAuth><ProfilePage /></RequireAuth>} />

      {CUSTOMER_LINKS.filter((item) => item.to !== ROUTES.PROFILE).map((item) => (
        <Route key={item.to} path={item.to} element={guarded('PET_OWNER', item.to, <CustomerLayout><ComingSoonPage title={item.label} /></CustomerLayout>)} />
      ))}

      {Object.entries(STAFF_AREAS).map(([area, config]) => [
        <Route key={`${area}-index`} path={`/${area}`} element={<Navigate to={config.home} replace />} />,
        ...config.links.map((item) => (
          <Route key={item.to} path={item.to} element={guarded(config.role, item.to, <StaffLayout area={area} title={item.label}><ComingSoonPage title={item.label} /></StaffLayout>)} />
        )),
      ])}

      <Route path="*" element={<Navigate to={ROUTES.HOME} replace />} />
    </Routes>
  );
}

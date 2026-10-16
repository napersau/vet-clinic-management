import { Navigate, Route, Routes } from 'react-router-dom';
import { ROUTES } from '../constants';
import { useScrollToHash } from '../hooks';
import { HomePage, LoginPage, RegisterPage } from '../pages';

export default function AppRoutes() {
  useScrollToHash();

  return (
    <Routes>
      <Route path={ROUTES.HOME} element={<HomePage />} />
      <Route path={ROUTES.LOGIN} element={<LoginPage />} />
      <Route path={ROUTES.REGISTER} element={<RegisterPage />} />
      <Route path="*" element={<Navigate to={ROUTES.HOME} replace />} />
    </Routes>
  );
}

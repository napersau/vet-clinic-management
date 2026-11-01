import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireAuth, RequireRole } from '../components';
import CustomerLayout from '../components/layout/CustomerLayout';
import StaffLayout from '../components/layout/StaffLayout';
import { CUSTOMER_LINKS, ROUTES, STAFF_AREAS } from '../constants';
import { useScrollToHash } from '../hooks';
import {
  AppointmentsPage, BookingPage, ComingSoonPage, HomePage, LoginPage, PetDetailPage, PetsPage, ProfilePage, ReceptionAppointmentsPage,
  ParaclinicEntryPage, ParaclinicQueuePage, ReceptionIntakePage, ReceptionQueuePage, RegisterPage, VetHistoryPage, VetMedicinesPage, VetTodayPage, VetVisitPage,
} from '../pages';

// Màn hình đã làm xong, theo đường dẫn. Mỗi trang tự dựng khung (StaffLayout / CustomerLayout) bên trong.
// Đường dẫn nào có trong menu (constants/navigation) mà chưa có ở đây thì hiện ComingSoonPage trong đúng khung của vai trò.
const LabQueuePage = () => <ParaclinicQueuePage kind="lab" />;
const ImagingQueuePage = () => <ParaclinicQueuePage kind="imaging" />;

const IMPLEMENTED = {
  '/portal/pets': PetsPage,
  '/portal/booking': BookingPage,
  '/portal/appointments': AppointmentsPage,
  '/reception/queue': ReceptionQueuePage,
  '/reception/intake': ReceptionIntakePage,
  '/reception/appointments': ReceptionAppointmentsPage,
  '/vet/today': VetTodayPage,
  '/vet/history': VetHistoryPage,
  '/vet/medicines': VetMedicinesPage,
  '/lab/queue': LabQueuePage,
  '/imaging/queue': ImagingQueuePage,
};

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
      <Route path="/portal/pets/:petId" element={<RequireRole role="PET_OWNER"><PetDetailPage /></RequireRole>} />

      {CUSTOMER_LINKS.filter((item) => item.to !== ROUTES.PROFILE).map((item) => (
        <Route key={item.to} path={item.to} element={guarded('PET_OWNER', item.to, <CustomerLayout><ComingSoonPage title={item.label} /></CustomerLayout>)} />
      ))}

      {Object.entries(STAFF_AREAS).map(([area, config]) => [
        <Route key={`${area}-index`} path={`/${area}`} element={<Navigate to={config.home} replace />} />,
        ...config.links.map((item) => (
          <Route key={item.to} path={item.to} element={guarded(config.role, item.to, <StaffLayout area={area} title={item.label}><ComingSoonPage title={item.label} /></StaffLayout>)} />
        )),
      ])}

      {/* Trang con của khu vực nhân viên (không có trong menu) */}
      <Route path="/vet/visits/:visitId" element={<RequireRole role="VETERINARIAN"><VetVisitPage /></RequireRole>} />
      <Route path="/lab/entry/:detailId" element={<RequireRole role="LAB_TECHNICIAN"><ParaclinicEntryPage kind="lab" /></RequireRole>} />
      <Route path="/imaging/entry/:detailId" element={<RequireRole role="IMAGING_TECHNICIAN"><ParaclinicEntryPage kind="imaging" /></RequireRole>} />

      <Route path="*" element={<Navigate to={ROUTES.HOME} replace />} />
    </Routes>
  );
}

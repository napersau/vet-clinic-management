import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from './hooks';
import AppRoutes from './routes';
import './App.css';

export default function App() {
  return (
    <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </BrowserRouter>
  );
}

import Footer from './Footer';
import Header from './Header';

// Khung trang công khai: header cố định + nội dung + footer.
export default function PublicLayout({ children, className = '' }) {
  return (
    <div className={`public-layout ${className}`}>
      <Header />
      <main id="top">{children}</main>
      <Footer />
    </div>
  );
}

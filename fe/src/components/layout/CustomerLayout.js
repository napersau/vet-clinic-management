import Footer from './Footer';
import Header from './Header';
import CustomerNavigation from './CustomerNavigation';
import '../../styles/customer.css';
import '../../styles/ui.css';

export default function CustomerLayout({ children }) {
  return (
    <div className="customer-shell">
      <Header />
      <CustomerNavigation />
      <main className="ui-portal"><div className="profile-container">{children}</div></main>
      <Footer />
    </div>
  );
}

import { useState } from 'react';
import './index.css';
import Topbar from './components/Topbar';
import Sidebar from './components/Sidebar';
import DashboardPage from './pages/DashboardPage';
import FilePoolPage from './pages/FilePoolPage';
import DatabasePage from './pages/DatabasePage';
import ProvisionerPage from './pages/ProvisionerPage';

function App() {
  const [activePage, setActivePage] = useState('dashboard');

  return (
    <>
      <Topbar />
      <div id="layout">
        <Sidebar activePage={activePage} setActivePage={setActivePage} />
        <div id="main">
          {activePage === 'dashboard' && <DashboardPage />}
          {activePage === 'filepool' && <FilePoolPage />}
          {activePage === 'database' && <DatabasePage />}
          {activePage === 'provisioner' && <ProvisionerPage />}
          {/* Add other pages here as needed */}
          {['topology', 'vector', 'compute', 'apikeys', 'analytics', 'emails', 'settings'].includes(activePage) && (
            <div className="page active">
              <div className="page-title">{activePage.toUpperCase()} - UNDER CONSTRUCTION</div>
              <div className="page-sub">This page is being migrated to React.</div>
            </div>
          )}
        </div>
      </div>
    </>
  );
}

export default App;

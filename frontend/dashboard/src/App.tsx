import { useState } from 'react';
import './index.css';
import Topbar from './components/Topbar';
import Sidebar from './components/Sidebar';
import DashboardPage from './pages/DashboardPage';
import FilePoolPage from './pages/FilePoolPage';
import DatabasePage from './pages/DatabasePage';
import ProvisionerPage from './pages/ProvisionerPage';
import TopologyPage from './pages/TopologyPage';
import VectorPage from './pages/VectorPage';
import ComputePage from './pages/ComputePage';
import ApiKeysPage from './pages/ApiKeysPage';
import AnalyticsPage from './pages/AnalyticsPage';
import EmailsPage from './pages/EmailsPage';
import SettingsPage from './pages/SettingsPage';

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
          {activePage === 'topology' && <TopologyPage />}
          {activePage === 'vector' && <VectorPage />}
          {activePage === 'compute' && <ComputePage />}
          {activePage === 'apikeys' && <ApiKeysPage />}
          {activePage === 'analytics' && <AnalyticsPage />}
          {activePage === 'emails' && <EmailsPage />}
          {activePage === 'settings' && <SettingsPage />}
        </div>
      </div>
    </>
  );
}

export default App;

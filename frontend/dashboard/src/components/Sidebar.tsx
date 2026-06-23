

type SidebarProps = {
  activePage: string;
  setActivePage: (page: string) => void;
};

export default function Sidebar({ activePage, setActivePage }: SidebarProps) {
  const navItems = [
    {
      id: 'dashboard',
      label: 'DASHBOARD',
      icon: (
        <svg className="nav-icon" viewBox="0 0 10 10" fill="none" stroke="currentColor" strokeWidth="1.5">
          <rect x="1" y="1" width="3.5" height="3.5"/><rect x="5.5" y="1" width="3.5" height="3.5"/>
          <rect x="1" y="5.5" width="3.5" height="3.5"/><rect x="5.5" y="5.5" width="3.5" height="3.5"/>
        </svg>
      )
    },
    {
      id: 'filepool',
      label: 'FILE POOL',
      icon: <svg className="nav-icon" viewBox="0 0 10 10" fill="none" stroke="currentColor" strokeWidth="1.5"><path d="M1 2h8M1 5h8M1 8h8"/></svg>
    },
    {
      id: 'database',
      label: 'DATABASE',
      icon: <svg className="nav-icon" viewBox="0 0 10 10" fill="none" stroke="currentColor" strokeWidth="1.5"><ellipse cx="5" cy="3" rx="4" ry="1.5"/><path d="M1 3v4c0 .83 1.79 1.5 4 1.5s4-.67 4-1.5V3"/></svg>
    },
    {
      id: 'topology',
      label: 'PROJECT TOPOLOGY',
      icon: <svg className="nav-icon" viewBox="0 0 10 10" fill="none" stroke="currentColor" strokeWidth="1.5"><rect x="1" y="1" width="3" height="3"/><rect x="6" y="1" width="3" height="3"/><rect x="1" y="6" width="3" height="3"/><rect x="6" y="6" width="3" height="3"/><path d="M4 2.5h2M2.5 4h5M4 7.5h2"/></svg>
    },
    {
      id: 'provisioner',
      label: 'DB PROVISIONER',
      icon: <svg className="nav-icon" viewBox="0 0 10 10" fill="none" stroke="currentColor" strokeWidth="1.5"><rect x="1" y="2" width="8" height="6" rx="1"/><line x1="3" y1="5" x2="7" y2="5"/></svg>
    },
    {
      id: 'vector',
      label: 'VECTOR SEARCH',
      icon: <svg className="nav-icon" viewBox="0 0 10 10" fill="none" stroke="currentColor" strokeWidth="1.5"><circle cx="5" cy="5" r="1"/><path d="M1.5 5h2M6.5 5h2M5 1.5v2M5 6.5v2"/></svg>
    },
    {
      id: 'compute',
      label: 'PaaS COMPUTE',
      icon: <svg className="nav-icon" viewBox="0 0 10 10" fill="none" stroke="currentColor" strokeWidth="1.5"><path d="M2 1h6a1 1 0 011 1v6a1 1 0 01-1 1H2a1 1 0 01-1-1V2a1 1 0 011-1zM2 4h6M4 1v8"/></svg>
    },
    {
      id: 'apikeys',
      label: 'API KEYS',
      icon: <svg className="nav-icon" viewBox="0 0 10 10" fill="none" stroke="currentColor" strokeWidth="1.5"><rect x="1" y="3" width="8" height="5" rx=".5"/><path d="M3 3V2a2 2 0 014 0v1"/></svg>
    },
    {
      id: 'analytics',
      label: 'ANALYTICS',
      icon: <svg className="nav-icon" viewBox="0 0 10 10" fill="none" stroke="currentColor" strokeWidth="1.5"><polyline points="1,7 3,4 5,5.5 7,2.5 9,3.5"/></svg>
    },
    {
      id: 'emails',
      label: 'EMAIL SANDBOX',
      icon: <svg className="nav-icon" viewBox="0 0 10 10" fill="none" stroke="currentColor" strokeWidth="1.5"><rect x="1" y="2" width="8" height="6" rx="1"/><path d="M1 2l4 3.5L9 2"/></svg>
    },
    {
      id: 'settings',
      label: 'SETTINGS',
      icon: <svg className="nav-icon" viewBox="0 0 10 10" fill="none" stroke="currentColor" strokeWidth="1.5"><circle cx="5" cy="5" r="1.5"/><path d="M5 1v1.5M5 7.5V9M1 5h1.5M7.5 5H9M2.1 2.1l1.1 1.1M6.8 6.8l1.1 1.1M7.9 2.1L6.8 3.2M3.2 6.8L2.1 7.9"/></svg>
    }
  ];

  return (
    <nav id="sidebar">
      {navItems.map(item => (
        <div 
          key={item.id}
          className={`nav-item ${activePage === item.id ? 'active' : ''}`}
          onClick={() => setActivePage(item.id)}
        >
          {item.icon}
          {item.label}
        </div>
      ))}
      <div className="sidebar-spacer"></div>
      <div className="sys-status">
        <div className="lbl">SYSTEM_STATUS</div>
        <div className="val"><span className="dot"></span>ONLINE / SECURE</div>
      </div>
    </nav>
  );
}

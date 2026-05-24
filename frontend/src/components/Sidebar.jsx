import React from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useSystemConfig } from '../context/SystemConfigContext';

const navMap = {
  PG_OWNER: [
    { to: '/owner/dashboard', icon: '📊', label: 'Dashboard' },
    { to: '/manager/maintenance',  icon: '🔧', label: 'Maintenance' },
    { to: '/owner/reports',   icon: '📈', label: 'Reports' },
    { to: '/owner/audit',     icon: '🗂️', label: 'Audit Log' },
  ],
  PG_MANAGER: [
    { to: '/manager/dashboard',    icon: '📊', label: 'Dashboard' },
    { to: '/manager/guests',       icon: '👥', label: 'Guests' },
    { to: '/manager/addons',       icon: '🥚', label: 'Daily Add-ons' },
    { to: '/manager/eb-bill',      icon: '⚡', label: 'EB Bill' },
    { to: '/manager/maintenance',  icon: '🔧', label: 'Maintenance' },
    { to: '/manager/reports',      icon: '📈', label: 'Reports' },
    { to: '/manager/audit',        icon: '🗂️', label: 'Audit Log' },
  ],
  GUEST: [
    { to: '/guest/dashboard', icon: '🏠', label: 'Dashboard' },
    { to: '/guest/daily-log', icon: '🍽️', label: 'Daily Log' },
    { to: '/guest/invoices',  icon: '🧾', label: 'Invoices' },
  ],
};

export default function Sidebar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { config } = useSystemConfig();
  const items = navMap[user?.role] || [];
  const brandName = config?.branding?.name || 'PG CRM';

  const handleLogout = async () => { await logout(); navigate('/login'); };

  return (
    <div className="sidebar">
      <div className="sidebar-logo">🏠 {brandName}</div>
      {items.map(item => (
        <NavLink key={item.to} to={item.to}
          className={({ isActive }) => `sidebar-item${isActive ? ' active' : ''}`}>
          <span style={{ fontSize: '1.1rem' }}>{item.icon}</span>
          <span>{item.label}</span>
        </NavLink>
      ))}
      <div style={{ marginTop: 'auto' }}>
        <div style={{ padding: '0.75rem 1rem', borderRadius: '10px', background: 'var(--bg-card)', marginBottom: '0.5rem' }}>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 600 }}>SIGNED IN AS</div>
          <div style={{ fontSize: '0.8rem', color: 'var(--accent)', fontWeight: 600, marginTop: '0.2rem' }}>{user?.role}</div>
        </div>
        <button id="btn-sidebar-logout" className="sidebar-item btn-ghost" onClick={handleLogout}
          style={{ color: 'var(--danger)', marginTop: '0.25rem' }}>
          <span>🚪</span><span>Logout</span>
        </button>
      </div>
    </div>
  );
}

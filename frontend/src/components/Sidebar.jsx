import React from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { 
  LayoutGrid, 
  Users, 
  ChefHat, 
  Zap, 
  Wrench, 
  BarChart3, 
  History, 
  CalendarDays, 
  FileText, 
  LogOut,
  Building2,
  Tag,
  Receipt,
  Settings as SettingsIcon
} from 'lucide-react';

const navMap = {
  PG_OWNER: [
    { to: '/owner/dashboard', label: 'Dashboard', icon: LayoutGrid },
    { to: '/owner/buildings', label: 'Buildings', icon: Building2 },
    { to: '/manager/maintenance', label: 'Maintenance', icon: Wrench },
    { to: '/owner/reports', label: 'Reports', icon: BarChart3 },
    { to: '/owner/audit', label: 'Audit Log', icon: History },
    { to: '/settings', label: 'Settings', icon: SettingsIcon },
  ],
  PG_MANAGER: [
    { to: '/manager/dashboard', label: 'Dashboard', icon: LayoutGrid },
    { to: '/manager/guests', label: 'Guests', icon: Users },
    { to: '/manager/addons', label: 'Meal & Add-on Tracker', icon: ChefHat },
    { to: '/manager/eb-bill', label: 'EB Bill', icon: Zap },
    { to: '/manager/invoices', label: 'Invoices', icon: Receipt },
    { to: '/manager/pricing', label: 'Pricing', icon: Tag },
    { to: '/manager/maintenance', label: 'Maintenance', icon: Wrench },
    { to: '/manager/reports', label: 'Reports', icon: BarChart3 },
    { to: '/manager/audit', label: 'Audit Log', icon: History },
    { to: '/settings', label: 'Settings', icon: SettingsIcon },
  ],
  GUEST: [
    { to: '/guest/dashboard', label: 'Dashboard', icon: LayoutGrid },
    { to: '/guest/daily-log', label: 'Meal Planner', icon: CalendarDays },
    { to: '/guest/invoices', label: 'Invoices', icon: FileText },
    { to: '/settings', label: 'Settings', icon: SettingsIcon },
  ],
};

export default function Sidebar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const items = navMap[user?.role] || [];

  const handleLogout = () => {
    // Clear local state first (synchronous), then navigate
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('user');
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <aside className="w-[190px] flex-shrink-0 bg-white border-r border-slate-200 flex flex-col justify-between p-4 h-full">
      <div className="flex flex-col gap-1">
        {items.map(item => {
          const Icon = item.icon;
          return (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => 
                `flex items-center gap-3 px-3 py-2 rounded-lg transition-colors text-sm font-medium ${
                  isActive 
                    ? 'bg-slate-50 text-primary font-semibold' 
                    : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
                }`
              }
            >
              {({ isActive }) => (
                <>
                  <Icon className={`w-4 h-4 transition-colors ${isActive ? 'text-primary' : 'text-slate-400'}`} />
                  <span>{item.label}</span>
                </>
              )}
            </NavLink>
          );
        })}
      </div>
      
      <div>
        <button 
          id="btn-sidebar-logout" 
          onClick={handleLogout}
          className="w-full flex items-center gap-3 px-3 py-2 rounded-lg text-red-600 hover:text-red-700 hover:bg-red-50/50 transition-colors text-sm font-medium"
        >
          <LogOut className="w-4 h-4 text-red-400" />
          <span>Logout</span>
        </button>
      </div>
    </aside>
  );
}

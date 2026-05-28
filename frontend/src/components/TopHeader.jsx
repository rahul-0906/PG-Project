import React from 'react';
import { useAuth } from '../context/AuthContext';
import { useSystemConfig } from '../context/SystemConfigContext';
import { Bell } from 'lucide-react';

const ROLE_LABELS = {
  PG_OWNER: 'Owner',
  PG_MANAGER: 'Manager',
  GUEST: 'Guest',
};

const ROLE_CLASSES = {
  PG_OWNER: 'bg-amber-50 text-amber-700 border-amber-200',
  PG_MANAGER: 'bg-violet-50 text-violet-700 border-violet-200',
  GUEST: 'bg-emerald-50 text-emerald-700 border-emerald-200',
};

const ROLE_DOTS = {
  PG_OWNER: 'bg-amber-500',
  PG_MANAGER: 'bg-violet-500',
  GUEST: 'bg-emerald-500',
};

export default function TopHeader() {
  const { user } = useAuth();
  const { config } = useSystemConfig();
  const brandName = config?.branding?.name || 'PG CRM';

  const badgeClass = ROLE_CLASSES[user?.role] || 'bg-slate-50 text-slate-700 border-slate-200';
  const dotClass = ROLE_DOTS[user?.role] || 'bg-slate-500';
  const userInitial = (() => {
    let name = user?.fullName?.trim() || '';
    if (name.toUpperCase().startsWith('PG ')) {
      name = name.substring(3).trim();
    } else if (name.toUpperCase() === 'PG') {
      name = '';
    }
    if (name) {
      return name.charAt(0).toUpperCase();
    }
    const label = ROLE_LABELS[user?.role] || 'User';
    return label.charAt(0).toUpperCase();
  })();

  return (
    <header className="fixed top-0 left-0 right-0 h-16 bg-white border-b border-slate-200 z-50 px-6 flex items-center justify-between">
      <div className="flex items-center gap-4">
        <span className="font-extrabold text-lg tracking-tight text-slate-900">
          {brandName}
        </span>
      </div>

      <div className="flex items-center gap-4">
        {/* User Badge */}
        <div className={`flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold border ${badgeClass}`}>
          <span className={`w-1.5 h-1.5 rounded-full ${dotClass}`} />
          {user?.fullName || ROLE_LABELS[user?.role] || user?.role}
        </div>

        {/* Notification Bell */}
        <button 
          className="p-2 rounded-lg border border-slate-200 bg-white text-slate-500 hover:text-slate-800 hover:bg-slate-50 transition-colors" 
          title="Notifications" 
          aria-label="Notifications"
        >
          <Bell className="w-4 h-4" />
        </button>

        {/* User Avatar */}
        <div 
          className="w-9 h-9 rounded-full bg-primary text-white flex items-center justify-center font-bold text-sm shadow-sm cursor-pointer select-none" 
          title={user?.fullName || 'User Profile'}
        >
          {userInitial}
        </div>
      </div>
    </header>
  );
}

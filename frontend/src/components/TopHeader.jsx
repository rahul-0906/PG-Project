import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { useSystemConfig } from '../context/SystemConfigContext';
import { Bell } from 'lucide-react';
import { managerApi } from '../api';

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

  const [assignedBuildings, setAssignedBuildings] = useState([]);
  const [selectedBranch, setSelectedBranch] = useState(() => localStorage.getItem('selectedBranchId') || '');

  useEffect(() => {
    if (user && (user.role === 'PG_MANAGER' || user.role === 'PG_OWNER')) {
      managerApi.getAssignedBuildings().then(res => {
        setAssignedBuildings(res.data || []);
        const stored = localStorage.getItem('selectedBranchId');
        if (res.data && res.data.length > 0) {
          const match = res.data.find(b => b.id === stored);
          if (!match) {
            localStorage.setItem('selectedBranchId', res.data[0].id);
            setSelectedBranch(res.data[0].id);
          }
        }
      }).catch(console.error);
    }
  }, [user]);

  const handleBranchChange = (e) => {
    const val = e.target.value;
    localStorage.setItem('selectedBranchId', val);
    setSelectedBranch(val);
    window.location.reload();
  };

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
      <div className="flex items-center gap-2.5">
        <div className="flex items-center justify-center w-8 h-8 rounded-lg bg-gradient-to-br from-indigo-500 to-indigo-700 text-white shadow-sm shadow-indigo-100">
          <svg className="w-4.5 h-4.5 fill-current" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M19 2H5C3.89543 2 3 2.89543 3 4V20C3 21.1046 3.89543 22 5 22H19C20.1046 22 21 21.1046 21 20V4C21 2.89543 20.1046 2 19 2ZM11 6H13V8H11V6ZM11 10H13V12H11V10ZM11 14H13V16H11V14ZM7 6H9V8H7V6ZM7 10H9V12H7V10ZM7 14H9V16H7V14ZM17 18H7V17H17V18ZM17 14H15V16H17V14ZM17 10H15V12H17V10ZM17 6H15V8H17V6Z" />
          </svg>
        </div>
        <span className="font-extrabold text-base tracking-tight text-slate-900">
          {brandName}
        </span>
      </div>

      <div className="flex items-center gap-4">
        {/* User Badge / Building Selector */}
        {user?.role === 'PG_MANAGER' && assignedBuildings.length > 1 ? (
          <select
            value={selectedBranch}
            onChange={handleBranchChange}
            className="form-input py-1 text-xs font-semibold"
            style={{ width: 'auto', minWidth: '160px', padding: '0.2rem 1.5rem 0.2rem 0.5rem', background: '#f5f3ff', border: '1px solid #ddd6fe', color: '#6d28d9', borderRadius: '9999px', cursor: 'pointer' }}
          >
            {assignedBuildings.map(b => (
              <option key={b.id} value={b.id}>🏢 {b.name}</option>
            ))}
          </select>
        ) : (
          <div className={`flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold border ${badgeClass}`}>
            <span className={`w-1.5 h-1.5 rounded-full ${dotClass}`} />
            {user?.fullName || ROLE_LABELS[user?.role] || user?.role}
          </div>
        )}

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

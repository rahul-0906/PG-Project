import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { useSystemConfig } from '../context/SystemConfigContext';
import { Bell } from 'lucide-react';
import { managerApi, notificationsApi } from '../api';
import { useQuery, useQueryClient } from '@tanstack/react-query';

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

  const queryClient = useQueryClient();
  const [showNotifications, setShowNotifications] = useState(false);

  const { data: notifications = [] } = useQuery({
    queryKey: ['guestNotifications'],
    queryFn: () => notificationsApi.getNotifications().then(res => res.data || []),
    enabled: !!user,
    refetchInterval: 10000
  });

  const formatTime = (timeStr) => {
    if (!timeStr) return '';
    try {
      const date = new Date(timeStr);
      const diffMs = Date.now() - date.getTime();
      const diffMins = Math.floor(diffMs / 60000);
      if (diffMins < 1) return 'Just now';
      if (diffMins < 60) return `${diffMins}m ago`;
      const diffHours = Math.floor(diffMins / 60);
      if (diffHours < 24) return `${diffHours}h ago`;
      return date.toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });
    } catch (e) {
      return '';
    }
  };

  const handleMarkRead = (id) => {
    notificationsApi.markRead(id)
      .then(() => {
        queryClient.invalidateQueries({ queryKey: ['guestNotifications'] });
        queryClient.invalidateQueries({ queryKey: ['guestDashboard'] });
      })
      .catch(console.error);
  };

  const handleMarkAllRead = () => {
    notificationsApi.markAllRead()
      .then(() => {
        queryClient.invalidateQueries({ queryKey: ['guestNotifications'] });
        queryClient.invalidateQueries({ queryKey: ['guestDashboard'] });
      })
      .catch(console.error);
  };

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
    const name = user?.fullName?.trim() || '';
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
        <span className="font-heading font-extrabold text-base tracking-tight text-slate-900">
          {brandName}
        </span>
      </div>

      <div className="flex items-center gap-4">
        {/* Building Selector */}
        {user?.role === 'PG_MANAGER' && assignedBuildings.length > 1 && (
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
        )}

        {/* User Badge / Pill */}
        <div className={`flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold border ${badgeClass}`}>
          <span className={`w-1.5 h-1.5 rounded-full ${dotClass}`} />
          {user?.fullName || ROLE_LABELS[user?.role] || 'User'}
        </div>

        {/* Notification Bell */}
        <div className="relative">
          <button 
            className="p-2 rounded-lg border border-slate-200 bg-white text-slate-500 hover:text-slate-800 hover:bg-slate-50 transition-colors relative" 
            title="Notifications" 
            aria-label="Notifications"
            onClick={() => setShowNotifications(!showNotifications)}
          >
            <Bell className="w-4 h-4" strokeWidth={1.5}/>
            {notifications.filter(n => !n.read).length > 0 && (
              <span className="absolute -top-1 -right-1 flex h-4 w-4 items-center justify-center rounded-full bg-rose-500 text-[9px] font-black text-white ring-2 ring-white animate-pulse">
                {notifications.filter(n => !n.read).length}
              </span>
            )}
          </button>

          {showNotifications && (
            <>
              <div className="fixed inset-0 z-40" onClick={() => setShowNotifications(false)} />
              <div className="absolute right-0 mt-2 w-80 bg-white/95 backdrop-blur-md rounded-xl border border-slate-200 shadow-xl z-50 overflow-hidden fade-in-up">
                <div className="px-4 py-2.5 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
                  <span className="text-xs font-bold text-slate-800">Notifications</span>
                  {notifications.filter(n => !n.read).length > 0 && (
                    <button 
                      onClick={handleMarkAllRead}
                      className="text-[10px] font-bold text-primary hover:text-primary-hover flex items-center gap-0.5 transition-colors bg-transparent border-0 cursor-pointer"
                    >
                      Mark all read
                    </button>
                  )}
                </div>
                <div className="max-h-64 overflow-y-auto divide-y divide-slate-100">
                  {notifications.length === 0 ? (
                    <div className="px-4 py-6 text-center text-slate-400 text-xs">
                      No notifications yet
                    </div>
                  ) : (
                    notifications.map(n => (
                      <div 
                        key={n.id} 
                        onClick={() => {
                          if (!n.read) handleMarkRead(n.id);
                        }}
                        className={`px-4 py-3 hover:bg-slate-50 transition-colors cursor-pointer flex items-start gap-2.5 ${!n.read ? 'bg-indigo-50/20' : ''}`}
                      >
                        <div className="flex-1">
                          <p className={`text-slate-700 text-xs leading-relaxed ${!n.read ? 'font-semibold text-slate-900' : ''}`}>
                            {n.message}
                          </p>
                          <span className="text-[9px] text-slate-400 font-medium mt-1 block">
                            {formatTime(n.sentAt)}
                          </span>
                        </div>
                        {!n.read && (
                          <span className="w-1.5 h-1.5 rounded-full bg-indigo-500 mt-1.5 flex-shrink-0" />
                        )}
                      </div>
                    ))
                  )}
                </div>
              </div>
            </>
          )}
        </div>

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

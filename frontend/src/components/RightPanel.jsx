import React, { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { ownerApi, managerApi, guestApi } from '../api';
import { X } from 'lucide-react';

function RadialProgress({ percent = 0, label, size = 64, strokeWidth = 6, color = '#2563eb' }) {
  const radius = (size - strokeWidth) / 2;
  const circumference = radius * 2 * Math.PI;
  const offset = circumference - (Math.min(100, Math.max(0, percent)) / 100) * circumference;

  return (
    <div className="flex flex-col items-center gap-2">
      <div className="relative" style={{ width: size, height: size }}>
        <svg width={size} height={size} className="-rotate-90">
          <circle
            className="fill-none stroke-slate-100"
            cx={size / 2}
            cy={size / 2}
            r={radius}
            strokeWidth={strokeWidth}
          />
          <circle
            className="fill-none transition-all duration-500 ease-out"
            cx={size / 2}
            cy={size / 2}
            r={radius}
            strokeWidth={strokeWidth}
            strokeDasharray={circumference}
            strokeDashoffset={offset}
            stroke={color}
            strokeLinecap="round"
          />
        </svg>
        <div className="absolute inset-0 flex items-center justify-center text-[11px] font-bold text-slate-800">
          {Math.round(percent)}%
        </div>
      </div>
      <div className="text-[10px] font-medium text-slate-500 text-center uppercase tracking-wider">{label}</div>
    </div>
  );
}

export default function RightPanel({ isOpen = false, onClose }) {
  const { user } = useAuth();
  const [stats, setStats] = useState(null);
  const [extraData, setExtraData] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!user || !isOpen) return;
    setLoading(true);

    if (user.role === 'PG_OWNER') {
      Promise.all([
        ownerApi.getDashboard(),
        ownerApi.getManagers()
      ]).then(([dashboardRes, managersRes]) => {
        setStats(dashboardRes.data);
        setExtraData(managersRes.data?.slice(0, 3) || []);
      }).catch(() => {})
        .finally(() => setLoading(false));
    } else if (user.role === 'PG_MANAGER') {
      Promise.all([
        managerApi.getDashboard(),
        managerApi.getMaintenanceTickets()
      ]).then(([dashboardRes, ticketsRes]) => {
        setStats(dashboardRes.data);
        setExtraData(ticketsRes.data?.slice(0, 3) || []);
      }).catch(() => {})
        .finally(() => setLoading(false));
    } else if (user.role === 'GUEST') {
      Promise.all([
        guestApi.getDashboard(),
        guestApi.getNotifications()
      ]).then(([dashboardRes, notificationsRes]) => {
        setStats(dashboardRes.data);
        setExtraData(notificationsRes.data?.slice(0, 3) || []);
      }).catch(() => {})
        .finally(() => setLoading(false));
    }
  }, [user, isOpen]);

  // Overlay Backdrop and Slide-Over structure
  return (
    <>
      {isOpen && (
        <div 
          className="fixed inset-0 bg-slate-900/20 backdrop-blur-sm z-30 transition-opacity duration-300"
          onClick={onClose}
        />
      )}
      <aside 
        className={`fixed right-0 top-16 bottom-0 w-[300px] bg-white border-l border-slate-200 shadow-2xl z-40 transform transition-transform duration-300 ease-in-out ${
          isOpen ? 'translate-x-0' : 'translate-x-full'
        } flex flex-col p-6 gap-6 overflow-y-auto`}
      >
        <div className="flex items-center justify-between border-b border-slate-100 pb-4">
          <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider">
            {user?.role === 'PG_OWNER' && 'Owner Dashboard metrics'}
            {user?.role === 'PG_MANAGER' && 'Live operational status'}
            {user?.role === 'GUEST' && 'Personal logs & feeds'}
          </h3>
          <button 
            onClick={onClose}
            className="p-1 rounded-md text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors"
            title="Close Panel"
          >
            <X className="w-4.5 h-4.5" />
          </button>
        </div>

        {loading ? (
          <div className="flex-1 flex items-center justify-center text-slate-400 text-xs font-medium">
            ⏳ Loading live feeds...
          </div>
        ) : (
          <div className="flex flex-col gap-6">
            {/* 1. OWNER VIEW */}
            {user?.role === 'PG_OWNER' && (
              <>
                <div className="bg-slate-50 border border-slate-100 rounded-xl p-4">
                  <h4 className="text-xs font-bold text-slate-800 mb-3 uppercase tracking-wider">Performance Ring</h4>
                  <div className="grid grid-cols-2 gap-4 justify-items-center">
                    <RadialProgress 
                      percent={stats?.totalBeds > 0 ? (stats.occupiedBeds / stats.totalBeds) * 100 : 0} 
                      label="Occupancy" 
                      color="var(--color-primary)"
                    />
                    <RadialProgress 
                      percent={92} 
                      label="Collections" 
                      color="#10b981" 
                    />
                  </div>
                </div>

                <div className="bg-slate-50 border border-slate-100 rounded-xl p-4">
                  <h4 className="text-xs font-bold text-slate-800 mb-3 uppercase tracking-wider">Active Managers</h4>
                  <div className="flex flex-col">
                    {extraData.map(m => (
                      <div key={m.id} className="flex items-center justify-between py-2 border-b border-slate-100 last:border-0 last:pb-0">
                        <div className="flex flex-col min-w-0 pr-2">
                          <span className="text-xs font-bold text-slate-700 truncate">{m.fullName}</span>
                          <span className="text-[10px] text-slate-400 truncate">{m.email}</span>
                        </div>
                        <span className="bg-emerald-50 text-emerald-700 text-[10px] font-bold px-2 py-0.5 rounded-full border border-emerald-100 flex-shrink-0">
                          Active
                        </span>
                      </div>
                    ))}
                    {extraData.length === 0 && (
                      <div className="text-[11px] text-slate-400">No managers assigned.</div>
                    )}
                  </div>
                </div>
              </>
            )}

            {/* 2. MANAGER VIEW */}
            {user?.role === 'PG_MANAGER' && (
              <>
                <div className="bg-slate-50 border border-slate-100 rounded-xl p-4">
                  <h4 className="text-xs font-bold text-slate-800 mb-3 uppercase tracking-wider">Live Metrics</h4>
                  <div className="grid grid-cols-2 gap-4 justify-items-center">
                    <RadialProgress 
                      percent={stats?.totalBeds > 0 ? (stats.occupiedBeds / stats.totalBeds) * 100 : 0} 
                      label="Occupancy" 
                      color="var(--color-primary)"
                    />
                    <RadialProgress 
                      percent={stats?.pendingMaintenanceTickets > 0 ? 50 : 100} 
                      label="Ticket Health" 
                      color="#f97316" 
                    />
                  </div>
                </div>

                <div className="bg-slate-50 border border-slate-100 rounded-xl p-4">
                  <h4 className="text-xs font-bold text-slate-800 mb-3 uppercase tracking-wider">Recent Tickets</h4>
                  <div className="flex flex-col">
                    {extraData.map(ticket => (
                      <div key={ticket.id} className="flex items-center justify-between py-2 border-b border-slate-100 last:border-0 last:pb-0 gap-2">
                        <div className="flex flex-col min-w-0 flex-1">
                          <span className="text-xs font-bold text-slate-700 truncate" title={ticket.description}>
                            {ticket.description}
                          </span>
                          <span className="text-[10px] text-slate-400 truncate">Loc: {ticket.location} • {ticket.priority}</span>
                        </div>
                        <span className={`text-[9px] font-bold px-2 py-0.5 rounded-full border flex-shrink-0 ${
                          ticket.status === 'PENDING' 
                            ? 'bg-amber-50 text-amber-700 border-amber-100' 
                            : 'bg-emerald-50 text-emerald-700 border-emerald-100'
                        }`}>
                          {ticket.status}
                        </span>
                      </div>
                    ))}
                    {extraData.length === 0 && (
                      <div className="text-[11px] text-slate-400">No maintenance tickets.</div>
                    )}
                  </div>
                </div>
              </>
            )}

            {/* 3. GUEST VIEW */}
            {user?.role === 'GUEST' && (
              <>
                <div className="bg-slate-50 border border-slate-100 rounded-xl p-4">
                  <h4 className="text-xs font-bold text-slate-800 mb-3 uppercase tracking-wider">Weekly Attendance</h4>
                  <div className="grid grid-cols-2 gap-4 justify-items-center">
                    <RadialProgress 
                      percent={85} 
                      label="Logs" 
                      color="#10b981" 
                    />
                    <RadialProgress 
                      percent={stats?.unreadNotifications === 0 ? 100 : 33} 
                      label="Notifications" 
                      color="#f97316" 
                    />
                  </div>
                </div>

                <div className="bg-slate-50 border border-slate-100 rounded-xl p-4">
                  <h4 className="text-xs font-bold text-slate-800 mb-3 uppercase tracking-wider">Recent Alerts</h4>
                  <div className="flex flex-col">
                    {extraData.map(n => (
                      <div key={n.id} className="flex items-start justify-between py-2 border-b border-slate-100 last:border-0 last:pb-0 gap-2">
                        <div className="flex flex-col min-w-0 flex-1">
                          <span className="text-xs font-bold text-slate-700 truncate">{n.title}</span>
                          <span className="text-[10px] text-slate-400 truncate" title={n.message}>{n.message}</span>
                        </div>
                        {!n.isRead && (
                          <span className="w-1.5 h-1.5 rounded-full bg-red-500 mt-1.5 flex-shrink-0" />
                        )}
                      </div>
                    ))}
                    {extraData.length === 0 && (
                      <div className="text-[11px] text-slate-400">No new alerts.</div>
                    )}
                  </div>
                </div>
              </>
            )}
          </div>
        )}
      </aside>
    </>
  );
}

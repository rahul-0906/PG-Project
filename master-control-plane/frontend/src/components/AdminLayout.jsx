import React from 'react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { LayoutDashboard, Users, Shield, LogOut, ChevronRight } from 'lucide-react';

export default function AdminLayout() {
  const location = useLocation();

  const navigation = [
    { name: 'Dashboard', path: '/admin', icon: LayoutDashboard },
    { name: 'Client Roster', path: '/admin/clients', icon: Users },
  ];

  return (
    <div className="bg-[#fafaf9] text-[#141414] min-h-screen font-sans flex overflow-hidden">
      {/* Sidebar navigation (Stark black theme) */}
      <aside className="w-64 bg-black text-white border-r-2 border-black flex flex-col shrink-0">
        {/* Logo and Branding Header */}
        <div className="p-6 border-b border-neutral-900 flex items-center space-x-3">
          <div className="w-9 h-9 rounded-full bg-white flex items-center justify-center shrink-0">
            <Shield className="w-5 h-5 text-black" />
          </div>
          <div>
            <h1 className="font-black text-sm text-white">
              Control Plane
            </h1>
            <p className="text-neutral-400 text-[10px] uppercase font-bold tracking-widest mt-0.5">
              Platform Admin
            </p>
          </div>
        </div>

        {/* Links Navigation */}
        <nav className="flex-1 px-4 py-6 space-y-2">
          {navigation.map((item) => {
            const isActive = location.pathname === item.path;
            const Icon = item.icon;
            return (
              <Link
                key={item.name}
                to={item.path}
                className={`flex items-center justify-between px-4 py-3 rounded-full text-xs font-black uppercase tracking-wider transition-all duration-150 group ${
                  isActive
                    ? 'bg-white text-black shadow-sm'
                    : 'text-neutral-400 hover:text-white hover:bg-neutral-900'
                }`}
              >
                <div className="flex items-center space-x-3">
                  <Icon className={`w-4 h-4 ${isActive ? 'text-black' : 'text-neutral-400 group-hover:text-white'}`} />
                  <span>{item.name}</span>
                </div>
                <ChevronRight className={`w-3.5 h-3.5 opacity-0 group-hover:opacity-100 transition-opacity ${isActive ? 'text-black' : 'text-neutral-500'}`} />
              </Link>
            );
          })}
        </nav>

        {/* User Footer */}
        <div className="p-4 border-t border-neutral-900 bg-neutral-950/40">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-2.5">
              <div className="w-8 h-8 rounded-full bg-white flex items-center justify-center font-bold text-xs text-black shrink-0">
                AD
              </div>
              <div className="truncate w-28 text-neutral-300">
                <p className="text-xs font-black truncate">Administrator</p>
                <p className="text-[9px] text-neutral-400 truncate uppercase tracking-widest font-bold">admin@pgcrm.com</p>
              </div>
            </div>
            <Link
              to="/"
              className="p-2 rounded-full hover:bg-red-500/10 text-neutral-400 hover:text-red-400 transition-colors"
              title="Logout"
            >
              <LogOut className="w-4 h-4" />
            </Link>
          </div>
        </div>
      </aside>

      {/* Main Admin Page Container */}
      <main className="flex-1 flex flex-col overflow-y-auto relative">
        {/* Top Header Navbar */}
        <header className="bg-white border-b-2 border-black px-8 py-5 flex justify-between items-center sticky top-0 z-20 text-black">
          <h2 className="text-lg font-black uppercase tracking-tight">
            {location.pathname === '/admin' ? 'Dashboard Metrics' : 'B2B Client Directory'}
          </h2>
          <div className="flex items-center space-x-3 text-xs text-neutral-500 font-bold uppercase tracking-wider">
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-ping animate-pulse" />
            <span>Database Monitor: Online</span>
          </div>
        </header>

        {/* Render child pages */}
        <div className="flex-1 p-8 relative z-10">
          <Outlet />
        </div>
      </main>
    </div>
  );
}

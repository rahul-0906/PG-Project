import React from 'react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { LayoutDashboard, Users, Server, Shield, LogOut, ChevronRight } from 'lucide-react';

export default function AdminLayout() {
  const location = useLocation();

  const navigation = [
    { name: 'Dashboard', path: '/admin', icon: LayoutDashboard },
    { name: 'Client Roster', path: '/admin/clients', icon: Users },
  ];

  return (
    <div className="bg-[#0b0f19] text-white min-h-screen font-sans flex overflow-hidden">
      {/* Sidebar navigation */}
      <aside className="w-64 bg-[#12182b] border-r border-gray-800 flex flex-col shrink-0">
        {/* Logo and Branding Header */}
        <div className="p-6 border-b border-gray-800 flex items-center space-x-3">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-indigo-500 to-blue-600 flex items-center justify-center shadow-lg shadow-indigo-500/10">
            <Shield className="w-5 h-5 text-white" />
          </div>
          <div>
            <h1 className="font-bold text-sm bg-gradient-to-r from-white to-gray-400 bg-clip-text text-transparent">
              Control Plane
            </h1>
            <p className="text-gray-500 text-[10px] uppercase font-bold tracking-widest mt-0.5">
              Platform Admin
            </p>
          </div>
        </div>

        {/* Links Navigation */}
        <nav className="flex-1 px-4 py-6 space-y-1.5">
          {navigation.map((item) => {
            const isActive = location.pathname === item.path;
            const Icon = item.icon;
            return (
              <Link
                key={item.name}
                to={item.path}
                className={`flex items-center justify-between px-4 py-3 rounded-xl text-sm font-medium transition-all duration-150 group ${
                  isActive
                    ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-600/15'
                    : 'text-gray-400 hover:text-white hover:bg-gray-800/40'
                }`}
              >
                <div className="flex items-center space-x-3">
                  <Icon className={`w-4 h-4 ${isActive ? 'text-white' : 'text-gray-400 group-hover:text-white'}`} />
                  <span>{item.name}</span>
                </div>
                <ChevronRight className={`w-3.5 h-3.5 opacity-0 group-hover:opacity-100 transition-opacity ${isActive ? 'text-indigo-200' : 'text-gray-500'}`} />
              </Link>
            );
          })}
        </nav>

        {/* User Footer and Log out */}
        <div className="p-4 border-t border-gray-800 bg-[#0c0f1d]/30">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-2.5">
              <div className="w-8 h-8 rounded-full bg-indigo-500/10 border border-indigo-500/30 flex items-center justify-center font-bold text-xs text-indigo-400">
                AD
              </div>
              <div className="truncate w-28">
                <p className="text-xs font-semibold text-gray-300 truncate">Administrator</p>
                <p className="text-[10px] text-gray-500 truncate">admin@pgcrm.com</p>
              </div>
            </div>
            <Link
              to="/"
              className="p-2 rounded-lg hover:bg-red-500/10 text-gray-400 hover:text-red-400 transition-colors"
              title="Logout"
            >
              <LogOut className="w-4 h-4" />
            </Link>
          </div>
        </div>
      </aside>

      {/* Main Admin Page Container */}
      <main className="flex-1 flex flex-col overflow-y-auto relative">
        {/* Glow Effects */}
        <div className="absolute top-0 right-0 w-[400px] h-[400px] bg-indigo-500/5 blur-[120px] rounded-full pointer-events-none" />
        
        {/* Top Header Navbar */}
        <header className="bg-[#12182b]/60 backdrop-blur border-b border-gray-800/80 px-8 py-5 flex justify-between items-center sticky top-0 z-20">
          <h2 className="text-xl font-bold tracking-tight">
            {location.pathname === '/admin' ? 'Dashboard Metrics' : 'B2B Client Directory'}
          </h2>
          <div className="flex items-center space-x-3 text-xs text-gray-400 font-medium">
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-ping" />
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

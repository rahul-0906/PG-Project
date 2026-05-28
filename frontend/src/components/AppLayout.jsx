import React from 'react';
import Sidebar from './Sidebar';
import TopHeader from './TopHeader';

export default function AppLayout({ children }) {
  return (
    <div className="h-screen w-full flex flex-col overflow-hidden bg-app">
      <TopHeader />
      <div className="flex-1 flex overflow-hidden pt-16">
        <Sidebar />
        <main className="flex-1 overflow-y-auto p-8 min-w-0">
          <div className="max-w-6xl mx-auto fade-in">
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}

import React, { useState } from 'react';
import Sidebar from './Sidebar';
import TopHeader from './TopHeader';
import RightPanel from './RightPanel';

export default function AppLayout({ children, showRightPanel = false }) {
  const [isRightPanelOpen, setIsRightPanelOpen] = useState(false);

  return (
    <div className="h-screen w-full flex flex-col overflow-hidden bg-app">
      <TopHeader 
        showPanelToggle={showRightPanel} 
        isPanelOpen={isRightPanelOpen}
        onToggleRightPanel={() => setIsRightPanelOpen(!isRightPanelOpen)} 
      />
      <div className="flex-1 flex overflow-hidden pt-16">
        <Sidebar />
        <main className="flex-1 overflow-y-auto p-8 min-w-0">
          <div className="max-w-6xl mx-auto fade-in">
            {children}
          </div>
        </main>
        {showRightPanel && (
          <RightPanel isOpen={isRightPanelOpen} onClose={() => setIsRightPanelOpen(false)} />
        )}
      </div>
    </div>
  );
}

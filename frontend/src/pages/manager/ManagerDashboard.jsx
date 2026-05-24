import React, { useEffect, useState } from 'react';
import Sidebar from '../../components/Sidebar';
import { managerApi } from '../../api';
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer } from 'recharts';

export default function ManagerDashboard() {
  const [data, setData] = useState(null);

  useEffect(() => { managerApi.getDashboard().then(r => setData(r.data)).catch(() => {}); }, []);

  const pieData = data ? [
    { name: 'Occupied', value: data.occupiedBeds },
    { name: 'Vacant', value: data.vacantBeds },
  ] : [];
  const COLORS = ['#6366f1', '#10b981'];

  return (
    <div className="layout">
      <Sidebar />
      <div className="main-content fade-in">
        <div className="page-header">
          <div>
            <h1 className="page-title">Manager Dashboard 👔</h1>
            <p className="page-subtitle">Live occupancy and operations overview</p>
          </div>
        </div>

        <div className="grid-4" style={{ marginBottom: '1.5rem' }}>
          {[
            { label: '🛏️ Total Beds', val: data?.totalBeds ?? '—' },
            { label: '✅ Occupied', val: data?.occupiedBeds ?? '—' },
            { label: '🟢 Vacant', val: data?.vacantBeds ?? '—' },
            { label: '🔧 Open Tickets', val: data?.pendingMaintenanceTickets ?? '—' },
          ].map(s => (
            <div key={s.label} className="stat-card">
              <div className="stat-value">{s.val}</div>
              <div className="stat-label">{s.label}</div>
            </div>
          ))}
        </div>

        <div className="grid-2">
          <div className="card">
            <h3 style={{ marginBottom: '1rem', fontWeight: 700 }}>🏠 Bed Occupancy</h3>
            {data && (
              <ResponsiveContainer width="100%" height={220}>
                <PieChart>
                  <Pie data={pieData} cx="50%" cy="50%" innerRadius={60} outerRadius={90}
                    dataKey="value" paddingAngle={3}>
                    {pieData.map((_, i) => <Cell key={i} fill={COLORS[i]} />)}
                  </Pie>
                  <Tooltip contentStyle={{ background: '#1a1a2e', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '10px', color: '#f1f5f9' }} />
                </PieChart>
              </ResponsiveContainer>
            )}
            <div style={{ display: 'flex', justifyContent: 'center', gap: '1.5rem', marginTop: '0.5rem' }}>
              {pieData.map((d, i) => (
                <div key={d.name} style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.85rem' }}>
                  <span style={{ width: 10, height: 10, borderRadius: '50%', background: COLORS[i], display: 'inline-block' }} />
                  {d.name}: <strong>{d.value}</strong>
                </div>
              ))}
            </div>
          </div>

          <div className="card">
            <h3 style={{ marginBottom: '1rem', fontWeight: 700 }}>⚡ Quick Actions</h3>
            {[
              { href: '/manager/guests', icon: '👥', label: 'Manage Guests' },
              { href: '/manager/eb-bill', icon: '⚡', label: 'Record EB Bill' },
              { href: '/manager/maintenance', icon: '🔧', label: 'View Maintenance' },
            ].map(a => (
              <a key={a.href} href={a.href} style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', padding: '0.875rem', borderRadius: '10px', background: 'var(--bg-secondary)', marginBottom: '0.6rem', textDecoration: 'none', color: 'var(--text-primary)', transition: 'all 0.2s' }}
                onMouseEnter={e => e.currentTarget.style.background = 'var(--accent-glow)'}
                onMouseLeave={e => e.currentTarget.style.background = 'var(--bg-secondary)'}>
                <span style={{ fontSize: '1.2rem' }}>{a.icon}</span>
                <span style={{ fontWeight: 500 }}>{a.label}</span>
                <span style={{ marginLeft: 'auto', color: 'var(--text-muted)' }}>→</span>
              </a>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

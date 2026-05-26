import React, { useEffect, useState } from 'react';
import AppLayout from '../../components/AppLayout';
import { managerApi } from '../../api';
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer } from 'recharts';
import { 
  LayoutGrid, 
  Bed, 
  CheckCircle2, 
  Circle, 
  Wrench, 
  PieChart as ChartIcon, 
  Zap, 
  Users, 
  ArrowRight 
} from 'lucide-react';

function StatCard({ label, value, icon: Icon, iconColor = 'text-slate-400' }) {
  return (
    <div className="stat-card flex flex-col gap-1.5">
      <div className="flex items-center justify-between w-full">
        <span className="stat-label">{label}</span>
        {Icon && <Icon className={`w-4 h-4 ${iconColor}`} />}
      </div>
      <div className="stat-value">{value}</div>
    </div>
  );
}

export default function ManagerDashboard() {
  const [data, setData] = useState(null);

  useEffect(() => { managerApi.getDashboard().then(r => setData(r.data)).catch(() => {}); }, []);

  const pieData = data ? [
    { name: 'Occupied', value: data.occupiedBeds },
    { name: 'Vacant', value: data.vacantBeds },
  ] : [];
  const COLORS = ['#2563eb', '#10b981'];

  return (
    <AppLayout showRightPanel={true}>
      <div className="page-header">
        <div>
          <h1 className="page-title flex items-center gap-2">
            <LayoutGrid className="w-6 h-6 text-primary" />
            <span>Manager Dashboard</span>
          </h1>
          <p className="page-subtitle">Live occupancy and operations overview</p>
        </div>
      </div>

      <div className="grid-4" style={{ marginBottom: '1.5rem' }}>
        <StatCard label="Total Beds" value={data?.totalBeds ?? '—'} icon={Bed} iconColor="text-blue-500" />
        <StatCard label="Occupied" value={data?.occupiedBeds ?? '—'} icon={CheckCircle2} iconColor="text-emerald-500" />
        <StatCard label="Vacant" value={data?.vacantBeds ?? '—'} icon={Circle} iconColor="text-slate-400" />
        <StatCard label="Open Tickets" value={data?.pendingMaintenanceTickets ?? '—'} icon={Wrench} iconColor="text-rose-500" />
      </div>

      <div className="grid-2">
        <div className="card">
          <h3 className="text-slate-800 font-bold mb-4 flex items-center gap-2">
            <ChartIcon className="w-5 h-5 text-slate-400" />
            <span>Bed Occupancy</span>
          </h3>
          {data && (
            <ResponsiveContainer width="100%" height={220}>
              <PieChart>
                <Pie data={pieData} cx="50%" cy="50%" innerRadius={60} outerRadius={90}
                  dataKey="value" paddingAngle={3}>
                  {pieData.map((_, i) => <Cell key={i} fill={COLORS[i]} />)}
                </Pie>
                <Tooltip contentStyle={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '10px', color: '#0f172a' }} />
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
          <h3 className="text-slate-800 font-bold mb-4 flex items-center gap-2">
            <Zap className="w-5 h-5 text-slate-400" />
            <span>Quick Actions</span>
          </h3>
          {[
            { href: '/manager/guests', icon: Users, label: 'Manage Guests', color: 'text-violet-500' },
            { href: '/manager/eb-bill', icon: Zap, label: 'Record EB Bill', color: 'text-amber-500' },
            { href: '/manager/maintenance', icon: Wrench, label: 'View Maintenance', color: 'text-rose-500' },
          ].map(a => {
            const Icon = a.icon;
            return (
              <a key={a.href} href={a.href} className="flex items-center gap-3 p-3.5 rounded-xl bg-slate-50 border border-slate-100 mb-2 hover:bg-slate-100/50 transition-colors text-slate-700 hover:text-slate-900 font-semibold text-sm">
                <Icon className={`w-4 h-4 ${a.color}`} />
                <span>{a.label}</span>
                <ArrowRight className="w-4 h-4 ml-auto text-slate-300" />
              </a>
            );
          })}
        </div>
      </div>
    </AppLayout>
  );
}

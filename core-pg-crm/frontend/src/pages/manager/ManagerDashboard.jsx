import React, { useEffect, useState } from 'react';
import AppLayout from '../../components/AppLayout';
import { managerApi } from '../../api';
import { useAuth } from '../../context/AuthContext';
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
  ArrowRight,
  User as UserIcon,
  IndianRupee,
  Loader2,
  Check
} from 'lucide-react';

import { useQuery } from '@tanstack/react-query';

function StatCard({ label, value, icon: Icon, iconColor = 'text-slate-500' }) {
  return (
    <div className="bg-white rounded-xl border border-slate-200 p-3.5 flex items-center gap-3.5 shadow-sm hover:shadow-md hover:border-primary/30 transition-all duration-200">
      {Icon && (
        <div className={`${iconColor} flex items-center justify-center`}>
          <Icon className="w-5 h-5" strokeWidth={1.5} />
        </div>
      )}
      <div>
        <div className="text-xs font-medium uppercase tracking-wider text-slate-500">{label}</div>
        <div className="text-xl font-bold text-slate-900 tracking-tight mt-0.5">{value}</div>
      </div>
    </div>
  );
}

export default function ManagerDashboard() {
  const { user } = useAuth();
  const [verifyingId, setVerifyingId] = useState('');
  const [toast, setToast] = useState('');

  const showToast = (msg) => {
    setToast(msg);
    setTimeout(() => setToast(''), 3000);
  };
  
  const activeBuildingId = sessionStorage.getItem('selectedBranchId') || (user?.branchId ? user.branchId.split(',')[0] : null);

  const { data, isLoading } = useQuery({
    queryKey: ['managerDashboard', activeBuildingId],
    queryFn: () => managerApi.getDashboard(activeBuildingId).then(r => r.data),
  });

  const { data: buildings } = useQuery({
    queryKey: ['assignedBuildings'],
    queryFn: () => managerApi.getAssignedBuildings().then(r => r.data),
    enabled: !!user,
  });

  const activeBuilding = buildings?.find(b => b.id === activeBuildingId);
  const activeBuildingName = activeBuilding?.name || data?.buildingName || 'Loading...';

  const { data: pendingCash, refetch: refetchPendingCash } = useQuery({
    queryKey: ['pendingCashInvoices'],
    queryFn: () => managerApi.getPendingCashInvoices().then(r => r.data),
    placeholderData: [],
  });

  const handleVerifyCash = async (invoiceId) => {
    setVerifyingId(invoiceId);
    try {
      await managerApi.verifyCash(invoiceId);
      showToast('Cash payment verified successfully!');
      refetchPendingCash();
    } catch (err) {
      showToast('Failed to verify cash payment');
    } finally {
      setVerifyingId('');
    }
  };

  const pieData = data ? [
    { name: 'Occupied', value: data.occupiedBeds },
    { name: 'Vacant', value: data.vacantBeds },
  ] : [];
  const COLORS = ['#2563eb', '#10b981'];

  return (
    <AppLayout>
      {/* Premium Welcome Banner */}
      <div className="relative overflow-hidden bg-gradient-to-br from-indigo-600 via-blue-600 to-violet-700 rounded-2xl p-4 sm:p-5 shadow-md shadow-indigo-100/60 mb-6 text-white">
        <div className="absolute right-0 top-0 translate-x-12 -translate-y-12 w-48 h-48 bg-white/5 rounded-full blur-3xl pointer-events-none" />
        <div className="absolute left-1/3 bottom-0 translate-y-1/2 w-36 h-36 bg-indigo-500/10 rounded-full blur-2xl pointer-events-none" />
        
        <div className="relative z-10 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
          <div className="flex items-center gap-3 sm:gap-4">
            <div className="p-2 sm:p-2.5 bg-white/10 backdrop-blur-md rounded-xl border border-white/10 shadow-inner">
              <UserIcon className="w-5 h-5 sm:w-6 sm:h-6 text-indigo-100" />
            </div>
            <div>
              <span className="text-indigo-200 text-[10px] sm:text-xs font-semibold tracking-wide uppercase">Manager Portal</span>
              <h1 className="text-lg sm:text-xl font-extrabold tracking-tight mt-0.5">
                Welcome back, {user?.fullName || 'Manager'}
              </h1>
              <p className="text-indigo-100 text-xs mt-1 font-semibold">
                Managing: {activeBuildingName}
              </p>
              <div className="flex flex-wrap gap-2 mt-2">
                <span className="bg-white/10 backdrop-blur-md px-2.5 py-0.5 rounded-full text-[9px] font-bold uppercase tracking-wider border border-white/5">
                  Beds: {data?.occupiedBeds ?? 0} / {data?.totalBeds ?? 0} Occupied
                </span>
                <span className="bg-white/10 backdrop-blur-md px-2.5 py-0.5 rounded-full text-[9px] font-bold uppercase tracking-wider border border-white/5">
                  Pending Tickets: {data?.pendingMaintenanceTickets ?? 0}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div className="page-header">
        <div>
          <h1 className="page-title flex items-center gap-2">
            <LayoutGrid className="w-6 h-6 text-primary" strokeWidth={1.5} />
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

      {pendingCash && pendingCash.length > 0 && (
        <div className="card mb-6" style={{ padding: '1.25rem' }}>
          <div className="flex items-center justify-between mb-4">
            <h3 className="font-heading text-base font-semibold text-slate-900 flex items-center gap-2">
              <IndianRupee className="w-5 h-5 text-emerald-500" strokeWidth={1.5} />
              <span>Pending Cash Verifications</span>
            </h3>
            <span className="badge badge-warning text-xs font-semibold px-2.5 py-1">
              {pendingCash.length} approvals pending
            </span>
          </div>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Guest</th>
                  <th>Room / Bed</th>
                  <th>Invoice Period</th>
                  <th className="text-right">Amount</th>
                  <th className="text-right">Action</th>
                </tr>
              </thead>
              <tbody>
                {pendingCash.map(inv => (
                  <tr key={inv.id}>
                    <td className="font-semibold text-slate-900">
                      {inv.guest?.fullName || 'Unknown'}
                    </td>
                    <td className="text-slate-700">
                      {inv.guest?.bed?.room?.roomNumber ? `Room ${inv.guest.bed.room.roomNumber}` : 'Unassigned'}
                      {inv.guest?.bed?.bedLabel ? ` - Bed ${inv.guest.bed.bedLabel}` : ''}
                    </td>
                    <td className="text-slate-600 font-semibold">
                      {inv.month}/{inv.year}
                    </td>
                    <td className="text-right font-bold text-slate-900">
                      ₹{parseFloat(inv.totalAmount || 0).toLocaleString('en-IN', { maximumFractionDigits: 0 })}
                    </td>
                    <td>
                      <div className="flex justify-end">
                        <button
                          className="btn btn-success text-xxs py-1.5 px-3 flex items-center gap-1.5 font-bold shadow-sm"
                          onClick={() => handleVerifyCash(inv.id)}
                          disabled={verifyingId === inv.id}
                        >
                          {verifyingId === inv.id ? (
                            <Loader2 className="w-3.5 h-3.5 animate-spin" strokeWidth={1.5} />
                          ) : (
                            <CheckCircle2 className="w-3.5 h-3.5" strokeWidth={1.5} />
                          )}
                          {verifyingId === inv.id ? 'Verifying...' : 'Verify Cash'}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <div className="grid-2">
        <div className="card">
          <h3 className="font-heading text-base font-semibold text-slate-900 mb-4 flex items-center gap-2">
            <ChartIcon className="w-5 h-5 text-slate-400" strokeWidth={1.5} />
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
          <h3 className="font-heading text-base font-semibold text-slate-900 mb-4 flex items-center gap-2">
            <Zap className="w-5 h-5 text-slate-400" strokeWidth={1.5} />
            <span>Quick Actions</span>
          </h3>
          {[
            { href: '/manager/guests', icon: Users, label: 'Manage Guests', color: 'text-violet-500' },
            { href: '/manager/eb-bill', icon: Zap, label: 'Record EB Bill', color: 'text-amber-500' },
            { href: '/manager/maintenance', icon: Wrench, label: 'View Maintenance', color: 'text-rose-500' },
          ].map(a => {
            const Icon = a.icon;
            return (
              <a key={a.href} href={a.href} className="flex items-center gap-2 p-3.5 rounded-xl bg-slate-50 border border-slate-200 mb-2 hover:bg-slate-100/50 transition-colors text-slate-700 hover:text-slate-900 font-semibold text-sm">
                <Icon className={`w-5 h-5 ${a.color}`} strokeWidth={1.5} />
                <span>{a.label}</span>
                <ArrowRight className="w-4 h-4 ml-auto text-slate-300" strokeWidth={1.5} />
              </a>
            );
          })}
        </div>
      </div>

      {toast && (
        <div className="fixed top-4 right-4 z-[9999] bg-green-600 text-white px-4 py-2.5 rounded-xl shadow-lg text-sm animate-fade-in-up flex items-center gap-2">
          <Check className="w-4 h-4" strokeWidth={1.5} /> {toast}
        </div>
      )}
    </AppLayout>
  );
}

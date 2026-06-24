import React, { useState, useEffect } from 'react';
import { 
  Building2, 
  Globe, 
  Mail, 
  Calendar, 
  CreditCard, 
  Loader2, 
  AlertCircle, 
  Search, 
  TrendingUp, 
  ShieldCheck, 
  Database,
  ArrowUpRight,
  Play,
  CheckCircle,
  PauseCircle
} from 'lucide-react';

export default function SuperAdminDashboard() {
  const [tenants, setTenants] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  useEffect(() => {
    fetchTenants();
  }, []);

  const fetchTenants = async () => {
    setLoading(true);
    setError('');

    try {
      const token = localStorage.getItem('token') || localStorage.getItem('jwt') || localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json'
      };

      if (token) {
        headers['Authorization'] = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
      }

      const response = await fetch('/api/admin/tenants', {
        method: 'GET',
        headers: headers
      });

      if (!response.ok) {
        throw new Error(`Failed to load tenant directories (HTTP ${response.status})`);
      }

      const data = await response.json();
      setTenants(data);
    } catch (err) {
      console.error('Super Admin Fetch Error:', err);
      setError(err.message || 'An unexpected error occurred while fetching tenant records.');
    } finally {
      setLoading(false);
    }
  };

  const handleStatusChange = async (tenantId, newStatus) => {
    setActionLoading(true);
    try {
      const token = localStorage.getItem('token') || localStorage.getItem('jwt') || localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json'
      };

      if (token) {
        headers['Authorization'] = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
      }

      const response = await fetch(`/api/admin/tenants/${tenantId}/status?status=${newStatus}`, {
        method: 'PUT',
        headers: headers
      });

      if (!response.ok) {
        throw new Error(`Failed to update tenant status (HTTP ${response.status})`);
      }

      // Re-fetch tenants
      await fetchTenants();
    } catch (err) {
      console.error('Status Change Error:', err);
      alert(err.message || 'Failed to update tenant status.');
    } finally {
      setActionLoading(false);
    }
  };

  const getStatusBadge = (status) => {
    switch (status) {
      case 'LIVE':
        return 'bg-emerald-500/10 border border-emerald-500/30 text-emerald-400';
      case 'PROVISIONING':
        return 'bg-blue-500/10 border border-blue-500/30 text-blue-400';
      case 'PENDING_SETUP':
      case 'PENDING_PAYMENT':
        return 'bg-amber-500/10 border border-amber-500/30 text-amber-400';
      case 'SUSPENDED':
        return 'bg-rose-500/10 border border-rose-500/30 text-rose-400';
      default:
        return 'bg-slate-500/10 border border-slate-500/30 text-slate-400';
    }
  };

  const getPaymentBadge = (status) => {
    switch (status) {
      case 'PAID':
        return 'bg-emerald-500/10 border border-emerald-500/30 text-emerald-400';
      case 'PENDING':
        return 'bg-amber-500/10 border border-amber-500/30 text-amber-400';
      case 'FAILED':
        return 'bg-rose-500/10 border border-rose-500/30 text-rose-400';
      default:
        return 'bg-slate-500/10 border border-slate-500/30 text-slate-400';
    }
  };

  const getPlanBadge = (plan) => {
    return plan === 'YEARLY' 
      ? 'bg-violet-500/10 border border-violet-500/30 text-violet-400' 
      : 'bg-indigo-500/10 border border-indigo-500/30 text-indigo-400';
  };

  const filteredTenants = tenants.filter(tenant => 
    tenant.pgName.toLowerCase().includes(searchTerm.toLowerCase()) ||
    tenant.customDomain.toLowerCase().includes(searchTerm.toLowerCase()) ||
    tenant.contactEmail.toLowerCase().includes(searchTerm.toLowerCase())
  );

  // Compute summary stats
  const totalCount = tenants.length;
  const liveCount = tenants.filter(t => t.status === 'LIVE').length;
  const pendingPaymentCount = tenants.filter(t => t.paymentStatus === 'PENDING').length;
  const provisioningCount = tenants.filter(t => t.status === 'PROVISIONING').length;

  return (
    <div className="min-h-screen bg-[#080a13] text-slate-150 p-6 md:p-10 relative overflow-hidden font-sans">
      {/* Glow ornaments */}
      <div className="absolute top-0 right-0 w-[500px] h-[500px] bg-violet-600/5 rounded-full blur-3xl pointer-events-none" />
      <div className="absolute bottom-0 left-0 w-[500px] h-[500px] bg-indigo-650/5 rounded-full blur-3xl pointer-events-none" />
      
      <div className="max-w-7xl mx-auto space-y-8 relative z-10">
        
        {/* Header Title */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <h1 className="text-3xl font-extrabold tracking-tight text-white flex items-center gap-3">
              <ShieldCheck className="w-8 h-8 text-violet-500" />
              <span>Super Admin Dashboard</span>
            </h1>
            <p className="text-slate-400 text-xs mt-1">Cross-tenant monitoring panel & server provisioning overview.</p>
          </div>
          <button 
            onClick={fetchTenants}
            className="self-start md:self-auto flex items-center gap-2 bg-slate-900 border border-slate-800 hover:border-slate-700 text-slate-355 text-xs font-bold rounded-xl px-4 py-2.5 transition-all"
          >
            Refresh Records
          </button>
        </div>

        {/* Stats Summary Panel */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
          {[
            { label: 'Total Tenants', value: totalCount, icon: Database, color: 'text-blue-400' },
            { label: 'Live Tenants', value: liveCount, icon: TrendingUp, color: 'text-emerald-400' },
            { label: 'Provisioning', value: provisioningCount, icon: Loader2, color: 'text-violet-400', animate: provisioningCount > 0 },
            { label: 'Pending Payments', value: pendingPaymentCount, icon: CreditCard, color: 'text-amber-400' }
          ].map((stat, idx) => {
            const Icon = stat.icon;
            return (
              <div key={idx} className="bg-slate-900/50 backdrop-blur-md border border-slate-800 rounded-2xl p-5 flex items-center justify-between">
                <div>
                  <span className="block text-slate-500 font-bold uppercase tracking-wider text-[10px]">{stat.label}</span>
                  <span className="block text-2xl font-black text-white mt-1.5">{loading ? '...' : stat.value}</span>
                </div>
                <div className="p-3 bg-slate-950/60 border border-slate-850 rounded-xl">
                  <Icon className={`w-5 h-5 ${stat.color} ${stat.animate ? 'animate-spin' : ''}`} />
                </div>
              </div>
            );
          })}
        </div>

        {/* Main Content Area */}
        <div className="bg-slate-900/40 backdrop-blur-md border border-slate-800 rounded-3xl overflow-hidden shadow-xl">
          
          {/* Table Header Filter panel */}
          <div className="p-6 border-b border-slate-800/80 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div className="flex items-center gap-2">
              <span className="w-1.5 h-4 bg-violet-500 rounded" />
              <h2 className="text-sm font-bold text-white uppercase tracking-wider">Registered Tenant Directory</h2>
            </div>
            
            {/* Search Input */}
            <div className="relative max-w-xs w-full">
              <Search className="absolute left-3.5 top-3.5 h-4 w-4 text-slate-600" />
              <input
                type="text"
                placeholder="Search by name, domain, email..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="w-full bg-slate-950/50 border border-slate-800 hover:border-slate-700 focus:border-violet-500 rounded-xl pl-10 pr-4 py-2.5 text-xs text-white placeholder-slate-600 outline-none transition-all"
              />
            </div>
          </div>

          {/* Table Container */}
          {loading ? (
            <div className="py-20 flex flex-col items-center justify-center space-y-4">
              <Loader2 className="w-8 h-8 text-violet-500 animate-spin" />
              <span className="text-xs text-slate-500 font-bold uppercase tracking-wider">Loading Tenant Data...</span>
            </div>
          ) : error ? (
            <div className="py-20 px-6 flex flex-col items-center justify-center text-center space-y-3">
              <AlertCircle className="w-8 h-8 text-rose-500" />
              <span className="text-sm font-bold text-rose-400">Failed to Load Records</span>
              <p className="text-xs text-slate-500 max-w-sm">{error}</p>
            </div>
          ) : filteredTenants.length === 0 ? (
            <div className="py-20 text-center text-xs text-slate-500 font-semibold uppercase tracking-wider">
              No matching tenant records found.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-slate-850 text-slate-500 text-[10px] font-bold uppercase tracking-wider bg-slate-950/20">
                    <th className="py-4.5 px-6">PG Name</th>
                    <th className="py-4.5 px-6">Target Domain</th>
                    <th className="py-4.5 px-6">Contact Email</th>
                    <th className="py-4.5 px-6">Subscription Plan</th>
                    <th className="py-4.5 px-6">Tenant Status</th>
                    <th className="py-4.5 px-6">Payment</th>
                    <th className="py-4.5 px-6 text-right">Created Date</th>
                    <th className="py-4.5 px-6 text-center">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-850/40 text-xs">
                  {filteredTenants.map((tenant) => (
                    <tr key={tenant.tenantId} className="hover:bg-slate-850/10 transition-colors">
                      
                      {/* PG Name */}
                      <td className="py-4 px-6 font-bold text-white flex items-center gap-2">
                        <Building2 className="w-4 h-4 text-slate-500 shrink-0" />
                        <span>{tenant.pgName}</span>
                      </td>

                      {/* Target Domain */}
                      <td className="py-4 px-6">
                        <a 
                          href={`https://${tenant.customDomain}.pgcrm.com`} 
                          target="_blank" 
                          rel="noreferrer" 
                          className="flex items-center gap-1.5 text-violet-400 hover:text-violet-300 font-semibold transition-colors group"
                        >
                          <Globe className="w-3.5 h-3.5 text-slate-500 group-hover:text-violet-400" />
                          <span>{tenant.customDomain}.pgcrm.com</span>
                          <ArrowUpRight className="w-3 h-3 text-slate-650 opacity-0 group-hover:opacity-100 transition-opacity" />
                        </a>
                      </td>

                      {/* Contact Email */}
                      <td className="py-4 px-6 text-slate-400">
                        <div className="flex items-center gap-1.5">
                          <Mail className="w-3.5 h-3.5 text-slate-600" />
                          <span>{tenant.contactEmail}</span>
                        </div>
                      </td>

                      {/* Subscription Plan */}
                      <td className="py-4 px-6">
                        <span className={`inline-flex items-center px-2 py-0.5 rounded-md text-[9px] font-black uppercase tracking-wider ${getPlanBadge(tenant.planType)}`}>
                          {tenant.planType || 'UNSET'}
                        </span>
                      </td>

                      {/* Tenant Status */}
                      <td className="py-4 px-6">
                        <span className={`inline-flex items-center px-2 py-0.5 rounded-md text-[9px] font-black uppercase tracking-wider ${getStatusBadge(tenant.status)}`}>
                          {tenant.status}
                        </span>
                      </td>

                      {/* Payment Status */}
                      <td className="py-4 px-6">
                        <span className={`inline-flex items-center px-2 py-0.5 rounded-md text-[9px] font-black uppercase tracking-wider ${getPaymentBadge(tenant.paymentStatus)}`}>
                          {tenant.paymentStatus || 'PENDING'}
                        </span>
                      </td>

                      {/* Created Date */}
                      <td className="py-4 px-6 text-right text-slate-500 font-medium">
                        <div className="flex items-center justify-end gap-1.5">
                          <Calendar className="w-3.5 h-3.5 text-slate-600" />
                          <span>
                            {tenant.createdAt 
                              ? new Date(tenant.createdAt).toLocaleDateString('en-IN', {
                                  day: '2-digit',
                                  month: 'short',
                                  year: 'numeric'
                                })
                              : 'N/A'
                            }
                          </span>
                        </div>
                      </td>

                      {/* Actions Column */}
                      <td className="py-4 px-6 text-center">
                        <div className="flex items-center justify-center gap-2">
                          
                          {/* Provision button */}
                          <button
                            onClick={() => handleStatusChange(tenant.tenantId, 'PROVISIONING')}
                            disabled={actionLoading || tenant.status === 'PROVISIONING'}
                            title="Force Provisioning Status"
                            className="p-1.5 rounded-lg bg-blue-500/10 border border-blue-500/20 hover:bg-blue-500/25 text-blue-450 disabled:opacity-30 disabled:hover:bg-blue-500/10 transition-all cursor-pointer"
                          >
                            <Play className="w-3.5 h-3.5" />
                          </button>

                          {/* Activate button */}
                          <button
                            onClick={() => handleStatusChange(tenant.tenantId, 'LIVE')}
                            disabled={actionLoading || tenant.status === 'LIVE'}
                            title="Force Live Status"
                            className="p-1.5 rounded-lg bg-emerald-500/10 border border-emerald-500/20 hover:bg-emerald-500/25 text-emerald-450 disabled:opacity-30 disabled:hover:bg-emerald-500/10 transition-all cursor-pointer"
                          >
                            <CheckCircle className="w-3.5 h-3.5" />
                          </button>

                          {/* Suspend button */}
                          <button
                            onClick={() => handleStatusChange(tenant.tenantId, 'SUSPENDED')}
                            disabled={actionLoading || tenant.status === 'SUSPENDED'}
                            title="Force Suspended Status"
                            className="p-1.5 rounded-lg bg-rose-500/10 border border-rose-500/20 hover:bg-rose-500/25 text-rose-450 disabled:opacity-30 disabled:hover:bg-rose-500/10 transition-all cursor-pointer"
                          >
                            <PauseCircle className="w-3.5 h-3.5" />
                          </button>
                          
                        </div>
                      </td>

                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

        </div>

      </div>
    </div>
  );
}

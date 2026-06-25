import React, { useState, useEffect } from 'react';
import { 
  Building2, 
  Globe, 
  Loader2, 
  CheckCircle, 
  PauseCircle, 
  AlertTriangle, 
  Play, 
  Calendar, 
  ShieldCheck, 
  Search, 
  RefreshCw,
  Clock
} from 'lucide-react';

export default function Dashboard() {
  const [tenants, setTenants] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [actionLoading, setActionLoading] = useState({});

  const fetchTenants = async (showLoading = false) => {
    if (showLoading) setLoading(true);
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
        throw new Error(`HTTP ${response.status}: Failed to fetch tenants`);
      }

      const data = await response.json();
      setTenants(data);
      setError('');
    } catch (err) {
      console.error('Fetch Tenants Error:', err);
      setError('Failed to fetch the tenant list from the server.');
    } finally {
      if (showLoading) setLoading(false);
    }
  };

  // Initial Fetch & Polling (every 2.5 seconds)
  useEffect(() => {
    fetchTenants(true);

    const intervalId = setInterval(() => {
      fetchTenants(false);
    }, 2500);

    return () => clearInterval(intervalId);
  }, []);

  const triggerAction = async (tenantId, action) => {
    setActionLoading(prev => ({ ...prev, [tenantId]: true }));
    try {
      const token = localStorage.getItem('token') || localStorage.getItem('jwt') || localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json'
      };

      if (token) {
        headers['Authorization'] = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
      }

      const response = await fetch(`/api/admin/tenants/${tenantId}/${action}`, {
        method: 'POST',
        headers: headers
      });

      if (!response.ok) {
        throw new Error(`Failed to ${action} tenant.`);
      }

      // Fetch immediately to update UI faster than next poll
      await fetchTenants(false);
    } catch (err) {
      console.error(`Action ${action} Error:`, err);
      alert(err.message || `An error occurred while trying to ${action} the tenant.`);
    } finally {
      setActionLoading(prev => ({ ...prev, [tenantId]: false }));
    }
  };

  const getStatusBadge = (status) => {
    switch (status) {
      case 'LIVE':
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold bg-emerald-50 border border-emerald-250 text-emerald-700">
            <CheckCircle className="w-3.5 h-3.5" />
            <span>LIVE</span>
          </span>
        );
      case 'PROVISIONING':
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold bg-blue-50 border border-blue-200 text-blue-700">
            <Loader2 className="w-3.5 h-3.5 animate-spin" />
            <span>PROVISIONING</span>
          </span>
        );
      case 'SUSPENDED':
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold bg-rose-50 border border-rose-200 text-rose-700">
            <PauseCircle className="w-3.5 h-3.5" />
            <span>SUSPENDED</span>
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold bg-amber-50 border border-amber-200 text-amber-700">
            <AlertTriangle className="w-3.5 h-3.5" />
            <span>{status || 'UNKNOWN'}</span>
          </span>
        );
    }
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return 'N/A';
    try {
      const date = new Date(dateStr);
      return date.toLocaleDateString('en-IN', {
        day: '2-digit',
        month: 'short',
        year: 'numeric'
      });
    } catch {
      return dateStr;
    }
  };

  const filteredTenants = tenants.filter(t => 
    t.pgName.toLowerCase().includes(searchTerm.toLowerCase()) ||
    t.customDomain.toLowerCase().includes(searchTerm.toLowerCase()) ||
    (t.customTld && t.customTld.toLowerCase().includes(searchTerm.toLowerCase()))
  );

  return (
    <div className="min-h-screen bg-slate-55 flex flex-col font-sans text-slate-800">
      
      {/* Top Navigation / Status bar */}
      <div className="bg-white border-b border-slate-200 shadow-sm sticky top-0 z-20">
        <div className="max-w-7xl mx-auto px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-blue-600 rounded-2xl text-white shadow-md shadow-blue-500/10">
              <ShieldCheck className="w-6 h-6" />
            </div>
            <div>
              <h1 className="text-lg font-extrabold tracking-tight text-slate-900">
                PG Command Center
              </h1>
              <p className="text-[10px] text-slate-500 font-medium">SaaS Tenant Fleet Management & Orchestrator</p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <button 
              onClick={() => fetchTenants(true)}
              className="flex items-center gap-2 px-3.5 py-2 border border-slate-200 hover:bg-slate-50 rounded-xl text-xs font-semibold transition-all active:scale-[0.98]"
            >
              <RefreshCw className={`w-3.5 h-3.5 text-slate-500 ${loading ? 'animate-spin' : ''}`} />
              <span>Refresh</span>
            </button>
          </div>
        </div>
      </div>

      {/* Main content grid */}
      <div className="max-w-7xl mx-auto px-6 py-8 w-full flex-1 flex flex-col space-y-6">
        
        {/* Filter / search panel */}
        <div className="bg-white border border-slate-200 rounded-3xl p-5 flex flex-col md:flex-row md:items-center justify-between gap-4 shadow-sm">
          <div className="relative max-w-md w-full">
            <Search className="absolute left-3.5 top-3 h-4.5 w-4.5 text-slate-400" />
            <input
              type="text"
              placeholder="Search by PG name, subdomain, custom domain..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full bg-slate-50 border border-slate-200 hover:border-slate-350 focus:bg-white focus:border-blue-500 rounded-xl pl-11 pr-4 py-2.5 text-sm text-slate-900 placeholder-slate-400 outline-none transition-all"
            />
          </div>

          <div className="flex items-center gap-2 text-slate-500 text-xs">
            <Clock className="w-4 h-4 animate-pulse text-blue-500" />
            <span>Auto-refreshing in background every 2.5s</span>
          </div>
        </div>

        {error && (
          <div className="p-4 rounded-xl border border-red-200 bg-red-50 text-red-600 text-xs font-semibold flex items-center gap-2.5 shadow-sm">
            <AlertTriangle className="w-4 h-4 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {/* Data Grid table */}
        <div className="bg-white border border-slate-200 rounded-3xl overflow-hidden shadow-sm flex-1 flex flex-col">
          {loading && tenants.length === 0 ? (
            <div className="py-24 flex flex-col items-center justify-center space-y-3">
              <Loader2 className="w-8 h-8 text-blue-600 animate-spin" />
              <span className="text-xs text-slate-500 font-bold uppercase tracking-wider">Syncing Fleet Status...</span>
            </div>
          ) : filteredTenants.length === 0 ? (
            <div className="py-24 text-center text-xs text-slate-500 font-bold uppercase tracking-wider">
              No matching tenant instances found
            </div>
          ) : (
            <div className="overflow-x-auto w-full">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-slate-200 text-slate-500 text-[10px] font-bold uppercase tracking-wider bg-slate-50">
                    <th className="py-4 px-6">PG Name</th>
                    <th className="py-4 px-6">Routing Domain</th>
                    <th className="py-4 px-6">Status</th>
                    <th className="py-4 px-6">Subscription Expiry</th>
                    <th className="py-4 px-6 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 text-sm text-slate-800">
                  {filteredTenants.map((tenant) => {
                    const isBusy = actionLoading[tenant.id];
                    return (
                      <tr key={tenant.id} className="hover:bg-slate-50/50 transition-colors">
                        
                        {/* PG Name */}
                        <td className="py-4 px-6 font-bold text-slate-900">
                          <div className="flex items-center gap-2.5">
                            <Building2 className="w-4 h-4 text-slate-400 shrink-0" />
                            <span>{tenant.pgName}</span>
                          </div>
                        </td>

                        {/* Domain / Subdomain */}
                        <td className="py-4 px-6">
                          <div className="flex flex-col gap-0.5">
                            <div className="flex items-center gap-1.5 text-xs text-slate-600 font-medium">
                              <Globe className="w-3.5 h-3.5 text-slate-400" />
                              <span>{tenant.customDomain}.pgcrm.com</span>
                            </div>
                            {tenant.customTld && tenant.customTld !== 'NONE' && (
                              <span className="text-[10px] text-blue-600 font-bold">
                                Custom: {tenant.customTld}
                              </span>
                            )}
                          </div>
                        </td>

                        {/* Status Badge */}
                        <td className="py-4 px-6">
                          {getStatusBadge(tenant.status)}
                        </td>

                        {/* Subscription Expiry */}
                        <td className="py-4 px-6">
                          <div className="flex items-center gap-1.5 text-xs text-slate-500">
                            <Calendar className="w-3.5 h-3.5 text-slate-400" />
                            <span>{formatDate(tenant.subscriptionExpiry)}</span>
                          </div>
                        </td>

                        {/* Action Buttons */}
                        <td className="py-4 px-6 text-right">
                          <div className="flex items-center justify-end gap-2.5">
                            
                            {/* Activate Action */}
                            <button
                              disabled={tenant.status === 'LIVE' || isBusy}
                              onClick={() => triggerAction(tenant.id, 'activate')}
                              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold transition-all border active:scale-[0.98] ${
                                tenant.status === 'LIVE'
                                  ? 'bg-slate-50 border-slate-100 text-slate-300 cursor-not-allowed'
                                  : 'bg-emerald-50 hover:bg-emerald-100 border-emerald-200 text-emerald-700 shadow-sm'
                              }`}
                            >
                              <Play className="w-3 h-3" />
                              <span>Activate</span>
                            </button>

                            {/* Suspend Action */}
                            <button
                              disabled={tenant.status === 'SUSPENDED' || tenant.status === 'PROVISIONING' || isBusy}
                              onClick={() => triggerAction(tenant.id, 'suspend')}
                              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold transition-all border active:scale-[0.98] ${
                                tenant.status === 'SUSPENDED' || tenant.status === 'PROVISIONING'
                                  ? 'bg-slate-50 border-slate-100 text-slate-300 cursor-not-allowed'
                                  : 'bg-rose-50 hover:bg-rose-100 border-rose-250 text-rose-700 shadow-sm'
                              }`}
                            >
                              <PauseCircle className="w-3 h-3" />
                              <span>Suspend</span>
                            </button>
                            
                          </div>
                        </td>

                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>

      </div>
    </div>
  );
}

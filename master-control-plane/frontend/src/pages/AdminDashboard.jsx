import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { Server, Loader2, RefreshCw, ShieldAlert, Terminal, ArrowRight } from 'lucide-react';

export default function AdminDashboard() {
  const [clients, setClients] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await axios.get('/api/admin/clients');
      setClients(response.data);
    } catch (err) {
      console.error('Failed to load metrics:', err);
      setError('Could not establish contact with backend Control Plane APIs.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center h-96 space-y-4">
        <Loader2 className="w-8 h-8 text-black animate-spin" />
        <span className="text-neutral-400 text-xs font-bold uppercase tracking-wider">Loading system logs...</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-6 rounded-3xl border-2 border-red-200 bg-red-50 text-center max-w-lg mx-auto">
        <ShieldAlert className="w-12 h-12 text-red-650 mx-auto mb-3" />
        <h3 className="font-black text-sm text-neutral-900 uppercase tracking-widest mb-1.5">Metrics Fetch Error</h3>
        <p className="text-red-750 text-xs font-semibold uppercase tracking-wider mb-6">{error}</p>
        <button
          onClick={fetchData}
          className="px-5 py-2.5 rounded-full bg-black text-white hover:bg-neutral-900 font-bold text-xs uppercase tracking-wider transition-colors flex items-center justify-center space-x-2 mx-auto"
        >
          <RefreshCw className="w-4 h-4" />
          <span>Retry Connection</span>
        </button>
      </div>
    );
  }

  // Calculate dynamic stats
  const clientCount = clients.length;
  
  // Calculate total revenue
  const totalRev = clients.reduce((acc, client) => {
    let clientRev = 0;
    client.instances?.forEach(inst => {
      if (inst.setupFeePaid) {
        clientRev += 15000;
        if (inst.amcStartDate && inst.amcExpiryDate) {
          const start = new Date(inst.amcStartDate);
          const expiry = new Date(inst.amcExpiryDate);
          const diffYears = Math.floor((expiry - start) / (1000 * 60 * 60 * 24 * 365.25));
          if (diffYears > 1) {
            clientRev += (diffYears - 1) * 35000;
          }
        }
      }
    });
    return acc + clientRev;
  }, 0);

  // Format currency
  const formatCurrency = (val) => {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0
    }).format(val);
  };

  // Compile provisioning queue
  const provisioningQueue = [];
  clients.forEach(client => {
    client.instances?.forEach(inst => {
      provisioningQueue.push({
        domainName: inst.domainName,
        status: inst.status,
        pgBrandName: client.pgBrandName,
        allocatedPort: inst.allocatedPort,
        whatsappEnabled: !!inst.whatsappToken,
        razorpayEnabled: !!inst.razorpayKeyId,
      });
    });
  });

  return (
    <div className="space-y-8">
      
      {/* Overview stats cards matching mockup */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        
        {/* Total Rev Widget */}
        <div className="bg-white border-2 border-black rounded-3xl p-6 relative overflow-hidden shadow-sm">
          <div className="flex items-center justify-between mb-3 border-b-2 border-neutral-100 pb-3">
            <span className="text-[10px] font-black text-neutral-400 uppercase tracking-widest">Revenue Ledger</span>
            <span className="px-2.5 py-0.5 rounded border border-black bg-neutral-50 text-[8px] font-black uppercase tracking-widest">Real-time</span>
          </div>
          <p className="text-3xl font-black text-neutral-900 mb-1">{formatCurrency(totalRev)}</p>
          <h3 className="font-bold text-xs uppercase text-neutral-500 tracking-wider">Total Rev (Setup + AMC)</h3>
        </div>

        {/* Client Count Widget */}
        <div className="bg-white border-2 border-black rounded-3xl p-6 relative overflow-hidden shadow-sm">
          <div className="flex items-center justify-between mb-3 border-b-2 border-neutral-100 pb-3">
            <span className="text-[10px] font-black text-neutral-400 uppercase tracking-widest">Brand Directory</span>
            <span className="px-2.5 py-0.5 rounded border border-black bg-neutral-50 text-[8px] font-black uppercase tracking-widest">Total</span>
          </div>
          <p className="text-3xl font-black text-neutral-900 mb-1">{clientCount}</p>
          <h3 className="font-bold text-xs uppercase text-neutral-500 tracking-wider">Active Client Brands</h3>
        </div>

      </div>

      {/* Provisioning Queue table */}
      <div className="bg-white border-2 border-black rounded-3xl p-6 shadow-sm">
        <div className="flex items-center justify-between pb-4 border-b border-neutral-100 mb-6">
          <div className="flex items-center space-x-2">
            <Server className="w-5 h-5 text-black" />
            <h3 className="font-black text-sm uppercase tracking-widest text-neutral-900">Provisioning Queue</h3>
          </div>
          <button
            onClick={fetchData}
            className="p-2 rounded-xl border border-neutral-200 hover:border-black bg-neutral-50 hover:bg-neutral-100 text-neutral-500 hover:text-black transition-colors"
            title="Refresh Queue"
          >
            <RefreshCw className="w-4 h-4" />
          </button>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs font-semibold">
            <thead>
              <tr className="text-neutral-450 border-b-2 border-black uppercase tracking-widest text-[9px]">
                <th className="py-2.5 pl-2">Subdomain</th>
                <th className="py-2.5">PG Brand</th>
                <th className="py-2.5">Port</th>
                <th className="py-2.5">Integrations</th>
                <th className="py-2.5">Status</th>
                <th className="py-2.5 text-right pr-2">Logs</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-neutral-150">
              {provisioningQueue.length === 0 ? (
                <tr>
                  <td colSpan="6" className="py-8 text-center text-neutral-400 font-bold uppercase tracking-wider">
                    No instances provisioned.
                  </td>
                </tr>
              ) : (
                provisioningQueue.map((item, idx) => {
                  let statusColor = 'bg-neutral-105 text-neutral-600 border-neutral-200';
                  if (item.status === 'ACTIVE') statusColor = 'bg-emerald-50 text-emerald-800 border-2 border-emerald-250';
                  else if (item.status === 'PROVISIONING' || item.status === 'PENDING_DEPLOYMENT') statusColor = 'bg-amber-50 text-amber-800 border-2 border-amber-250';
                  else if (item.status === 'DELETED') statusColor = 'bg-red-50 text-red-800 border-2 border-red-250';

                  return (
                    <tr key={idx} className="text-neutral-700 hover:bg-neutral-50/40 transition-colors">
                      <td className="py-3.5 pl-2 font-black text-neutral-900">{item.domainName}.pgcrm.com</td>
                      <td className="py-3.5 text-neutral-500 font-bold uppercase text-[10px]">{item.pgBrandName}</td>
                      <td className="py-3.5 font-mono">{item.allocatedPort || 'N/A'}</td>
                      <td className="py-3.5">
                        <div className="flex space-x-1">
                          {item.whatsappEnabled && (
                            <span className="px-1.5 py-0.5 rounded border border-black bg-neutral-50 text-black text-[8px] font-black uppercase tracking-wider">
                              WA
                            </span>
                          )}
                          {item.razorpayEnabled && (
                            <span className="px-1.5 py-0.5 rounded border border-black bg-neutral-50 text-black text-[8px] font-black uppercase tracking-wider">
                              RP
                            </span>
                          )}
                          {!item.whatsappEnabled && !item.razorpayEnabled && (
                            <span className="text-[9px] text-neutral-400 uppercase tracking-widest font-semibold">Standard</span>
                          )}
                        </div>
                      </td>
                      <td className="py-3.5">
                        <span className={`inline-block px-2.5 py-0.5 rounded border text-[9px] font-black uppercase tracking-wider ${statusColor}`}>
                          {item.status}
                        </span>
                      </td>
                      <td className="py-3.5 text-right pr-2">
                        <a
                          href={`/api/billing/status?domainName=${item.domainName}`}
                          target="_blank"
                          rel="noreferrer"
                          className="inline-flex items-center space-x-1 text-black hover:text-neutral-600 transition-colors font-bold uppercase tracking-wider text-[9px]"
                        >
                          <span>Read logs</span>
                          <Terminal className="w-3.5 h-3.5" />
                        </a>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

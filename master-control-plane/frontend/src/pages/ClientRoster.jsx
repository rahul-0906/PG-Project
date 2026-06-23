import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { Loader2, RefreshCw, Check, X, ShieldCheck, Mail, Phone, ServerCrash, ExternalLink } from 'lucide-react';

export default function ClientRoster() {
  const [clients, setClients] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [activatingId, setActivatingId] = useState(null);

  const fetchClients = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await axios.get('/api/admin/clients');
      setClients(response.data);
    } catch (err) {
      console.error('Failed to load clients directory:', err);
      setError('Could not fetch client rosters from administrative server.');
    } finally {
      setLoading(false);
    }
  };

  const handleActivate = async (tenantId) => {
    setActivatingId(tenantId);
    try {
      await axios.put(`/api/admin/tenants/${tenantId}/activate`);
      await fetchClients();
    } catch (err) {
      console.error('Activation failure:', err);
      alert('Manual activation call failed. Please verify target server configurations.');
    } finally {
      setActivatingId(null);
    }
  };

  useEffect(() => {
    fetchClients();
  }, []);

  if (loading && clients.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center h-96 space-y-4">
        <Loader2 className="w-8 h-8 text-black animate-spin" />
        <span className="text-neutral-400 text-xs font-bold uppercase tracking-wider">Loading client directory...</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-6 rounded-3xl border-2 border-red-200 bg-red-50 text-center max-w-lg mx-auto">
        <ServerCrash className="w-12 h-12 text-red-650 mx-auto mb-3" />
        <h3 className="font-black text-sm text-neutral-905 mb-1.5 uppercase tracking-widest">Network Error</h3>
        <p className="text-red-750 text-xs font-semibold uppercase tracking-wider mb-6">{error}</p>
        <button
          onClick={fetchClients}
          className="px-5 py-2.5 rounded-full bg-black text-white hover:bg-neutral-900 font-bold text-xs uppercase tracking-wider transition-colors flex items-center justify-center space-x-2 mx-auto"
        >
          <RefreshCw className="w-4 h-4" />
          <span>Retry Connection</span>
        </button>
      </div>
    );
  }

  // Flatten clients list to list rows per tenant instance
  const rosterRows = clients.flatMap((client) =>
    client.instances?.map((instance) => ({
      clientId: client.clientId,
      ownerName: client.ownerName,
      email: client.email,
      phone: client.phone,
      pgBrandName: client.pgBrandName,
      ...instance,
    })) || []
  );

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h3 className="text-neutral-900 font-black uppercase tracking-widest text-xs">Instance Roster</h3>
          <p className="text-neutral-450 text-[10px] font-bold uppercase tracking-wider mt-0.5">
            Total active tenant allocations: {rosterRows.length}
          </p>
        </div>
        <button
          onClick={fetchClients}
          className="p-2 rounded-full border border-neutral-200 hover:border-black bg-white text-neutral-500 hover:text-black transition-colors flex items-center space-x-2 text-xs font-bold uppercase tracking-wider"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          <span>Reload</span>
        </button>
      </div>

      <div className="bg-white border-2 border-black rounded-3xl overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-xs font-semibold">
            <thead>
              <tr className="bg-neutral-50 border-b-2 border-black text-neutral-455 font-black uppercase tracking-widest text-[9px]">
                <th className="px-6 py-4">Owner Profile</th>
                <th className="px-6 py-4">PG Brand</th>
                <th className="px-6 py-4">Subdomain</th>
                <th className="px-6 py-4 text-center">Setup Fee</th>
                <th className="px-6 py-4">AMC Expiry</th>
                <th className="px-6 py-4">Status</th>
                <th className="px-6 py-4 text-right pr-6">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-neutral-200 text-neutral-700">
              {rosterRows.length === 0 ? (
                <tr>
                  <td colSpan="7" className="px-6 py-12 text-center text-neutral-400 uppercase tracking-widest font-black">
                    No onboarded client instances found in the database.
                  </td>
                </tr>
              ) : (
                rosterRows.map((row) => {
                  const isPending = row.status === 'PENDING_DEPLOYMENT';
                  const isActive = row.status === 'ACTIVE';
                  const appUrl = `http://${row.domainName}.pgcrm.com`;

                  let statusBadge = 'bg-neutral-105 text-neutral-600 border-neutral-200';
                  if (isActive) statusBadge = 'bg-emerald-50 text-emerald-800 border-2 border-emerald-250';
                  else if (isPending) statusBadge = 'bg-amber-50 text-amber-800 border-2 border-amber-250 animate-pulse';

                  return (
                    <tr key={row.tenantId} className="hover:bg-neutral-50/40 transition-colors">
                      <td className="px-6 py-4">
                        <p className="font-black text-neutral-900 text-sm">{row.ownerName}</p>
                        <div className="flex flex-col space-y-0.5 mt-1 text-[10px] text-neutral-450 uppercase font-bold tracking-wider">
                          <span className="flex items-center space-x-1">
                            <Mail className="w-3.5 h-3.5 shrink-0 text-neutral-400" />
                            <span className="normal-case">{row.email}</span>
                          </span>
                          <span className="flex items-center space-x-1">
                            <Phone className="w-3.5 h-3.5 shrink-0 text-neutral-400" />
                            <span>{row.phone}</span>
                          </span>
                        </div>
                      </td>
                      <td className="px-6 py-4 font-bold text-neutral-800">
                        <span>{row.pgBrandName}</span>
                        {row.primaryColor && (
                          <div className="flex items-center space-x-1.5 mt-1">
                            <span className="w-3 h-3 rounded-full border border-neutral-200 inline-block" style={{ backgroundColor: row.primaryColor }} />
                            <span className="text-[8px] font-mono text-neutral-400 uppercase tracking-wider">{row.primaryColor}</span>
                          </div>
                        )}
                      </td>
                      <td className="px-6 py-4 font-mono text-xs">
                        <a
                          href={appUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="text-black hover:text-neutral-650 hover:underline flex items-center space-x-1 font-bold"
                        >
                          <span>{row.domainName}.pgcrm.com</span>
                          <ExternalLink className="w-3 h-3" />
                        </a>
                      </td>
                      <td className="px-6 py-4 text-center">
                        {row.setupFeePaid ? (
                          <div className="w-6 h-6 rounded-full bg-emerald-50 text-emerald-700 border-2 border-emerald-250 flex items-center justify-center mx-auto" title="Paid">
                            <Check className="w-3.5 h-3.5" />
                          </div>
                        ) : (
                          <div className="w-6 h-6 rounded-full bg-neutral-100 text-neutral-400 border border-neutral-200 flex items-center justify-center mx-auto" title="Pending">
                            <X className="w-3.5 h-3.5" />
                          </div>
                        )}
                      </td>
                      <td className="px-6 py-4">
                        {row.amcExpiryDate ? (
                          <div>
                            <p className="text-neutral-900 font-black">{row.amcExpiryDate}</p>
                            <span className={`inline-block text-[8px] px-2 py-0.5 rounded border-2 mt-1 uppercase font-black tracking-wider ${
                              row.licenseState === 'ACTIVE'
                                ? 'bg-emerald-50 text-emerald-800 border-emerald-250'
                                : 'bg-red-50 text-red-800 border-red-250'
                            }`}>
                              {row.licenseState}
                            </span>
                          </div>
                        ) : (
                          <span className="text-neutral-400 font-mono">—</span>
                        )}
                      </td>
                      <td className="px-6 py-4">
                        <span className={`inline-flex items-center px-2.5 py-0.5 rounded border-2 text-[9px] font-black uppercase tracking-wider ${statusBadge}`}>
                          {row.status}
                        </span>
                      </td>
                      <td className="px-6 py-4 text-right pr-6">
                        {isPending ? (
                          <button
                            onClick={() => handleActivate(row.tenantId)}
                            disabled={activatingId === row.tenantId}
                            className="px-3.5 py-1.5 rounded-full border-2 border-black bg-black text-white hover:bg-transparent hover:text-black font-black uppercase text-[10px] tracking-wider transition-colors inline-flex items-center space-x-1 disabled:opacity-50 ml-auto"
                          >
                            {activatingId === row.tenantId ? (
                              <>
                                <Loader2 className="w-3 h-3 animate-spin" />
                                <span>Deploying...</span>
                              </>
                            ) : (
                              <>
                                <ShieldCheck className="w-3.5 h-3.5" />
                                <span>Activate</span>
                              </>
                            )}
                          </button>
                        ) : (
                          <span className="text-neutral-400 text-[10px] font-black uppercase tracking-widest">Provisioned</span>
                        )}
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

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
      // Refresh list to update state
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
        <Loader2 className="w-10 h-10 text-indigo-500 animate-spin" />
        <span className="text-gray-400 text-sm">Loading client directory...</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-6 rounded-2xl border border-red-500/20 bg-red-500/5 text-center max-w-lg mx-auto">
        <ServerCrash className="w-12 h-12 text-red-500 mx-auto mb-4" />
        <h3 className="font-bold text-lg text-white mb-2">Network Error</h3>
        <p className="text-gray-400 text-sm mb-6">{error}</p>
        <button
          onClick={fetchClients}
          className="px-5 py-2.5 rounded-xl bg-red-500 hover:bg-red-600 text-white font-semibold text-sm transition-colors flex items-center justify-center space-x-2 mx-auto"
        >
          <RefreshCw className="w-4 h-4" />
          <span>Retry Connection</span>
        </button>
      </div>
    );
  }

  // Flatten clients list to list rows per tenant instance
  const rosterRows = clients.flatMap((client) =>
    client.instances.map((instance) => ({
      clientId: client.clientId,
      ownerName: client.ownerName,
      email: client.email,
      phone: client.phone,
      pgBrandName: client.pgBrandName,
      ...instance,
    }))
  );

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h3 className="text-lg font-bold text-white">Instance Roster</h3>
          <p className="text-gray-500 text-xs mt-0.5">
            Total active tenant allocations: {rosterRows.length}
          </p>
        </div>
        <button
          onClick={fetchClients}
          className="p-2.5 rounded-xl border border-gray-800 hover:border-gray-700 bg-gray-900/40 text-gray-400 hover:text-white transition-colors flex items-center space-x-2 text-sm font-medium"
        >
          <RefreshCw className="w-4 h-4" />
          <span>Reload</span>
        </button>
      </div>

      <div className="bg-[#12182b] border border-gray-800 rounded-3xl overflow-hidden shadow-xl">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-[#181f37]/80 border-b border-gray-800 text-xs font-bold uppercase tracking-wider text-indigo-200">
                <th className="px-6 py-4">Owner Profile</th>
                <th className="px-6 py-4">PG Brand</th>
                <th className="px-6 py-4">Domain Subdomain</th>
                <th className="px-6 py-4 text-center">Setup Fee</th>
                <th className="px-6 py-4">AMC Expiry</th>
                <th className="px-6 py-4">Status</th>
                <th className="px-6 py-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-800/60 text-sm">
              {rosterRows.length === 0 ? (
                <tr>
                  <td colSpan="7" className="px-6 py-12 text-center text-gray-500">
                    No onboarded client instances found in the database.
                  </td>
                </tr>
              ) : (
                rosterRows.map((row) => {
                  const isPending = row.status === 'PENDING_DEPLOYMENT';
                  const isActive = row.status === 'ACTIVE';
                  const appUrl = `http://${row.domainName}.pgcrm.com`;

                  return (
                    <tr key={row.tenantId} className="hover:bg-gray-800/10 transition-colors">
                      <td className="px-6 py-4">
                        <p className="font-semibold text-white">{row.ownerName}</p>
                        <div className="flex flex-col space-y-0.5 mt-1 text-xs text-gray-500">
                          <span className="flex items-center space-x-1">
                            <Mail className="w-3.5 h-3.5 shrink-0" />
                            <span>{row.email}</span>
                          </span>
                          <span className="flex items-center space-x-1">
                            <Phone className="w-3.5 h-3.5 shrink-0" />
                            <span>{row.phone}</span>
                          </span>
                        </div>
                      </td>
                      <td className="px-6 py-4 font-medium text-gray-300">{row.pgBrandName}</td>
                      <td className="px-6 py-4 font-mono text-xs">
                        <a
                          href={appUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="text-indigo-400 hover:text-indigo-300 hover:underline flex items-center space-x-1"
                        >
                          <span>{row.domainName}.pgcrm.com</span>
                          <ExternalLink className="w-3 h-3" />
                        </a>
                      </td>
                      <td className="px-6 py-4 text-center">
                        {row.setupFeePaid ? (
                          <div className="w-6 h-6 rounded-full bg-emerald-500/10 text-emerald-400 flex items-center justify-center mx-auto" title="Paid">
                            <Check className="w-3.5 h-3.5" />
                          </div>
                        ) : (
                          <div className="w-6 h-6 rounded-full bg-gray-800 text-gray-500 flex items-center justify-center mx-auto" title="Pending">
                            <X className="w-3.5 h-3.5" />
                          </div>
                        )}
                      </td>
                      <td className="px-6 py-4">
                        {row.amcExpiryDate ? (
                          <div>
                            <p className="text-gray-300 font-medium">{row.amcExpiryDate}</p>
                            <span className={`inline-block text-[10px] px-2 py-0.5 rounded-full mt-1 uppercase font-bold tracking-wider ${
                              row.licenseState === 'ACTIVE'
                                ? 'bg-emerald-500/10 text-emerald-400'
                                : 'bg-red-500/10 text-red-400'
                            }`}>
                              {row.licenseState}
                            </span>
                          </div>
                        ) : (
                          <span className="text-gray-600 font-mono">—</span>
                        )}
                      </td>
                      <td className="px-6 py-4">
                        <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold ${
                          isActive
                            ? 'bg-emerald-500/10 text-emerald-400'
                            : isPending
                            ? 'bg-amber-500/10 text-amber-400 animate-pulse'
                            : 'bg-indigo-500/10 text-indigo-400'
                        }`}>
                          {row.status}
                        </span>
                      </td>
                      <td className="px-6 py-4 text-right">
                        {isPending ? (
                          <button
                            onClick={() => handleActivate(row.tenantId)}
                            disabled={activatingId === row.tenantId}
                            className="px-3.5 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white font-semibold text-xs transition-colors flex items-center space-x-1.5 ml-auto disabled:opacity-50"
                          >
                            {activatingId === row.tenantId ? (
                              <>
                                <Loader2 className="w-3 h-3 animate-spin" />
                                <span>Deploying...</span>
                              </>
                            ) : (
                              <>
                                <ShieldCheck className="w-3.5 h-3.5" />
                                <span>Mark as Deployed</span>
                              </>
                            )}
                          </button>
                        ) : (
                          <span className="text-gray-600 text-xs italic font-medium">Provisioned</span>
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

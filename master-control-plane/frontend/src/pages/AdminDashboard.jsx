import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { Users, Server, AlertTriangle, Loader2, RefreshCw, BarChart2, ShieldAlert } from 'lucide-react';

export default function AdminDashboard() {
  const [metrics, setMetrics] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchMetrics = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await axios.get('/api/admin/metrics');
      setMetrics(response.data);
    } catch (err) {
      console.error('Failed to load metrics:', err);
      setError('Could not establish contact with backend Control Plane APIs.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchMetrics();
  }, []);

  if (loading && !metrics) {
    return (
      <div className="flex flex-col items-center justify-center h-96 space-y-4">
        <Loader2 className="w-10 h-10 text-indigo-500 animate-spin" />
        <span className="text-gray-400 text-sm">Loading admin metrics...</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-6 rounded-2xl border border-red-500/20 bg-red-500/5 text-center max-w-lg mx-auto">
        <ShieldAlert className="w-12 h-12 text-red-500 mx-auto mb-4" />
        <h3 className="font-bold text-lg text-white mb-2">Metrics Fetch Error</h3>
        <p className="text-gray-400 text-sm mb-6">{error}</p>
        <button
          onClick={fetchMetrics}
          className="px-5 py-2.5 rounded-xl bg-red-500 hover:bg-red-600 text-white font-semibold text-sm transition-colors flex items-center justify-center space-x-2 mx-auto"
        >
          <RefreshCw className="w-4 h-4" />
          <span>Retry Connection</span>
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-10">
      {/* Overview stats grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {/* Total Active Clients */}
        <div className="bg-[#12182b] border border-gray-800 rounded-3xl p-6 relative overflow-hidden shadow-lg group hover:border-indigo-500/30 transition-all duration-300">
          <div className="absolute top-0 right-0 w-24 h-24 bg-indigo-500/5 blur-2xl rounded-full group-hover:bg-indigo-500/10 transition-all" />
          <div className="flex items-center justify-between mb-4">
            <div className="p-3 rounded-xl bg-indigo-500/10 text-indigo-400">
              <Users className="w-6 h-6" />
            </div>
            <span className="text-gray-500 text-xs font-bold uppercase tracking-wider">Clients</span>
          </div>
          <p className="text-4xl font-extrabold text-white mb-1.5">{metrics?.totalActiveClients ?? 0}</p>
          <h3 className="font-semibold text-gray-300 text-sm">Total Active Clients</h3>
          <p className="text-gray-500 text-xs mt-2">PG Brands running whitelabeled backend servers.</p>
        </div>

        {/* Pending Deployments */}
        <div className="bg-[#12182b] border border-gray-800 rounded-3xl p-6 relative overflow-hidden shadow-lg group hover:border-amber-500/30 transition-all duration-300">
          <div className="absolute top-0 right-0 w-24 h-24 bg-amber-500/5 blur-2xl rounded-full group-hover:bg-amber-500/10 transition-all" />
          <div className="flex items-center justify-between mb-4">
            <div className="p-3 rounded-xl bg-amber-500/10 text-amber-400">
              <Server className="w-6 h-6" />
            </div>
            <span className="text-gray-500 text-xs font-bold uppercase tracking-wider">Queue</span>
          </div>
          <p className="text-4xl font-extrabold text-white mb-1.5">{metrics?.pendingDeployments ?? 0}</p>
          <h3 className="font-semibold text-gray-300 text-sm">Pending Deployments</h3>
          <p className="text-gray-500 text-xs mt-2">Instances paid and awaiting manual VPS configuration.</p>
        </div>

        {/* Upcoming AMC Warnings */}
        <div className="bg-[#12182b] border border-gray-800 rounded-3xl p-6 relative overflow-hidden shadow-lg group hover:border-rose-500/30 transition-all duration-300">
          <div className="absolute top-0 right-0 w-24 h-24 bg-rose-500/5 blur-2xl rounded-full group-hover:bg-rose-500/10 transition-all" />
          <div className="flex items-center justify-between mb-4">
            <div className="p-3 rounded-xl bg-rose-500/10 text-rose-400">
              <AlertTriangle className="w-6 h-6" />
            </div>
            <span className="text-gray-500 text-xs font-bold uppercase tracking-wider">Alerts</span>
          </div>
          <p className="text-4xl font-extrabold text-white mb-1.5">{metrics?.upcomingAmcExpirations ?? 0}</p>
          <h3 className="font-semibold text-gray-300 text-sm">AMC Expiry Warnings</h3>
          <p className="text-gray-500 text-xs mt-2">Active contracts expiring within the next 30 days.</p>
        </div>
      </div>

      {/* Auxiliary platform details panel */}
      <div className="bg-[#12182b] border border-gray-800 rounded-3xl p-8 shadow-xl">
        <div className="flex items-center justify-between pb-6 border-b border-gray-800 mb-6">
          <div className="flex items-center space-x-3">
            <div className="p-2 bg-indigo-500/10 text-indigo-400 rounded-lg">
              <BarChart2 className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-bold text-lg">System Telemetry</h3>
              <p className="text-gray-500 text-xs mt-0.5">Control plane server operational checkpoints</p>
            </div>
          </div>
          <button
            onClick={fetchMetrics}
            className="p-2.5 rounded-xl border border-gray-800 hover:border-gray-700 bg-gray-900/40 text-gray-400 hover:text-white transition-colors"
            title="Refresh Metrics"
          >
            <RefreshCw className="w-4 h-4" />
          </button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-8 text-sm">
          <div className="space-y-4">
            <div className="flex justify-between items-center py-2.5 border-b border-gray-800/40">
              <span className="text-gray-400">SaaS Framework Version</span>
              <span className="text-gray-200 font-mono">v1.2.0-Spring3.2</span>
            </div>
            <div className="flex justify-between items-center py-2.5 border-b border-gray-800/40">
              <span className="text-gray-400">Database Engine</span>
              <span className="text-gray-200 font-mono">PostgreSQL 15+</span>
            </div>
            <div className="flex justify-between items-center py-2.5 border-b border-gray-800/40">
              <span className="text-gray-400">AMC Reminders Cron Job</span>
              <span className="text-emerald-400 font-semibold flex items-center space-x-1">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 mr-1.5" />
                <span>Enabled (0 0 2 * * ?)</span>
              </span>
            </div>
          </div>

          <div className="space-y-4">
            <div className="flex justify-between items-center py-2.5 border-b border-gray-800/40">
              <span className="text-gray-400">Webhook Reconciler Key</span>
              <span className="text-gray-200 font-mono">RZP_SIGNATURE_VERIFIED</span>
            </div>
            <div className="flex justify-between items-center py-2.5 border-b border-gray-800/40">
              <span className="text-gray-400">Ansible Deploy Scripts Path</span>
              <span className="text-gray-200 font-mono">/opt/playbooks/provision.yml</span>
            </div>
            <div className="flex justify-between items-center py-2.5 border-b border-gray-800/40">
              <span className="text-gray-400">Default Sandbox Gateway</span>
              <span className="text-amber-400 font-semibold">Bypass Active</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

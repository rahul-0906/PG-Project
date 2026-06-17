import React from 'react';
import { Calendar, CreditCard, Shield, ShieldCheck, ShieldAlert, Loader2 } from 'lucide-react';

export default function AmcStatusCard({ amcData, onRenew, loading }) {
  if (!amcData) return null;

  const { pgBrandName, domainName, amcExpiryDate, licenseState } = amcData;

  const getStatusBadge = (state) => {
    switch (state?.toUpperCase()) {
      case 'ACTIVE':
        return (
          <span className="inline-flex items-center space-x-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
            <ShieldCheck className="w-3.5 h-3.5" />
            <span>Active</span>
          </span>
        );
      case 'GRACE_PERIOD':
        return (
          <span className="inline-flex items-center space-x-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/20">
            <Shield className="w-3.5 h-3.5 animate-pulse" />
            <span>Grace Period</span>
          </span>
        );
      case 'EXPIRED':
      case 'SUSPENDED':
        return (
          <span className="inline-flex items-center space-x-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-rose-500/10 text-rose-400 border border-rose-500/20">
            <ShieldAlert className="w-3.5 h-3.5" />
            <span>Suspended</span>
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center space-x-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-gray-500/10 text-gray-400 border border-gray-500/20">
            <span>Unknown</span>
          </span>
        );
    }
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return 'N/A';
    const options = { year: 'numeric', month: 'long', day: 'numeric' };
    return new Date(dateStr).toLocaleDateString(undefined, options);
  };

  return (
    <div className="bg-[#12182b] border border-gray-800 rounded-3xl p-8 relative overflow-hidden shadow-2xl">
      {/* Decorative background grid pattern */}
      <div className="absolute inset-0 bg-[linear-gradient(to_right,#8080800a_1px,transparent_1px),linear-gradient(to_bottom,#8080800a_1px,transparent_1px)] bg-[size:14px_24px] pointer-events-none" />

      <div className="relative z-10 space-y-6">
        {/* Card Header */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-6 border-b border-gray-800/60">
          <div>
            <h2 className="text-2xl font-bold text-white mb-1">{pgBrandName || 'PG Tenant'}</h2>
            <p className="text-gray-400 text-sm">{domainName ? `${domainName}.pgcrm.com` : 'No subdomain linked'}</p>
          </div>
          <div className="self-start md:self-center">
            {getStatusBadge(licenseState)}
          </div>
        </div>

        {/* Subscription Info Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 py-2">
          <div className="flex items-center space-x-4 p-4 rounded-2xl bg-[#080c16] border border-gray-800/50">
            <Calendar className="w-8 h-8 text-indigo-400 shrink-0" />
            <div>
              <p className="text-gray-500 text-xs uppercase font-semibold tracking-wider">AMC Contract Expiration</p>
              <p className="text-white font-medium text-lg mt-0.5">{formatDate(amcExpiryDate)}</p>
            </div>
          </div>

          <div className="flex items-center space-x-4 p-4 rounded-2xl bg-[#080c16] border border-gray-800/50">
            <CreditCard className="w-8 h-8 text-purple-400 shrink-0" />
            <div>
              <p className="text-gray-500 text-xs uppercase font-semibold tracking-wider">Annual Renewal Fee</p>
              <p className="text-white font-medium text-lg mt-0.5">₹35,000 <span className="text-xs text-gray-400 font-normal">/ year</span></p>
            </div>
          </div>
        </div>

        {/* Action Button */}
        <div className="pt-2">
          <button
            onClick={onRenew}
            disabled={loading}
            className="w-full md:w-auto px-8 py-4 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white font-semibold transition-all duration-200 shadow-lg shadow-indigo-600/30 hover:shadow-indigo-600/50 flex items-center justify-center space-x-2 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {loading ? (
              <>
                <Loader2 className="w-5 h-5 animate-spin" />
                <span>Processing Renewal...</span>
              </>
            ) : (
              <span>Renew Subscription (₹35,000/yr)</span>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}

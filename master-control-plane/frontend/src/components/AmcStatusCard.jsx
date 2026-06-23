import React from 'react';
import { Calendar, CreditCard, ShieldCheck, ShieldAlert, ShieldAlert as ShieldGrace, Loader2, ArrowRight } from 'lucide-react';

export default function AmcStatusCard({ amcData, onRenew, loading }) {
  if (!amcData) return null;

  const { pgBrandName, domainName, amcExpiryDate, licenseState } = amcData;

  const getStatusBadge = (state) => {
    switch (state?.toUpperCase()) {
      case 'ACTIVE':
        return (
          <span className="inline-flex items-center space-x-1.5 px-4 py-1.5 rounded-full text-xs font-black uppercase tracking-widest bg-emerald-50 text-emerald-800 border-2 border-emerald-250">
            <ShieldCheck className="w-3.5 h-3.5" />
            <span>Active</span>
          </span>
        );
      case 'GRACE_PERIOD':
        return (
          <span className="inline-flex items-center space-x-1.5 px-4 py-1.5 rounded-full text-xs font-black uppercase tracking-widest bg-amber-50 text-amber-800 border-2 border-amber-250 animate-pulse">
            <ShieldGrace className="w-3.5 h-3.5" />
            <span>Grace Period</span>
          </span>
        );
      case 'EXPIRED':
      case 'SUSPENDED':
        return (
          <span className="inline-flex items-center space-x-1.5 px-4 py-1.5 rounded-full text-xs font-black uppercase tracking-widest bg-red-50 text-red-800 border-2 border-red-250 animate-pulse">
            <ShieldAlert className="w-3.5 h-3.5" />
            <span>Suspended</span>
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center space-x-1.5 px-4 py-1.5 rounded-full text-xs font-black uppercase tracking-widest bg-neutral-100 text-neutral-600 border border-neutral-200">
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
    <div className="bg-white border-2 border-black rounded-3xl p-6 md:p-8 relative overflow-hidden shadow-sm">
      {/* Subtle grid accent background */}
      <div className="absolute inset-0 bg-[linear-gradient(to_right,#80808003_1px,transparent_1px),linear-gradient(to_bottom,#80808003_1px,transparent_1px)] bg-[size:12px_20px] pointer-events-none" />

      <div className="relative z-10 space-y-6">
        {/* Card Header */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-4 border-b border-neutral-100">
          <div>
            <h2 className="text-2xl font-black uppercase tracking-tight text-black mb-1">{pgBrandName || 'PG Tenant'}</h2>
            <p className="text-neutral-400 text-[10px] font-bold uppercase tracking-wider">
              {domainName ? `${domainName}.pgcrm.com` : 'No subdomain linked'}
            </p>
          </div>
          <div className="self-start md:self-center">
            {getStatusBadge(licenseState)}
          </div>
        </div>

        {/* Subscription Info Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 py-2">
          <div className="flex items-center space-x-4 p-5 rounded-2xl bg-neutral-50 border-2 border-neutral-200">
            <div className="w-10 h-10 rounded-full border border-black bg-white flex items-center justify-center shrink-0">
              <Calendar className="w-5 h-5 text-black" />
            </div>
            <div>
              <p className="text-neutral-400 text-[10px] uppercase font-black tracking-widest">AMC Expiration Date</p>
              <p className="text-black font-black text-lg mt-0.5">{formatDate(amcExpiryDate)}</p>
            </div>
          </div>

          <div className="flex items-center space-x-4 p-5 rounded-2xl bg-neutral-50 border-2 border-neutral-200">
            <div className="w-10 h-10 rounded-full border border-black bg-white flex items-center justify-center shrink-0">
              <CreditCard className="w-5 h-5 text-black" />
            </div>
            <div>
              <p className="text-neutral-400 text-[10px] uppercase font-black tracking-widest">Annual Upkeep Cost</p>
              <p className="text-black font-black text-lg mt-0.5">₹35,000 <span className="text-xs text-neutral-400 font-bold uppercase tracking-wide">/ yr</span></p>
            </div>
          </div>
        </div>

        {/* Action Button */}
        <div className="pt-2">
          <button
            onClick={onRenew}
            disabled={loading}
            className="group relative inline-flex items-center justify-between border-2 border-black rounded-full px-8 py-4 bg-black text-white hover:bg-transparent hover:text-black transition-all duration-300 w-full md:w-auto"
          >
            {loading ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin mr-2" />
                <span>Processing Renewal</span>
              </>
            ) : (
              <>
                <span className="text-sm font-bold tracking-wider uppercase mr-6">Renew Subscription (₹35,000/yr)</span>
                <div className="w-6 h-6 rounded-full bg-white group-hover:bg-black flex items-center justify-center transition-all duration-300 shrink-0">
                  <ArrowRight className="w-3.5 h-3.5 text-black group-hover:text-white" />
                </div>
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}

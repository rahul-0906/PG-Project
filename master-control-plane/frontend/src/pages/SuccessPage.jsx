import React from 'react';
import { useLocation, Link, Navigate } from 'react-router-dom';
import { CheckCircle2, Server, Key, Mail, ArrowRight, ExternalLink, Loader2 } from 'lucide-react';

export default function SuccessPage() {
  const location = useLocation();
  const state = location.state;

  if (!state) {
    return <Navigate to="/" replace />;
  }

  const { orderId, paymentId, domainName, brandName } = state;
  const tenantUrl = `http://${domainName}.pgcrm.com`;

  return (
    <div className="bg-[#fafaf9] text-[#141414] min-h-screen font-sans py-16 px-6 flex items-center justify-center relative overflow-hidden">
      {/* Decorative light grid background */}
      <div className="absolute inset-0 bg-[linear-gradient(to_right,#80808008_1px,transparent_1px),linear-gradient(to_bottom,#80808008_1px,transparent_1px)] bg-[size:16px_28px] pointer-events-none" />

      <div className="max-w-xl w-full relative z-10 text-center space-y-6">
        
        {/* Success Icon Badge */}
        <div className="w-16 h-16 rounded-full bg-emerald-50 border-2 border-emerald-250 flex items-center justify-center mx-auto mb-2 shadow-sm">
          <CheckCircle2 className="w-8 h-8 text-emerald-600" />
        </div>

        <div className="space-y-2">
          <h1 className="text-3xl font-black uppercase tracking-tight text-neutral-900">Payment Successful!</h1>
          <p className="text-neutral-500 text-xs font-bold uppercase tracking-wider max-w-sm mx-auto">
            Deployment initialized for <strong className="text-neutral-900">{brandName}</strong>.
          </p>
        </div>

        {/* Provisioning state indicator */}
        <div className="inline-flex items-center space-x-2 border-2 border-black bg-neutral-50 rounded-full px-4 py-1.5 text-[10px] font-black text-black uppercase tracking-widest">
          <Loader2 className="w-3.5 h-3.5 animate-spin" />
          <span>Provisioning instance (2-5 mins)</span>
        </div>

        {/* Deployment Steps Progress Card */}
        <div className="bg-white border-2 border-black rounded-3xl p-6 md:p-8 text-left space-y-6 shadow-sm">
          <h2 className="text-xs font-black text-neutral-400 uppercase tracking-widest border-b-2 border-neutral-100 pb-3">
            Automated Stack Deploying
          </h2>

          <div className="space-y-5">
            <div className="flex items-start space-x-3.5">
              <div className="w-6 h-6 rounded-full bg-neutral-105 border-2 border-black text-black font-black text-xs flex items-center justify-center shrink-0 mt-0.5">
                1
              </div>
              <div>
                <h3 className="font-black text-black text-xs uppercase tracking-wider">Database Isolation</h3>
                <p className="text-neutral-500 text-[9px] font-bold uppercase tracking-wider mt-1">
                  Creating PostgreSQL schema `pgcrm_{domainName.replace(/-/g, '_')}`.
                </p>
              </div>
            </div>

            <div className="flex items-start space-x-3.5">
              <div className="w-6 h-6 rounded-full bg-neutral-105 border-2 border-black text-black font-black text-xs flex items-center justify-center shrink-0 mt-0.5">
                2
              </div>
              <div>
                <h3 className="font-black text-black text-xs uppercase tracking-wider">Registering Domain Routing</h3>
                <p className="text-neutral-500 text-[9px] font-bold uppercase tracking-wider mt-1">
                  Configuring reverse proxy rules to map: <a href={tenantUrl} target="_blank" rel="noreferrer" className="text-black hover:underline inline-flex items-center space-x-0.5 font-black uppercase">{domainName}.pgcrm.com <ExternalLink className="w-2.5 h-2.5 ml-0.5" /></a>.
                </p>
              </div>
            </div>

            <div className="flex items-start space-x-3.5">
              <div className="w-6 h-6 rounded-full bg-neutral-105 border-2 border-black text-black font-black text-xs flex items-center justify-center shrink-0 mt-0.5">
                3
              </div>
              <div>
                <h3 className="font-black text-black text-xs uppercase tracking-wider">Administrative Credentials</h3>
                <p className="text-neutral-500 text-[9px] font-bold uppercase tracking-wider mt-1">
                  Temporary owner credentials sent to email. Check email inbox or central dashboard.
                </p>
              </div>
            </div>
          </div>
        </div>

        {/* Transaction Records */}
        <div className="p-4 rounded-2xl bg-neutral-50 border-2 border-neutral-200 text-[10px] text-left text-neutral-500 space-y-1.5 font-mono">
          <div className="flex justify-between">
            <span>RAZORPAY ORDER:</span>
            <span className="text-black font-black select-all">{orderId}</span>
          </div>
          <div className="flex justify-between">
            <span>RAZORPAY PAYMENT:</span>
            <span className="text-black font-black select-all">{paymentId}</span>
          </div>
        </div>

        {/* Actions */}
        <div className="flex flex-col sm:flex-row gap-4 pt-2">
          <a
            href={tenantUrl}
            target="_blank"
            rel="noreferrer"
            className="group relative inline-flex items-center justify-between border-2 border-black rounded-full px-8 py-4 bg-black text-white hover:bg-transparent hover:text-black transition-all duration-300 flex-1"
          >
            <span className="text-sm font-bold tracking-wider uppercase mr-3">Visit App Portal</span>
            <div className="w-5 h-5 rounded-full bg-white group-hover:bg-black flex items-center justify-center transition-all duration-300 shrink-0">
              <ExternalLink className="w-3 h-3 text-black group-hover:text-white" />
            </div>
          </a>
          <Link
            to="/"
            className="flex-1 py-4 border-2 border-neutral-300 bg-white hover:border-black text-neutral-700 hover:text-black rounded-full text-xs font-bold uppercase tracking-wider transition-all duration-300 flex items-center justify-center space-x-1"
          >
            <span>Back to Home</span>
          </Link>
        </div>
      </div>
    </div>
  );
}

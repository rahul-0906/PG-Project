import React from 'react';
import { useLocation, Link, Navigate } from 'react-router-dom';
import { CheckCircle2, Server, Key, Mail, ArrowRight, ExternalLink } from 'lucide-react';

export default function SuccessPage() {
  const location = useLocation();
  const state = location.state;

  // If page is accessed directly without payment state, redirect to home
  if (!state) {
    return <Navigate to="/" replace />;
  }

  const { orderId, paymentId, domainName, brandName } = state;
  const tenantUrl = `http://${domainName}.pgcrm.com`;

  return (
    <div className="bg-[#0b0f19] text-white min-h-screen font-sans selection:bg-indigo-500 py-16 px-6 flex items-center justify-center relative">
      {/* Glow background */}
      <div className="absolute top-1/4 left-1/4 w-[400px] h-[400px] bg-emerald-500/5 blur-[120px] rounded-full pointer-events-none" />
      <div className="absolute bottom-1/4 right-1/4 w-[400px] h-[400px] bg-indigo-500/5 blur-[120px] rounded-full pointer-events-none" />

      <div className="max-w-xl w-full relative z-10 text-center">
        {/* Success Icon Badge */}
        <div className="w-20 h-20 rounded-full bg-emerald-500/10 border border-emerald-500/30 flex items-center justify-center mx-auto mb-8 shadow-lg shadow-emerald-500/5 animate-pulse">
          <CheckCircle2 className="w-10 h-10 text-emerald-400" />
        </div>

        <h1 className="text-4xl font-extrabold mb-4 tracking-tight">Payment Successful!</h1>
        <p className="text-gray-400 text-lg mb-8 max-w-md mx-auto">
          Thank you for deploying with us. Your transaction for <strong className="text-white">{brandName}</strong> has been reconciled.
        </p>

        {/* Deployment Steps Progress Card */}
        <div className="bg-[#12182b] border border-gray-800 rounded-3xl p-6 mb-8 text-left space-y-6 shadow-xl">
          <h2 className="text-sm font-bold text-indigo-400 uppercase tracking-widest">
            Automated Deployment Status
          </h2>

          <div className="space-y-4">
            <div className="flex items-start space-x-3.5">
              <div className="w-6 h-6 rounded-full bg-indigo-500/10 text-indigo-400 font-semibold text-xs flex items-center justify-center shrink-0 mt-0.5">
                1
              </div>
              <div>
                <h3 className="font-semibold text-gray-200 text-sm">Provisioning Isolated Database</h3>
                <p className="text-gray-400 text-xs mt-1">
                  Creating PostgreSQL database schema and applying Flyway migration logs.
                </p>
              </div>
            </div>

            <div className="flex items-start space-x-3.5">
              <div className="w-6 h-6 rounded-full bg-indigo-500/10 text-indigo-400 font-semibold text-xs flex items-center justify-center shrink-0 mt-0.5">
                2
              </div>
              <div>
                <h3 className="font-semibold text-gray-200 text-sm">Registering Subdomain DNS</h3>
                <p className="text-gray-400 text-xs mt-1">
                  Configuring Nginx routing rules to bind <a href={tenantUrl} target="_blank" rel="noreferrer" className="text-indigo-400 hover:underline inline-flex items-center space-x-0.5 font-medium">{tenantUrl} <ExternalLink className="w-3 h-3 ml-0.5" /></a>.
                </p>
              </div>
            </div>

            <div className="flex items-start space-x-3.5">
              <div className="w-6 h-6 rounded-full bg-indigo-500/10 text-indigo-400 font-semibold text-xs flex items-center justify-center shrink-0 mt-0.5">
                3
              </div>
              <div>
                <h3 className="font-semibold text-gray-200 text-sm">Emailing Administrative Credentials</h3>
                <p className="text-gray-400 text-xs mt-1">
                  Dispatched owner login coordinates and setup instructions to your registered inbox.
                </p>
              </div>
            </div>
          </div>
        </div>

        {/* Transaction Records */}
        <div className="p-4 rounded-2xl bg-gray-900/40 border border-gray-800/60 text-xs text-left text-gray-500 space-y-2 mb-8 font-mono">
          <div className="flex justify-between">
            <span>Razorpay Order:</span>
            <span className="text-gray-400 select-all">{orderId}</span>
          </div>
          <div className="flex justify-between">
            <span>Razorpay Payment:</span>
            <span className="text-gray-400 select-all">{paymentId}</span>
          </div>
        </div>

        {/* Actions */}
        <div className="flex flex-col sm:flex-row space-y-4 sm:space-y-0 sm:space-x-4">
          <a
            href={tenantUrl}
            target="_blank"
            rel="noreferrer"
            className="flex-1 py-4 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white font-semibold text-sm transition-all duration-200 flex items-center justify-center space-x-2 shadow-lg shadow-indigo-500/10 hover:shadow-indigo-500/30"
          >
            <span>Visit App Portal</span>
            <ExternalLink className="w-4 h-4" />
          </a>
          <Link
            to="/"
            className="flex-1 py-4 rounded-xl border border-gray-800 hover:border-gray-700 bg-gray-900/50 hover:bg-gray-950/80 text-gray-300 font-medium text-sm transition-all duration-200 flex items-center justify-center space-x-1"
          >
            <span>Back to Home</span>
          </Link>
        </div>
      </div>
    </div>
  );
}

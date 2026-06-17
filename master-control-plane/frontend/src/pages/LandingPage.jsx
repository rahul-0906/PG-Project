import React from 'react';
import { Link } from 'react-router-dom';
import { CheckCircle2, Shield, Zap, Database, Globe, RefreshCw } from 'lucide-react';

export default function LandingPage() {
  const priceCheckmarks = [
    "Dedicated PostgreSQL Database Isolation",
    "Automated Daily System Backups",
    "Let's Encrypt SSL Domain Certificates",
    "Free Security Updates & Feature Upgrades",
    "Role-Based Access Control Audit Logs",
  ];

  return (
    <div className="bg-slate-900 text-white min-h-screen font-sans selection:bg-indigo-500 selection:text-white overflow-x-hidden">
      {/* Navbar */}
      <nav className="max-w-7xl mx-auto px-6 py-6 flex justify-between items-center border-b border-slate-800/80">
        <div className="flex items-center space-x-2">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-indigo-500 to-purple-600 flex items-center justify-center shadow-lg shadow-indigo-500/20">
            <Shield className="w-5 h-5 text-white" />
          </div>
          <span className="font-bold text-xl bg-gradient-to-r from-white via-slate-200 to-slate-400 bg-clip-text text-transparent">
            PG CRM Control Plane
          </span>
        </div>
        <div className="flex items-center space-x-4">
          <Link
            to="/signup"
            className="px-5 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-medium transition-all duration-200 shadow-lg shadow-indigo-600/30 hover:shadow-indigo-600/50 hover:-translate-y-0.5"
          >
            Launch Instance
          </Link>
        </div>
      </nav>

      {/* Hero Section */}
      <section className="max-w-7xl mx-auto px-6 pt-24 pb-20 text-center relative">
        {/* Glow Effects */}
        <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[500px] h-[500px] bg-indigo-500/10 blur-[120px] rounded-full pointer-events-none" />
        <div className="absolute top-1/3 left-1/3 w-[300px] h-[300px] bg-purple-500/10 blur-[100px] rounded-full pointer-events-none" />

        <div className="relative z-10 space-y-6">
          <span className="px-4 py-1.5 rounded-full border border-indigo-500/30 bg-indigo-500/5 text-indigo-400 text-sm font-medium inline-block shadow-inner">
            🚀 Whitelabel B2B SaaS Provisioning
          </span>
          <h1 className="text-5xl md:text-7xl font-extrabold tracking-tight leading-tight max-w-4xl mx-auto">
            Enterprise PG & Hostel Management,
            <span className="block mt-2 bg-gradient-to-r from-indigo-400 via-purple-400 to-pink-400 bg-clip-text text-transparent">
              Fully Isolated.
            </span>
          </h1>
          <p className="text-slate-400 text-lg md:text-xl max-w-2xl mx-auto leading-relaxed">
            Provision your private paying guest management portal instantly. Complete database isolation, custom subdomains, and automated arrears collection templates.
          </p>
          <div className="flex flex-col sm:flex-row justify-center items-center gap-4 pt-4">
            <Link
              to="/signup"
              className="w-full sm:w-auto px-8 py-4 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white font-semibold text-lg transition-all duration-200 shadow-xl shadow-indigo-600/25 hover:shadow-indigo-600/40 hover:-translate-y-0.5 flex items-center justify-center space-x-2"
            >
              <span>Start Your Onboarding</span>
              <Zap className="w-5 h-5 fill-current" />
            </Link>
            <a
              href="#pricing"
              className="w-full sm:w-auto px-8 py-4 rounded-xl border border-slate-800 hover:border-slate-700 bg-slate-900/50 hover:bg-slate-950/80 text-slate-300 font-medium transition-all duration-200 flex items-center justify-center"
            >
              View Pricing
            </a>
          </div>
        </div>
      </section>

      {/* Features Grid: Technical Moats */}
      <section className="max-w-7xl mx-auto px-6 py-16 border-t border-b border-slate-800/60 bg-slate-900/30">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          <div className="flex items-start space-x-4 p-6 rounded-2xl bg-slate-950/30 border border-slate-800/40 hover:border-indigo-500/20 transition-colors">
            <div className="p-3 rounded-lg bg-indigo-500/10 text-indigo-400 shrink-0">
              <Database className="w-6 h-6" />
            </div>
            <div>
              <h3 className="font-bold text-lg mb-2 text-white">Dedicated Database Isolation</h3>
              <p className="text-slate-400 text-sm leading-relaxed">
                Full PostgreSQL datastore isolation for client security. Eliminates shared table risks and physically prevents cross-tenant data leaks.
              </p>
            </div>
          </div>
          <div className="flex items-start space-x-4 p-6 rounded-2xl bg-slate-950/30 border border-slate-800/40 hover:border-indigo-500/20 transition-colors">
            <div className="p-3 rounded-lg bg-purple-500/10 text-purple-400 shrink-0">
              <RefreshCw className="w-6 h-6" />
            </div>
            <div>
              <h3 className="font-bold text-lg mb-2 text-white">Automated Arrears Billing</h3>
              <p className="text-slate-400 text-sm leading-relaxed">
                Hands-free monthly invoice generation, utility sub-meter splitting calculations, and automated daily email check-in reminders.
              </p>
            </div>
          </div>
          <div className="flex items-start space-x-4 p-6 rounded-2xl bg-slate-950/30 border border-slate-800/40 hover:border-indigo-500/20 transition-colors">
            <div className="p-3 rounded-lg bg-pink-500/10 text-pink-400 shrink-0">
              <Globe className="w-6 h-6" />
            </div>
            <div>
              <h3 className="font-bold text-lg mb-2 text-white">White-Labeled Subdomains</h3>
              <p className="text-slate-400 text-sm leading-relaxed">
                Run client portals under custom subdomains. Configure custom logos, colors, and header branding elements dynamically at startup.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Pricing Tier: Hybrid Asset Card */}
      <section id="pricing" className="max-w-7xl mx-auto px-6 py-24 relative">
        <div className="text-center mb-16 space-y-3">
          <h2 className="text-4xl font-extrabold tracking-tight">Predictable Hybrid Hosting</h2>
          <p className="text-slate-400 max-w-xl mx-auto text-lg">
            Own your dedicated single-tenant operations portal combined with central billing.
          </p>
        </div>

        {/* Pricing Card */}
        <div className="max-w-lg mx-auto rounded-3xl border border-indigo-500/30 bg-gradient-to-b from-[#13192e] to-[#0c0f19] p-8 md:p-10 shadow-2xl shadow-indigo-500/5 hover:border-indigo-500/50 transition-all duration-300 relative">
          <div className="absolute top-0 right-1/2 translate-x-1/2 -translate-y-1/2 px-4 py-1 rounded-full bg-gradient-to-r from-indigo-500 to-purple-600 text-xs font-bold uppercase tracking-wider text-white shadow-lg">
            Popular Choice
          </div>

          <div className="mb-6 space-y-2">
            <h3 className="text-2xl font-bold">Hybrid Asset Model</h3>
            <p className="text-slate-400 text-sm">
              Your own isolated application portal with central system maintenance.
            </p>
          </div>

          {/* Pricing blocks */}
          <div className="space-y-4 py-6 border-t border-b border-slate-800 mb-8">
            <div className="flex justify-between items-baseline">
              <span className="text-slate-400 text-sm font-semibold">One-Time Setup Fee</span>
              <span className="text-3xl font-extrabold text-white">₹15,000</span>
            </div>
            <div className="flex justify-between items-baseline">
              <span className="text-slate-400 text-sm font-semibold">Annual Maintenance Contract (AMC)</span>
              <span className="text-2xl font-extrabold text-indigo-400">₹35,000<span className="text-xs text-slate-500 font-normal">/yr</span></span>
            </div>
            <p className="text-xs text-slate-500 border-t border-slate-800/40 pt-2">
              Note: The initial ₹15,000 setup fee covers database provisioning and includes the first year of AMC coverage.
            </p>
          </div>

          {/* Feature List */}
          <div className="mb-8">
            <p className="text-xs font-bold text-indigo-400 uppercase tracking-widest mb-4">
              Included Features
            </p>
            <ul className="space-y-3.5">
              {priceCheckmarks.map((feature, idx) => (
                <li key={idx} className="flex items-start space-x-3 text-slate-300 text-sm">
                  <CheckCircle2 className="w-4 h-4 text-emerald-400 mt-0.5 shrink-0" />
                  <span>{feature}</span>
                </li>
              ))}
            </ul>
          </div>

          <Link
            to="/signup"
            className="block text-center w-full py-4 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white font-semibold text-lg transition-all duration-200 shadow-xl shadow-indigo-600/35 hover:shadow-indigo-600/50"
          >
            Purchase & Provision Now
          </Link>

          <p className="text-center text-xs text-slate-500 mt-4">
            Subsequent AMC contract renewals billed annually. Standard terms apply.
          </p>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-slate-800/80 bg-slate-950 py-8">
        <div className="max-w-7xl mx-auto px-6 flex flex-col md:flex-row justify-between items-center text-slate-500 text-sm">
          <p>© 2026 PG CRM Inc. All rights reserved.</p>
          <div className="flex space-x-6 mt-4 md:mt-0">
            <a href="#" className="hover:text-slate-400 transition-colors">Privacy Policy</a>
            <a href="#" className="hover:text-slate-400 transition-colors">Terms of Service</a>
            <a href="#" className="hover:text-slate-400 transition-colors">Support Portal</a>
          </div>
        </div>
      </footer>
    </div>
  );
}

import React from 'react';
import { Link } from 'react-router-dom';
import { CheckCircle, Shield, Zap, Database, Server, RefreshCw } from 'lucide-react';

export default function LandingPage() {
  const features = [
    "Isolated PostgreSQL Database-per-Tenant",
    "Whitelabeled Custom Subdomain (e.g., brand.pgcrm.com)",
    "Dynamic Email Notification & Reminder Schedulers",
    "Pre-integrated Razorpay Payment Gateway",
    "Automated Ansible VM Provisioning & Deployments",
    "Manager & Owner Control Panels with Role Auditing",
  ];

  return (
    <div className="bg-[#0b0f19] text-white min-h-screen font-sans selection:bg-indigo-500 selection:text-white overflow-x-hidden">
      {/* Navbar */}
      <nav className="max-w-7xl mx-auto px-6 py-6 flex justify-between items-center border-b border-gray-800/80">
        <div className="flex items-center space-x-2">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-indigo-500 to-purple-600 flex items-center justify-center shadow-lg shadow-indigo-500/20">
            <Server className="w-5 h-5 text-white" />
          </div>
          <span className="font-bold text-xl bg-gradient-to-r from-white via-gray-200 to-gray-400 bg-clip-text text-transparent">
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
      <section className="max-w-7xl mx-auto px-6 pt-20 pb-16 text-center relative">
        {/* Glow Effects */}
        <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[500px] h-[500px] bg-indigo-500/10 blur-[120px] rounded-full pointer-events-none" />
        <div className="absolute top-1/3 left-1/3 w-[300px] h-[300px] bg-purple-500/10 blur-[100px] rounded-full pointer-events-none" />

        <div className="relative z-10">
          <span className="px-4 py-1.5 rounded-full border border-indigo-500/30 bg-indigo-500/5 text-indigo-400 text-sm font-medium inline-block mb-6 shadow-inner">
            🚀 Whitelabel B2B SaaS Infrastructure
          </span>
          <h1 className="text-5xl md:text-7xl font-extrabold tracking-tight mb-8 leading-tight max-w-4xl mx-auto">
            Deploy Your Private
            <span className="block mt-2 bg-gradient-to-r from-indigo-400 via-purple-400 to-pink-400 bg-clip-text text-transparent">
              Hostel Management Instance
            </span>
          </h1>
          <p className="text-gray-400 text-lg md:text-xl max-w-2xl mx-auto mb-10 leading-relaxed">
            Get an isolated database, custom subdomain, automated billing engine, and full branding rights. Zero configuration required.
          </p>
          <div className="flex flex-col sm:flex-row justify-center items-center space-y-4 sm:space-y-0 sm:space-x-6">
            <Link
              to="/signup"
              className="w-full sm:w-auto px-8 py-4 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white font-semibold text-lg transition-all duration-200 shadow-xl shadow-indigo-600/25 hover:shadow-indigo-600/40 hover:-translate-y-0.5 flex items-center justify-center space-x-2"
            >
              <span>Get Started Now</span>
              <Zap className="w-5 h-5 fill-current" />
            </Link>
            <a
              href="#pricing"
              className="w-full sm:w-auto px-8 py-4 rounded-xl border border-gray-800 hover:border-gray-700 bg-gray-900/50 hover:bg-gray-950/80 text-gray-300 font-medium transition-all duration-200 flex items-center justify-center"
            >
              View Pricing
            </a>
          </div>
        </div>
      </section>

      {/* Trust Pillars */}
      <section className="max-w-7xl mx-auto px-6 py-12 border-t border-b border-gray-900 bg-gray-900/10">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          <div className="flex items-start space-x-4 p-4">
            <div className="p-3 rounded-lg bg-indigo-500/10 text-indigo-400">
              <Database className="w-6 h-6" />
            </div>
            <div>
              <h3 className="font-semibold text-lg mb-2">Independent Datastore</h3>
              <p className="text-gray-400 text-sm leading-relaxed">
                Full PostgreSQL isolation for client security. No shared tables or crosstalk risk.
              </p>
            </div>
          </div>
          <div className="flex items-start space-x-4 p-4">
            <div className="p-3 rounded-lg bg-purple-500/10 text-purple-400">
              <Shield className="w-6 h-6" />
            </div>
            <div>
              <h3 className="font-semibold text-lg mb-2">Automated AMC Tracking</h3>
              <p className="text-gray-400 text-sm leading-relaxed">
                Schedules daily reminder cron jobs to notify expiration 30 days, 7 days, and 1 day prior.
              </p>
            </div>
          </div>
          <div className="flex items-start space-x-4 p-4">
            <div className="p-3 rounded-lg bg-pink-500/10 text-pink-400">
              <RefreshCw className="w-6 h-6" />
            </div>
            <div>
              <h3 className="font-semibold text-lg mb-2">Instant VM Provisioning</h3>
              <p className="text-gray-400 text-sm leading-relaxed">
                Razorpay checkout triggers automated Ansible plays to deploy subdomains and databases.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Pricing Block */}
      <section id="pricing" className="max-w-7xl mx-auto px-6 py-24 relative">
        <div className="text-center mb-16">
          <h2 className="text-4xl font-extrabold mb-4 tracking-tight">Simple, Transparent Hosting</h2>
          <p className="text-gray-400 max-w-xl mx-auto text-lg">
            Complete ownership of your operations software instance with standard support tiers.
          </p>
        </div>

        {/* Pricing Card */}
        <div className="max-w-lg mx-auto rounded-3xl border border-indigo-500/30 bg-gradient-to-b from-[#13192e] to-[#0c0f19] p-8 md:p-10 shadow-2xl shadow-indigo-500/5 hover:border-indigo-500/50 transition-all duration-300 relative">
          <div className="absolute top-0 right-1/2 translate-x-1/2 -translate-y-1/2 px-4 py-1 rounded-full bg-gradient-to-r from-indigo-500 to-purple-600 text-xs font-bold uppercase tracking-wider text-white shadow-lg">
            Popular Choice
          </div>

          <div className="mb-6">
            <h3 className="text-2xl font-bold mb-2">Hybrid Asset Instance</h3>
            <p className="text-gray-400 text-sm">
              Your own isolated application portal with standard system maintenance.
            </p>
          </div>

          <div className="flex items-baseline space-x-1 mb-8 pb-6 border-b border-gray-800">
            <span className="text-5xl font-extrabold text-white">₹15,000</span>
            <span className="text-gray-400 text-sm font-medium">one-time setup fee</span>
          </div>

          <div className="mb-8">
            <p className="text-xs font-bold text-indigo-400 uppercase tracking-widest mb-4">
              Annual Contract Cost
            </p>
            <div className="flex items-center space-x-2 text-gray-300 font-medium mb-6">
              <CheckCircle className="w-5 h-5 text-indigo-400 shrink-0" />
              <span>Includes 1st Year Annual Maintenance Contract (AMC)</span>
            </div>

            <ul className="space-y-4">
              {features.map((feature, idx) => (
                <li key={idx} className="flex items-start space-x-3 text-gray-300 text-sm">
                  <CheckCircle className="w-4 h-4 text-emerald-400 mt-0.5 shrink-0" />
                  <span>{feature}</span>
                </li>
              ))}
            </ul>
          </div>

          <Link
            to="/signup"
            className="block text-center w-full py-4 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white font-semibold text-lg transition-all duration-200 shadow-xl shadow-indigo-600/35 hover:shadow-indigo-600/50"
          >
            Deploy My Instance
          </Link>

          <p className="text-center text-xs text-gray-500 mt-4">
            Subsequent AMC renewal billed annually. Standard terms apply.
          </p>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-gray-900/80 bg-[#070a12]/80 backdrop-blur py-8">
        <div className="max-w-7xl mx-auto px-6 flex flex-col md:flex-row justify-between items-center text-gray-500 text-sm">
          <p>© 2026 PG CRM Inc. All rights reserved.</p>
          <div className="flex space-x-6 mt-4 md:mt-0">
            <a href="#" className="hover:text-gray-400 transition-colors">Privacy Policy</a>
            <a href="#" className="hover:text-gray-400 transition-colors">Terms of Service</a>
            <a href="#" className="hover:text-gray-400 transition-colors">Support Portal</a>
          </div>
        </div>
      </footer>
    </div>
  );
}

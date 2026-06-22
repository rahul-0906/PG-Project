import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, CheckCircle2, Database, Globe, RefreshCw, Plus, ShieldAlert, Sparkles } from 'lucide-react';

export default function LandingPage() {
  const [typedText, setTypedText] = useState('');
  const [phraseIdx, setPhraseIdx] = useState(0);
  const [isDeleting, setIsDeleting] = useState(false);
  const phrases = ["DATABASE ISOLATION", "AUTOMATED BILLING", "WHITELABELED SUBDOMAINS"];

  const [activeDrawer, setActiveDrawer] = useState(null);

  const priceCheckmarks = [
    "Dedicated PostgreSQL Database Isolation",
    "Automated Daily System Backups",
    "Let's Encrypt SSL Domain Certificates",
    "Free Security Updates & Feature Upgrades",
    "Role-Based Access Control Audit Logs",
  ];

  // Typewriter effect loop
  useEffect(() => {
    let timer;
    const currentPhrase = phrases[phraseIdx];
    const speed = isDeleting ? 45 : 80;

    if (!isDeleting && typedText === currentPhrase) {
      timer = setTimeout(() => setIsDeleting(true), 2200);
    } else if (isDeleting && typedText === '') {
      setIsDeleting(false);
      setPhraseIdx((prev) => (prev + 1) % phrases.length);
    } else {
      timer = setTimeout(() => {
        setTypedText(
          isDeleting
            ? currentPhrase.substring(0, typedText.length - 1)
            : currentPhrase.substring(0, typedText.length + 1)
        );
      }, speed);
    }
    return () => clearTimeout(timer);
  }, [typedText, isDeleting, phraseIdx]);

  const toggleDrawer = (idx) => {
    setActiveDrawer(activeDrawer === idx ? null : idx);
  };

  return (
    <div className="bg-[#fafaf9] text-[#141414] min-h-screen font-sans selection:bg-black selection:text-white overflow-x-hidden">
      {/* Blinking cursor style */}
      <style>{`
        @keyframes cursorBlink {
          0%, 100% { opacity: 1; }
          50% { opacity: 0; }
        }
        .typed-cursor {
          animation: cursorBlink 0.8s infinite;
        }
      `}</style>

      {/* Navbar */}
      <nav className="sticky top-0 z-50 backdrop-blur-md bg-white/80 border-b border-neutral-250 py-5 px-6 md:px-12 flex justify-between items-center transition-all">
        <div className="flex items-center space-x-2">
          <div className="w-8 h-8 rounded-full bg-black flex items-center justify-center shrink-0">
            <span className="text-white font-extrabold text-sm tracking-tighter">CP</span>
          </div>
          <span className="font-black text-xl tracking-tight text-black">
            PG CRM <span className="font-normal text-neutral-500">CONTROL PLANE</span>
          </span>
        </div>
        <div className="flex items-center space-x-6">
          <Link
            to="/billing"
            className="text-sm font-bold hover:text-neutral-500 transition-colors uppercase tracking-wider hidden md:inline-block"
          >
            Client Portal
          </Link>
          <Link
            to="/signup"
            className="group relative inline-flex items-center justify-between border-2 border-black rounded-full px-5 py-2.5 bg-black text-white hover:bg-transparent hover:text-black transition-all duration-300"
          >
            <span className="text-sm font-bold tracking-wider uppercase mr-3">Launch</span>
            <div className="w-5 h-5 rounded-full bg-white group-hover:bg-black flex items-center justify-center transition-all duration-300 shrink-0">
              <ArrowRight className="w-3 h-3 text-black group-hover:text-white" />
            </div>
          </Link>
        </div>
      </nav>

      {/* Hero Section */}
      <section className="max-w-6xl mx-auto px-6 md:px-12 pt-28 pb-20 relative">
        <div className="space-y-8">
          <div className="flex items-center space-x-2">
            <span className="w-2.5 h-2.5 rounded-full bg-red-600 animate-pulse" />
            <span className="text-xs font-black uppercase tracking-widest text-neutral-500">
              Decoupled SaaS Infrastructure
            </span>
          </div>

          {/* Typewriter phrase container */}
          <div className="h-8 md:h-10 flex items-center">
            <span className="text-md md:text-lg font-black tracking-widest text-red-600 bg-red-50 px-3 py-1.5 border border-red-200 rounded-lg">
              {typedText}
              <span className="typed-cursor font-bold">|</span>
            </span>
          </div>

          <h1 className="text-5xl md:text-8xl font-black tracking-tight leading-none text-black max-w-5xl uppercase">
            Enterprise PG Operations <br />
            <span className="text-neutral-400">Fully Isolated.</span>
          </h1>

          <p className="text-neutral-600 text-lg md:text-2xl max-w-3xl leading-relaxed font-medium">
            We deploy secure, white-labeled single-tenant property management platforms. 
            Because isolated databases are structural assets that protect your customer and your back pocket.
          </p>

          <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-4 pt-6">
            <Link
              to="/signup"
              className="group relative inline-flex items-center justify-between border-2 border-black rounded-full px-8 py-4 bg-black text-white hover:bg-transparent hover:text-black transition-all duration-300 text-center"
            >
              <span className="text-lg font-bold tracking-wider uppercase mr-6">Start Your Onboarding</span>
              <div className="w-8 h-8 rounded-full bg-white group-hover:bg-black flex items-center justify-center transition-all duration-300 shrink-0">
                <ArrowRight className="w-4 h-4 text-black group-hover:text-white" />
              </div>
            </Link>

            <a
              href="#pricing"
              className="group relative inline-flex items-center justify-center border-2 border-neutral-300 rounded-full px-8 py-4 bg-transparent text-neutral-800 hover:border-black hover:text-black transition-all duration-300 text-center font-bold uppercase tracking-wider"
            >
              View Pricing
            </a>
          </div>
        </div>
      </section>

      {/* Grid: Section Divider 01 */}
      <div className="max-w-6xl mx-auto px-6 md:px-12 py-4 flex items-center space-x-4">
        <span className="text-xs font-black uppercase tracking-widest text-neutral-400">01 / Technical Moats</span>
        <div className="flex-1 h-[1px] bg-neutral-200" />
      </div>

      {/* Features Grid: 3-Column Dinergy Border Outline Style */}
      <section className="max-w-6xl mx-auto px-6 md:px-12 py-10">
        <div className="grid grid-cols-1 md:grid-cols-3 border border-neutral-200 bg-white rounded-3xl divide-y md:divide-y-0 md:divide-x divide-neutral-200 overflow-hidden shadow-sm">
          <div className="p-8 space-y-4 hover:bg-neutral-50/50 transition-colors">
            <div className="w-12 h-12 rounded-full border border-black flex items-center justify-center text-black">
              <Database className="w-5 h-5" />
            </div>
            <h3 className="text-xl font-bold tracking-tight uppercase text-black">Database Isolation</h3>
            <p className="text-neutral-500 text-sm leading-relaxed">
              Full PostgreSQL datastore isolation per tenant. Eliminates cross-tenant data leaks and satisfies security compliance checks.
            </p>
          </div>
          <div className="p-8 space-y-4 hover:bg-neutral-50/50 transition-colors">
            <div className="w-12 h-12 rounded-full border border-black flex items-center justify-center text-black">
              <RefreshCw className="w-5 h-5" />
            </div>
            <h3 className="text-xl font-bold tracking-tight uppercase text-black">Arrears Automation</h3>
            <p className="text-neutral-500 text-sm leading-relaxed">
              Hands-free monthly rent generation, utility split algorithms, and automated daily email check-in reminders.
            </p>
          </div>
          <div className="p-8 space-y-4 hover:bg-neutral-50/50 transition-colors">
            <div className="w-12 h-12 rounded-full border border-black flex items-center justify-center text-black">
              <Globe className="w-5 h-5" />
            </div>
            <h3 className="text-xl font-bold tracking-tight uppercase text-black">White-Label Portals</h3>
            <p className="text-neutral-500 text-sm leading-relaxed">
              Run client environments under custom subdomains. Dynamically updates logos, brands, and theme color parameters at startup.
            </p>
          </div>
        </div>
      </section>

      {/* Section Divider 02 */}
      <div className="max-w-6xl mx-auto px-6 md:px-12 py-4 flex items-center space-x-4">
        <span className="text-xs font-black uppercase tracking-widest text-neutral-400">02 / Pricing Tier</span>
        <div className="flex-1 h-[1px] bg-neutral-200" />
      </div>

      {/* Pricing Tier: Hybrid Asset Model Card */}
      <section id="pricing" className="max-w-6xl mx-auto px-6 md:px-12 py-12">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 items-start">
          <div className="lg:col-span-5 space-y-4">
            <h2 className="text-4xl md:text-6xl font-black uppercase tracking-tight text-black">
              Hybrid Asset Pricing.
            </h2>
            <p className="text-neutral-600 leading-relaxed text-lg">
              We offer structured, transparent pricing tailored to your B2B property business. 
              Own your isolated customer portal with central subscription upkeep.
            </p>
          </div>

          <div className="lg:col-span-7 bg-white border-2 border-black rounded-3xl p-8 md:p-10 shadow-lg relative overflow-hidden">
            <div className="absolute top-0 right-8 px-4 py-1.5 bg-black text-white text-xs font-bold uppercase tracking-widest rounded-b-xl">
              Standard Plan
            </div>

            <div className="space-y-6">
              <h3 className="text-2xl font-bold uppercase tracking-tight text-black">The Hybrid Model</h3>
              
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6 py-6 border-t border-b border-neutral-100">
                <div className="space-y-1">
                  <span className="text-xs font-semibold text-neutral-400 uppercase tracking-wider block">One-Time Setup Fee</span>
                  <span className="text-4xl font-black text-black">₹15,000</span>
                </div>
                <div className="space-y-1">
                  <span className="text-xs font-semibold text-neutral-400 uppercase tracking-wider block">Annual Contract (AMC)</span>
                  <span className="text-3xl font-black text-neutral-800">₹35,000<span className="text-xs font-medium text-neutral-400">/yr</span></span>
                </div>
              </div>

              <div className="space-y-4">
                <p className="text-xs uppercase font-black tracking-widest text-neutral-400">Included Features</p>
                <ul className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  {priceCheckmarks.map((feature, idx) => (
                    <li key={idx} className="flex items-start space-x-2.5 text-neutral-600 text-sm">
                      <CheckCircle2 className="w-4 h-4 text-black shrink-0 mt-0.5" />
                      <span>{feature}</span>
                    </li>
                  ))}
                </ul>
              </div>

              <div className="pt-4">
                <Link
                  to="/signup"
                  className="group relative inline-flex items-center justify-between border-2 border-black rounded-full px-8 py-4 bg-black text-white hover:bg-transparent hover:text-black transition-all duration-300 w-full text-center"
                >
                  <span className="text-lg font-bold tracking-wider uppercase mr-6">Purchase & Provision</span>
                  <div className="w-8 h-8 rounded-full bg-white group-hover:bg-black flex items-center justify-center transition-all duration-300 shrink-0">
                    <ArrowRight className="w-4 h-4 text-black group-hover:text-white" />
                  </div>
                </Link>
              </div>

              <p className="text-center text-[10px] uppercase font-bold tracking-wider text-neutral-400">
                First year of AMC is fully included in the setup fee. Subsequent renewals billed annually.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Section Divider 03 */}
      <div className="max-w-6xl mx-auto px-6 md:px-12 py-4 flex items-center space-x-4">
        <span className="text-xs font-black uppercase tracking-widest text-neutral-400">03 / How it Works</span>
        <div className="flex-1 h-[1px] bg-neutral-200" />
      </div>

      {/* FAQ / How it Works Accordions: Dinergy style drawer menu */}
      <section className="max-w-6xl mx-auto px-6 md:px-12 py-10 pb-28">
        <div className="border-t border-neutral-300">
          <div className="border-b border-neutral-300 py-6">
            <button
              onClick={() => toggleDrawer(0)}
              className="w-full flex justify-between items-center text-left focus:outline-none group"
            >
              <span className="text-xl md:text-2xl font-black uppercase tracking-tight text-neutral-900">
                1. How does automated provisioning work?
              </span>
              <div className={`w-8 h-8 rounded-full border border-black flex items-center justify-center transition-all duration-300 shrink-0 ${activeDrawer === 0 ? 'bg-black text-white' : 'bg-transparent text-black'}`}>
                <Plus className={`w-3.5 h-3.5 transition-transform duration-300 ${activeDrawer === 0 ? 'rotate-45' : ''}`} />
              </div>
            </button>
            <div className={`overflow-hidden transition-all duration-300 ${activeDrawer === 0 ? 'max-h-40 mt-4 opacity-100' : 'max-h-0 opacity-0'}`}>
              <p className="text-neutral-600 leading-relaxed text-sm">
                Once payment is confirmed, the control plane triggers an asynchronous script that reserves a unique port, creates a PostgreSQL database schema, sets up file structures under /opt/pgcrm, and builds a dedicated Docker Compose runtime environment.
              </p>
            </div>
          </div>

          <div className="border-b border-neutral-300 py-6">
            <button
              onClick={() => toggleDrawer(1)}
              className="w-full flex justify-between items-center text-left focus:outline-none group"
            >
              <span className="text-xl md:text-2xl font-black uppercase tracking-tight text-neutral-900">
                2. Can I use my own custom subdomain?
              </span>
              <div className={`w-8 h-8 rounded-full border border-black flex items-center justify-center transition-all duration-300 shrink-0 ${activeDrawer === 1 ? 'bg-black text-white' : 'bg-transparent text-black'}`}>
                <Plus className={`w-3.5 h-3.5 transition-transform duration-300 ${activeDrawer === 1 ? 'rotate-45' : ''}`} />
              </div>
            </button>
            <div className={`overflow-hidden transition-all duration-300 ${activeDrawer === 1 ? 'max-h-40 mt-4 opacity-100' : 'max-h-0 opacity-0'}`}>
              <p className="text-neutral-600 leading-relaxed text-sm">
                Yes. During onboarding, you register your desired domain name prefix (e.g. brandname). The system configures Nginx reverse proxy routes automatically, serving your whitelabel properties directly under brandname.pgcrm.com.
              </p>
            </div>
          </div>

          <div className="border-b border-neutral-300 py-6">
            <button
              onClick={() => toggleDrawer(2)}
              className="w-full flex justify-between items-center text-left focus:outline-none group"
            >
              <span className="text-xl md:text-2xl font-black uppercase tracking-tight text-neutral-900">
                3. What happens if the AMC expires?
              </span>
              <div className={`w-8 h-8 rounded-full border border-black flex items-center justify-center transition-all duration-300 shrink-0 ${activeDrawer === 2 ? 'bg-black text-white' : 'bg-transparent text-black'}`}>
                <Plus className={`w-3.5 h-3.5 transition-transform duration-300 ${activeDrawer === 2 ? 'rotate-45' : ''}`} />
              </div>
            </button>
            <div className={`overflow-hidden transition-all duration-300 ${activeDrawer === 2 ? 'max-h-40 mt-4 opacity-100' : 'max-h-0 opacity-0'}`}>
              <p className="text-neutral-600 leading-relaxed text-sm">
                Our scheduled alert engine sends notifications 30, 7, and 1 days before expiration. If the contract expires, portal access is suspended. Access is instantly restored once a renewal payment is processed in the billing command center.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-neutral-200 bg-white py-12 px-6 md:px-12">
        <div className="max-w-6xl mx-auto flex flex-col md:flex-row justify-between items-center text-neutral-400 text-xs font-bold uppercase tracking-wider space-y-4 md:space-y-0">
          <p>© 2026 PG CRM Inc. All rights reserved.</p>
          <div className="flex space-x-6">
            <a href="#" className="hover:text-black transition-colors">Privacy Policy</a>
            <a href="#" className="hover:text-black transition-colors">Terms of Service</a>
            <a href="#" className="hover:text-black transition-colors">Support Portal</a>
          </div>
        </div>
      </footer>
    </div>
  );
}


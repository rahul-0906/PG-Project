import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { 
  Server, 
  Database, 
  ShieldCheck, 
  Globe, 
  CheckCircle2, 
  AlertTriangle,
  ArrowRight, 
  Loader2,
  RefreshCw
} from 'lucide-react';

export default function ClientProvisioningTracker() {
  const [status, setStatus] = useState('PENDING_SETUP');
  const [progress, setProgress] = useState(0);
  const [currentStep, setCurrentStep] = useState('Initializing Setup...');
  const [tenantInfo, setTenantInfo] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  
  const pollingIntervalRef = useRef(null);
  const progressIntervalRef = useRef(null);
  const stepIndexRef = useRef(0);

  const steps = [
    'Initializing deployment pipeline...',
    'Allocating dedicated PostgreSQL database schema...',
    'Running database migrations...',
    'Registering routing configurations...',
    'Configuring reverse proxy rules...',
    'Acquiring SSL certificates...',
    'Bootstrapping initial administrative records...',
    'Finalizing workspace environments...'
  ];

  // Fetch status helper
  const fetchTenantStatus = async () => {
    try {
      const token = localStorage.getItem('token') || localStorage.getItem('jwt') || localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json'
      };
      if (token) {
        headers['Authorization'] = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
      }

      const response = await fetch('/api/tenant/me', {
        method: 'GET',
        headers: headers
      });

      if (response.status === 404) {
        setStatus('NOT_FOUND');
        setError('No active tenant onboarding profile found.');
        clearInterval(pollingIntervalRef.current);
        clearInterval(progressIntervalRef.current);
        return;
      }

      if (!response.ok) {
        throw new Error(`Failed to fetch tenant status (HTTP ${response.status})`);
      }

      const data = await response.json();
      setTenantInfo(data);
      setStatus(data.status);
      setError('');

      if (data.status === 'LIVE') {
        setProgress(100);
        setCurrentStep('Workspace Ready');
        clearInterval(pollingIntervalRef.current);
        clearInterval(progressIntervalRef.current);
      } else if (data.status === 'SUSPENDED') {
        clearInterval(pollingIntervalRef.current);
        clearInterval(progressIntervalRef.current);
      }
    } catch (err) {
      console.error('Error polling tenant status:', err);
      // We don't clear the interval here, hoping for transient recovery.
    } finally {
      setLoading(false);
    }
  };

  // Initial fetch and polling setup
  useEffect(() => {
    fetchTenantStatus();

    pollingIntervalRef.current = setInterval(() => {
      fetchTenantStatus();
    }, 2500);

    return () => {
      if (pollingIntervalRef.current) clearInterval(pollingIntervalRef.current);
      if (progressIntervalRef.current) clearInterval(progressIntervalRef.current);
    };
  }, []);

  // Simulated progress loop for PROVISIONING status
  useEffect(() => {
    if (status === 'PROVISIONING') {
      if (progressIntervalRef.current) clearInterval(progressIntervalRef.current);
      
      progressIntervalRef.current = setInterval(() => {
        setProgress((prev) => {
          if (prev >= 90) {
            // Keep at 90 until status turns LIVE
            return 90;
          }
          // Increment progress slightly
          const increment = Math.floor(Math.random() * 3) + 1;
          return Math.min(prev + increment, 90);
        });

        // Rotate steps
        stepIndexRef.current = (stepIndexRef.current + 1) % steps.length;
        setCurrentStep(steps[stepIndexRef.current]);
      }, 3000);
    } else if (status === 'PENDING_PAYMENT') {
      setProgress(15);
      setCurrentStep('Awaiting Subscription Payment Confirmation...');
    } else if (status === 'PENDING_SETUP') {
      setProgress(5);
      setCurrentStep('Profile Saved. Awaiting Onboarding Sequence...');
    }

    return () => {
      if (progressIntervalRef.current) clearInterval(progressIntervalRef.current);
    };
  }, [status]);

  if (loading && progress === 0) {
    return (
      <div className="bg-[#0c0a09] text-[#fafaf9] min-h-screen flex items-center justify-center font-sans">
        <div className="text-center space-y-4">
          <Loader2 className="w-10 h-10 animate-spin text-violet-500 mx-auto" />
          <p className="text-neutral-400 text-sm uppercase tracking-widest font-black">Loading Tracker...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-[#0c0a09] text-[#fafaf9] min-h-screen font-sans flex items-center justify-center py-16 px-6 relative overflow-hidden">
      {/* Mesh Background Gradients */}
      <div className="absolute top-[-10%] left-[-10%] w-[50%] h-[50%] rounded-full bg-violet-950/20 blur-[120px] pointer-events-none" />
      <div className="absolute bottom-[-10%] right-[-10%] w-[50%] h-[50%] rounded-full bg-indigo-950/20 blur-[120px] pointer-events-none" />
      
      {/* Decorative tech grid */}
      <div className="absolute inset-0 bg-[linear-gradient(to_right,#80808003_1px,transparent_1px),linear-gradient(to_bottom,#80808003_1px,transparent_1px)] bg-[size:24px_24px] pointer-events-none" />

      <div className="max-w-2xl w-full relative z-10 space-y-8">
        
        {/* Title Section */}
        <div className="text-center space-y-3">
          <div className="inline-flex items-center space-x-2 px-3 py-1 rounded-full border border-neutral-800 bg-neutral-900/50 backdrop-blur-sm text-[10px] font-black text-violet-400 uppercase tracking-widest">
            {status === 'LIVE' ? (
              <span className="flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
                Infrastructure Live
              </span>
            ) : status === 'SUSPENDED' ? (
              <span className="flex items-center gap-1.5 text-rose-400">
                <span className="w-1.5 h-1.5 rounded-full bg-rose-500" />
                Pipeline Error
              </span>
            ) : (
              <span className="flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-violet-500 animate-ping" />
                Provisioning Node
              </span>
            )}
          </div>
          <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight bg-gradient-to-r from-neutral-100 to-neutral-400 bg-clip-text text-transparent">
            {status === 'LIVE' ? 'Workspace Is Ready' : 'Setting Up Your Space'}
          </h1>
          <p className="text-neutral-500 text-xs md:text-sm max-w-md mx-auto">
            {status === 'LIVE' 
              ? 'Your high-performance properties portal is deployed and available.' 
              : 'Our automated scheduler is deploying database servers, DNS routes, and SSL instances.'
            }
          </p>
        </div>

        {/* Glassmorphic Progress Console */}
        <div className="border border-neutral-800 bg-neutral-900/40 backdrop-blur-xl rounded-3xl p-8 shadow-2xl relative">
          
          <div className="space-y-6">
            
            {/* Upper state labels */}
            <div className="flex justify-between items-center text-xs">
              <span className="font-bold text-neutral-400 uppercase tracking-wider">
                {currentStep}
              </span>
              <span className="font-mono font-bold text-violet-400 bg-violet-950/30 border border-violet-900/50 px-2.5 py-0.5 rounded-md">
                {progress}%
              </span>
            </div>

            {/* Glowing Progress Bar container */}
            <div className="w-full h-3 bg-neutral-950 rounded-full overflow-hidden border border-neutral-900 relative">
              <div 
                className="h-full bg-gradient-to-r from-violet-500 via-purple-500 to-indigo-500 rounded-full transition-all duration-700 ease-out shadow-[0_0_20px_rgba(139,92,246,0.6)]"
                style={{ width: `${progress}%` }}
              />
            </div>

            {/* Steps checklists */}
            <div className="border-t border-neutral-800/80 pt-6 space-y-4">
              <h3 className="text-[10px] font-black text-neutral-500 uppercase tracking-widest">
                Deployment Sequence
              </h3>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                
                {/* Step 1: Database */}
                <div className={`flex items-center space-x-3 p-3 rounded-2xl border transition-all duration-300 ${
                  progress >= 30 ? 'bg-neutral-950/40 border-neutral-800' : 'border-transparent opacity-40'
                }`}>
                  <div className={`p-2 rounded-xl border ${
                    progress >= 30 ? 'bg-violet-950/20 border-violet-900/50 text-violet-400' : 'bg-neutral-900 border-neutral-800 text-neutral-600'
                  }`}>
                    <Database className="w-4 h-4" />
                  </div>
                  <div>
                    <h4 className="text-xs font-bold text-neutral-200">Database Schema</h4>
                    <p className="text-[10px] text-neutral-500">PostgreSQL Isolated tables</p>
                  </div>
                </div>

                {/* Step 2: Routing */}
                <div className={`flex items-center space-x-3 p-3 rounded-2xl border transition-all duration-300 ${
                  progress >= 60 ? 'bg-neutral-950/40 border-neutral-800' : 'border-transparent opacity-40'
                }`}>
                  <div className={`p-2 rounded-xl border ${
                    progress >= 60 ? 'bg-violet-950/20 border-violet-900/50 text-violet-400' : 'bg-neutral-900 border-neutral-800 text-neutral-600'
                  }`}>
                    <Globe className="w-4 h-4" />
                  </div>
                  <div>
                    <h4 className="text-xs font-bold text-neutral-200">Subdomain Mapping</h4>
                    <p className="text-[10px] text-neutral-500">Proxy redirects configured</p>
                  </div>
                </div>

                {/* Step 3: Security SSL */}
                <div className={`flex items-center space-x-3 p-3 rounded-2xl border transition-all duration-300 ${
                  progress >= 80 ? 'bg-neutral-950/40 border-neutral-800' : 'border-transparent opacity-40'
                }`}>
                  <div className={`p-2 rounded-xl border ${
                    progress >= 80 ? 'bg-violet-950/20 border-violet-900/50 text-violet-400' : 'bg-neutral-900 border-neutral-800 text-neutral-600'
                  }`}>
                    <ShieldCheck className="w-4 h-4" />
                  </div>
                  <div>
                    <h4 className="text-xs font-bold text-neutral-200">SSL Certificate</h4>
                    <p className="text-[10px] text-neutral-500">Certbot authorization verified</p>
                  </div>
                </div>

                {/* Step 4: App Server */}
                <div className={`flex items-center space-x-3 p-3 rounded-2xl border transition-all duration-300 ${
                  progress >= 100 ? 'bg-neutral-950/40 border-neutral-800' : 'border-transparent opacity-40'
                }`}>
                  <div className={`p-2 rounded-xl border ${
                    progress >= 100 ? 'bg-emerald-950/20 border-emerald-900/50 text-emerald-400' : 'bg-neutral-900 border-neutral-800 text-neutral-600'
                  }`}>
                    <Server className="w-4 h-4" />
                  </div>
                  <div>
                    <h4 className="text-xs font-bold text-neutral-200">Gateway Active</h4>
                    <p className="text-[10px] text-neutral-500">Websockets & ports open</p>
                  </div>
                </div>

              </div>
            </div>

          </div>
        </div>

        {/* Conditional Actions/Logs based on status */}
        {status === 'LIVE' && tenantInfo && (
          <div className="flex flex-col items-center space-y-4 animate-fade-in">
            <a
              href={`https://${tenantInfo.customDomain}.pgcrm.com`}
              target="_blank"
              rel="noreferrer"
              className="group relative inline-flex items-center justify-between border border-violet-500 rounded-2xl px-8 py-4 bg-violet-600 hover:bg-violet-500 text-white font-bold tracking-wider transition-all duration-300 w-full text-center shadow-[0_0_30px_rgba(139,92,246,0.4)] animate-bounce"
            >
              <span className="text-sm uppercase tracking-widest text-center w-full flex items-center justify-center gap-2">
                Enter Workspace 
                <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
              </span>
            </a>
            <div className="text-[10px] text-neutral-500">
              Workspace Endpoint: <span className="font-mono text-violet-400">{tenantInfo.customDomain}.pgcrm.com</span>
            </div>
          </div>
        )}

        {status === 'SUSPENDED' && (
          <div className="p-5 border border-rose-900/30 bg-rose-950/10 rounded-2xl flex items-start space-x-3 text-left">
            <AlertTriangle className="w-5 h-5 text-rose-500 shrink-0 mt-0.5" />
            <div className="space-y-1">
              <h4 className="text-xs font-bold text-rose-200 uppercase tracking-wide">Deployment Suspended</h4>
              <p className="text-[10px] text-neutral-400">
                An issue was encountered while setting up database partitions or proxy routing. Support team has been notified.
              </p>
            </div>
          </div>
        )}

        {error && (
          <div className="p-4 border border-yellow-900/30 bg-yellow-950/10 rounded-2xl flex items-center space-x-3 text-left">
            <AlertTriangle className="w-5 h-5 text-yellow-500 shrink-0" />
            <p className="text-[10px] text-neutral-400 font-medium">{error}</p>
          </div>
        )}

        {/* Footer utilities */}
        <div className="text-center">
          <button
            onClick={fetchTenantStatus}
            className="inline-flex items-center space-x-2 text-[10px] font-black uppercase text-neutral-600 hover:text-neutral-300 transition-colors"
          >
            <RefreshCw className="w-3 h-3" />
            <span>Force Sync Status</span>
          </button>
        </div>

      </div>
    </div>
  );
}

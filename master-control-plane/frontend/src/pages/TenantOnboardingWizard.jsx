import React, { useState, useEffect } from 'react';
import { 
  Building2, 
  Sparkles, 
  Key, 
  CreditCard, 
  Lock, 
  ArrowRight, 
  ArrowLeft, 
  Check, 
  CheckCircle2, 
  AlertCircle, 
  Loader2,
  Mail,
  Smartphone,
  Server
} from 'lucide-react';

const PLAN_FEE_MAPPING = {
  MONTHLY: 1999.00,
  YEARLY: 19999.00
};

export default function TenantOnboardingWizard() {
  const [step, setStep] = useState(1);
  const [formData, setFormData] = useState({
    pgName: '',
    pgShortTitle: '',
    customDomain: '',
    routerIp: '',
    whatsappNumber: '',
    contactEmail: '',
    whatsappToken: '',
    whatsappKey: '',
    razorpayKey: '',
    razorpaySecret: '',
    planType: 'YEARLY',
    amcFee: PLAN_FEE_MAPPING.YEARLY
  });

  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState('');
  const [validationErrors, setValidationErrors] = useState({});

  // Sync amcFee whenever planType changes
  useEffect(() => {
    setFormData(prev => ({
      ...prev,
      amcFee: PLAN_FEE_MAPPING[formData.planType] || 0
    }));
  }, [formData.planType]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    if (name === 'customDomain') {
      const sanitized = value.toLowerCase().replace(/[^a-z0-9-]/g, '');
      setFormData(prev => ({ ...prev, [name]: sanitized }));
    } else {
      setFormData(prev => ({ ...prev, [name]: value }));
    }

    if (validationErrors[name]) {
      setValidationErrors(prev => ({ ...prev, [name]: '' }));
    }
  };

  const validateStep = (currentStep) => {
    const errors = {};
    if (currentStep === 1) {
      if (!formData.pgName.trim()) errors.pgName = 'PG Name is required';
      if (!formData.customDomain.trim()) {
        errors.customDomain = 'Custom domain prefix is required';
      } else if (formData.customDomain.length < 3) {
        errors.customDomain = 'Domain must be at least 3 characters';
      }
    } else if (currentStep === 2) {
      if (!formData.contactEmail.trim()) {
        errors.contactEmail = 'Contact email is required';
      } else if (!/\S+@\S+\.\S+/.test(formData.contactEmail)) {
        errors.contactEmail = 'Invalid email address format';
      }
      if (!formData.whatsappNumber.trim()) {
        errors.whatsappNumber = 'WhatsApp number is required';
      }
      if (!formData.whatsappToken.trim()) {
        errors.whatsappToken = 'WhatsApp Business Token is required';
      }
      if (!formData.whatsappKey.trim()) {
        errors.whatsappKey = 'WhatsApp Key parameter is required';
      }
    } else if (currentStep === 3) {
      if (!formData.razorpayKey.trim()) errors.razorpayKey = 'Razorpay Key ID is required';
      if (!formData.razorpaySecret.trim()) errors.razorpaySecret = 'Razorpay Key Secret is required';
    }

    setValidationErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleNext = () => {
    if (validateStep(step)) {
      setStep(prev => prev + 1);
    }
  };

  const handleBack = () => {
    setStep(prev => prev - 1);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validateStep(3)) return;

    setLoading(true);
    setError('');

    try {
      const token = localStorage.getItem('token') || localStorage.getItem('jwt') || localStorage.getItem('accessToken');
      
      const headers = {
        'Content-Type': 'application/json'
      };

      if (token) {
        headers['Authorization'] = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
      }

      const response = await fetch('/api/onboarding/signup', {
        method: 'POST',
        headers: headers,
        body: JSON.stringify(formData)
      });

      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(errorText || `Failed to onboard (HTTP ${response.status})`);
      }

      setSuccess(true);
    } catch (err) {
      console.error('Onboarding API Error:', err);
      setError(err.message || 'An unexpected error occurred during onboarding registration.');
    } finally {
      setLoading(false);
    }
  };

  if (success) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center p-6 font-sans">
        <div className="w-full max-w-md bg-white border border-slate-200 rounded-3xl p-8 text-center shadow-xl">
          <div className="w-16 h-16 bg-emerald-50 border border-emerald-200 rounded-full flex items-center justify-center mx-auto mb-6">
            <CheckCircle2 className="w-8 h-8 text-emerald-600" />
          </div>
          <h2 className="text-2xl font-extrabold text-slate-900 mb-3">Onboarding Submitted!</h2>
          <p className="text-slate-500 text-sm mb-8 leading-relaxed">
            Your tenant workspace for <span className="text-slate-900 font-semibold">{formData.pgName}</span> was successfully initialized. Click below to enter your management dashboard.
          </p>
          <button 
            onClick={() => window.location.href = '/admin'}
            className="w-full py-3.5 px-6 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-xl shadow-md transition-colors"
          >
            Go to Admin Dashboard
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50 flex items-center justify-center p-6 font-sans text-slate-800">
      <div className="w-full max-w-4xl bg-white border border-slate-200 rounded-3xl shadow-xl overflow-hidden">
        {/* Top Accent Gradient Bar */}
        <div className="h-1.5 w-full bg-gradient-to-r from-blue-600 via-indigo-500 to-blue-700" />

        <div className="p-8 md:p-10">
          {/* Header */}
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-6 mb-8 pb-6 border-b border-slate-200">
            <div>
              <h1 className="text-2xl font-extrabold tracking-tight text-slate-900 flex items-center gap-2">
                <Sparkles className="w-6 h-6 text-blue-600 animate-pulse" />
                <span>Tenant Onboarding Wizard</span>
              </h1>
              <p className="text-slate-500 text-xs mt-1">Configure your full-stack property management system.</p>
            </div>

            {/* Stepper progress indicator */}
            <div className="flex items-center space-x-2 bg-slate-50 border border-slate-200 p-1.5 rounded-2xl shrink-0">
              {[
                { number: 1, label: 'Workspace', icon: Building2 },
                { number: 2, label: 'Integration', icon: Key },
                { number: 3, label: 'Billing', icon: CreditCard }
              ].map((s) => {
                const ActiveIcon = s.icon;
                const isCompleted = step > s.number;
                const isActive = step === s.number;
                return (
                  <React.Fragment key={s.number}>
                    <div 
                      className={`flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-bold transition-all duration-300 ${
                        isCompleted 
                          ? 'bg-emerald-50 border border-emerald-200 text-emerald-600' 
                          : isActive 
                            ? 'bg-blue-600 text-white shadow-md shadow-blue-500/10' 
                            : 'text-slate-400 bg-transparent border-transparent'
                      }`}
                    >
                      {isCompleted ? <Check className="w-3.5 h-3.5" /> : <ActiveIcon className="w-3.5 h-3.5" />}
                      <span>{s.label}</span>
                    </div>
                    {s.number < 3 && <div className="h-px w-4 bg-slate-200" />}
                  </React.Fragment>
                );
              })}
            </div>
          </div>

          {error && (
            <div className="mb-6 p-4 rounded-xl border border-red-200 bg-red-50 text-red-600 text-xs font-semibold flex items-start space-x-2.5 shadow-sm">
              <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
              <span>{error}</span>
            </div>
          )}

          {/* Form */}
          <form onSubmit={handleSubmit} className="space-y-6">
            
            {/* STEP 1: Workspace Core Configuration */}
            {step === 1 && (
              <div className="space-y-5 animate-fadeIn">
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                  <div>
                    <label className="block text-slate-600 font-bold uppercase tracking-wider text-[10px] mb-2 pl-1">PG Business Name</label>
                    <div className="relative">
                      <Building2 className="absolute left-4 top-3.5 h-4.5 w-4.5 text-slate-400" />
                      <input
                        type="text"
                        name="pgName"
                        value={formData.pgName}
                        onChange={handleChange}
                        className={`w-full bg-slate-50 border ${validationErrors.pgName ? 'border-red-400' : 'border-slate-200 hover:border-slate-350 focus:bg-white focus:border-blue-500'} rounded-xl pl-11 pr-4 py-3 text-sm text-slate-900 placeholder-slate-400 outline-none transition-all`}
                        placeholder="e.g. Stanza Premium Living"
                      />
                    </div>
                    {validationErrors.pgName && <p className="text-red-500 text-[10px] font-semibold mt-1.5 pl-1">{validationErrors.pgName}</p>}
                  </div>

                  <div>
                    <label className="block text-slate-600 font-bold uppercase tracking-wider text-[10px] mb-2 pl-1">Brand Short Title</label>
                    <input
                      type="text"
                      name="pgShortTitle"
                      value={formData.pgShortTitle}
                      onChange={handleChange}
                      className="w-full bg-slate-50 border border-slate-200 hover:border-slate-350 focus:bg-white focus:border-blue-500 rounded-xl px-4 py-3 text-sm text-slate-900 placeholder-slate-400 outline-none transition-all"
                      placeholder="e.g. Stanza"
                    />
                  </div>

                  <div>
                    <label className="block text-slate-600 font-bold uppercase tracking-wider text-[10px] mb-2 pl-1">Subdomain Name</label>
                    <div className="flex rounded-xl bg-slate-50 border border-slate-200 focus-within:bg-white focus-within:border-blue-500 transition-all overflow-hidden">
                      <input
                        type="text"
                        name="customDomain"
                        value={formData.customDomain}
                        onChange={handleChange}
                        className="bg-transparent flex-1 px-4 py-3 text-sm text-slate-900 placeholder-slate-400 outline-none"
                        placeholder="stanza-premium"
                      />
                      <span className="bg-slate-100 border-l border-slate-200 text-slate-500 px-4 py-3 font-semibold text-xs flex items-center">
                        .pgcrm.com
                      </span>
                    </div>
                    {validationErrors.customDomain ? (
                      <p className="text-red-500 text-[10px] font-semibold mt-1.5 pl-1">{validationErrors.customDomain}</p>
                    ) : (
                      <p className="text-slate-400 text-[9px] mt-1 pl-1">Identifies your dedicated instance hosting URL.</p>
                    )}
                  </div>

                  <div>
                    <label className="block text-slate-600 font-bold uppercase tracking-wider text-[10px] mb-2 pl-1">Workspace Router IP</label>
                    <div className="relative">
                      <Server className="absolute left-4 top-3.5 h-4.5 w-4.5 text-slate-400" />
                      <input
                        type="text"
                        name="routerIp"
                        value={formData.routerIp}
                        onChange={handleChange}
                        className="w-full bg-slate-50 border border-slate-200 hover:border-slate-350 focus:bg-white focus:border-blue-500 rounded-xl pl-11 pr-4 py-3 text-sm text-slate-900 placeholder-slate-400 outline-none transition-all"
                        placeholder="e.g. 192.168.1.1"
                      />
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* STEP 2: Communications & Integration */}
            {step === 2 && (
              <div className="space-y-5 animate-fadeIn">
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                  <div>
                    <label className="block text-slate-600 font-bold uppercase tracking-wider text-[10px] mb-2 pl-1">WhatsApp Business Number</label>
                    <div className="relative">
                      <Smartphone className="absolute left-4 top-3.5 h-4.5 w-4.5 text-slate-400" />
                      <input
                        type="tel"
                        name="whatsappNumber"
                        value={formData.whatsappNumber}
                        onChange={handleChange}
                        className={`w-full bg-slate-50 border ${validationErrors.whatsappNumber ? 'border-red-400' : 'border-slate-200 hover:border-slate-350 focus:bg-white focus:border-blue-500'} rounded-xl pl-11 pr-4 py-3 text-sm text-slate-900 placeholder-slate-400 outline-none transition-all`}
                        placeholder="e.g. +91 98765 43210"
                      />
                    </div>
                    {validationErrors.whatsappNumber && <p className="text-red-500 text-[10px] font-semibold mt-1.5 pl-1">{validationErrors.whatsappNumber}</p>}
                  </div>

                  <div>
                    <label className="block text-slate-600 font-bold uppercase tracking-wider text-[10px] mb-2 pl-1">Contact Email Address</label>
                    <div className="relative">
                      <Mail className="absolute left-4 top-3.5 h-4.5 w-4.5 text-slate-400" />
                      <input
                        type="email"
                        name="contactEmail"
                        value={formData.contactEmail}
                        onChange={handleChange}
                        className={`w-full bg-slate-50 border ${validationErrors.contactEmail ? 'border-red-400' : 'border-slate-200 hover:border-slate-350 focus:bg-white focus:border-blue-500'} rounded-xl pl-11 pr-4 py-3 text-sm text-slate-900 placeholder-slate-400 outline-none transition-all`}
                        placeholder="e.g. help@stanza.com"
                      />
                    </div>
                    {validationErrors.contactEmail && <p className="text-red-500 text-[10px] font-semibold mt-1.5 pl-1">{validationErrors.contactEmail}</p>}
                  </div>

                  <div>
                    <label className="block text-slate-600 font-bold uppercase tracking-wider text-[10px] mb-2 pl-1">WhatsApp Cloud API Token</label>
                    <div className="relative">
                      <Lock className="absolute left-4 top-3.5 h-4.5 w-4.5 text-slate-400" />
                      <input
                        type="text"
                        name="whatsappToken"
                        value={formData.whatsappToken}
                        onChange={handleChange}
                        className={`w-full bg-slate-50 border ${validationErrors.whatsappToken ? 'border-red-400' : 'border-slate-200 hover:border-slate-350 focus:bg-white focus:border-blue-500'} rounded-xl pl-11 pr-4 py-3 text-sm text-slate-900 placeholder-slate-400 outline-none transition-all`}
                        placeholder="EAAGNO..."
                      />
                    </div>
                    {validationErrors.whatsappToken && <p className="text-red-500 text-[10px] font-semibold mt-1.5 pl-1">{validationErrors.whatsappToken}</p>}
                  </div>

                  <div>
                    <label className="block text-slate-600 font-bold uppercase tracking-wider text-[10px] mb-2 pl-1">WhatsApp Key / ID Parameter</label>
                    <div className="relative">
                      <Key className="absolute left-4 top-3.5 h-4.5 w-4.5 text-slate-400" />
                      <input
                        type="text"
                        name="whatsappKey"
                        value={formData.whatsappKey}
                        onChange={handleChange}
                        className={`w-full bg-slate-50 border ${validationErrors.whatsappKey ? 'border-red-400' : 'border-slate-200 hover:border-slate-350 focus:bg-white focus:border-blue-500'} rounded-xl pl-11 pr-4 py-3 text-sm text-slate-900 placeholder-slate-400 outline-none transition-all`}
                        placeholder="e.g. 104523789456"
                      />
                    </div>
                    {validationErrors.whatsappKey && <p className="text-red-500 text-[10px] font-semibold mt-1.5 pl-1">{validationErrors.whatsappKey}</p>}
                  </div>
                </div>
              </div>
            )}

            {/* STEP 3: Razorpay Credentials & Billing Subscription Plans */}
            {step === 3 && (
              <div className="space-y-6 animate-fadeIn">
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                  <div>
                    <label className="block text-slate-600 font-bold uppercase tracking-wider text-[10px] mb-2 pl-1">Razorpay Key ID</label>
                    <div className="relative">
                      <Lock className="absolute left-4 top-3.5 h-4 w-4 text-slate-400" />
                      <input
                        type="text"
                        name="razorpayKey"
                        value={formData.razorpayKey}
                        onChange={handleChange}
                        className={`w-full bg-slate-50 border ${validationErrors.razorpayKey ? 'border-red-400' : 'border-slate-200 hover:border-slate-350 focus:bg-white focus:border-blue-500'} rounded-xl pl-11 pr-4 py-3 text-sm text-slate-900 placeholder-slate-400 outline-none transition-all`}
                        placeholder="rzp_live_..."
                      />
                    </div>
                    {validationErrors.razorpayKey && <p className="text-red-500 text-[10px] font-semibold mt-1.5 pl-1">{validationErrors.razorpayKey}</p>}
                  </div>

                  <div>
                    <label className="block text-slate-600 font-bold uppercase tracking-wider text-[10px] mb-2 pl-1">Razorpay Secret Key</label>
                    <div className="relative">
                      <Lock className="absolute left-4 top-3.5 h-4 w-4 text-slate-400" />
                      <input
                        type="password"
                        name="razorpaySecret"
                        value={formData.razorpaySecret}
                        onChange={handleChange}
                        className={`w-full bg-slate-50 border ${validationErrors.razorpaySecret ? 'border-red-400' : 'border-slate-200 hover:border-slate-350 focus:bg-white focus:border-blue-500'} rounded-xl pl-11 pr-4 py-3 text-sm text-slate-900 placeholder-slate-400 outline-none transition-all`}
                        placeholder="••••••••••••••••••••••••"
                      />
                    </div>
                    {validationErrors.razorpaySecret && <p className="text-red-500 text-[10px] font-semibold mt-1.5 pl-1">{validationErrors.razorpaySecret}</p>}
                  </div>
                </div>

                {/* Subscription Options */}
                <div>
                  <label className="block text-slate-600 font-bold uppercase tracking-wider text-[10px] mb-3 pl-1">Select AMC Contract Plan</label>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    
                    {/* Monthly option */}
                    <label className={`flex items-start gap-4 p-4 rounded-2xl border transition-all cursor-pointer ${
                      formData.planType === 'MONTHLY' 
                        ? 'border-blue-500 bg-blue-50/30' 
                        : 'border-slate-200 hover:border-slate-300 bg-slate-50/50'
                    }`}>
                      <input
                        type="radio"
                        name="planType"
                        value="MONTHLY"
                        checked={formData.planType === 'MONTHLY'}
                        onChange={handleChange}
                        className="mt-1 accent-blue-600 cursor-pointer"
                      />
                      <div>
                        <span className="block font-bold text-sm text-slate-900">Monthly Upkeep Contract</span>
                        <span className="block text-[10px] text-slate-500 mt-1 leading-normal">
                          Billed monthly. Standard SaaS configuration maintenance.
                        </span>
                        <span className="block text-sm font-black text-blue-600 mt-3">₹1,999 / mo</span>
                      </div>
                    </label>

                    {/* Yearly option */}
                    <label className={`flex items-start gap-4 p-4 rounded-2xl border transition-all cursor-pointer ${
                      formData.planType === 'YEARLY' 
                        ? 'border-blue-500 bg-blue-50/30' 
                        : 'border-slate-200 hover:border-slate-300 bg-slate-50/50'
                    }`}>
                      <input
                        type="radio"
                        name="planType"
                        value="YEARLY"
                        checked={formData.planType === 'YEARLY'}
                        onChange={handleChange}
                        className="mt-1 accent-blue-600 cursor-pointer"
                      />
                      <div className="w-full">
                        <div className="flex items-center justify-between gap-2">
                          <span className="font-bold text-sm text-slate-900">Yearly Upkeep Contract</span>
                          <span className="text-[8px] font-black tracking-widest text-emerald-700 bg-emerald-100 border border-emerald-200 px-1.5 py-0.5 rounded uppercase">Save 15%</span>
                        </div>
                        <span className="block text-[10px] text-slate-500 mt-1 leading-normal">
                          Billed yearly. Premium automated server optimizations.
                        </span>
                        <span className="block text-sm font-black text-blue-600 mt-3">₹19,999 / yr</span>
                      </div>
                    </label>

                  </div>
                </div>

                {/* Fee display */}
                <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 flex justify-between items-center text-xs">
                  <span className="text-slate-500 font-bold uppercase tracking-wider text-[10px]">Auto-Calculated AMC Subscription Total:</span>
                  <span className="text-slate-900 font-extrabold text-sm">₹{formData.amcFee.toLocaleString('en-IN')}.00</span>
                </div>
              </div>
            )}

            {/* Actions Panel */}
            <div className="flex items-center justify-between gap-4 pt-6 border-t border-slate-200">
              {step > 1 ? (
                <button
                  type="button"
                  onClick={handleBack}
                  className="flex items-center justify-center gap-2 bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold border border-slate-200 rounded-xl px-5 py-3 text-xs transition-all active:scale-[0.98]"
                >
                  <ArrowLeft className="w-4 h-4" />
                  <span>Back</span>
                </button>
              ) : (
                <div /> // Spacer
              )}

              {step < 3 ? (
                <button
                  type="button"
                  onClick={handleNext}
                  className="flex items-center justify-center gap-2 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-xl px-6 py-3 text-xs shadow-md shadow-blue-500/10 transition-all active:scale-[0.98]"
                >
                  <span>Next Step</span>
                  <ArrowRight className="w-4 h-4" />
                </button>
              ) : (
                <button
                  type="submit"
                  disabled={loading}
                  className="flex items-center justify-center gap-2 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-xl px-6 py-3 text-xs shadow-md shadow-blue-500/10 transition-all active:scale-[0.98] disabled:opacity-55"
                >
                  {loading ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      <span>Registering Workspace...</span>
                    </>
                  ) : (
                    <>
                      <span>Submit & Proceed to Payment</span>
                      <CheckCircle2 className="w-4 h-4" />
                    </>
                  )}
                </button>
              )}
            </div>

          </form>
        </div>
      </div>
    </div>
  );
}

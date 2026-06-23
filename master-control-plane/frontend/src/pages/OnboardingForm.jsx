import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import axios from 'axios';
import { ArrowLeft, Loader2, Sparkles, CheckCircle2, AlertTriangle, ShieldCheck, ArrowRight, Settings2, Palette } from 'lucide-react';

const PRESET_COLORS = [
  { name: 'Indigo', hex: '#6366f1' },
  { name: 'Blue', hex: '#3b82f6' },
  { name: 'Emerald', hex: '#10b981' },
  { name: 'Orange', hex: '#f97316' },
  { name: 'Red', hex: '#ef4444' },
  { name: 'Purple', hex: '#8b5cf6' },
  { name: 'Charcoal', hex: '#1e293b' },
];

export default function OnboardingForm() {
  const navigate = useNavigate();
  const [formData, setFormData] = useState({
    ownerName: '',
    email: '',
    phone: '',
    pgBrandName: '',
    domainName: '',
    whatsappToken: '',
    razorpayKeyId: '',
    razorpayKeySecret: '',
    primaryColor: '#6366f1',
  });

  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [step, setStep] = useState(1);

  const handleChange = (e) => {
    const { name, value } = e.target;
    if (name === 'domainName') {
      const sanitized = value.toLowerCase().replace(/[^a-z0-9-]/g, '');
      setFormData((prev) => ({ ...prev, [name]: sanitized }));
    } else {
      setFormData((prev) => ({ ...prev, [name]: value }));
    }

    if (errors[name]) {
      setErrors((prev) => ({ ...prev, [name]: '' }));
    }
  };

  const selectColor = (hex) => {
    setFormData((prev) => ({ ...prev, primaryColor: hex }));
  };

  const validate = () => {
    const newErrors = {};
    if (!formData.ownerName.trim()) newErrors.ownerName = 'Owner name is required';
    
    if (!formData.email.trim()) {
      newErrors.email = 'Email is required';
    } else if (!/\S+@\S+\.\S+/.test(formData.email)) {
      newErrors.email = 'Invalid email address';
    }

    if (!formData.phone.trim()) {
      newErrors.phone = 'Phone number is required';
    } else if (!/^\+?[1-9]\d{1,14}$/.test(formData.phone.replace(/[\s-()]/g, ''))) {
      newErrors.phone = 'Invalid phone number format (e.g. +919876543210)';
    }

    if (!formData.pgBrandName.trim()) newErrors.pgBrandName = 'PG brand name is required';
    
    if (!formData.domainName.trim()) {
      newErrors.domainName = 'Subdomain prefix is required';
    } else if (formData.domainName.length < 3) {
      newErrors.domainName = 'Subdomain must be at least 3 characters';
    } else if (!/^[a-z0-9-]+$/.test(formData.domainName)) {
      newErrors.domainName = 'Subdomain can only contain lowercase letters, numbers, and hyphens';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const loadRazorpayScript = () => {
    return new Promise((resolve) => {
      const script = document.createElement('script');
      script.src = 'https://checkout.razorpay.com/v1/checkout.js';
      script.onload = () => resolve(true);
      script.onerror = () => resolve(false);
      document.body.appendChild(script);
    });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrorMessage('');
    
    if (!validate()) {
      setStep(1);
      return;
    }

    setStep(3);
    setLoading(true);
    try {
      const initiateResponse = await axios.post('/api/public/checkout/initiate-order', formData);
      const { orderId, amount, currency, keyId, clientEmail, clientPhone, pgBrandName } = initiateResponse.data;

      const scriptLoaded = await loadRazorpayScript();
      if (!scriptLoaded) {
        throw new Error('Razorpay SDK failed to load. Please verify your connection.');
      }

      const options = {
        key: keyId,
        amount: amount * 100,
        currency: currency,
        name: pgBrandName || 'PG CRM Control Plane',
        description: 'Single-Tenant Setup & 1-Year AMC Fee',
        order_id: orderId,
        prefill: {
          name: formData.ownerName,
          email: clientEmail,
          contact: clientPhone,
        },
        notes: {
          subdomain: formData.domainName,
          brandName: formData.pgBrandName,
        },
        theme: {
          color: '#000000', // Set to Dinergy black for sandbox popup
        },
        handler: async function (response) {
          try {
            setLoading(true);
            const webhookPayload = {
              event: 'order.paid',
              payload: {
                payment: {
                  entity: {
                    order_id: response.razorpay_order_id,
                    id: response.razorpay_payment_id,
                  },
                },
              },
            };

            const signatureHeader = response.razorpay_signature || 'sandbox_mock_signature';

            await axios.post('/api/public/checkout/webhook/reconcile', webhookPayload, {
              headers: {
                'X-Razorpay-Signature': signatureHeader,
              },
            });

            navigate('/success', {
              state: {
                orderId: response.razorpay_order_id,
                paymentId: response.razorpay_payment_id,
                domainName: formData.domainName,
                brandName: formData.pgBrandName,
              },
            });
          } catch (err) {
            console.error('Checkout verification failed:', err);
            setErrorMessage('Payment succeeded, but automated VM configuration reconciliation failed. Please contact support.');
          } finally {
            setLoading(false);
          }
        },
        modal: {
          ondismiss: function () {
            setLoading(false);
            setStep(2);
            setErrorMessage('Payment checkout cancelled.');
          },
        },
      };

      const rzpInstance = new window.Razorpay(options);
      rzpInstance.open();

    } catch (err) {
      console.error('Checkout request error:', err);
      if (err.response && err.response.status === 409) {
        setErrors((prev) => ({ ...prev, domainName: 'Subdomain is already registered.' }));
        setStep(1);
      } else {
        setErrorMessage(err.response?.data?.message || err.message || 'An unexpected error occurred. Please try again.');
        setStep(2);
      }
      setLoading(false);
    }
  };

  return (
    <div className="bg-[#fafaf9] text-[#141414] min-h-screen font-sans py-12 px-6 flex items-center justify-center relative overflow-hidden">
      {/* Subtle grid background */}
      <div className="absolute inset-0 bg-[linear-gradient(to_right,#80808005_1px,transparent_1px),linear-gradient(to_bottom,#80808005_1px,transparent_1px)] bg-[size:16px_28px] pointer-events-none" />

      <div className="max-w-4xl w-full relative z-10 space-y-6">
        <Link
          to="/"
          className="group inline-flex items-center space-x-2 text-neutral-400 hover:text-black transition-colors font-bold uppercase tracking-wider text-xs"
        >
          <ArrowLeft className="w-4 h-4 group-hover:-translate-x-1 transition-transform" />
          <span>Back to Home</span>
        </Link>

        <div className="bg-white border-2 border-black rounded-3xl p-8 md:p-10 shadow-sm relative overflow-hidden">
          {/* Header */}
          <div className="mb-8 pb-6 border-b-2 border-neutral-100 flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div>
              <h1 className="text-3xl font-black uppercase tracking-tight text-black mb-1">
                Deploy PG Instance
              </h1>
              <p className="text-neutral-550 text-xs font-semibold uppercase tracking-wider">
                Spin up your whitelabeled instance of PG CRM.
              </p>
            </div>

            {/* Step Wizard Progress Bar */}
            <div className="flex items-center space-x-2 md:space-x-3 text-[10px] font-black uppercase tracking-wider shrink-0 bg-neutral-50 border border-neutral-200 p-2 rounded-2xl">
              <span className={`px-2.5 py-1 rounded-lg transition-all ${step === 1 ? 'bg-black text-white' : 'text-neutral-400'}`}>
                1. Basic Info
              </span>
              <span className="text-neutral-300">/</span>
              <span className={`px-2.5 py-1 rounded-lg transition-all ${step === 2 ? 'bg-black text-white' : 'text-neutral-400'}`}>
                2. Config
              </span>
              <span className="text-neutral-300">/</span>
              <span className={`px-2.5 py-1 rounded-lg transition-all ${step === 3 ? 'bg-black text-white' : 'text-neutral-400'}`}>
                3. Payment
              </span>
            </div>
          </div>

          {errorMessage && (
            <div className="mb-6 p-4 rounded-2xl border border-red-200 bg-red-50 text-red-750 text-xs font-bold flex items-start space-x-2.5 shadow-sm">
              <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
              <span>{errorMessage}</span>
            </div>
          )}

          {/* Form */}
          <form onSubmit={handleSubmit} className="space-y-8">
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
              
              {/* Left Column: Basic Info & Subdomain */}
              <div className={`space-y-5 transition-opacity ${step === 3 ? 'opacity-50 pointer-events-none' : 'opacity-100'}`}>
                <div className="flex items-center space-x-2 border-b-2 border-black pb-2 mb-4">
                  <span className="w-1.5 h-4 bg-black rounded" />
                  <h3 className="text-xs font-black uppercase tracking-widest text-black">Basic Registration</h3>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-black font-black uppercase tracking-widest text-[9px] mb-1.5 pl-1">Owner Name</label>
                    <input
                      type="text"
                      name="ownerName"
                      value={formData.ownerName}
                      onChange={handleChange}
                      onFocus={() => setStep(1)}
                      className={`w-full bg-neutral-50 border-2 ${errors.ownerName ? 'border-red-500' : 'border-neutral-200 hover:border-black focus:border-black'} rounded-full px-5 py-3.5 text-black font-bold placeholder-neutral-400 focus:outline-none transition-colors text-xs`}
                      placeholder="Rahul Sharma"
                    />
                    {errors.ownerName && <p className="text-red-600 text-[10px] font-bold uppercase mt-1 pl-1">{errors.ownerName}</p>}
                  </div>

                  <div>
                    <label className="block text-black font-black uppercase tracking-widest text-[9px] mb-1.5 pl-1">Email Address</label>
                    <input
                      type="email"
                      name="email"
                      value={formData.email}
                      onChange={handleChange}
                      onFocus={() => setStep(1)}
                      className={`w-full bg-neutral-50 border-2 ${errors.email ? 'border-red-500' : 'border-neutral-200 hover:border-black focus:border-black'} rounded-full px-5 py-3.5 text-black font-bold placeholder-neutral-400 focus:outline-none transition-colors text-xs`}
                      placeholder="rahul@example.com"
                    />
                    {errors.email && <p className="text-red-600 text-[10px] font-bold uppercase mt-1 pl-1">{errors.email}</p>}
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-black font-black uppercase tracking-widest text-[9px] mb-1.5 pl-1">Phone Number</label>
                    <input
                      type="tel"
                      name="phone"
                      value={formData.phone}
                      onChange={handleChange}
                      onFocus={() => setStep(1)}
                      className={`w-full bg-neutral-50 border-2 ${errors.phone ? 'border-red-500' : 'border-neutral-200 hover:border-black focus:border-black'} rounded-full px-5 py-3.5 text-black font-bold placeholder-neutral-400 focus:outline-none transition-colors text-xs`}
                      placeholder="+919876543210"
                    />
                    {errors.phone && <p className="text-red-600 text-[10px] font-bold uppercase mt-1 pl-1">{errors.phone}</p>}
                  </div>

                  <div>
                    <label className="block text-black font-black uppercase tracking-widest text-[9px] mb-1.5 pl-1">PG Brand Name</label>
                    <input
                      type="text"
                      name="pgBrandName"
                      value={formData.pgBrandName}
                      onChange={handleChange}
                      onFocus={() => setStep(1)}
                      className={`w-full bg-neutral-50 border-2 ${errors.pgBrandName ? 'border-red-500' : 'border-neutral-200 hover:border-black focus:border-black'} rounded-full px-5 py-3.5 text-black font-bold placeholder-neutral-400 focus:outline-none transition-colors text-xs`}
                      placeholder="Stanza Living"
                    />
                    {errors.pgBrandName && <p className="text-red-600 text-[10px] font-bold uppercase mt-1 pl-1">{errors.pgBrandName}</p>}
                  </div>
                </div>

                <div>
                  <label className="block text-black font-black uppercase tracking-widest text-[9px] mb-1.5 pl-1">Desired Subdomain</label>
                  <div className="flex rounded-full bg-neutral-50 border-2 border-neutral-200 hover:border-black focus-within:border-black transition-colors overflow-hidden">
                    <input
                      type="text"
                      name="domainName"
                      value={formData.domainName}
                      onChange={handleChange}
                      onFocus={() => setStep(1)}
                      className="bg-transparent flex-1 px-5 py-3.5 text-black font-bold placeholder-neutral-450 focus:outline-none text-xs"
                      placeholder="my-awesome-pg"
                    />
                    <span className="bg-neutral-100 border-l-2 border-neutral-200 text-neutral-405 px-6 py-3.5 font-black uppercase tracking-wider flex items-center text-[10px]">
                      .pgcrm.com
                    </span>
                  </div>
                  {errors.domainName ? (
                    <p className="text-red-650 text-[10px] font-bold uppercase mt-1 pl-1">{errors.domainName}</p>
                  ) : (
                    <p className="text-neutral-400 text-[9px] font-semibold uppercase tracking-wider mt-1 pl-1">
                      Hosting address for your isolated single-tenant database frontend.
                    </p>
                  )}
                </div>
              </div>

              {/* Right Column: Custom Configuration Panel */}
              <div className={`bg-neutral-50 border-2 border-black rounded-3xl p-6 space-y-5 transition-opacity ${step === 3 ? 'opacity-50 pointer-events-none' : 'opacity-100'}`}>
                <div className="flex items-center space-x-2 border-b-2 border-black pb-2 mb-2">
                  <span className="w-1.5 h-4 bg-black rounded" />
                  <h3 className="text-xs font-black uppercase tracking-widest text-black flex items-center gap-1.5">
                    <Settings2 className="w-4 h-4 text-black" />
                    <span>Configuration Panel</span>
                  </h3>
                </div>

                {/* WhatsApp Token */}
                <div>
                  <label className="block text-black font-black uppercase tracking-widest text-[9px] mb-1 pl-1">WhatsApp API Keys</label>
                  <input
                    type="password"
                    name="whatsappToken"
                    value={formData.whatsappToken}
                    onChange={handleChange}
                    onFocus={() => setStep(2)}
                    className="w-full bg-white border border-neutral-200 hover:border-black focus:border-black rounded-full px-4 py-2.5 text-black font-semibold placeholder-neutral-400 focus:outline-none transition-colors text-xs"
                    placeholder="whatsapp.api.token"
                  />
                  <p className="text-[8px] text-neutral-400 font-bold uppercase tracking-wide mt-1 pl-1">Optional. Injected into VM context variables.</p>
                </div>

                {/* Razorpay Keys */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  <div>
                    <label className="block text-black font-black uppercase tracking-widest text-[9px] mb-1 pl-1">Razorpay Key ID</label>
                    <input
                      type="text"
                      name="razorpayKeyId"
                      value={formData.razorpayKeyId}
                      onChange={handleChange}
                      onFocus={() => setStep(2)}
                      className="w-full bg-white border border-neutral-200 hover:border-black focus:border-black rounded-full px-4 py-2.5 text-black font-semibold placeholder-neutral-400 focus:outline-none transition-colors text-xs"
                      placeholder="rzp_test_..."
                    />
                  </div>
                  <div>
                    <label className="block text-black font-black uppercase tracking-widest text-[9px] mb-1 pl-1">Key Secret</label>
                    <input
                      type="password"
                      name="razorpayKeySecret"
                      value={formData.razorpayKeySecret}
                      onChange={handleChange}
                      onFocus={() => setStep(2)}
                      className="w-full bg-white border border-neutral-200 hover:border-black focus:border-black rounded-full px-4 py-2.5 text-black font-semibold placeholder-neutral-400 focus:outline-none transition-colors text-xs"
                      placeholder="••••••••••••••"
                    />
                  </div>
                </div>

                {/* Theme Color Picker */}
                <div className="space-y-2">
                  <label className="block text-black font-black uppercase tracking-widest text-[9px] pl-1 flex items-center gap-1">
                    <span>Theme Color Picker</span>
                  </label>
                  
                  {/* Presets layout */}
                  <div className="flex flex-wrap gap-2.5 pl-1">
                    {PRESET_COLORS.map((color) => {
                      const isSelected = formData.primaryColor === color.hex;
                      return (
                        <button
                          key={color.name}
                          type="button"
                          onClick={() => {
                            setStep(2);
                            selectColor(color.hex);
                          }}
                          className={`w-6 h-6 rounded-full cursor-pointer transition-transform relative ${isSelected ? 'scale-110 ring-2 ring-black ring-offset-1' : 'hover:scale-105'}`}
                          style={{ backgroundColor: color.hex }}
                          title={color.name}
                        />
                      );
                    })}
                  </div>
                  
                  {/* Selected indicator */}
                  <div className="flex items-center space-x-2 pl-1 pt-1">
                    <span className="text-[9px] font-bold text-neutral-400 uppercase tracking-widest">Active Brand Color:</span>
                    <span className="w-4 h-4 rounded border border-neutral-200 inline-block" style={{ backgroundColor: formData.primaryColor }} />
                    <span className="font-mono text-[10px] text-neutral-700 font-bold uppercase">{formData.primaryColor}</span>
                  </div>
                </div>
              </div>

            </div>

            {/* Pricing Details summary */}
            <div className="p-4 rounded-2xl bg-neutral-50 border border-neutral-200 space-y-2">
              <div className="flex justify-between items-center text-xs font-bold">
                <span className="text-neutral-500 uppercase tracking-wide">VM Instance Provisioning Setup Fee</span>
                <span className="text-neutral-900 font-black text-sm">₹15,000</span>
              </div>
              <div className="flex justify-between items-center text-xs font-bold border-t border-neutral-100 pt-2">
                <span className="text-neutral-500 uppercase tracking-wide flex items-center space-x-1">
                  <span>1-Year Maintenance Upkeep</span>
                  <Sparkles className="w-3 h-3 text-black" />
                </span>
                <span className="text-emerald-700 bg-emerald-50 border border-emerald-100 px-2 py-0.5 rounded text-[8px] font-black uppercase tracking-widest">Included</span>
              </div>
            </div>

            {/* Submit */}
            <button
              type="submit"
              disabled={loading}
              className="group relative inline-flex items-center justify-between border-2 border-black rounded-full px-8 py-4 bg-black text-white hover:bg-transparent hover:text-black transition-all duration-300 w-full"
            >
              {loading ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin mr-2" />
                  <span>Processing Checkout Gateway...</span>
                </>
              ) : (
                <>
                  <span className="text-sm font-bold tracking-wider uppercase mr-6">Pay & Provision Setup Fee (₹15,000)</span>
                  <div className="w-6 h-6 rounded-full bg-white group-hover:bg-black flex items-center justify-center transition-all duration-300 shrink-0">
                    <ArrowRight className="w-3.5 h-3.5 text-black group-hover:text-white" />
                  </div>
                </>
              )}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}

import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import axios from 'axios';
import { ArrowLeft, Loader2, Sparkles, CheckCircle2, AlertTriangle, ShieldCheck } from 'lucide-react';

export default function OnboardingForm() {
  const navigate = useNavigate();
  const [formData, setFormData] = useState({
    ownerName: '',
    email: '',
    phone: '',
    pgBrandName: '',
    domainName: '',
  });

  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  const handleChange = (e) => {
    const { name, value } = e.target;
    // For domainName, force lowercase and replace non-alphanumeric chars with hyphens
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
    
    if (!validate()) return;

    setLoading(true);
    try {
      // 1. Post registration details to backend
      const initiateResponse = await axios.post('/api/public/checkout/initiate-order', formData);
      const { orderId, amount, currency, keyId, clientEmail, clientPhone, pgBrandName } = initiateResponse.data;

      // 2. Load Razorpay CDN Script
      const scriptLoaded = await loadRazorpayScript();
      if (!scriptLoaded) {
        throw new Error('Razorpay SDK failed to load. Please verify your connection.');
      }

      // 3. Configure Razorpay Options
      const options = {
        key: keyId,
        amount: amount * 100, // Rupee to Paise
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
          color: '#4f46e5', // Indigo-600
        },
        handler: async function (response) {
          // Reconcile signature and complete transaction
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

            // Navigate to Success page on successful reconciliation
            navigate('/success', {
              state: {
                orderId: response.razorpay_order_id,
                paymentId: response.razorpay_payment_id,
                domainName: formData.domainName,
                brandName: formData.pgBrandName,
              },
            });
          } catch (err) {
            logError(err);
            setErrorMessage('Payment succeeded, but automated VM configuration reconciliation failed. Please contact support.');
          } finally {
            setLoading(false);
          }
        },
        modal: {
          ondismiss: function () {
            setLoading(false);
            setErrorMessage('Payment checkout cancelled.');
          },
        },
      };

      // 4. Open Razorpay payment portal
      const rzpInstance = new window.Razorpay(options);
      rzpInstance.open();

    } catch (err) {
      logError(err);
      if (err.response && err.response.status === 409) {
        setErrors((prev) => ({ ...prev, domainName: 'Subdomain is already registered.' }));
      } else {
        setErrorMessage(err.message || 'An unexpected error occurred. Please try again.');
      }
      setLoading(false);
    }
  };

  const logError = (err) => {
    console.error('Checkout error detail:', err);
  };

  return (
    <div className="bg-[#0b0f19] text-white min-h-screen font-sans selection:bg-indigo-500 py-12 px-6 flex items-center justify-center relative">
      {/* Glow background */}
      <div className="absolute top-10 left-10 w-96 h-96 bg-indigo-500/10 blur-[100px] rounded-full pointer-events-none" />
      <div className="absolute bottom-10 right-10 w-96 h-96 bg-purple-500/10 blur-[100px] rounded-full pointer-events-none" />

      <div className="max-w-2xl w-full relative z-10">
        <Link
          to="/"
          className="inline-flex items-center space-x-2 text-gray-400 hover:text-white transition-colors mb-8 text-sm group"
        >
          <ArrowLeft className="w-4 h-4 group-hover:-translate-x-0.5 transition-transform" />
          <span>Back to Home</span>
        </Link>

        <div className="bg-[#12182b] border border-gray-800 rounded-3xl p-8 md:p-10 shadow-2xl relative overflow-hidden">
          {/* Header */}
          <div className="mb-8 relative pb-6 border-b border-gray-800">
            <h1 className="text-3xl font-extrabold mb-2 bg-gradient-to-r from-white to-gray-300 bg-clip-text text-transparent">
              Deploy Your PG Instance
            </h1>
            <p className="text-gray-400 text-sm">
              Complete details below to spin up your whitelabeled instance of PG CRM.
            </p>
          </div>

          {errorMessage && (
            <div className="mb-6 p-4 rounded-xl border border-red-500/20 bg-red-500/5 text-red-400 text-sm flex items-start space-x-3">
              <AlertTriangle className="w-5 h-5 shrink-0 mt-0.5" />
              <span>{errorMessage}</span>
            </div>
          )}

          {/* Form */}
          <form onSubmit={handleSubmit} className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div>
                <label className="block text-gray-300 font-medium text-sm mb-2">Owner Name</label>
                <input
                  type="text"
                  name="ownerName"
                  value={formData.ownerName}
                  onChange={handleChange}
                  className={`w-full bg-[#080c16] border ${errors.ownerName ? 'border-red-500' : 'border-gray-800 hover:border-gray-700Focus'} focus:border-indigo-500 rounded-xl px-4 py-3 text-white placeholder-gray-600 transition-colors focus:outline-none`}
                  placeholder="E.g. Rahul Sharma"
                />
                {errors.ownerName && <p className="text-red-500 text-xs mt-1.5">{errors.ownerName}</p>}
              </div>

              <div>
                <label className="block text-gray-300 font-medium text-sm mb-2">Email Address</label>
                <input
                  type="email"
                  name="email"
                  value={formData.email}
                  onChange={handleChange}
                  className={`w-full bg-[#080c16] border ${errors.email ? 'border-red-500' : 'border-gray-800 hover:border-gray-700Focus'} focus:border-indigo-500 rounded-xl px-4 py-3 text-white placeholder-gray-600 transition-colors focus:outline-none`}
                  placeholder="rahul@example.com"
                />
                {errors.email && <p className="text-red-500 text-xs mt-1.5">{errors.email}</p>}
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div>
                <label className="block text-gray-300 font-medium text-sm mb-2">Phone Number</label>
                <input
                  type="tel"
                  name="phone"
                  value={formData.phone}
                  onChange={handleChange}
                  className={`w-full bg-[#080c16] border ${errors.phone ? 'border-red-500' : 'border-gray-800 hover:border-gray-700Focus'} focus:border-indigo-500 rounded-xl px-4 py-3 text-white placeholder-gray-600 transition-colors focus:outline-none`}
                  placeholder="E.g. +919876543210"
                />
                {errors.phone && <p className="text-red-500 text-xs mt-1.5">{errors.phone}</p>}
              </div>

              <div>
                <label className="block text-gray-300 font-medium text-sm mb-2">PG Brand Name</label>
                <input
                  type="text"
                  name="pgBrandName"
                  value={formData.pgBrandName}
                  onChange={handleChange}
                  className={`w-full bg-[#080c16] border ${errors.pgBrandName ? 'border-red-500' : 'border-gray-800 hover:border-gray-700Focus'} focus:border-indigo-500 rounded-xl px-4 py-3 text-white placeholder-gray-600 transition-colors focus:outline-none`}
                  placeholder="E.g. Stanza Living, Zolo Stay"
                />
                {errors.pgBrandName && <p className="text-red-500 text-xs mt-1.5">{errors.pgBrandName}</p>}
              </div>
            </div>

            <div>
              <label className="block text-gray-300 font-medium text-sm mb-2">Desired Subdomain</label>
              <div className="flex rounded-xl bg-[#080c16] border border-gray-800 hover:border-gray-700 focus-within:border-indigo-500 transition-colors overflow-hidden">
                <input
                  type="text"
                  name="domainName"
                  value={formData.domainName}
                  onChange={handleChange}
                  className="bg-transparent flex-1 px-4 py-3 text-white placeholder-gray-600 transition-colors focus:outline-none"
                  placeholder="my-awesome-pg"
                />
                <span className="bg-[#181f37] border-l border-gray-800 text-gray-400 px-4 py-3 font-medium flex items-center">
                  .pgcrm.com
                </span>
              </div>
              {errors.domainName ? (
                <p className="text-red-500 text-xs mt-1.5">{errors.domainName}</p>
              ) : (
                <p className="text-gray-500 text-xs mt-1.5">
                  Subdomain will host your single-tenant React frontend.
                </p>
              )}
            </div>

            {/* Pricing details indicator */}
            <div className="p-4 rounded-2xl bg-indigo-950/20 border border-indigo-500/10 space-y-3">
              <div className="flex justify-between items-center text-sm">
                <span className="text-gray-400">VM Instance Setup Fee</span>
                <span className="text-white font-semibold">₹15,000</span>
              </div>
              <div className="flex justify-between items-center text-sm border-t border-indigo-950/50 pt-2">
                <span className="text-gray-400 flex items-center space-x-1">
                  <span>1-Year Maintenance Contract</span>
                  <Sparkles className="w-3.5 h-3.5 text-indigo-400" />
                </span>
                <span className="text-emerald-400 font-bold uppercase text-xs">Included</span>
              </div>
            </div>

            {/* Submit */}
            <button
              type="submit"
              disabled={loading}
              className="w-full py-4 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white font-semibold text-lg transition-all duration-200 shadow-xl shadow-indigo-600/35 hover:shadow-indigo-600/50 flex items-center justify-center space-x-2 disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:from-indigo-600 disabled:hover:to-purple-600"
            >
              {loading ? (
                <>
                  <Loader2 className="w-5 h-5 animate-spin" />
                  <span>Processing Checkout...</span>
                </>
              ) : (
                <>
                  <ShieldCheck className="w-5 h-5" />
                  <span>Pay with Razorpay</span>
                </>
              )}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}

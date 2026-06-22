import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { Search, Globe, User, Mail, AlertTriangle, CheckCircle, Loader2, Sparkles, ArrowRight } from 'lucide-react';
import AmcStatusCard from '../components/AmcStatusCard';

export default function BillingDashboard() {
  const [domainSearch, setDomainSearch] = useState('');
  const [amcData, setAmcData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [checkoutLoading, setCheckoutLoading] = useState(false);
  const [searchError, setSearchError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [errorMessage, setErrorMessage] = useState('');

  // Check URL query parameters for 'domain' on load
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const domainParam = params.get('domain');
    if (domainParam) {
      setDomainSearch(domainParam);
      fetchAmcStatus(domainParam);
    }
  }, []);

  const fetchAmcStatus = async (domain) => {
    const targetDomain = domain || domainSearch;
    if (!targetDomain.trim()) return;

    setLoading(true);
    setSearchError('');
    setSuccessMessage('');
    setErrorMessage('');
    try {
      const response = await axios.get(`/api/billing/status?domainName=${targetDomain.trim()}`);
      setAmcData(response.data);
    } catch (err) {
      setAmcData(null);
      setSearchError('Subdomain instance not found. Please verify the prefix.');
    } finally {
      setLoading(false);
    }
  };

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    fetchAmcStatus();
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

  const handleRenewal = async () => {
    if (!amcData) return;
    
    setCheckoutLoading(true);
    setErrorMessage('');
    setSuccessMessage('');

    try {
      // 1. Request renewal order from backend
      const initiateResponse = await axios.post('/api/billing/renew-amc', {
        tenantInstanceId: amcData.tenantInstanceId,
      });

      const { orderId, amount, currency, keyId, clientEmail, clientPhone, pgBrandName } = initiateResponse.data;

      // 2. Load Razorpay CDN script
      const scriptLoaded = await loadRazorpayScript();
      if (!scriptLoaded) {
        throw new Error('Razorpay SDK failed to load. Please verify your connection.');
      }

      // 3. Configure Razorpay modal options
      const options = {
        key: keyId,
        amount: amount * 100, // INR to Paise
        currency: currency,
        name: pgBrandName || 'PG CRM Control Plane',
        description: 'Annual Maintenance Contract (AMC) Renewal Fee',
        order_id: orderId,
        prefill: {
          name: amcData.ownerName,
          email: clientEmail,
          contact: clientPhone,
        },
        notes: {
          subdomain: amcData.domainName,
          tenantInstanceId: amcData.tenantInstanceId,
        },
        theme: {
          color: '#000000', // Black branding
        },
        handler: async function (response) {
          // Reconcile signature and complete renewal
          try {
            setCheckoutLoading(true);
            const webhookPayload = {
              event: 'order.paid',
              payload: {
                payment: {
                  entity: {
                    order_id: response.razorpay_order_id,
                    id: response.razorpay_payment_id,
                    amount: amount * 100,
                  },
                },
              },
            };

            const signatureHeader = response.razorpay_signature || 'sandbox_mock_signature';

            await axios.post('/api/billing/verify-amc', webhookPayload, {
              headers: {
                'X-Razorpay-Signature': signatureHeader,
              },
            });

            setSuccessMessage(`Renewal successful! Your AMC has been extended by 1 year.`);
            
            // Refresh data to show updated expiry date and status
            fetchAmcStatus(amcData.domainName);
          } catch (err) {
            console.error('Reconciliation error:', err);
            setErrorMessage('Payment succeeded, but subscription database extension failed. Please contact billing support.');
          } finally {
            setCheckoutLoading(false);
          }
        },
        modal: {
          ondismiss: function () {
            setCheckoutLoading(false);
            setErrorMessage('Renewal checkout cancelled.');
          },
        },
      };

      // 4. Open Razorpay modal
      const rzpInstance = new window.Razorpay(options);
      rzpInstance.open();

    } catch (err) {
      console.error('Renewal initiation error:', err);
      setErrorMessage(err.response?.data?.message || err.message || 'Failed to initiate AMC renewal.');
      setCheckoutLoading(false);
    }
  };

  return (
    <div className="bg-[#fafaf9] text-[#141414] min-h-screen font-sans py-16 px-6 flex items-center justify-center relative overflow-hidden">
      {/* Decorative light grid background */}
      <div className="absolute inset-0 bg-[linear-gradient(to_right,#80808008_1px,transparent_1px),linear-gradient(to_bottom,#80808008_1px,transparent_1px)] bg-[size:16px_28px] pointer-events-none" />

      <div className="max-w-3xl w-full relative z-10 space-y-10">
        
        {/* Header */}
        <div className="text-center space-y-4">
          <span className="px-3.5 py-1.5 rounded-full text-xs font-black bg-black text-white inline-flex items-center space-x-1.5 uppercase tracking-widest">
            <Sparkles className="w-3.5 h-3.5" />
            <span>Billing Command Center</span>
          </span>
          <h1 className="text-4xl md:text-6xl font-black uppercase tracking-tight text-black">
            Client Billing Portal
          </h1>
          <p className="text-neutral-500 text-sm md:text-base max-w-md mx-auto font-medium">
            Manage your annual maintenance subscription contracts and process secure online renewals.
          </p>
        </div>

        {/* Subdomain search card */}
        <div className="bg-white border-2 border-black rounded-3xl p-6 md:p-8 shadow-sm">
          <form onSubmit={handleSearchSubmit} className="flex flex-col md:flex-row gap-4">
            <div className="relative flex-1 rounded-full bg-neutral-50 border-2 border-neutral-300 hover:border-black focus-within:border-black transition-colors overflow-hidden">
              <Search className="absolute left-4 top-1/2 -translate-y-1/2 text-neutral-400 w-5 h-5" />
              <input
                type="text"
                value={domainSearch}
                onChange={(e) => setDomainSearch(e.target.value)}
                className="w-full bg-transparent pl-12 pr-28 py-4 text-black font-bold placeholder-neutral-400 focus:outline-none text-base"
                placeholder="Enter subdomain prefix"
              />
              <span className="absolute right-5 top-1/2 -translate-y-1/2 text-neutral-400 text-sm font-black uppercase tracking-wider pointer-events-none">
                .pgcrm.com
              </span>
            </div>
            <button
              type="submit"
              disabled={loading}
              className="group relative inline-flex items-center justify-between border-2 border-black rounded-full px-8 py-4 bg-black text-white hover:bg-transparent hover:text-black transition-all duration-300 font-bold uppercase tracking-wider text-sm disabled:opacity-50"
            >
              {loading ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin mr-2" />
                  <span>Searching</span>
                </>
              ) : (
                <>
                  <span className="mr-3">Fetch Status</span>
                  <div className="w-5 h-5 rounded-full bg-white group-hover:bg-black flex items-center justify-center transition-all duration-300 shrink-0">
                    <ArrowRight className="w-3 h-3 text-black group-hover:text-white" />
                  </div>
                </>
              )}
            </button>
          </form>

          {searchError && (
            <p className="text-red-600 text-xs font-bold uppercase tracking-wide mt-3 pl-2 flex items-center space-x-1">
              <span className="w-1.5 h-1.5 rounded-full bg-red-600 inline-block mr-1" />
              <span>{searchError}</span>
            </p>
          )}
        </div>

        {/* Alerts and errors feedback */}
        {errorMessage && (
          <div className="p-5 rounded-3xl border-2 border-red-200 bg-red-50 text-red-700 text-sm font-bold flex items-start space-x-3 shadow-sm">
            <AlertTriangle className="w-5 h-5 shrink-0 mt-0.5" />
            <span>{errorMessage}</span>
          </div>
        )}

        {successMessage && (
          <div className="p-5 rounded-3xl border-2 border-emerald-250 bg-emerald-50 text-emerald-800 text-sm font-bold flex items-start space-x-3 shadow-sm">
            <CheckCircle className="w-5 h-5 shrink-0 mt-0.5" />
            <span>{successMessage}</span>
          </div>
        )}

        {/* Active AMC card and details */}
        {amcData && (
          <div className="space-y-6">
            <AmcStatusCard
              amcData={amcData}
              onRenew={handleRenewal}
              loading={checkoutLoading}
            />

            {/* Tenant details */}
            <div className="bg-white border-2 border-black rounded-3xl p-6 md:p-8 space-y-4 shadow-sm">
              <h3 className="text-black font-black uppercase tracking-widest text-xs border-b border-neutral-100 pb-3">
                Client Details
              </h3>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-6 text-sm font-bold">
                <div className="flex items-center space-x-3 text-neutral-600">
                  <div className="w-8 h-8 rounded-full border border-neutral-200 flex items-center justify-center shrink-0">
                    <User className="w-4 h-4 text-black" />
                  </div>
                  <span>{amcData.ownerName}</span>
                </div>
                <div className="flex items-center space-x-3 text-neutral-600">
                  <div className="w-8 h-8 rounded-full border border-neutral-200 flex items-center justify-center shrink-0">
                    <Mail className="w-4 h-4 text-black" />
                  </div>
                  <span className="truncate">{amcData.clientEmail}</span>
                </div>
                <div className="flex items-center space-x-3 text-neutral-600">
                  <div className="w-8 h-8 rounded-full border border-neutral-200 flex items-center justify-center shrink-0">
                    <Globe className="w-4 h-4 text-black" />
                  </div>
                  <span>{amcData.domainName}.pgcrm.com</span>
                </div>
              </div>
            </div>
          </div>
        )}

      </div>
    </div>
  );
}


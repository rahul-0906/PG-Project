import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { Search, Globe, User, Mail, AlertTriangle, CheckCircle, Loader2, Sparkles } from 'lucide-react';
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
          color: '#4f46e5', // Indigo-600
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
    <div className="bg-[#0b0f19] text-white min-h-screen font-sans py-12 px-6 flex items-center justify-center relative overflow-hidden">
      {/* Dynamic backdrop glows */}
      <div className="absolute top-1/4 left-1/4 w-[400px] h-[400px] bg-indigo-500/10 blur-[120px] rounded-full pointer-events-none" />
      <div className="absolute bottom-1/4 right-1/4 w-[400px] h-[400px] bg-purple-500/10 blur-[120px] rounded-full pointer-events-none" />

      <div className="max-w-3xl w-full relative z-10 space-y-8">
        
        {/* Header */}
        <div className="text-center space-y-3">
          <span className="px-3.5 py-1 rounded-full text-xs font-semibold bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 inline-flex items-center space-x-1.5 uppercase tracking-wide">
            <Sparkles className="w-3.5 h-3.5" />
            <span>Billing Command Center</span>
          </span>
          <h1 className="text-4xl font-extrabold bg-gradient-to-r from-white via-gray-100 to-gray-400 bg-clip-text text-transparent">
            Client Billing Portal
          </h1>
          <p className="text-gray-400 text-sm max-w-md mx-auto">
            Manage your annual maintenance subscription contracts and process secure online renewals.
          </p>
        </div>

        {/* Subdomain search card */}
        <div className="bg-[#12182b] border border-gray-800 rounded-3xl p-6 shadow-xl">
          <form onSubmit={handleSearchSubmit} className="flex flex-col md:flex-row gap-4">
            <div className="relative flex-1 rounded-xl bg-[#080c16] border border-gray-800 hover:border-gray-700 focus-within:border-indigo-500 transition-colors overflow-hidden">
              <Search className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-500 w-5 h-5" />
              <input
                type="text"
                value={domainSearch}
                onChange={(e) => setDomainSearch(e.target.value)}
                className="w-full bg-transparent pl-12 pr-4 py-4 text-white placeholder-gray-600 transition-colors focus:outline-none"
                placeholder="Enter subdomain prefix (e.g. stanza-living)"
              />
              <span className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-500 text-sm font-semibold pointer-events-none">
                .pgcrm.com
              </span>
            </div>
            <button
              type="submit"
              disabled={loading}
              className="py-4 px-8 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-semibold transition-colors flex items-center justify-center space-x-2 disabled:opacity-50"
            >
              {loading ? (
                <>
                  <Loader2 className="w-5 h-5 animate-spin" />
                  <span>Searching...</span>
                </>
              ) : (
                <span>Fetch Status</span>
              )}
            </button>
          </form>

          {searchError && (
            <p className="text-red-500 text-xs mt-3 pl-1">{searchError}</p>
          )}
        </div>

        {/* Alerts and errors feedback */}
        {errorMessage && (
          <div className="p-4 rounded-2xl border border-red-500/20 bg-red-500/5 text-red-400 text-sm flex items-start space-x-3 shadow-lg">
            <AlertTriangle className="w-5 h-5 shrink-0 mt-0.5" />
            <span>{errorMessage}</span>
          </div>
        )}

        {successMessage && (
          <div className="p-4 rounded-2xl border border-emerald-500/20 bg-emerald-500/5 text-emerald-400 text-sm flex items-start space-x-3 shadow-lg">
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
            <div className="bg-[#12182b]/60 border border-gray-800/80 rounded-3xl p-6 space-y-4">
              <h3 className="text-gray-300 font-bold text-sm border-b border-gray-800 pb-2">Client Details</h3>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-sm">
                <div className="flex items-center space-x-3 text-gray-400">
                  <User className="w-4 h-4 text-indigo-400 shrink-0" />
                  <span>{amcData.ownerName}</span>
                </div>
                <div className="flex items-center space-x-3 text-gray-400">
                  <Mail className="w-4 h-4 text-indigo-400 shrink-0" />
                  <span className="truncate">{amcData.clientEmail}</span>
                </div>
                <div className="flex items-center space-x-3 text-gray-400">
                  <Globe className="w-4 h-4 text-indigo-400 shrink-0" />
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

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
      const initiateResponse = await axios.post('/api/billing/renew-amc', {
        tenantInstanceId: amcData.tenantInstanceId,
      });

      const { orderId, amount, currency, keyId, clientEmail, clientPhone, pgBrandName } = initiateResponse.data;

      const scriptLoaded = await loadRazorpayScript();
      if (!scriptLoaded) {
        throw new Error('Razorpay SDK failed to load. Please verify your connection.');
      }

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
          color: '#000000', // Dinergy black
        },
        handler: async function (response) {
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
          <span className="px-3.5 py-1.5 rounded-full text-xs font-black bg-black text-white inline-flex items-center space-x-1.5 uppercase tracking-widest shadow-sm">
            <Sparkles className="w-3.5 h-3.5" />
            <span>Billing Command Center</span>
          </span>
          <h1 className="text-4xl md:text-6xl font-black uppercase tracking-tight text-black">
            Client Billing Portal
          </h1>
          <p className="text-neutral-500 text-sm max-w-sm mx-auto font-semibold uppercase tracking-wider">
            Manage subscription contracts and process online renewals.
          </p>
        </div>

        {/* Subdomain search card */}
        <div className="bg-white border-2 border-black rounded-3xl p-6 md:p-8 shadow-sm">
          <form onSubmit={handleSearchSubmit} className="flex flex-col md:flex-row gap-4">
            <div className="relative flex-1 rounded-full bg-neutral-50 border-2 border-neutral-200 hover:border-black focus-within:border-black transition-colors overflow-hidden">
              <Search className="absolute left-4 top-1/2 -translate-y-1/2 text-neutral-400 w-4 h-4" />
              <input
                type="text"
                value={domainSearch}
                onChange={(e) => setDomainSearch(e.target.value)}
                className="w-full bg-transparent pl-11 pr-28 py-3.5 text-black font-bold placeholder-neutral-400 focus:outline-none text-sm"
                placeholder="Enter subdomain prefix"
              />
              <span className="absolute right-5 top-1/2 -translate-y-1/2 text-neutral-400 text-xs font-black uppercase tracking-widest pointer-events-none">
                .pgcrm.com
              </span>
            </div>
            
            <button
              type="submit"
              disabled={loading}
              className="group relative inline-flex items-center justify-between border-2 border-black rounded-full px-6 py-3 bg-black text-white hover:bg-transparent hover:text-black transition-all duration-300 w-full md:w-auto font-bold uppercase tracking-wider text-xs"
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
            <p className="text-red-650 text-[10px] font-bold uppercase tracking-widest mt-3 pl-2 flex items-center space-x-1">
              <span className="w-1 h-1 rounded-full bg-red-650 inline-block mr-1" />
              <span>{searchError}</span>
            </p>
          )}
        </div>

        {/* Alerts and errors feedback */}
        {errorMessage && (
          <div className="p-4 rounded-2xl border border-red-200 bg-red-50 text-red-750 text-xs font-bold flex items-start space-x-2.5 shadow-sm">
            <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
            <span>{errorMessage}</span>
          </div>
        )}

        {successMessage && (
          <div className="p-4 rounded-2xl border border-emerald-200 bg-emerald-50 text-emerald-800 text-xs font-bold flex items-start space-x-2.5 shadow-sm">
            <CheckCircle className="w-4 h-4 shrink-0 mt-0.5" />
            <span>{successMessage}</span>
          </div>
        )}

        {amcData && (
          <div className="space-y-6">
            <AmcStatusCard
              amcData={amcData}
              onRenew={handleRenewal}
              loading={checkoutLoading}
            />

            {/* Payment History card */}
            {amcData.paymentHistory && amcData.paymentHistory.length > 0 && (
              <div className="bg-white border-2 border-black rounded-3xl p-6 md:p-8 space-y-4 shadow-sm">
                <h3 className="text-black font-black uppercase tracking-widest text-xs border-b border-neutral-100 pb-3">
                  Payment History
                </h3>
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs font-semibold">
                    <thead>
                      <tr className="text-neutral-400 border-b-2 border-black uppercase tracking-widest text-[9px]">
                        <th className="py-2.5">Date</th>
                        <th className="py-2.5">Description</th>
                        <th className="py-2.5 text-right">Amount</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-neutral-100">
                      {amcData.paymentHistory.map((item, idx) => (
                        <tr key={idx} className="text-neutral-700 hover:bg-neutral-50/40 transition-colors">
                          <td className="py-3 font-mono">{item.date}</td>
                          <td className="py-3 uppercase tracking-wide text-[10px]">{item.description}</td>
                          <td className="py-3 text-right font-black text-neutral-900">{item.amount}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            {/* Tenant details */}
            <div className="bg-white border-2 border-black rounded-3xl p-6 md:p-8 space-y-4 shadow-sm">
              <h3 className="text-black font-black uppercase tracking-widest text-xs border-b border-neutral-100 pb-3">
                Client Details
              </h3>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-6 text-xs font-bold">
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

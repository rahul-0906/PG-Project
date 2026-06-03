import React, { useEffect, useState } from 'react';
import AppLayout from '../../components/AppLayout';
import { guestApi } from '../../api';
import api from '../../api';
import { 
  FileText, 
  CreditCard, 
  Download, 
  Check, 
  Loader2,
  X,
  IndianRupee,
  Clock
} from 'lucide-react';
import { useSystemConfig } from '../../context/SystemConfigContext';

const MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
const STATUS_COLORS = { 
  GENERATED: 'badge-info', 
  PAID: 'badge-success', 
  OVERDUE: 'badge-danger',
  PENDING_CASH_VERIFICATION: 'badge-warning'
};

const STATUS_LABELS = {
  GENERATED: 'Unpaid',
  PAID: 'Paid',
  OVERDUE: 'Overdue',
  PENDING_CASH_VERIFICATION: 'Pending Cash Verification'
};

function loadRazorpayScript() {
  return new Promise((resolve) => {
    if (window.Razorpay) { resolve(true); return; }
    const script = document.createElement('script');
    script.src = 'https://checkout.razorpay.com/v1/checkout.js';
    script.onload = () => resolve(true);
    script.onerror = () => resolve(false);
    document.body.appendChild(script);
  });
}

export default function GuestInvoices() {
  const { config } = useSystemConfig();
  const [invoices, setInvoices] = useState([]);
  const [paying, setPaying] = useState(null);
  const [allowedPaymentModes, setAllowedPaymentModes] = useState('BOTH');
  const [selectedInvoice, setSelectedInvoice] = useState(null);
  const [showPayModal, setShowPayModal] = useState(false);
  const [cashView, setCashView] = useState(false);

  const refresh = () => guestApi.getInvoices().then(r => setInvoices(r.data)).catch(() => {});
  
  useEffect(() => { 
    refresh(); 
    guestApi.getConfig()
      .then(res => {
        setAllowedPaymentModes(res.data.allowedPaymentModes ?? 'BOTH');
      })
      .catch(() => {});
  }, []);

  const getAmt = (inv, type) => inv.lineItems?.find(l => l.type === type)?.amount ?? 0;

  const handlePayClick = (invoice) => {
    setSelectedInvoice(invoice);
    setCashView(false);
    setShowPayModal(true);
  };

  const handleConfirmCashHandover = async () => {
    if (!selectedInvoice) return;
    setPaying(selectedInvoice.id);
    try {
      await guestApi.payCash(selectedInvoice.id);
      alert('✅ Cash handover confirmation sent. Manager notified.');
      setShowPayModal(false);
      setSelectedInvoice(null);
      refresh();
    } catch (err) {
      alert('Handover initiation failed: ' + (err.response?.data?.error || err.message));
    } finally {
      setPaying(null);
    }
  };

  const handlePayNow = async (invoice) => {
    setPaying(invoice.id);
    try {
      // Load Razorpay SDK
      await loadRazorpayScript();

      // Create order on backend
      const orderRes = await api.post(`/guest/invoices/${invoice.id}/initiate-payment`);
      const order = orderRes.data;

      if (order.mock) {
        // Dev mode — simulate payment success
        if (window.confirm(`[DEV MODE] Simulate payment of ₹${invoice.totalAmount} for ${MONTHS[invoice.month-1]} ${invoice.year}?`)) {
          await api.post(`/guest/invoices/${invoice.id}/verify-payment`, {
            razorpayOrderId: order.orderId,
            razorpayPaymentId: 'pay_mock_' + Date.now(),
            razorpaySignature: 'mock_signature'
          });
          alert('✅ Payment recorded successfully!');
          refresh();
        }
        return;
      }

      // Real Razorpay checkout
      const options = {
        key: order.keyId,
        amount: order.amount,
        currency: order.currency,
        name: config?.branding?.name || 'PG CRM',
        description: `Invoice — ${MONTHS[invoice.month-1]} ${invoice.year}`,
        order_id: order.orderId,
        prefill: { name: order.guestName, email: order.guestEmail },
        theme: { color: '#6366f1' },
        handler: async (response) => {
          try {
            await api.post(`/guest/invoices/${invoice.id}/verify-payment`, {
              razorpayOrderId: response.razorpay_order_id,
              razorpayPaymentId: response.razorpay_payment_id,
              razorpaySignature: response.razorpay_signature
            });
            alert('✅ Payment successful! Thank you.');
            refresh();
          } catch { alert('Payment verification failed. Please contact management.'); }
        },
        modal: { ondismiss: () => setPaying(null) }
      };
      const rzp = new window.Razorpay(options);
      rzp.open();
    } catch (err) {
      alert('Payment initiation failed: ' + (err.response?.data?.error || err.message));
    } finally {
      setPaying(null);
    }
  };

  return (
    <AppLayout>
      <div className="page-header">
        <div>
          <h1 className="page-title flex items-center gap-2">
            <FileText className="w-6 h-6 text-primary" />
            <span>Invoices</span>
          </h1>
          <p className="page-subtitle">{invoices.length} invoice{invoices.length !== 1 ? 's' : ''} found</p>
        </div>
      </div>

      {invoices.length === 0 ? (
        <div className="card shadow-sm border border-slate-200 bg-white rounded-xl p-10 text-center text-slate-500 text-sm flex flex-col items-center justify-center">
          <FileText className="w-10 h-10 text-slate-300 mb-3" />
          <h4 className="font-heading text-base font-semibold text-slate-900 mb-1">No Data Available</h4>
          <p className="text-xs text-slate-500 max-w-xs mx-auto">No invoices have been billed to your account yet.</p>
        </div>
      ) : (
        <div className="card shadow-sm border border-slate-200 bg-white rounded-xl overflow-hidden p-0">
          <div className="table-wrap">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200">
                  <th className="py-3 px-4 text-xs font-medium uppercase tracking-wider text-slate-500">Period</th>
                  <th className="py-3 px-4 text-xs font-medium uppercase tracking-wider text-slate-500">Rent</th>
                  <th className="py-3 px-4 text-xs font-medium uppercase tracking-wider text-slate-500">EB</th>
                  <th className="py-3 px-4 text-xs font-medium uppercase tracking-wider text-slate-500">Food</th>
                  <th className="py-3 px-4 text-xs font-medium uppercase tracking-wider text-slate-500">Laundry</th>
                  <th className="py-3 px-4 text-xs font-medium uppercase tracking-wider text-slate-500">Total</th>
                  <th className="py-3 px-4 text-xs font-medium uppercase tracking-wider text-slate-500">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200">
                {invoices.map((inv) => (
                  <tr key={inv.id} className="hover:bg-slate-50/50 transition-colors">
                    <td className="py-3.5 px-4">
                      <div className="font-semibold text-slate-900 text-sm">
                        {MONTHS[inv.month-1]} {inv.year}
                      </div>
                      <div className="flex items-center gap-2 mt-1">
                        <span className={`badge ${STATUS_COLORS[inv.status] || 'badge-info'}`}>
                          {STATUS_LABELS[inv.status] || inv.status}
                        </span>
                        {inv.dueDate && inv.status !== 'PAID' && inv.status !== 'PENDING_CASH_VERIFICATION' && (
                          <span className="text-xs font-medium text-slate-500">Due: {inv.dueDate}</span>
                        )}
                      </div>
                    </td>
                    <td className="py-3.5 px-4 text-slate-700 text-sm font-medium">
                      ₹{Number(getAmt(inv,'RENT')).toLocaleString('en-IN')}
                    </td>
                    <td className="py-3.5 px-4 text-slate-700 text-sm font-medium">
                      ₹{Number(getAmt(inv,'EB')).toLocaleString('en-IN')}
                    </td>
                    <td className="py-3.5 px-4 text-slate-700 text-sm font-medium">
                      ₹{Number(getAmt(inv,'FOOD')).toLocaleString('en-IN')}
                    </td>
                    <td className="py-3.5 px-4 text-slate-700 text-sm font-medium">
                      ₹{Number(getAmt(inv,'LAUNDRY')).toLocaleString('en-IN')}
                    </td>
                    <td className="py-3.5 px-4 text-slate-900 text-sm font-semibold">
                      ₹{Number(inv.totalAmount).toLocaleString('en-IN')}
                    </td>
                    <td className="py-3.5 px-4">
                      <div className="flex items-center gap-2">
                        {inv.status === 'PAID' ? (
                          <>
                            <span className="inline-flex items-center gap-1 text-xs font-semibold text-green-800 bg-green-100 border border-green-200 rounded-full px-2.5 py-0.5">
                              <Check className="w-3.5 h-3.5" />
                              <span>Paid</span>
                            </span>
                            <button 
                              className="btn btn-ghost py-1 px-3 text-xs flex items-center gap-1"
                              onClick={async () => {
                                try {
                                  const res = await guestApi.downloadInvoicePdf(inv.id);
                                  const url = window.URL.createObjectURL(new Blob([res.data]));
                                  const link = document.createElement('a');
                                  link.href = url;
                                  link.setAttribute('download', `Invoice-${inv.id.slice(0,8)}.pdf`);
                                  document.body.appendChild(link);
                                  link.click();
                                  link.remove();
                                } catch (err) { alert('Failed to download PDF'); }
                              }}
                            >
                              <Download className="w-3.5 h-3.5" />
                              <span>PDF</span>
                            </button>
                          </>
                        ) : inv.status === 'PENDING_CASH_VERIFICATION' ? (
                          <span className="inline-flex items-center gap-1 text-xs font-semibold text-amber-800 bg-amber-100 border border-amber-200 rounded-full px-2.5 py-0.5">
                            <Clock className="w-3.5 h-3.5 text-amber-600" />
                            <span>Pending Cash Approval</span>
                          </span>
                        ) : (
                          <button id={`btn-pay-${inv.id?.slice(0,8)}`}
                            onClick={() => handlePayClick(inv)}
                            disabled={paying === inv.id}
                            className="btn btn-primary py-1 px-3 text-xs flex items-center gap-1 shadow-sm"
                          >
                            {paying === inv.id ? (
                              <>
                                <Loader2 className="w-3 h-3 animate-spin" />
                                <span>Paying...</span>
                              </>
                            ) : (
                              <>
                                <CreditCard className="w-3 h-3" />
                                <span>Pay Now</span>
                              </>
                            )}
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {showPayModal && selectedInvoice && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm animate-fade-in p-4">
          <div className="bg-white rounded-2xl border border-slate-200 shadow-2xl w-full max-w-md p-6 animate-scale-in relative animate-fade-in">
            <button 
              onClick={() => { setShowPayModal(false); setSelectedInvoice(null); }}
              className="absolute right-4 top-4 p-1.5 rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition-colors"
            >
              <X className="w-4 h-4" />
            </button>
            
            <div className="mb-5 pr-6">
              <h3 className="font-heading text-lg font-bold text-slate-900">Choose Payment Method</h3>
              <p className="text-xs text-slate-500 mt-1">
                Invoice amount: <strong className="text-slate-950 font-semibold">₹{Number(selectedInvoice.totalAmount).toLocaleString('en-IN')}</strong> for {MONTHS[selectedInvoice.month-1]} {selectedInvoice.year}
              </p>
            </div>

            {allowedPaymentModes === 'CASH_ONLY' ? (
              <div className="space-y-4">
                <div className="p-3 bg-amber-50 border border-amber-200 rounded-xl text-amber-800 text-xs flex gap-2">
                  <span className="flex-shrink-0">⚠️</span>
                  <span>Online payment is disabled. Only Cash Handover is allowed for this building.</span>
                </div>
                <div className="p-4 rounded-xl border border-slate-100 bg-slate-50 text-slate-600 text-xs space-y-2.5">
                  <p className="font-semibold text-slate-800 text-sm">Cash Handover Instructions</p>
                  <p>1. Hand over the physical cash amount of <strong>₹{Number(selectedInvoice.totalAmount).toLocaleString('en-IN')}</strong> to your manager.</p>
                  <p>2. Once paid, click <strong>Confirm Cash Handover</strong> below to request manager verification.</p>
                  <p>3. Your invoice status will be updated to <em>Pending Cash Verification</em> until the manager confirms receipt.</p>
                </div>
                <button
                  onClick={handleConfirmCashHandover}
                  disabled={paying}
                  className="btn btn-primary w-full py-2.5 flex items-center justify-center gap-2 font-bold"
                >
                  {paying ? <Loader2 className="w-4 h-4 animate-spin" /> : <Check className="w-4 h-4" />}
                  Confirm Cash Handover
                </button>
              </div>
            ) : (
              <div className="space-y-4">
                <div className="grid grid-cols-1 gap-3">
                  {/* Pay Online Button */}
                  <button
                    onClick={() => {
                      setShowPayModal(false);
                      handlePayNow(selectedInvoice);
                    }}
                    className="flex items-center justify-between p-4 rounded-xl border border-slate-200 hover:border-primary hover:bg-indigo-50/10 transition-all text-left"
                  >
                    <div className="flex items-center gap-3">
                      <div className="p-2.5 rounded-lg bg-indigo-50 text-indigo-600">
                        <CreditCard className="w-5 h-5" />
                      </div>
                      <div>
                        <span className="font-semibold text-slate-800 text-sm block">Pay Online (UPI / Card)</span>
                        <span className="text-[11px] text-slate-400">Instant activation via Razorpay gateway</span>
                      </div>
                    </div>
                    <span className="text-slate-400">➔</span>
                  </button>

                  {/* Pay Cash Option */}
                  <button
                    onClick={() => {
                      setCashView(prev => !prev);
                    }}
                    className={`flex items-center justify-between p-4 rounded-xl border transition-all text-left ${
                      cashView ? 'border-emerald-500 bg-emerald-50/10' : 'border-slate-200 hover:border-emerald-500 hover:bg-emerald-50/10'
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      <div className="p-2.5 rounded-lg bg-emerald-50 text-emerald-600">
                        <IndianRupee className="w-5 h-5" />
                      </div>
                      <div>
                        <span className="font-semibold text-slate-800 text-sm block">Cash Handover</span>
                        <span className="text-[11px] text-slate-400">Hand over physical cash to building manager</span>
                      </div>
                    </div>
                    <span className="text-slate-400">➔</span>
                  </button>
                </div>

                {cashView && (
                  <div className="p-4 rounded-xl border border-emerald-100 bg-slate-50 text-slate-600 text-xs space-y-2.5 animate-fade-in">
                    <p className="font-semibold text-slate-800 text-sm">Cash Handover Instructions</p>
                    <p>1. Hand over the physical cash amount of <strong>₹{Number(selectedInvoice.totalAmount).toLocaleString('en-IN')}</strong> to your manager.</p>
                    <p>2. Once paid, click <strong>Confirm Cash Handover</strong> below to request manager verification.</p>
                    <p>3. Your invoice status will be updated to <em>Pending Cash Verification</em> until the manager confirms receipt.</p>
                    <button
                      onClick={handleConfirmCashHandover}
                      disabled={paying}
                      className="btn btn-emerald w-full py-2.5 mt-2 flex items-center justify-center gap-2 font-bold text-white bg-emerald-600 hover:bg-emerald-700 rounded-xl"
                    >
                      {paying ? <Loader2 className="w-4 h-4 animate-spin" /> : <Check className="w-4 h-4" />}
                      Confirm Cash Handover
                    </button>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      )}
    </AppLayout>
  );
}

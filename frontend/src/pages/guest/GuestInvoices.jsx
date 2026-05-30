import React, { useEffect, useState } from 'react';
import AppLayout from '../../components/AppLayout';
import { guestApi } from '../../api';
import api from '../../api';
import { 
  FileText, 
  CreditCard, 
  Download, 
  Check, 
  Loader2 
} from 'lucide-react';
import { useSystemConfig } from '../../context/SystemConfigContext';

const MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
const STATUS_COLORS = { GENERATED:'badge-info', PAID:'badge-success', OVERDUE:'badge-danger' };

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

  const refresh = () => guestApi.getInvoices().then(r => setInvoices(r.data)).catch(() => {});
  useEffect(() => { refresh(); }, []);

  const getAmt = (inv, type) => inv.lineItems?.find(l => l.type === type)?.amount ?? 0;

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
        <div className="card shadow-sm border border-slate-200 bg-white rounded-2xl p-10 text-center text-slate-400 text-sm flex flex-col items-center justify-center">
          <FileText className="w-10 h-10 text-slate-300 mb-3" />
          <h4 className="text-sm font-bold text-slate-800 mb-1">No Data Available</h4>
          <p className="text-xs text-slate-500 max-w-xs mx-auto">No invoices have been billed to your account yet.</p>
        </div>
      ) : (
        <div className="card shadow-sm border border-slate-200 bg-white rounded-2xl overflow-hidden p-0">
          <div className="table-wrap">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200">
                  <th className="py-3 px-4 text-xs font-bold text-slate-500 uppercase tracking-wider">Period</th>
                  <th className="py-3 px-4 text-xs font-bold text-slate-500 uppercase tracking-wider">Rent</th>
                  <th className="py-3 px-4 text-xs font-bold text-slate-500 uppercase tracking-wider">EB</th>
                  <th className="py-3 px-4 text-xs font-bold text-slate-500 uppercase tracking-wider">Food</th>
                  <th className="py-3 px-4 text-xs font-bold text-slate-500 uppercase tracking-wider">Laundry</th>
                  <th className="py-3 px-4 text-xs font-bold text-slate-500 uppercase tracking-wider">Total</th>
                  <th className="py-3 px-4 text-xs font-bold text-slate-500 uppercase tracking-wider">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {invoices.map((inv) => (
                  <tr key={inv.id} className="hover:bg-slate-50/50 transition-colors">
                    <td className="py-3.5 px-4">
                      <div className="font-bold text-slate-800 text-sm">
                        {MONTHS[inv.month-1]} {inv.year}
                      </div>
                      <div className="flex items-center gap-2 mt-1">
                        <span className={`badge ${STATUS_COLORS[inv.status] || 'badge-info'}`}>{inv.status}</span>
                        {inv.dueDate && inv.status !== 'PAID' && (
                          <span className="text-[10px] text-slate-400 font-medium">Due: {inv.dueDate}</span>
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
                        {inv.status !== 'PAID' ? (
                          <button id={`btn-pay-${inv.id?.slice(0,8)}`}
                            onClick={() => handlePayNow(inv)}
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
                        ) : (
                          <span className="inline-flex items-center gap-1 text-xs font-bold text-emerald-600 bg-emerald-50 border border-emerald-100 rounded-full px-2.5 py-0.5">
                            <Check className="w-3.5 h-3.5" />
                            <span>Paid</span>
                          </span>
                        )}
                        <button 
                          className="btn btn-secondary py-1 px-3 text-xs flex items-center gap-1"
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
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </AppLayout>
  );
}

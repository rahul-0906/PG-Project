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

      <div className="card" style={{ padding: 0, overflow:'hidden' }}>
        {/* Header */}
        <div style={{ display:'grid', gridTemplateColumns:'1.5fr 1fr 1fr 1fr 1fr 1fr 1.2fr',
          padding:'0.75rem 1rem', background:'var(--bg-main)', borderBottom:'1px solid var(--border)',
          fontSize:'0.75rem', fontWeight:700, color:'var(--text-muted)', textTransform:'uppercase' }}>
          <span>Period</span><span>Rent</span><span>EB</span><span>Food</span><span>Laundry</span><span>Total</span><span>Action</span>
        </div>

        {invoices.length === 0 && (
          <div style={{ padding:'3rem', textAlign:'center', color:'var(--text-muted)' }} className="flex flex-col items-center justify-center">
            <FileText className="w-12 h-12 text-slate-300 mb-2" />
            <span>No invoices yet. They are generated monthly by the billing scheduler.</span>
          </div>
        )}

        {invoices.map((inv, i) => (
          <div key={inv.id} style={{
            display:'grid', gridTemplateColumns:'1.5fr 1fr 1fr 1fr 1fr 1fr 1.2fr',
            padding:'1rem', alignItems:'center', borderBottom:'1px solid var(--border)',
            background: i % 2 === 0 ? 'transparent' : 'rgba(99,102,241,0.02)'
          }}>
            <div>
              <div style={{ fontWeight:700, color:'var(--text-primary)' }}>
                {MONTHS[inv.month-1]} {inv.year}
              </div>
              <div style={{ display:'flex', gap:'0.5rem', marginTop:'0.25rem', flexWrap:'wrap' }}>
                <span className={`badge ${STATUS_COLORS[inv.status] || 'badge-info'}`}>{inv.status}</span>
                {inv.dueDate && inv.status !== 'PAID' && (
                  <span style={{ fontSize:'0.7rem', color:'var(--text-muted)' }}>Due: {inv.dueDate}</span>
                )}
              </div>
            </div>
            <span style={{ color:'var(--text-secondary)' }}>₹{Number(getAmt(inv,'RENT')).toLocaleString('en-IN')}</span>
            <span style={{ color:'var(--text-secondary)' }}>₹{Number(getAmt(inv,'EB')).toLocaleString('en-IN')}</span>
            <span style={{ color:'var(--text-secondary)' }}>₹{Number(getAmt(inv,'FOOD')).toLocaleString('en-IN')}</span>
            <span style={{ color:'var(--text-secondary)' }}>₹{Number(getAmt(inv,'LAUNDRY')).toLocaleString('en-IN')}</span>
            <span style={{ fontWeight:800, color:'var(--accent)', fontSize:'1.05rem' }}>
              ₹{Number(inv.totalAmount).toLocaleString('en-IN')}
            </span>
            <div>
              <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', alignItems: 'center' }}>
                {inv.status !== 'PAID' ? (
                  <button id={`btn-pay-${inv.id?.slice(0,8)}`}
                    onClick={() => handlePayNow(inv)}
                    disabled={paying === inv.id}
                    className="btn btn-primary flex items-center gap-1"
                    style={{ fontSize:'0.78rem', padding:'0.4rem 0.85rem' }}>
                    {paying === inv.id ? (
                      <>
                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                        <span>Paying...</span>
                      </>
                    ) : (
                      <>
                        <CreditCard className="w-3.5 h-3.5" />
                        <span>Pay Now</span>
                      </>
                    )}
                  </button>
                ) : (
                  <span style={{ color:'#10b981' }} className="flex items-center gap-1 text-sm font-semibold py-1.5">
                    <Check className="w-4 h-4" />
                    <span>Paid</span>
                  </span>
                )}
                <button className="btn btn-secondary flex items-center gap-1" onClick={async () => {
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
                  style={{ fontSize:'0.78rem', padding:'0.4rem 0.85rem' }}>
                  <Download className="w-3.5 h-3.5" />
                  <span>PDF</span>
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>
    </AppLayout>
  );
}

import React, { useState, useEffect } from 'react';
import AppLayout from '../../components/AppLayout';
import { managerApi } from '../../api';
import {
  Receipt, Search, CheckCircle2, AlertCircle, Clock,
  Loader2, RefreshCcw, Send, Users, IndianRupee, Zap
} from 'lucide-react';

const MONTHS = [
  'January','February','March','April','May','June',
  'July','August','September','October','November','December'
];

function StatusBadge({ generated, status }) {
  if (status === 'PAID') {
    return (
      <span className="badge badge-success text-[10px]">
        <CheckCircle2 className="w-3 h-3" /> Paid
      </span>
    );
  }
  if (status === 'PENDING_CASH_VERIFICATION') {
    return (
      <span className="badge bg-amber-100 text-amber-800 border border-amber-200 text-[10px] animate-pulse">
        <Clock className="w-3 h-3" /> Pending Verification
      </span>
    );
  }
  if (generated) {
    return (
      <span className="badge badge-info text-[10px]">
        <CheckCircle2 className="w-3 h-3" /> Generated
      </span>
    );
  }
  return (
    <span className="badge badge-warning text-[10px]">
      <Clock className="w-3 h-3" /> Pending
    </span>
  );
}

function MoneyCell({ amount }) {
  const n = parseFloat(amount ?? 0);
  return <span className="font-semibold text-slate-800">₹{n.toFixed(0)}</span>;
}

export default function ManagerInvoiceGenerator() {
  const getDefaultBillingPeriod = () => {
    const d = new Date();
    d.setMonth(d.getMonth() - 1);
    return {
      month: d.getMonth() + 1, // 1-indexed (Jan is 1, Dec is 12)
      year: d.getFullYear()
    };
  };

  const initialPeriod = getDefaultBillingPeriod();
  const [month, setMonth] = useState(initialPeriod.month);
  const [year, setYear] = useState(initialPeriod.year);
  const [previews, setPreviews] = useState([]);
  const [loading, setLoading] = useState(false);
  const [generating, setGenerating] = useState('');
  const [generatingAll, setGeneratingAll] = useState(false);
  const [result, setResult] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [filter, setFilter] = useState('all'); // 'all' | 'pending' | 'generated'
  const [rowStatus, setRowStatus] = useState({}); // guestId -> 'sending' | 'done' | 'error'
  const [schedulerEnabled, setSchedulerEnabled] = useState(false);
  const [verifyingId, setVerifyingId] = useState('');

  const handleVerifyCash = async (invoiceId) => {
    setVerifyingId(invoiceId);
    try {
      await managerApi.verifyCash(invoiceId);
      alert('✅ Cash payment verified successfully!');
      loadPreviews();
    } catch (err) {
      alert('Failed to verify cash payment: ' + (err.response?.data?.error || err.response?.data?.message || err.message));
    } finally {
      setVerifyingId('');
    }
  };

  useEffect(() => {
    managerApi.getPricing()
      .then(res => {
        setSchedulerEnabled(res.data.billingSchedulerEnabled ?? false);
      })
      .catch(() => {});
  }, []);

  const loadPreviews = async () => {
    setLoading(true);
    setResult(null);
    setRowStatus({});
    try {
      const res = await managerApi.previewInvoices(month, year);
      setPreviews(res.data || []);
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to load previews');
    } finally {
      setLoading(false);
    }
  };

  const handleGenerateOne = async (guestId, guestName) => {
    setGenerating(guestId);
    setRowStatus(s => ({ ...s, [guestId]: 'sending' }));
    try {
      await managerApi.generateInvoice(guestId, month, year);
      setRowStatus(s => ({ ...s, [guestId]: 'done' }));
      // Refresh previews
      const res = await managerApi.previewInvoices(month, year);
      setPreviews(res.data || []);
    } catch (err) {
      setRowStatus(s => ({ ...s, [guestId]: 'error' }));
      alert(`Failed for ${guestName}: ${err.response?.data?.message || err.message}`);
    } finally {
      setGenerating('');
    }
  };

  const handleGenerateAll = async () => {
    setGeneratingAll(true);
    setResult(null);
    try {
      const res = await managerApi.generateAllInvoices(month, year);
      setResult(res.data);
      // Refresh previews
      const previewRes = await managerApi.previewInvoices(month, year);
      setPreviews(previewRes.data || []);
    } catch (err) {
      alert(err.response?.data?.message || 'Batch generation failed');
    } finally {
      setGeneratingAll(false);
    }
  };

  const filtered = previews.filter(p => {
    const matchSearch = !searchQuery || p.guestName?.toLowerCase().includes(searchQuery.toLowerCase())
      || p.roomNumber?.toLowerCase().includes(searchQuery.toLowerCase());
    const matchFilter = filter === 'all'
      || (filter === 'pending' && !p.alreadyGenerated)
      || (filter === 'generated' && p.alreadyGenerated);
    return matchSearch && matchFilter;
  });

  const pendingCount = previews.filter(p => !p.alreadyGenerated).length;
  const generatedCount = previews.filter(p => p.alreadyGenerated).length;
  const totalAmount = previews.reduce((sum, p) => sum + parseFloat(p.total || 0), 0);

  return (
    <AppLayout>
      <div className="page-header flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="page-title flex items-center gap-2">
            <Receipt className="w-6 h-6 text-primary" />
            <span>Invoice Generator</span>
          </h1>
          <p className="page-subtitle">Preview and generate monthly invoices for all guests</p>
        </div>
        <div className="flex-shrink-0">
          {schedulerEnabled ? (
            <span className="badge badge-success text-[11px] px-3 py-1.5 flex items-center gap-1.5">
              <span className="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse"></span>
              Auto-Billing: Active (1st of month)
            </span>
          ) : (
            <span className="badge badge-warning text-[11px] px-3 py-1.5 flex items-center gap-1.5">
              <span className="w-1.5 h-1.5 rounded-full bg-amber-500"></span>
              Auto-Billing: Paused (Manual Mode)
            </span>
          )}
        </div>
      </div>

      {/* Controls bar */}
      <div className="card mb-5" style={{ padding: '1.25rem' }}>
        <div className="flex flex-wrap items-end gap-4">
          <div className="form-group mb-0">
            <label className="form-label">Month</label>
            <select
              id="invoice-month"
              className="form-input py-1.5"
              value={month}
              onChange={e => setMonth(parseInt(e.target.value))}
            >
              {MONTHS.map((m, i) => (
                <option key={i+1} value={i+1}>{m}</option>
              ))}
            </select>
          </div>
          <div className="form-group mb-0">
            <label className="form-label">Year</label>
            <select
              id="invoice-year"
              className="form-input py-1.5"
              value={year}
              onChange={e => setYear(parseInt(e.target.value))}
            >
              {[2024, 2025, 2026, 2027].map(y => (
                <option key={y} value={y}>{y}</option>
              ))}
            </select>
          </div>
          <button
            id="btn-preview-invoices"
            className="btn btn-primary flex items-center gap-2"
            onClick={loadPreviews}
            disabled={loading}
          >
            {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <RefreshCcw className="w-4 h-4" />}
            {loading ? 'Loading...' : 'Preview Invoices'}
          </button>

          {previews.length > 0 && pendingCount > 0 && (
            <button
              id="btn-generate-all"
              className="btn btn-success flex items-center gap-2 font-semibold"
              onClick={handleGenerateAll}
              disabled={generatingAll}
            >
              {generatingAll
                ? <Loader2 className="w-4 h-4 animate-spin" />
                : <Zap className="w-4 h-4" />}
              {generatingAll ? 'Generating...' : `Generate All (${pendingCount} Pending)`}
            </button>
          )}
        </div>
      </div>

      {/* Result banner */}
      {result && (
        <div className="mb-4 p-4 rounded-xl bg-green-50 border border-green-200 flex flex-wrap gap-4 items-center">
          <CheckCircle2 className="w-5 h-5 text-green-600 flex-shrink-0" />
          <div className="flex gap-4 text-sm font-semibold">
            <span className="text-green-800">✅ {result.generated} generated</span>
            <span className="text-slate-500">⏭ {result.skipped} already existed</span>
            {result.failed > 0 && <span className="text-red-600">❌ {result.failed} failed</span>}
          </div>
          {result.errors?.length > 0 && (
            <div className="w-full text-xs text-red-600 mt-1 pl-7">
              {result.errors.map((e, i) => <div key={i}>{e}</div>)}
            </div>
          )}
        </div>
      )}

      {/* Summary stats */}
      {previews.length > 0 && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-5">
          {[
            { label: 'Total Guests', value: previews.length, icon: Users, color: 'text-blue-500' },
            { label: 'Pending', value: pendingCount, icon: Clock, color: 'text-amber-500' },
            { label: 'Generated', value: generatedCount, icon: CheckCircle2, color: 'text-emerald-500' },
            { label: 'Total Revenue', value: `₹${totalAmount.toLocaleString('en-IN', { maximumFractionDigits: 0 })}`, icon: IndianRupee, color: 'text-violet-500' },
          ].map(({ label, value, icon: Icon, color }) => (
            <div key={label} className="stat-card">
              <div className="flex items-center justify-between mb-1">
                <span className="stat-label">{label}</span>
                <Icon className={`w-4 h-4 ${color}`} />
              </div>
              <div className="stat-value">{value}</div>
            </div>
          ))}
        </div>
      )}

      {/* Table */}
      {previews.length > 0 && (
        <div className="card" style={{ padding: '1.25rem' }}>
          {/* Filter + search */}
          <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
            <div className="flex gap-1.5">
              {[
                { key: 'all', label: `All (${previews.length})` },
                { key: 'pending', label: `Pending (${pendingCount})` },
                { key: 'generated', label: `Generated (${generatedCount})` },
              ].map(({ key, label }) => (
                <button
                  key={key}
                  className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                    filter === key
                      ? 'bg-primary text-white shadow-sm'
                      : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                  }`}
                  onClick={() => setFilter(key)}
                >
                  {label}
                </button>
              ))}
            </div>
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
              <input
                className="form-input pl-9 py-1.5 text-sm w-48"
                placeholder="Search guest or room..."
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
              />
            </div>
          </div>

          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Guest</th>
                  <th>Room / Bed</th>
                  <th className="text-right">Rent</th>
                  <th className="text-right">EB</th>
                  <th className="text-right">Food</th>
                  <th className="text-right">Laundry</th>
                  <th className="text-right">Total</th>
                  <th className="text-center">Status</th>
                  <th className="text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map(p => {
                  const rs = rowStatus[p.guestId];
                  return (
                    <tr
                      key={p.guestId}
                      className={`transition-colors ${
                        rs === 'done' ? 'bg-green-50' :
                        rs === 'error' ? 'bg-red-50' : ''
                      }`}
                    >
                      <td className="font-semibold text-slate-900">
                        <div>{p.guestName}</div>
                        <div className="text-xs text-slate-500 font-normal mt-0.5">{p.floor}</div>
                      </td>
                      <td className="text-slate-700 font-normal">
                        <div>{p.roomNumber}</div>
                        <div className="text-xs text-slate-500 font-normal mt-0.5">{p.bedLabel}</div>
                      </td>
                      <td className="text-right"><MoneyCell amount={p.rent} /></td>
                      <td className="text-right"><MoneyCell amount={p.ebShare} /></td>
                      <td className="text-right"><MoneyCell amount={p.food} /></td>
                      <td className="text-right"><MoneyCell amount={p.laundry} /></td>
                      <td className="text-right font-bold text-slate-900">
                        ₹{parseFloat(p.total || 0).toLocaleString('en-IN', { maximumFractionDigits: 0 })}
                      </td>
                      <td className="text-center">
                        {rs === 'done'
                          ? <span className="text-xs text-green-600 font-semibold">✅ Sent</span>
                          : rs === 'error'
                          ? <span className="text-xs text-red-600 font-semibold">❌ Error</span>
                          : <StatusBadge generated={p.alreadyGenerated} status={p.status} />
                        }
                      </td>
                      <td>
                        <div className="flex justify-end gap-2">
                          {p.status === 'PENDING_CASH_VERIFICATION' && (
                            <button
                              className="btn btn-success text-xxs py-1 px-2.5 flex items-center gap-1 min-w-[100px] justify-center shadow-sm font-bold text-white bg-green-600 hover:bg-green-700"
                              onClick={() => handleVerifyCash(p.id)}
                              disabled={verifyingId === p.id}
                            >
                              {verifyingId === p.id ? (
                                <Loader2 className="w-3.5 h-3.5 animate-spin" />
                              ) : (
                                <CheckCircle2 className="w-3.5 h-3.5" />
                              )}
                              {verifyingId === p.id ? 'Verifying...' : 'Verify Cash'}
                            </button>
                          )}
                          {!p.alreadyGenerated && rs !== 'done' && (
                            <button
                              className="btn btn-primary text-xxs py-1 px-2.5 flex items-center gap-1 min-w-[120px] justify-center"
                              onClick={() => handleGenerateOne(p.guestId, p.guestName)}
                              disabled={generating === p.guestId}
                            >
                              {generating === p.guestId
                                ? <Loader2 className="w-3 h-3 animate-spin" />
                                : <Send className="w-3 h-3" />}
                              {generating === p.guestId ? 'Sending...' : 'Generate & Send'}
                            </button>
                          )}
                          {p.alreadyGenerated && p.status !== 'PENDING_CASH_VERIFICATION' && (
                            <button
                              className="btn btn-secondary text-xxs py-1 px-2.5 flex items-center gap-1 min-w-[75px] justify-center"
                              onClick={() => handleGenerateOne(p.guestId, p.guestName)}
                              disabled={generating === p.guestId}
                            >
                              <RefreshCcw className="w-3 h-3" /> Resend
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
            {filtered.length === 0 && (
              <div className="py-10 text-center text-slate-400 text-sm font-medium">No guests match the filter.</div>
            )}
          </div>
        </div>
      )}

      {previews.length === 0 && !loading && (
        <div className="card text-center py-12">
          <Receipt className="w-10 h-10 text-slate-300 mx-auto mb-3" />
          <p className="text-slate-500 text-sm">Select a month & year, then click <strong>Preview Invoices</strong> to load guest data.</p>
        </div>
      )}
    </AppLayout>
  );
}

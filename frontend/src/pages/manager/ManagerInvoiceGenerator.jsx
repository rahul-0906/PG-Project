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

function StatusBadge({ generated }) {
  if (generated) {
    return (
      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-emerald-100 text-emerald-700 border border-emerald-200">
        <CheckCircle2 className="w-3 h-3" /> Generated
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-amber-100 text-amber-700 border border-amber-200">
      <Clock className="w-3 h-3" /> Pending
    </span>
  );
}

function MoneyCell({ amount }) {
  const n = parseFloat(amount ?? 0);
  return <span className="font-medium text-slate-800">₹{n.toFixed(0)}</span>;
}

export default function ManagerInvoiceGenerator() {
  const today = new Date();
  const [month, setMonth] = useState(today.getMonth() + 1);
  const [year, setYear] = useState(today.getFullYear());
  const [previews, setPreviews] = useState([]);
  const [loading, setLoading] = useState(false);
  const [generating, setGenerating] = useState('');
  const [generatingAll, setGeneratingAll] = useState(false);
  const [result, setResult] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [filter, setFilter] = useState('all'); // 'all' | 'pending' | 'generated'
  const [rowStatus, setRowStatus] = useState({}); // guestId -> 'sending' | 'done' | 'error'
  const [schedulerEnabled, setSchedulerEnabled] = useState(false);

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
            <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
              Auto-Billing: Active (1st of month)
            </span>
          ) : (
            <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold bg-amber-50 text-amber-700 border border-amber-200">
              <span className="w-1.5 h-1.5 rounded-full bg-amber-500"></span>
              Auto-Billing: Paused (Manual Mode)
            </span>
          )}
        </div>
      </div>

      {/* Controls bar */}
      <div className="card mb-5">
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
              className="btn btn-primary flex items-center gap-2 bg-emerald-600 hover:bg-emerald-700"
              style={{ background: 'linear-gradient(135deg, #059669, #047857)' }}
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
        <div className="mb-4 p-4 rounded-2xl bg-emerald-50 border border-emerald-200 flex flex-wrap gap-4 items-center">
          <CheckCircle2 className="w-5 h-5 text-emerald-600 flex-shrink-0" />
          <div className="flex gap-4 text-sm">
            <span className="font-semibold text-emerald-800">✅ {result.generated} generated</span>
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
        <div className="grid grid-cols-4 gap-4 mb-5">
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
        <div className="card">
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
                  className={`px-3 py-1 rounded-lg text-xs font-medium transition-colors ${
                    filter === key
                      ? 'bg-indigo-600 text-white'
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

          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-100">
                  <th className="text-left py-3 px-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">Guest</th>
                  <th className="text-left py-3 px-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">Room / Bed</th>
                  <th className="text-right py-3 px-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">Rent</th>
                  <th className="text-right py-3 px-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">EB</th>
                  <th className="text-right py-3 px-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">Food</th>
                  <th className="text-right py-3 px-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">Laundry</th>
                  <th className="text-right py-3 px-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">Total</th>
                  <th className="text-center py-3 px-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">Status</th>
                  <th className="py-3 px-3" />
                </tr>
              </thead>
              <tbody>
                {filtered.map(p => {
                  const rs = rowStatus[p.guestId];
                  return (
                    <tr
                      key={p.guestId}
                      className={`border-b border-slate-50 transition-colors ${
                        rs === 'done' ? 'bg-emerald-50' :
                        rs === 'error' ? 'bg-red-50' : 'hover:bg-slate-50'
                      }`}
                    >
                      <td className="py-3 px-3">
                        <div className="font-semibold text-slate-800">{p.guestName}</div>
                        <div className="text-xs text-slate-400">{p.floor}</div>
                      </td>
                      <td className="py-3 px-3">
                        <div className="text-slate-700">{p.roomNumber}</div>
                        <div className="text-xs text-slate-400">{p.bedLabel}</div>
                      </td>
                      <td className="py-3 px-3 text-right"><MoneyCell amount={p.rent} /></td>
                      <td className="py-3 px-3 text-right"><MoneyCell amount={p.ebShare} /></td>
                      <td className="py-3 px-3 text-right"><MoneyCell amount={p.food} /></td>
                      <td className="py-3 px-3 text-right"><MoneyCell amount={p.laundry} /></td>
                      <td className="py-3 px-3 text-right">
                        <span className="font-bold text-slate-900">
                          ₹{parseFloat(p.total || 0).toLocaleString('en-IN', { maximumFractionDigits: 0 })}
                        </span>
                      </td>
                      <td className="py-3 px-3 text-center">
                        {rs === 'done'
                          ? <span className="text-xs text-emerald-600 font-semibold">✅ Sent</span>
                          : rs === 'error'
                          ? <span className="text-xs text-red-600 font-semibold">❌ Error</span>
                          : <StatusBadge generated={p.alreadyGenerated} />
                        }
                      </td>
                      <td className="py-3 px-3">
                        {!p.alreadyGenerated && rs !== 'done' && (
                          <button
                            className="flex items-center gap-1 px-2.5 py-1 rounded-lg text-xs font-medium bg-indigo-50 text-indigo-700 hover:bg-indigo-100 transition-colors disabled:opacity-50"
                            onClick={() => handleGenerateOne(p.guestId, p.guestName)}
                            disabled={generating === p.guestId}
                          >
                            {generating === p.guestId
                              ? <Loader2 className="w-3 h-3 animate-spin" />
                              : <Send className="w-3 h-3" />}
                            {generating === p.guestId ? 'Sending...' : 'Generate & Send'}
                          </button>
                        )}
                        {p.alreadyGenerated && (
                          <button
                            className="flex items-center gap-1 px-2.5 py-1 rounded-lg text-xs font-medium bg-slate-50 text-slate-500 hover:bg-slate-100 transition-colors"
                            onClick={() => handleGenerateOne(p.guestId, p.guestName)}
                            disabled={generating === p.guestId}
                          >
                            <RefreshCcw className="w-3 h-3" /> Resend
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
            {filtered.length === 0 && (
              <div className="py-10 text-center text-slate-400 text-sm">No guests match the filter.</div>
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

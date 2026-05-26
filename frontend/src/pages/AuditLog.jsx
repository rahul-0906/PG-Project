import React, { useState, useEffect } from 'react';
import api from '../api';
import AppLayout from '../components/AppLayout';
import { History, Download, Search } from 'lucide-react';

const ACTION_LABELS = {
  GUEST_CHECKIN: 'Check-In', GUEST_CHECKOUT_NOTICE: 'Notice Given',
  GUEST_CHECKOUT_CONFIRMED: 'Checked Out', INVOICE_GENERATED: 'Invoice Generated',
  PAYMENT_RECEIVED: 'Payment Received', PAYMENT_REMINDER_SENT: 'Reminder Sent',
  EB_BILL_RECORDED: 'EB Bill Recorded', MAINTENANCE_CREATED: 'Maintenance Created',
  MAINTENANCE_RESOLVED: 'Maintenance Resolved', BED_ADDED: 'Bed Added',
  BED_REMOVED: 'Bed Removed', TENANT_CONFIG_UPDATED: 'Config Updated',
  MANAGER_CREATED: 'Manager Created', TENANT_CREATED: 'Tenant Created',
  PASSWORD_CHANGED: 'Password Changed', USER_DEACTIVATED: 'User Deactivated',
  BUILDING_CREATED: 'Building Created', FLOOR_CREATED: 'Floor Created',
  BLOCK_CREATED: 'Block Created', ROOM_CREATED: 'Room Created'
};

const ACTION_COLORS = {
  GUEST_CHECKIN:'#10b981', GUEST_CHECKOUT_CONFIRMED:'#6366f1', PAYMENT_RECEIVED:'#22d3ee',
  EB_BILL_RECORDED:'#f59e0b', MAINTENANCE_CREATED:'#f97316', MAINTENANCE_RESOLVED:'#10b981',
  INVOICE_GENERATED:'#8b5cf6', PAYMENT_REMINDER_SENT:'#ef4444',
};

export default function AuditLog() {
  const [logs, setLogs] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [filters, setFilters] = useState({ action: '', from: '', to: '' });
  const [exporting, setExporting] = useState(false);

  const PAGE_SIZE = 50;

  const fetchLogs = async (p = 0) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: p, size: PAGE_SIZE });
      if (filters.action) params.append('action', filters.action);
      if (filters.from) params.append('from', filters.from);
      if (filters.to) params.append('to', filters.to);
      const res = await api.get(`/reports/audit?${params}`);
      setLogs(res.data.content || []);
      setTotal(res.data.totalElements || 0);
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  useEffect(() => { fetchLogs(page); }, [page]);

  const handleSearch = (e) => { e.preventDefault(); setPage(0); fetchLogs(0); };

  const handleExport = async () => {
    setExporting(true);
    try {
      const params = new URLSearchParams();
      if (filters.from) params.append('from', filters.from);
      if (filters.to) params.append('to', filters.to);
      const res = await api.get(`/reports/audit/export?${params}`, { responseType: 'blob' });
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const a = document.createElement('a'); a.href = url;
      a.download = `audit-log-${new Date().toISOString().slice(0,10)}.csv`;
      a.click(); window.URL.revokeObjectURL(url);
    } catch (err) { console.error(err); }
    finally { setExporting(false); }
  };

  return (
    <AppLayout>
      <div className="page-header">
        <div>
          <h1 className="page-title flex items-center gap-2">
            <History className="w-6 h-6 text-primary" />
            <span>Audit Log</span>
          </h1>
          <p className="page-subtitle">Complete trail of all business actions — {total} total entries</p>
        </div>
        <button id="btn-export-audit" onClick={handleExport} disabled={exporting}
          className="btn btn-primary flex items-center gap-2">
          <Download className="w-4 h-4" />
          <span>{exporting ? 'Exporting...' : 'Export CSV'}</span>
        </button>
      </div>

      {/* Filters */}
      <form onSubmit={handleSearch} className="card" style={{ marginBottom:'1.5rem' }}>
        <div style={{ display:'grid', gridTemplateColumns:'repeat(auto-fit,minmax(200px,1fr))', gap:'1rem', alignItems:'flex-end' }}>
          <div className="form-group" style={{ margin:0 }}>
            <label className="form-label">Action Type</label>
            <select value={filters.action} onChange={e => setFilters(f => ({ ...f, action: e.target.value }))}
              className="form-input">
              <option value="">All Actions</option>
              {Object.entries(ACTION_LABELS).map(([k, v]) => (
                <option key={k} value={k}>{v}</option>
              ))}
            </select>
          </div>
          <div className="form-group" style={{ margin:0 }}>
            <label className="form-label">From Date</label>
            <input type="date" className="form-input" value={filters.from}
              onChange={e => setFilters(f => ({ ...f, from: e.target.value }))} />
          </div>
          <div className="form-group" style={{ margin:0 }}>
            <label className="form-label">To Date</label>
            <input type="date" className="form-input" value={filters.to}
              onChange={e => setFilters(f => ({ ...f, to: e.target.value }))} />
          </div>
          <button type="submit" className="btn btn-primary flex items-center gap-2">
            <Search className="w-4 h-4" />
            <span>Search</span>
          </button>
        </div>
      </form>

      {/* Table */}
      <div className="card">
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                {['Time','Action','Description','Actor','Entity'].map(h => (
                  <th key={h}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={5} style={{ padding:'3rem', textAlign:'center', color:'var(--text-muted)' }}>
                  ⏳ Loading audit trail...
                </td></tr>
              ) : logs.length === 0 ? (
                <tr><td colSpan={5} style={{ padding:'3rem', textAlign:'center', color:'var(--text-muted)' }}>
                  No audit records found for the selected filters.
                </td></tr>
              ) : logs.map((log, i) => (
                <tr key={log.id || i}>
                  <td style={{ color:'var(--text-muted)', whiteSpace:'nowrap', fontSize:'0.8rem' }}>
                    {new Date(log.timestamp).toLocaleString('en-IN', { dateStyle:'short', timeStyle:'short' })}
                  </td>
                  <td>
                    <span style={{
                      display:'inline-block', padding:'2px 10px', borderRadius:'20px', fontSize:'0.75rem',
                      fontWeight:700, background:`${ACTION_COLORS[log.action] || '#6366f1'}22`,
                      color: ACTION_COLORS[log.action] || '#6366f1', whiteSpace:'nowrap'
                    }}>
                      {ACTION_LABELS[log.action] || log.action}
                    </span>
                  </td>
                  <td style={{ color:'var(--text-primary)', maxWidth:300 }}>
                    {log.description}
                  </td>
                  <td>
                    <span className="badge badge-accent">
                      {log.actorRole || 'SYSTEM'}
                    </span>
                  </td>
                  <td style={{ color:'var(--text-muted)', fontSize:'0.8rem' }}>
                    {log.entityType ? `${log.entityType}` : '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {total > PAGE_SIZE && (
          <div style={{ display:'flex', justifyContent:'center', gap:'0.5rem', marginTop:'1.5rem' }}>
            <button disabled={page === 0} onClick={() => setPage(p => p - 1)}
              className="btn btn-ghost" style={{ fontSize:'0.8rem', padding:'0.4rem 1rem' }}>← Prev</button>
            <span style={{ color:'var(--text-muted)', display:'flex', alignItems:'center', fontSize:'0.85rem' }}>
              Page {page + 1} of {Math.ceil(total / PAGE_SIZE)}
            </span>
            <button disabled={(page + 1) * PAGE_SIZE >= total} onClick={() => setPage(p => p + 1)}
              className="btn btn-ghost" style={{ fontSize:'0.8rem', padding:'0.4rem 1rem' }}>Next →</button>
          </div>
        )}
      </div>
    </AppLayout>
  );
}

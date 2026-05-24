import React, { useState, useEffect } from 'react';
import api from '../api';
import Sidebar from '../components/Sidebar';

const ACTION_LABELS = {
  GUEST_CHECKIN: '🛏️ Check-In', GUEST_CHECKOUT_NOTICE: '📋 Notice Given',
  GUEST_CHECKOUT_CONFIRMED: '🚪 Checked Out', INVOICE_GENERATED: '📄 Invoice Generated',
  PAYMENT_RECEIVED: '💳 Payment Received', PAYMENT_REMINDER_SENT: '⏰ Reminder Sent',
  EB_BILL_RECORDED: '⚡ EB Bill Recorded', MAINTENANCE_CREATED: '🔧 Maintenance Created',
  MAINTENANCE_RESOLVED: '✅ Maintenance Resolved', BED_ADDED: '🛏️ Bed Added',
  BED_REMOVED: '🗑️ Bed Removed', TENANT_CONFIG_UPDATED: '⚙️ Config Updated',
  MANAGER_CREATED: '👔 Manager Created', TENANT_CREATED: '🏢 Tenant Created',
  PASSWORD_CHANGED: '🔐 Password Changed', USER_DEACTIVATED: '🚫 User Deactivated',
  BUILDING_CREATED: '🏗️ Building Created', FLOOR_CREATED: '📐 Floor Created',
  BLOCK_CREATED: '🧱 Block Created', ROOM_CREATED: '🚪 Room Created'
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
      const res = await api.get(`/api/reports/audit?${params}`);
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
      const res = await api.get(`/api/reports/audit/export?${params}`, { responseType: 'blob' });
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const a = document.createElement('a'); a.href = url;
      a.download = `audit-log-${new Date().toISOString().slice(0,10)}.csv`;
      a.click(); window.URL.revokeObjectURL(url);
    } catch (err) { console.error(err); }
    finally { setExporting(false); }
  };

  return (
    <div className="layout">
      <Sidebar />
      <div className="main-content fade-in">
        <div style={{ display:'flex', alignItems:'center', justifyContent:'space-between', marginBottom:'1.5rem', flexWrap:'wrap', gap:'1rem' }}>
          <div>
            <h1 style={{ fontSize:'1.75rem', fontWeight:800, color:'var(--text-primary)', margin:0 }}>
              🗂️ Audit Log
            </h1>
          <p style={{ color:'var(--text-muted)', margin:'0.25rem 0 0', fontSize:'0.9rem' }}>
            Complete trail of all business actions — {total} total entries
          </p>
        </div>
        <button id="btn-export-audit" onClick={handleExport} disabled={exporting}
          className="btn btn-primary" style={{ fontSize:'0.85rem' }}>
          {exporting ? '⏳ Exporting...' : '⬇️ Export CSV'}
        </button>
      </div>

      {/* Filters */}
      <form onSubmit={handleSearch} className="card" style={{ marginBottom:'1.5rem' }}>
        <div style={{ display:'grid', gridTemplateColumns:'repeat(auto-fit,minmax(200px,1fr))', gap:'1rem', alignItems:'flex-end' }}>
          <div className="form-group" style={{ margin:0 }}>
            <label className="form-label">Action Type</label>
            <select value={filters.action} onChange={e => setFilters(f => ({ ...f, action: e.target.value }))}
              className="form-input" style={{ padding:'0.5rem' }}>
              <option value="">All Actions</option>
              {Object.entries(ACTION_LABELS).map(([k, v]) => (
                <option key={k} value={k}>{v}</option>
              ))}
            </select>
          </div>
          <div className="form-group" style={{ margin:0 }}>
            <label className="form-label">From Date</label>
            <input type="date" className="form-input" value={filters.from}
              onChange={e => setFilters(f => ({ ...f, from: e.target.value }))} style={{ padding:'0.5rem' }} />
          </div>
          <div className="form-group" style={{ margin:0 }}>
            <label className="form-label">To Date</label>
            <input type="date" className="form-input" value={filters.to}
              onChange={e => setFilters(f => ({ ...f, to: e.target.value }))} style={{ padding:'0.5rem' }} />
          </div>
          <button type="submit" className="btn btn-primary" style={{ alignSelf:'flex-end' }}>
            🔍 Search
          </button>
        </div>
      </form>

      {/* Table */}
      <div className="card" style={{ padding:0, overflow:'hidden' }}>
        <div style={{ overflowX:'auto' }}>
          <table style={{ width:'100%', borderCollapse:'collapse', fontSize:'0.85rem' }}>
            <thead>
              <tr style={{ background:'var(--bg-main)' }}>
                {['Time','Action','Description','Actor','Entity'].map(h => (
                  <th key={h} style={{ padding:'0.75rem 1rem', textAlign:'left', color:'var(--text-muted)',
                    fontWeight:700, fontSize:'0.75rem', textTransform:'uppercase', letterSpacing:'0.05em',
                    borderBottom:'1px solid var(--border)' }}>
                    {h}
                  </th>
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
                <tr key={log.id || i}
                  style={{ borderBottom:'1px solid var(--border)',
                    background: i % 2 === 0 ? 'transparent' : 'rgba(99,102,241,0.02)' }}>
                  <td style={{ padding:'0.75rem 1rem', color:'var(--text-muted)', whiteSpace:'nowrap', fontSize:'0.78rem' }}>
                    {new Date(log.timestamp).toLocaleString('en-IN', { dateStyle:'short', timeStyle:'short' })}
                  </td>
                  <td style={{ padding:'0.75rem 1rem' }}>
                    <span style={{
                      display:'inline-block', padding:'2px 10px', borderRadius:'20px', fontSize:'0.75rem',
                      fontWeight:700, background:`${ACTION_COLORS[log.action] || '#6366f1'}22`,
                      color: ACTION_COLORS[log.action] || '#6366f1', whiteSpace:'nowrap'
                    }}>
                      {ACTION_LABELS[log.action] || log.action}
                    </span>
                  </td>
                  <td style={{ padding:'0.75rem 1rem', color:'var(--text-primary)', maxWidth:300 }}>
                    {log.description}
                  </td>
                  <td style={{ padding:'0.75rem 1rem', color:'var(--text-secondary)', fontSize:'0.78rem' }}>
                    <span style={{ background:'var(--bg-main)', padding:'2px 8px', borderRadius:'4px', fontWeight:600 }}>
                      {log.actorRole || 'SYSTEM'}
                    </span>
                  </td>
                  <td style={{ padding:'0.75rem 1rem', color:'var(--text-muted)', fontSize:'0.75rem' }}>
                    {log.entityType ? `${log.entityType}` : '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {total > PAGE_SIZE && (
          <div style={{ display:'flex', justifyContent:'center', gap:'0.5rem', padding:'1rem', borderTop:'1px solid var(--border)' }}>
            <button disabled={page === 0} onClick={() => setPage(p => p - 1)}
              className="btn" style={{ fontSize:'0.8rem', padding:'0.4rem 1rem' }}>← Prev</button>
            <span style={{ color:'var(--text-muted)', display:'flex', alignItems:'center', fontSize:'0.85rem' }}>
              Page {page + 1} of {Math.ceil(total / PAGE_SIZE)}
            </span>
            <button disabled={(page + 1) * PAGE_SIZE >= total} onClick={() => setPage(p => p + 1)}
              className="btn" style={{ fontSize:'0.8rem', padding:'0.4rem 1rem' }}>Next →</button>
          </div>
        )}
      </div>
    </div>
  </div>
  );
}

import React, { useState, useEffect } from 'react';
import api from '../api';
import AppLayout from '../components/AppLayout';
import { 
  History, 
  Download, 
  Search, 
  Users, 
  Coins, 
  AlertTriangle, 
  Building2, 
  ChevronDown, 
  ChevronUp, 
  Link2 
} from 'lucide-react';

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

const getActionCategory = (action) => {
  if (action.startsWith('GUEST_')) return { label: 'Guest', color: '#10b981', icon: Users };
  if (action.startsWith('PAYMENT_') || action.startsWith('INVOICE_') || action === 'EB_BILL_RECORDED') return { label: 'Finance', color: '#6366f1', icon: Coins };
  if (action.startsWith('MAINTENANCE_')) return { label: 'Maintenance', color: '#f97316', icon: AlertTriangle };
  if (action.endsWith('_CREATED') || action.endsWith('_ADDED') || action.endsWith('_REMOVED')) return { label: 'Inventory', color: '#8b5cf6', icon: Building2 };
  return { label: 'System', color: '#64748b', icon: History };
};

export default function AuditLog() {
  const [logs, setLogs] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [filters, setFilters] = useState({ action: '', search: '', from: '', to: '' });
  const [exporting, setExporting] = useState(false);
  const [expandedRowId, setExpandedRowId] = useState(null);

  const PAGE_SIZE = 50;

  const fetchLogs = async (p = 0) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: p, size: PAGE_SIZE });
      if (filters.action) params.append('action', filters.action);
      if (filters.search) params.append('search', filters.search);
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
            <History className="w-6 h-6 text-primary" strokeWidth={1.5}/>
            <span>Audit Log</span>
          </h1>
          <p className="page-subtitle">Complete trail of all business actions — {total} total entries</p>
        </div>
        <button id="btn-export-audit" onClick={handleExport} disabled={exporting}
          className="btn btn-primary flex items-center gap-2">
          <Download className="w-4 h-4" strokeWidth={1.5}/>
          <span>{exporting ? 'Exporting...' : 'Export CSV'}</span>
        </button>
      </div>

      {/* Filters */}
      <form onSubmit={handleSearch} className="card" style={{ marginBottom:'1.5rem' }}>
        <div style={{ display:'grid', gridTemplateColumns:'repeat(auto-fit,minmax(180px,1fr))', gap:'1rem', alignItems:'flex-end' }}>
          <div className="form-group" style={{ margin:0 }}>
            <label className="form-label">Search Keywords</label>
            <input type="text" className="form-input" placeholder="Search actor, description, ID..."
              value={filters.search} onChange={e => setFilters(f => ({ ...f, search: e.target.value }))} />
          </div>
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
            <Search className="w-4 h-4" strokeWidth={1.5}/>
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
                <th style={{ width: '40px' }}></th>
                {['Time','Action','Description','Actor','Entity'].map(h => (
                  <th key={h}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-slate-500 text-sm">
                    ⏳ Loading audit trail...
                  </td>
                </tr>
              ) : logs.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-slate-500 text-sm">
                    No audit records found for the selected filters.
                  </td>
                </tr>
              ) : logs.map((log, i) => {
                const category = getActionCategory(log.action);
                const CatIcon = category.icon;
                const isExpanded = expandedRowId === log.id;
                return (
                  <React.Fragment key={log.id || i}>
                    <tr 
                      onClick={() => setExpandedRowId(isExpanded ? null : log.id)}
                      className="hover:bg-slate-50/80 cursor-pointer transition-colors"
                    >
                      <td className="text-center p-2">
                        {isExpanded ? (
                          <ChevronUp className="w-4 h-4 text-slate-400" />
                        ) : (
                          <ChevronDown className="w-4 h-4 text-slate-400" />
                        )}
                      </td>
                      <td className="text-slate-500 whitespace-nowrap text-xs">
                        {new Date(log.timestamp).toLocaleString('en-IN', { dateStyle:'short', timeStyle:'short' })}
                      </td>
                      <td>
                        <div className="flex items-center gap-1.5">
                          <span 
                            title={`Category: ${category.label}`}
                            style={{ 
                              display: 'inline-flex', 
                              alignItems: 'center', 
                              justifyContent: 'center',
                              padding: '4px',
                              borderRadius: '6px',
                              background: `${category.color}15`,
                              color: category.color
                            }}
                          >
                            <CatIcon className="w-3.5 h-3.5" />
                          </span>
                          <span style={{
                            display:'inline-block', padding:'2px 8px', borderRadius:'12px', fontSize:'0.75rem',
                            fontWeight:700, background:`${ACTION_COLORS[log.action] || '#6366f1'}22`,
                            color: ACTION_COLORS[log.action] || '#6366f1', whiteSpace:'nowrap'
                          }}>
                            {ACTION_LABELS[log.action] || log.action}
                          </span>
                        </div>
                      </td>
                      <td className="text-slate-700 text-sm max-w-[280px] truncate">
                        {log.description}
                      </td>
                      <td>
                        <div className="flex flex-col">
                          <span className="badge badge-accent mb-0.5 self-start">
                            {log.actorRole || 'SYSTEM'}
                          </span>
                          {(log.actorName || log.actorEmail) && (
                            <span className="text-[10px] text-slate-500 max-w-[150px] truncate" title={`${log.actorName} (${log.actorEmail})`}>
                              {log.actorName || log.actorEmail}
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="text-slate-500 text-xs">
                        {log.entityType ? (
                          <span className="flex items-center gap-1">
                            {log.entityType}
                            {log.entityId && (() => {
                              let path = '';
                              if (log.entityType === 'Guest') path = `/manager/guests?search=${log.entityId}`;
                              else if (log.entityType === 'Invoice') path = `/manager/invoices?search=${log.entityId}`;
                              else if (log.entityType === 'Maintenance') path = `/manager/maintenance?search=${log.entityId}`;
                              else if (['Building', 'Floor', 'Room', 'Block', 'Bed'].includes(log.entityType)) path = `/owner/buildings`;
                              else if (log.entityType === 'User' || log.entityType === 'Manager') path = `/owner/dashboard`;
                              
                              if (!path) return <span className="text-[10px] text-slate-400">({log.entityId.substring(0, 6)})</span>;
                              return (
                                <a 
                                  href={path} 
                                  onClick={e => e.stopPropagation()} 
                                  className="text-primary hover:underline flex items-center gap-0.5 text-[10px] font-semibold"
                                >
                                  <Link2 className="w-2.5 h-2.5" />
                                  <span>Link</span>
                                </a>
                              );
                            })()}
                          </span>
                        ) : '—'}
                      </td>
                    </tr>
                    {isExpanded && (
                      <tr className="bg-slate-50/50">
                        <td colSpan={6} className="p-4 border-t border-slate-100">
                          <div className="flex flex-col gap-3">
                            <div>
                              <h4 className="text-[10px] font-bold text-slate-700 uppercase tracking-wider mb-1">Full Description</h4>
                              <p className="text-slate-800 text-sm whitespace-normal leading-relaxed">{log.description}</p>
                            </div>
                            
                            {log.metadata && (() => {
                              try {
                                const parsed = JSON.parse(log.metadata);
                                return (
                                  <div>
                                    <h4 className="text-[10px] font-bold text-slate-700 uppercase tracking-wider mb-1">Activity Metadata</h4>
                                    <pre className="p-3 bg-slate-800 text-slate-100 rounded-lg text-xs font-mono overflow-x-auto max-w-full leading-normal">
                                      {JSON.stringify(parsed, null, 2)}
                                    </pre>
                                  </div>
                                );
                              } catch (e) {
                                return (
                                  <div>
                                    <h4 className="text-[10px] font-bold text-slate-700 uppercase tracking-wider mb-1">Raw Metadata</h4>
                                    <pre className="p-3 bg-slate-800 text-slate-100 rounded-lg text-xs font-mono overflow-x-auto max-w-full leading-normal">
                                      {log.metadata}
                                    </pre>
                                  </div>
                                );
                              }
                            })()}
                          </div>
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                );
              })}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {total > PAGE_SIZE && (
          <div style={{ display:'flex', justifyContent:'center', gap:'0.5rem', marginTop:'1.5rem' }}>
            <button disabled={page === 0} onClick={() => setPage(p => p - 1)}
              className="btn btn-ghost" style={{ fontSize:'0.8rem', padding:'0.4rem 1rem' }}>← Prev</button>
            <span className="text-slate-500 flex items-center text-sm">
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

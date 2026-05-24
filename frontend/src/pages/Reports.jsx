import React, { useState, useEffect } from 'react';
import api from '../api';

const COLORS = ['#6366f1','#8b5cf6','#22d3ee','#10b981','#f59e0b','#ef4444',
                 '#06b6d4','#84cc16','#f97316','#a78bfa','#34d399','#fb923c'];

function BarChart({ data, valueKey, labelKey, color = '#6366f1', prefix = '₹' }) {
  if (!data?.length) return <div style={{ color:'var(--text-muted)', textAlign:'center', padding:'2rem' }}>No data</div>;
  const max = Math.max(...data.map(d => Number(d[valueKey]) || 0), 1);
  return (
    <div style={{ display:'flex', alignItems:'flex-end', gap:'8px', height:'180px', padding:'0 4px' }}>
      {data.map((d, i) => {
        const val = Number(d[valueKey]) || 0;
        const pct = (val / max) * 100;
        return (
          <div key={i} style={{ flex:1, display:'flex', flexDirection:'column', alignItems:'center', gap:'4px' }}>
            <span style={{ fontSize:'9px', color:'var(--text-muted)' }}>{prefix}{val.toLocaleString('en-IN')}</span>
            <div style={{ width:'100%', height:`${Math.max(pct, 2)}%`, background:color,
              borderRadius:'4px 4px 0 0', transition:'height 0.5s ease', minHeight:'4px' }} />
            <span style={{ fontSize:'9px', color:'var(--text-muted)', whiteSpace:'nowrap' }}>{d[labelKey]}</span>
          </div>
        );
      })}
    </div>
  );
}

function StatCard({ label, value, icon, color = '#6366f1' }) {
  return (
    <div className="stat-card" style={{ textAlign:'center' }}>
      <div style={{ fontSize:'2rem', marginBottom:'0.5rem' }}>{icon}</div>
      <div style={{ fontSize:'2rem', fontWeight:900, color }}>{value}</div>
      <div style={{ color:'var(--text-muted)', fontSize:'0.8rem', marginTop:'0.25rem' }}>{label}</div>
    </div>
  );
}

export default function Reports() {
  const currentYear = new Date().getFullYear();
  const [year, setYear] = useState(currentYear);
  const [revenue, setRevenue] = useState([]);
  const [occupancy, setOccupancy] = useState([]);
  const [turnover, setTurnover] = useState([]);
  const [payments, setPayments] = useState(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('revenue');

  useEffect(() => {
    setLoading(true);
    Promise.all([
      api.get(`/api/reports/revenue?year=${year}`),
      api.get(`/api/reports/occupancy?year=${year}`),
      api.get(`/api/reports/guests?year=${year}`),
      api.get(`/api/reports/payments?year=${year}`)
    ]).then(([r, o, g, p]) => {
      setRevenue(r.data);
      setOccupancy(o.data);
      setTurnover(g.data);
      setPayments(p.data);
    }).catch(console.error)
    .finally(() => setLoading(false));
  }, [year]);

  const TABS = [
    { id:'revenue',   label:'💰 Revenue' },
    { id:'occupancy', label:'🛏️ Occupancy' },
    { id:'turnover',  label:'👥 Guests' },
    { id:'payments',  label:'💳 Payments' },
  ];

  const totalRevenue = revenue.reduce((s, r) => s + Number(r.total || 0), 0);
  const avgOccupancy = occupancy.length
    ? Math.round(occupancy.reduce((s, o) => s + Number(o.occupancyPct || 0), 0) / occupancy.length)
    : 0;

  return (
    <div>
      <div style={{ display:'flex', alignItems:'center', justifyContent:'space-between', marginBottom:'1.5rem', flexWrap:'wrap', gap:'1rem' }}>
        <div>
          <h1 style={{ fontSize:'1.75rem', fontWeight:800, color:'var(--text-primary)', margin:0 }}>
            📊 Reports &amp; Analytics
          </h1>
          <p style={{ color:'var(--text-muted)', margin:'0.25rem 0 0', fontSize:'0.9rem' }}>
            Business performance overview for {year}
          </p>
        </div>
        <select value={year} onChange={e => setYear(Number(e.target.value))}
          style={{ background:'var(--bg-card)', border:'1px solid var(--border)', color:'var(--text-primary)',
            borderRadius:'8px', padding:'0.5rem 1rem', fontSize:'0.9rem', cursor:'pointer' }}>
          {[currentYear, currentYear-1, currentYear-2].map(y => (
            <option key={y} value={y}>{y}</option>
          ))}
        </select>
      </div>

      {/* Summary KPIs */}
      <div className="stats-grid" style={{ gridTemplateColumns:'repeat(auto-fit,minmax(160px,1fr))', marginBottom:'1.5rem' }}>
        <StatCard label="Total Revenue" value={`₹${(totalRevenue/1000).toFixed(0)}K`} icon="💰" color="#6366f1" />
        <StatCard label="Avg Occupancy" value={`${avgOccupancy}%`} icon="🛏️" color="#10b981" />
        <StatCard label="Invoices Paid" value={payments?.paid ?? '—'} icon="✅" color="#22d3ee" />
        <StatCard label="Pending" value={payments?.generated ?? '—'} icon="⏳" color="#f59e0b" />
        <StatCard label="Overdue" value={payments?.overdue ?? '—'} icon="⚠️" color="#ef4444" />
      </div>

      {/* Tabs */}
      <div style={{ display:'flex', gap:'0.5rem', marginBottom:'1.5rem', flexWrap:'wrap' }}>
        {TABS.map(t => (
          <button key={t.id} onClick={() => setActiveTab(t.id)}
            style={{ padding:'0.5rem 1.25rem', borderRadius:'8px', border:'none', cursor:'pointer', fontWeight:600,
              background: activeTab === t.id ? 'var(--accent)' : 'var(--bg-card)',
              color: activeTab === t.id ? '#fff' : 'var(--text-secondary)', fontSize:'0.85rem' }}>
            {t.label}
          </button>
        ))}
      </div>

      {loading ? (
        <div style={{ textAlign:'center', padding:'3rem', color:'var(--text-muted)' }}>⏳ Loading reports...</div>
      ) : (
        <div className="card">
          {activeTab === 'revenue' && (
            <>
              <h3 style={{ color:'var(--text-primary)', margin:'0 0 1.5rem' }}>Monthly Revenue Breakdown</h3>
              <BarChart data={revenue} valueKey="total" labelKey="month" color="#6366f1" />
              <div style={{ overflowX:'auto', marginTop:'1.5rem' }}>
                <table style={{ width:'100%', borderCollapse:'collapse', fontSize:'0.85rem' }}>
                  <thead>
                    <tr style={{ borderBottom:'1px solid var(--border)' }}>
                      {['Month','Rent','EB','Food','Laundry','Total'].map(h => (
                        <th key={h} style={{ padding:'0.5rem', color:'var(--text-muted)', textAlign:'right', fontWeight:600 }}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {revenue.map((r, i) => (
                      <tr key={i} style={{ borderBottom:'1px solid var(--border)', opacity: r.total > 0 ? 1 : 0.4 }}>
                        <td style={{ padding:'0.5rem', color:'var(--text-primary)', fontWeight:600 }}>{r.month}</td>
                        {['rent','eb','food','laundry','total'].map(k => (
                          <td key={k} style={{ padding:'0.5rem', color: k==='total' ? 'var(--accent)' : 'var(--text-secondary)', textAlign:'right' }}>
                            ₹{Number(r[k]||0).toLocaleString('en-IN')}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}

          {activeTab === 'occupancy' && (
            <>
              <h3 style={{ color:'var(--text-primary)', margin:'0 0 1.5rem' }}>Monthly Occupancy Rate</h3>
              <BarChart data={occupancy} valueKey="occupancyPct" labelKey="month" color="#10b981" prefix="" />
              <div style={{ overflowX:'auto', marginTop:'1.5rem' }}>
                <table style={{ width:'100%', borderCollapse:'collapse', fontSize:'0.85rem' }}>
                  <thead>
                    <tr style={{ borderBottom:'1px solid var(--border)' }}>
                      {['Month','Total Beds','Occupied','Occupancy %'].map(h => (
                        <th key={h} style={{ padding:'0.5rem', color:'var(--text-muted)', textAlign:'right', fontWeight:600 }}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {occupancy.map((o, i) => (
                      <tr key={i} style={{ borderBottom:'1px solid var(--border)' }}>
                        <td style={{ padding:'0.5rem', color:'var(--text-primary)', fontWeight:600 }}>{o.month}</td>
                        <td style={{ padding:'0.5rem', textAlign:'right', color:'var(--text-secondary)' }}>{o.totalBeds}</td>
                        <td style={{ padding:'0.5rem', textAlign:'right', color:'var(--text-secondary)' }}>{o.occupiedBeds}</td>
                        <td style={{ padding:'0.5rem', textAlign:'right', color:'#10b981', fontWeight:700 }}>{o.occupancyPct}%</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}

          {activeTab === 'turnover' && (
            <>
              <h3 style={{ color:'var(--text-primary)', margin:'0 0 1.5rem' }}>Guest Turnover</h3>
              <div style={{ display:'flex', gap:'1rem', flexWrap:'wrap' }}>
                <div style={{ flex:1, minWidth:280 }}>
                  <p style={{ color:'var(--text-muted)', fontSize:'0.8rem', marginBottom:'0.5rem' }}>Check-Ins</p>
                  <BarChart data={turnover} valueKey="checkIns" labelKey="month" color="#22d3ee" prefix="" />
                </div>
                <div style={{ flex:1, minWidth:280 }}>
                  <p style={{ color:'var(--text-muted)', fontSize:'0.8rem', marginBottom:'0.5rem' }}>Check-Outs</p>
                  <BarChart data={turnover} valueKey="checkOuts" labelKey="month" color="#ef4444" prefix="" />
                </div>
              </div>
            </>
          )}

          {activeTab === 'payments' && payments && (
            <div>
              <h3 style={{ color:'var(--text-primary)', margin:'0 0 1.5rem' }}>Payment Summary — {year}</h3>
              <div style={{ display:'grid', gridTemplateColumns:'repeat(auto-fit,minmax(200px,1fr))', gap:'1rem' }}>
                {[
                  { label:'Total Revenue Collected', value:`₹${Number(payments.totalRevenue||0).toLocaleString('en-IN')}`, color:'#6366f1', icon:'💰' },
                  { label:'Invoices Paid', value:payments.paid, color:'#10b981', icon:'✅' },
                  { label:'Invoices Pending', value:payments.generated, color:'#f59e0b', icon:'⏳' },
                  { label:'Invoices Overdue', value:payments.overdue, color:'#ef4444', icon:'⚠️' },
                ].map((s, i) => (
                  <div key={i} className="stat-card" style={{ borderLeft:`4px solid ${s.color}` }}>
                    <div style={{ fontSize:'1.5rem' }}>{s.icon}</div>
                    <div style={{ fontSize:'1.8rem', fontWeight:900, color:s.color }}>{s.value}</div>
                    <div style={{ color:'var(--text-muted)', fontSize:'0.75rem' }}>{s.label}</div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

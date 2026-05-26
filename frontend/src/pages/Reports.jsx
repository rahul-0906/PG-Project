import React, { useState, useEffect } from 'react';
import api from '../api';
import AppLayout from '../components/AppLayout';
import { 
  BarChart3, 
  DollarSign, 
  Bed, 
  CheckCircle2, 
  Clock, 
  AlertTriangle, 
  Users, 
  CreditCard,
  Download,
  Coins
} from 'lucide-react';

const COLORS = ['#6366f1','#8b5cf6','#22d3ee','#10b981','#f59e0b','#ef4444',
                 '#06b6d4','#84cc16','#f97316','#a78bfa','#34d399','#fb923c'];

function BarChart({ data, valueKey, labelKey, color = '#6366f1', prefix = '₹', selectedLabel, onBarClick }) {
  const [hoveredIndex, setHoveredIndex] = useState(null);
  if (!data?.length) return <div style={{ color:'var(--text-muted)', textAlign:'center', padding:'2rem' }}>No data</div>;
  const max = Math.max(...data.map(d => Number(d[valueKey]) || 0), 1);
  return (
    <div style={{ display:'flex', alignItems:'flex-end', gap:'8px', height:'220px', padding:'0 4px' }}>
      {data.map((d, i) => {
        const val = Number(d[valueKey]) || 0;
        const pct = (val / max) * 100;
        const isSelected = selectedLabel && d[labelKey] === selectedLabel;
        const isHovered = hoveredIndex === i;
        const barFillColor = (isSelected || isHovered) ? color : `${color}40`;
        return (
          <div 
            key={i} 
            onClick={() => onBarClick && onBarClick(d[labelKey])}
            onMouseEnter={() => setHoveredIndex(i)}
            onMouseLeave={() => setHoveredIndex(null)}
            style={{ 
              flex:1, 
              display:'flex', 
              flexDirection:'column', 
              alignItems:'center', 
              height:'100%', 
              justifyContent:'flex-end',
              cursor: onBarClick ? 'pointer' : 'default',
              transition: 'transform 0.2s ease',
              transform: isSelected ? 'scale(1.02)' : (isHovered ? 'translateY(-2px) scale(1.01)' : 'none')
            }}
            className="chart-col-hover"
          >
            <span style={{ 
              fontSize:'9px', 
              color: isSelected ? color : 'var(--text-muted)', 
              marginBottom:'4px',
              fontWeight: isSelected ? 700 : 'normal'
            }}>
              {prefix}{val.toLocaleString('en-IN')}
            </span>
            <div style={{ 
              height:'160px', 
              width:'100%', 
              display:'flex', 
              alignItems:'flex-end', 
              background: isSelected ? `${color}15` : '#f8fafc', 
              borderRadius:'6px', 
              overflow:'hidden', 
              border: isSelected 
                ? `2px solid ${color}` 
                : (isHovered ? `1px solid ${color}80` : '1px solid #f1f5f9'), 
              boxShadow: isSelected ? `0 4px 12px ${color}24` : 'none',
              marginBottom:'4px',
              transition: 'all 0.2s ease'
            }}>
              <div 
                style={{ 
                  width:'100%', 
                  height:`${Math.max(pct, 2)}%`, 
                  background: barFillColor,
                  borderRadius:'4px 4px 0 0', 
                  minHeight:'4px',
                  transition: 'height 0.3s ease, background-color 0.2s ease'
                }} 
              />
            </div>
            <span style={{ 
              fontSize:'10px', 
              color: isSelected ? color : 'var(--text-muted)', 
              whiteSpace:'nowrap', 
              fontWeight: isSelected ? 700 : 600 
            }}>
              {d[labelKey]}
            </span>
          </div>
        );
      })}
    </div>
  );
}

function StatCard({ label, value, icon: Icon, iconColor = 'text-slate-400' }) {
  return (
    <div className="stat-card flex flex-col gap-1.5">
      <div className="flex items-center justify-between w-full">
        <span className="stat-label">{label}</span>
        {Icon && <Icon className={`w-4 h-4 ${iconColor}`} />}
      </div>
      <div className="stat-value">{value}</div>
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
  const [selectedMonthName, setSelectedMonthName] = useState(null);

  useEffect(() => {
    setLoading(true);
    Promise.all([
      api.get(`/reports/revenue?year=${year}`),
      api.get(`/reports/occupancy?year=${year}`),
      api.get(`/reports/guests?year=${year}`),
      api.get(`/reports/payments?year=${year}`)
    ]).then(([r, o, g, p]) => {
      setRevenue(r.data);
      setOccupancy(o.data);
      setTurnover(g.data);
      setPayments(p.data);
    }).catch(console.error)
    .finally(() => setLoading(false));
  }, [year]);

  useEffect(() => {
    if (activeTab === 'revenue' && revenue.length > 0) {
      const activeMonths = revenue.filter(r => r.total > 0);
      if (activeMonths.length > 0) {
        setSelectedMonthName(activeMonths[activeMonths.length - 1].month);
      } else {
        setSelectedMonthName(revenue[0].month);
      }
    } else if (activeTab === 'occupancy' && occupancy.length > 0) {
      const activeMonths = occupancy.filter(o => o.occupiedBeds > 0);
      if (activeMonths.length > 0) {
        setSelectedMonthName(activeMonths[activeMonths.length - 1].month);
      } else {
        setSelectedMonthName(occupancy[0].month);
      }
    }
  }, [revenue, occupancy, activeTab]);

  const handleExportRevenue = () => {
    const headers = ['Month', 'Rent', 'EB', 'Food', 'Laundry', 'Total'];
    const rows = revenue.map(r => [
      r.month,
      r.rent,
      r.eb,
      r.food,
      r.laundry,
      r.total
    ]);
    const csvContent = [headers, ...rows]
      .map(e => e.map(val => `"${val}"`).join(","))
      .join("\n");
    
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.setAttribute("href", url);
    link.setAttribute("download", `revenue_report_${year}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const handleExportOccupancy = () => {
    const headers = ['Month', 'Total Beds', 'Occupied Beds', 'Occupancy Pct'];
    const rows = occupancy.map(o => [
      o.month,
      o.totalBeds,
      o.occupiedBeds,
      o.occupancyPct + '%'
    ]);
    const csvContent = [headers, ...rows]
      .map(e => e.map(val => `"${val}"`).join(","))
      .join("\n");
    
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.setAttribute("href", url);
    link.setAttribute("download", `occupancy_report_${year}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const TABS = [
    { id:'revenue',   label:'Revenue', icon: DollarSign },
    { id:'occupancy', label:'Occupancy', icon: Bed },
    { id:'turnover',  label:'Guests', icon: Users },
    { id:'payments',  label:'Payments', icon: CreditCard },
  ];

  const totalRevenue = revenue.reduce((s, r) => s + Number(r.total || 0), 0);
  const avgOccupancy = occupancy.length
    ? Math.round(occupancy.reduce((s, o) => s + Number(o.occupancyPct || 0), 0) / occupancy.length)
    : 0;

  return (
    <AppLayout>
      <div className="page-header">
        <div>
          <h1 className="page-title flex items-center gap-2">
            <BarChart3 className="w-6 h-6 text-primary" />
            <span>Reports &amp; Analytics</span>
          </h1>
          <p className="page-subtitle">Business performance overview for {year}</p>
        </div>
        <select value={year} onChange={e => setYear(Number(e.target.value))}
          className="form-input" style={{ width: 'auto', cursor: 'pointer', marginRight: '3.5rem' }}>
          {[currentYear, currentYear-1, currentYear-2].map(y => (
            <option key={y} value={y}>{y}</option>
          ))}
        </select>
      </div>

      {/* Summary KPIs */}
      <div className="stats-grid-5" style={{ marginBottom:'1.5rem' }}>
        <StatCard label="Total Revenue" value={`₹${(totalRevenue/1000).toFixed(0)}K`} icon={DollarSign} iconColor="text-blue-500" />
        <StatCard label="Avg Occupancy" value={`${avgOccupancy}%`} icon={Bed} iconColor="text-emerald-500" />
        <StatCard label="Invoices Paid" value={payments?.paid ?? '—'} icon={CheckCircle2} iconColor="text-green-500" />
        <StatCard label="Pending" value={payments?.generated ?? '—'} icon={Clock} iconColor="text-amber-500" />
        <StatCard label="Overdue" value={payments?.overdue ?? '—'} icon={AlertTriangle} iconColor="text-rose-500" />
      </div>

      {/* Tabs */}
      <div className="tabs-container">
        {TABS.map(t => {
          const Icon = t.icon;
          return (
            <button key={t.id} onClick={() => setActiveTab(t.id)}
              className={`tab-btn flex items-center gap-1.5 ${activeTab === t.id ? 'active' : ''}`}>
              <Icon className="w-3.5 h-3.5" />
              <span>{t.label}</span>
            </button>
          );
        })}
      </div>

      {loading ? (
        <div style={{ textAlign:'center', padding:'3rem', color:'var(--text-muted)' }}>⏳ Loading reports...</div>
      ) : (
        <div className="card">
          {activeTab === 'revenue' && (
            <>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
                <h3 style={{ color:'var(--text-primary)', margin: 0, fontWeight: 700 }}>Monthly Revenue Breakdown</h3>
                <button onClick={handleExportRevenue} className="btn btn-ghost flex items-center gap-2" style={{ padding: '0.4rem 0.8rem', fontSize: '0.8rem' }}>
                  <Download className="w-4 h-4 text-slate-500" />
                  <span>Export CSV</span>
                </button>
              </div>
              <BarChart 
                data={revenue} 
                valueKey="total" 
                labelKey="month" 
                color="#6366f1" 
                selectedLabel={selectedMonthName}
                onBarClick={setSelectedMonthName}
              />

              {/* Selected Month Detail Card */}
              {selectedMonthName && (() => {
                const selectedData = revenue.find(r => r.month === selectedMonthName);
                if (!selectedData) return null;
                return (
                  <div className="card" style={{ marginTop: '2rem', background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '12px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem', borderBottom: '1px solid #e2e8f0', paddingBottom: '0.75rem' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Coins className="w-5 h-5 text-indigo-500" />
                        <h4 style={{ margin: 0, fontWeight: 800, fontSize: '0.95rem', color: '#1e293b' }}>
                          {selectedMonthName} {year} Revenue Details
                        </h4>
                      </div>
                      <span className="badge badge-accent" style={{ marginLeft: 'auto' }}>
                        Total: ₹{Number(selectedData.total || 0).toLocaleString('en-IN')}
                      </span>
                    </div>
                    <div className="grid-4">
                      <div className="stat-card" style={{ padding: '0.75rem 1rem', background: 'white' }}>
                        <span className="stat-label">Rent</span>
                        <div className="stat-value" style={{ fontSize: '1.25rem', color: 'var(--color-primary)' }}>
                          ₹{Number(selectedData.rent || 0).toLocaleString('en-IN')}
                        </div>
                      </div>
                      <div className="stat-card" style={{ padding: '0.75rem 1rem', background: 'white' }}>
                        <span className="stat-label">EB Bill</span>
                        <div className="stat-value" style={{ fontSize: '1.25rem', color: '#eab308' }}>
                          ₹{Number(selectedData.eb || 0).toLocaleString('en-IN')}
                        </div>
                      </div>
                      <div className="stat-card" style={{ padding: '0.75rem 1rem', background: 'white' }}>
                        <span className="stat-label">Food</span>
                        <div className="stat-value" style={{ fontSize: '1.25rem', color: '#10b981' }}>
                          ₹{Number(selectedData.food || 0).toLocaleString('en-IN')}
                        </div>
                      </div>
                      <div className="stat-card" style={{ padding: '0.75rem 1rem', background: 'white' }}>
                        <span className="stat-label">Laundry</span>
                        <div className="stat-value" style={{ fontSize: '1.25rem', color: '#a855f7' }}>
                          ₹{Number(selectedData.laundry || 0).toLocaleString('en-IN')}
                        </div>
                      </div>
                    </div>
                  </div>
                );
              })()}
            </>
          )}

          {activeTab === 'occupancy' && (
            <>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
                <h3 style={{ color:'var(--text-primary)', margin: 0, fontWeight: 700 }}>Monthly Occupancy Rate</h3>
                <button onClick={handleExportOccupancy} className="btn btn-ghost flex items-center gap-2" style={{ padding: '0.4rem 0.8rem', fontSize: '0.8rem' }}>
                  <Download className="w-4 h-4 text-slate-500" />
                  <span>Export CSV</span>
                </button>
              </div>
              <BarChart 
                data={occupancy} 
                valueKey="occupancyPct" 
                labelKey="month" 
                color="#10b981" 
                prefix="" 
                selectedLabel={selectedMonthName}
                onBarClick={setSelectedMonthName}
              />

              {/* Selected Month Detail Card */}
              {selectedMonthName && (() => {
                const selectedData = occupancy.find(o => o.month === selectedMonthName);
                if (!selectedData) return null;
                const vacantBeds = Math.max(0, selectedData.totalBeds - selectedData.occupiedBeds);
                return (
                  <div className="card" style={{ marginTop: '2rem', background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '12px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem', borderBottom: '1px solid #e2e8f0', paddingBottom: '0.75rem' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Bed className="w-5 h-5 text-emerald-500" />
                        <h4 style={{ margin: 0, fontWeight: 800, fontSize: '0.95rem', color: '#1e293b' }}>
                          {selectedMonthName} {year} Occupancy Details
                        </h4>
                      </div>
                      <span className="badge badge-success" style={{ marginLeft: 'auto' }}>
                        Occupancy: {selectedData.occupancyPct}%
                      </span>
                    </div>
                    <div className="grid-3">
                      <div className="stat-card" style={{ padding: '0.75rem 1rem', background: 'white' }}>
                        <span className="stat-label">Total Beds</span>
                        <div className="stat-value" style={{ fontSize: '1.25rem', color: 'var(--text-secondary)' }}>
                          {selectedData.totalBeds}
                        </div>
                      </div>
                      <div className="stat-card" style={{ padding: '0.75rem 1rem', background: 'white' }}>
                        <span className="stat-label">Occupied</span>
                        <div className="stat-value" style={{ fontSize: '1.25rem', color: '#10b981' }}>
                          {selectedData.occupiedBeds}
                        </div>
                      </div>
                      <div className="stat-card" style={{ padding: '0.75rem 1rem', background: 'white' }}>
                        <span className="stat-label">Vacant</span>
                        <div className="stat-value" style={{ fontSize: '1.25rem', color: '#64748b' }}>
                          {vacantBeds}
                        </div>
                      </div>
                    </div>
                  </div>
                );
              })()}
            </>
          )}

          {activeTab === 'turnover' && (
            <>
              <h3 style={{ color:'var(--text-primary)', marginBottom:'1.5rem', fontWeight: 700 }}>Guest Turnover</h3>
              <div style={{ display:'flex', gap:'1.5rem', flexWrap:'wrap' }}>
                <div style={{ flex:1, minWidth:280 }}>
                  <p style={{ color:'var(--text-muted)', fontSize:'0.85rem', marginBottom:'0.75rem', fontWeight: 600 }}>Check-Ins</p>
                  <BarChart data={turnover} valueKey="checkIns" labelKey="month" color="#22d3ee" prefix="" />
                </div>
                <div style={{ flex:1, minWidth:280 }}>
                  <p style={{ color:'var(--text-muted)', fontSize:'0.85rem', marginBottom:'0.75rem', fontWeight: 600 }}>Check-Outs</p>
                  <BarChart data={turnover} valueKey="checkOuts" labelKey="month" color="#ef4444" prefix="" />
                </div>
              </div>
            </>
          )}

          {activeTab === 'payments' && payments && (
            <div>
              <h3 className="text-slate-800 font-bold mb-4">Payment Summary — {year}</h3>
              <div className="grid-4">
                {[
                  { label:'Total Collected', value:`₹${Number(payments.totalRevenue||0).toLocaleString('en-IN')}`, icon: DollarSign, iconColor: 'text-blue-500' },
                  { label:'Invoices Paid', value:payments.paid, icon: CheckCircle2, iconColor: 'text-emerald-500' },
                  { label:'Invoices Pending', value:payments.generated, icon: Clock, iconColor: 'text-amber-500' },
                  { label:'Invoices Overdue', value:payments.overdue, icon: AlertTriangle, iconColor: 'text-rose-500' },
                ].map((s, i) => (
                  <StatCard key={i} label={s.label} value={s.value} icon={s.icon} iconColor={s.iconColor} />
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </AppLayout>
  );
}

import React, { useState, useEffect } from 'react';
import api, { ownerApi } from '../api';
import AppLayout from '../components/AppLayout';
import { 
  BarChart3, 
  IndianRupee, 
  Bed, 
  CheckCircle2, 
  Clock, 
  AlertTriangle, 
  Users, 
  CreditCard,
  Download,
  Coins,
  Building2,
  Table as TableIcon,
  Printer,
  TrendingUp,
  TrendingDown
} from 'lucide-react';

function BarChart({ data, valueKey, labelKey, color = '#6366f1', prefix = '₹', selectedLabel, onBarClick, tooltipFormatter }) {
  const [hoveredIndex, setHoveredIndex] = useState(null);
  if (!data?.length) return <div style={{ color:'var(--text-muted)', textAlign:'center', padding:'2rem' }}>No data available</div>;
  const max = Math.max(...data.map(d => Number(d[valueKey]) || 0), 1);
  return (
    <div style={{ display:'flex', alignItems:'flex-end', gap:'8px', height:'230px', padding:'16px 4px 0 4px', position: 'relative' }}>
      {data.map((d, i) => {
        const val = Number(d[valueKey]) || 0;
        const pct = (val / max) * 100;
        const isSelected = selectedLabel && d[labelKey] === selectedLabel;
        const isHovered = hoveredIndex === i;
        const barFillColor = (isSelected || isHovered) ? color : `${color}40`;
        const tooltipText = tooltipFormatter ? tooltipFormatter(d) : null;
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
              transform: isSelected ? 'scale(1.02)' : (isHovered ? 'translateY(-2px) scale(1.01)' : 'none'),
              position: 'relative'
            }}
            className="chart-col-hover"
          >
            {/* Tooltip on Hover */}
            {isHovered && tooltipText && (
              <div className="absolute -top-9 z-20 bg-slate-900 text-white text-[10px] font-medium py-1 px-2.5 rounded shadow-lg pointer-events-none whitespace-nowrap">
                {tooltipText}
                <div className="absolute -bottom-1 left-1/2 -translate-x-1/2 w-2 h-2 bg-slate-900 rotate-45"></div>
              </div>
            )}
            
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
                  background: isSelected 
                    ? `linear-gradient(180deg, ${color} 0%, ${color}dd 100%)` 
                    : barFillColor,
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

function LineChart({ data, valueKey, valueKey2 = null, labelKey, color = '#6366f1', color2 = '#ef4444', prefix = '₹', selectedLabel, onPointClick }) {
  if (!data?.length) return <div style={{ color:'var(--text-muted)', textAlign:'center', padding:'2rem' }}>No data available</div>;
  
  const values1 = data.map(d => Number(d[valueKey]) || 0);
  const values2 = valueKey2 ? data.map(d => Number(d[valueKey2]) || 0) : [];
  const max = Math.max(...values1, ...values2, 1);
  
  const width = 600;
  const height = 200;
  const paddingX = 40;
  const paddingY = 25;
  const chartWidth = width - paddingX * 2;
  const chartHeight = height - paddingY * 2;
  
  const points1 = data.map((d, i) => {
    const x = paddingX + (i / (data.length - 1)) * chartWidth;
    const y = height - paddingY - ((Number(d[valueKey]) || 0) / max) * chartHeight;
    return { x, y, val: Number(d[valueKey]) || 0, label: d[labelKey] };
  });

  const points2 = valueKey2 ? data.map((d, i) => {
    const x = paddingX + (i / (data.length - 1)) * chartWidth;
    const y = height - paddingY - ((Number(d[valueKey2]) || 0) / max) * chartHeight;
    return { x, y, val: Number(d[valueKey2]) || 0, label: d[labelKey] };
  }) : [];

  const getLinePath = (pts) => {
    if (pts.length === 0) return '';
    return pts.reduce((path, p, i) => {
      if (i === 0) return `M ${p.x} ${p.y}`;
      const prev = pts[i - 1];
      const cpX1 = prev.x + (p.x - prev.x) / 3;
      const cpY1 = prev.y;
      const cpX2 = prev.x + 2 * (p.x - prev.x) / 3;
      const cpY2 = p.y;
      return `${path} C ${cpX1} ${cpY1}, ${cpX2} ${cpY2}, ${p.x} ${p.y}`;
    }, '');
  };

  const path1 = getLinePath(points1);
  const path2 = getLinePath(points2);

  const areaPath1 = points1.length > 0 
    ? `${path1} L ${points1[points1.length - 1].x} ${height - paddingY} L ${points1[0].x} ${height - paddingY} Z`
    : '';

  return (
    <div className="relative w-full overflow-hidden" style={{ height: '240px' }}>
      <svg className="w-full h-full" viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none">
        <defs>
          <linearGradient id={`grad-${valueKey}`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={color} stopOpacity="0.25" />
            <stop offset="100%" stopColor={color} stopOpacity="0.0" />
          </linearGradient>
        </defs>

        {/* Grid lines */}
        {[0, 0.25, 0.5, 0.75, 1].map((p, idx) => {
          const y = paddingY + p * chartHeight;
          const valLabel = Math.round(max * (1 - p));
          return (
            <g key={idx}>
              <line 
                x1={paddingX} 
                y1={y} 
                x2={width - paddingX} 
                y2={y} 
                stroke="#f1f5f9" 
                strokeWidth="1.5" 
                strokeDasharray="4"
              />
              <text 
                x={paddingX - 8} 
                y={y + 3} 
                textAnchor="end" 
                fill="#94a3b8" 
                className="text-[9px] font-semibold font-mono"
              >
                {prefix}{valLabel.toLocaleString('en-IN')}
              </text>
            </g>
          );
        })}

        {/* Area fill for Line 1 */}
        {!valueKey2 && areaPath1 && (
          <path d={areaPath1} fill={`url(#grad-${valueKey})`} />
        )}

        {/* Paths */}
        {path1 && (
          <path 
            d={path1} 
            fill="none" 
            stroke={color} 
            strokeWidth="3" 
            strokeLinecap="round" 
          />
        )}
        {path2 && (
          <path 
            d={path2} 
            fill="none" 
            stroke={color2} 
            strokeWidth="3" 
            strokeLinecap="round" 
          />
        )}

        {/* Interactive Points */}
        {points1.map((p, idx) => (
          <g key={`pt1-${idx}`} className="cursor-pointer group">
            <circle 
              cx={p.x} 
              cy={p.y} 
              r="4" 
              fill="white" 
              stroke={color} 
              strokeWidth="2.5" 
              onClick={() => onPointClick && onPointClick(p.label)}
            />
            <title>{p.label}: {prefix}{p.val.toLocaleString('en-IN')}</title>
          </g>
        ))}

        {points2.map((p, idx) => (
          <g key={`pt2-${idx}`} className="cursor-pointer group">
            <circle 
              cx={p.x} 
              cy={p.y} 
              r="4" 
              fill="white" 
              stroke={color2} 
              strokeWidth="2.5" 
              onClick={() => onPointClick && onPointClick(p.label)}
            />
            <title>{p.label}: {prefix}{p.val.toLocaleString('en-IN')}</title>
          </g>
        ))}

        {/* X Axis Labels */}
        {data.map((d, i) => {
          const x = paddingX + (i / (data.length - 1)) * chartWidth;
          return (
            <text 
              key={i} 
              x={x} 
              y={height - 6} 
              textAnchor="middle" 
              fill="#64748b" 
              className="text-[9px] font-bold font-sans"
            >
              {d[labelKey]}
            </text>
          );
        })}
      </svg>
    </div>
  );
}

function StatCard({ label, value, icon: Icon, iconColor = 'text-slate-400', trend = null }) {
  return (
    <div className="stat-card flex flex-col gap-1.5 bg-white p-3.5 rounded-xl border border-slate-200/60 shadow-sm hover:shadow transition-all">
      <div className="flex items-center justify-between w-full">
        <span className="stat-label text-slate-500 text-xs font-semibold">{label}</span>
        {Icon && <Icon className={`w-4 h-4 ${iconColor}`} />}
      </div>
      <div className="flex items-baseline justify-between gap-2 mt-0.5">
        <div className="stat-value font-heading font-bold text-slate-900 text-lg">{value}</div>
        {trend !== null && trend !== undefined && !isNaN(trend) && (
          <div className={`text-[10px] font-bold px-1.5 py-0.5 rounded flex items-center gap-0.5 ${
            trend >= 0 ? 'bg-emerald-50 text-emerald-600' : 'bg-rose-50 text-rose-600'
          }`}>
            {trend >= 0 ? <TrendingUp className="w-3 h-3" /> : <TrendingDown className="w-3 h-3" />}
            <span>{trend >= 0 ? `+${trend}%` : `${trend}%`}</span>
          </div>
        )}
      </div>
    </div>
  );
}

export default function Reports() {
  const currentYear = new Date().getFullYear();
  const [year, setYear] = useState(currentYear);
  const [buildings, setBuildings] = useState([]);
  const [selectedBuilding, setSelectedBuilding] = useState('');
  
  const [revenue, setRevenue] = useState([]);
  const [occupancy, setOccupancy] = useState([]);
  const [turnover, setTurnover] = useState([]);
  const [payments, setPayments] = useState(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('revenue');
  const [selectedMonthName, setSelectedMonthName] = useState(null);
  const [viewMode, setViewMode] = useState('chart'); // 'chart' | 'table'
  const [revenueChartType, setRevenueChartType] = useState('bar'); // 'bar' | 'line'

  // Fetch building list for filter
  useEffect(() => {
    ownerApi.getBranches()
      .then(res => setBuildings(res.data || []))
      .catch(console.error);
  }, []);

  // Fetch reporting datasets
  useEffect(() => {
    setLoading(true);
    const buildingParam = selectedBuilding ? `&buildingId=${selectedBuilding}` : '';
    Promise.all([
      api.get(`/reports/revenue?year=${year}${buildingParam}`),
      api.get(`/reports/occupancy?year=${year}${buildingParam}`),
      api.get(`/reports/guests?year=${year}${buildingParam}`),
      api.get(`/reports/payments?year=${year}${buildingParam}`)
    ]).then(([r, o, g, p]) => {
      setRevenue(r.data);
      setOccupancy(o.data.map(item => ({
        ...item,
        occupancyPct: item.totalBeds > 0 ? Math.round((item.occupiedBeds / item.totalBeds) * 100) : 0
      })));
      setTurnover(g.data);
      setPayments(p.data);
    }).catch(console.error)
    .finally(() => setLoading(false));
  }, [year, selectedBuilding]);

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
    { id:'revenue',   label:'Revenue', icon: IndianRupee },
    { id:'occupancy', label:'Occupancy', icon: Bed },
    { id:'turnover',  label:'Guests', icon: Users },
    { id:'payments',  label:'Payments', icon: CreditCard },
  ];

  const totalRevenue = revenue.reduce((s, r) => s + Number(r.total || 0), 0);
  const avgOccupancy = occupancy.length
    ? Math.round(occupancy.reduce((s, o) => s + Number(o.occupancyPct || 0), 0) / occupancy.length)
    : 0;

  // Calculate MoM growth for Revenue
  let revenueMoMTrend = null;
  if (revenue.length > 1) {
    const activeMonths = revenue.filter(r => Number(r.total || 0) > 0);
    if (activeMonths.length >= 2) {
      const curr = Number(activeMonths[activeMonths.length - 1].total);
      const prev = Number(activeMonths[activeMonths.length - 2].total);
      if (prev > 0) {
        revenueMoMTrend = Math.round(((curr - prev) / prev) * 100);
      }
    }
  }

  // Calculate MoM trend for Occupancy
  let occupancyMoMTrend = null;
  if (occupancy.length > 1) {
    const activeMonths = occupancy.filter(o => Number(o.occupiedBeds || 0) > 0);
    if (activeMonths.length >= 2) {
      const curr = Number(activeMonths[activeMonths.length - 1].occupancyPct);
      const prev = Number(activeMonths[activeMonths.length - 2].occupancyPct);
      occupancyMoMTrend = curr - prev;
    }
  }

  return (
    <AppLayout>
      <div className="page-header flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="page-title flex items-center gap-2">
            <BarChart3 className="w-6 h-6 text-primary" strokeWidth={1.5}/>
            <span>Reports &amp; Analytics</span>
          </h1>
          <p className="page-subtitle">
            Business performance overview for {year}
            {selectedBuilding && buildings.find(b => b.id === selectedBuilding) 
              ? ` • ${buildings.find(b => b.id === selectedBuilding).name}` 
              : ' • All Branches'}
          </p>
        </div>

        {/* Global Filters */}
        <div className="flex items-center gap-3">
          {/* Branch Filter */}
          <div className="flex items-center gap-1.5 bg-white border border-slate-200 rounded-lg px-2.5 py-1.5 shadow-sm">
            <Building2 className="w-4 h-4 text-slate-400" />
            <select 
              value={selectedBuilding} 
              onChange={e => setSelectedBuilding(e.target.value)}
              className="text-xs font-semibold bg-transparent text-slate-700 focus:outline-none cursor-pointer"
            >
              <option value="">All Branches</option>
              {buildings.map(b => (
                <option key={b.id} value={b.id}>{b.name}</option>
              ))}
            </select>
          </div>

          {/* Year Filter */}
          <select 
            value={year} 
            onChange={e => setYear(Number(e.target.value))}
            className="form-input text-xs font-semibold py-1.5 px-3 cursor-pointer bg-white border-slate-200 rounded-lg shadow-sm"
            style={{ width: 'auto' }}
          >
            {[currentYear, currentYear-1, currentYear-2].map(y => (
              <option key={y} value={y}>{y}</option>
            ))}
          </select>

          {/* Print Button */}
          <button 
            onClick={() => window.print()} 
            className="btn btn-ghost py-1.5 px-2.5 border border-slate-200 bg-white rounded-lg hover:bg-slate-50 text-slate-600 shadow-sm"
            title="Print Report"
          >
            <Printer className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Summary KPIs */}
      <div className="stats-grid-5" style={{ marginBottom:'1.5rem' }}>
        <StatCard label="Total Revenue" value={`₹${(totalRevenue/1000).toFixed(0)}K`} icon={IndianRupee} iconColor="text-blue-500" trend={revenueMoMTrend} />
        <StatCard label="Avg Occupancy" value={`${avgOccupancy}%`} icon={Bed} iconColor="text-emerald-500" trend={occupancyMoMTrend} />
        <StatCard label="Invoices Paid" value={payments?.paid ?? '—'} icon={CheckCircle2} iconColor="text-green-500" />
        <StatCard label="Pending" value={payments?.generated ?? '—'} icon={Clock} iconColor="text-amber-500" />
        <StatCard label="Overdue" value={payments?.overdue ?? '—'} icon={AlertTriangle} iconColor="text-rose-500" />
      </div>

      {/* Tabs */}
      <div className="tabs-container flex items-center justify-between">
        <div className="flex gap-2">
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
      </div>

      {/* View Mode Toggle (Chart vs Table) - Placed below the tabs bar */}
      {(activeTab === 'revenue' || activeTab === 'occupancy') && (
        <div className="flex justify-end mt-3 mb-2">
          <div className="flex items-center gap-1 bg-slate-100 p-1 rounded-lg">
            <button
              onClick={() => setViewMode('chart')}
              className={`p-1.5 rounded-md text-xs font-semibold flex items-center gap-1 transition-all ${
                viewMode === 'chart' ? 'bg-white text-slate-800 shadow-sm' : 'text-slate-500 hover:text-slate-700'
              }`}
              title="Chart View"
            >
              <BarChart3 className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">Chart</span>
            </button>
            <button
              onClick={() => setViewMode('table')}
              className={`p-1.5 rounded-md text-xs font-semibold flex items-center gap-1 transition-all ${
                viewMode === 'table' ? 'bg-white text-slate-800 shadow-sm' : 'text-slate-500 hover:text-slate-700'
              }`}
              title="Table View"
            >
              <TableIcon className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">Table</span>
            </button>
          </div>
        </div>
      )}

      {loading ? (
        <div style={{ textAlign:'center', padding:'3rem', color:'var(--text-muted)' }}>⏳ Loading reports...</div>
      ) : (
        <div className="card">
          {/* Revenue Tab */}
          {activeTab === 'revenue' && (
            <>
              <div className="flex justify-between items-center mb-6">
                <h3 className="font-heading text-base font-semibold text-slate-900">Monthly Revenue Breakdown</h3>
                <div className="flex items-center gap-2">
                  {/* Chart Style Selector */}
                  {viewMode === 'chart' && (
                    <div className="flex items-center gap-1 bg-slate-100 p-0.5 rounded-md text-[10px] mr-2">
                      <button 
                        onClick={() => setRevenueChartType('bar')} 
                        className={`px-2.5 py-1 rounded font-bold transition-all ${revenueChartType === 'bar' ? 'bg-white text-slate-800 shadow-sm' : 'text-slate-500 hover:text-slate-700'}`}
                      >
                        Bar
                      </button>
                      <button 
                        onClick={() => setRevenueChartType('line')} 
                        className={`px-2.5 py-1 rounded font-bold transition-all ${revenueChartType === 'line' ? 'bg-white text-slate-800 shadow-sm' : 'text-slate-500 hover:text-slate-700'}`}
                      >
                        Line
                      </button>
                    </div>
                  )}
                  <button onClick={handleExportRevenue} className="btn btn-ghost flex items-center gap-2 py-1.5 px-3 text-xs border border-slate-200 bg-white">
                    <Download className="w-4 h-4 text-slate-500" strokeWidth={1.5}/>
                    <span>Export CSV</span>
                  </button>
                </div>
              </div>

              {viewMode === 'chart' ? (
                <>
                  {revenueChartType === 'bar' ? (
                    <BarChart 
                      data={revenue} 
                      valueKey="total" 
                      labelKey="month" 
                      color="#6366f1" 
                      selectedLabel={selectedMonthName}
                      onBarClick={setSelectedMonthName}
                      tooltipFormatter={d => `${d.month}: ₹${Number(d.total || 0).toLocaleString('en-IN')}`}
                    />
                  ) : (
                    <LineChart 
                      data={revenue} 
                      valueKey="total" 
                      labelKey="month" 
                      color="#6366f1" 
                      selectedLabel={selectedMonthName}
                      onPointClick={setSelectedMonthName}
                    />
                  )}

                  {/* Selected Month Detail Card */}
                  {selectedMonthName && (() => {
                    const selectedData = revenue.find(r => r.month === selectedMonthName);
                    if (!selectedData) return null;
                    return (
                      <div className="card mt-8 bg-slate-50 border border-slate-200 rounded-xl p-6">
                        <div className="flex justify-between items-center mb-4 border-b border-slate-200 pb-3">
                          <div className="flex items-center gap-2">
                            <Coins className="w-5 h-5 text-indigo-500" strokeWidth={1.5}/>
                            <h4 className="font-heading text-sm font-semibold text-slate-900">
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
              ) : (
                /* Print-friendly Table View */
                <div className="overflow-x-auto">
                  <table className="w-full text-xs text-left border-collapse">
                    <thead>
                      <tr className="bg-slate-50 border-b border-slate-200 text-slate-500 font-semibold uppercase tracking-wider">
                        <th className="py-3 px-4">Month</th>
                        <th className="py-3 px-4 text-right">Rent (₹)</th>
                        <th className="py-3 px-4 text-right">EB Bill (₹)</th>
                        <th className="py-3 px-4 text-right">Food (₹)</th>
                        <th className="py-3 px-4 text-right">Laundry (₹)</th>
                        <th className="py-3 px-4 text-right">Total (₹)</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100 font-mono">
                      {revenue.map(r => (
                        <tr key={r.month} className="hover:bg-slate-50/80">
                          <td className="py-3 px-4 font-semibold font-sans text-slate-800">{r.month} {year}</td>
                          <td className="py-3 px-4 text-right text-slate-600">₹{Number(r.rent || 0).toLocaleString('en-IN')}</td>
                          <td className="py-3 px-4 text-right text-amber-600 font-medium">₹{Number(r.eb || 0).toLocaleString('en-IN')}</td>
                          <td className="py-3 px-4 text-right text-emerald-600 font-medium">₹{Number(r.food || 0).toLocaleString('en-IN')}</td>
                          <td className="py-3 px-4 text-right text-purple-600 font-medium">₹{Number(r.laundry || 0).toLocaleString('en-IN')}</td>
                          <td className="py-3 px-4 text-right font-bold text-indigo-600">₹{Number(r.total || 0).toLocaleString('en-IN')}</td>
                        </tr>
                      ))}
                    </tbody>
                    <tfoot>
                      <tr className="bg-slate-100/70 font-bold border-t-2 border-slate-200">
                        <td className="py-3 px-4 font-sans text-slate-800">Total Year Revenue</td>
                        <td className="py-3 px-4 text-right text-slate-700">₹{revenue.reduce((s, r) => s + Number(r.rent||0), 0).toLocaleString('en-IN')}</td>
                        <td className="py-3 px-4 text-right text-amber-700">₹{revenue.reduce((s, r) => s + Number(r.eb||0), 0).toLocaleString('en-IN')}</td>
                        <td className="py-3 px-4 text-right text-emerald-700">₹{revenue.reduce((s, r) => s + Number(r.food||0), 0).toLocaleString('en-IN')}</td>
                        <td className="py-3 px-4 text-right text-purple-700">₹{revenue.reduce((s, r) => s + Number(r.laundry||0), 0).toLocaleString('en-IN')}</td>
                        <td className="py-3 px-4 text-right text-indigo-700">₹{totalRevenue.toLocaleString('en-IN')}</td>
                      </tr>
                    </tfoot>
                  </table>
                </div>
              )}
            </>
          )}

          {/* Occupancy Tab */}
          {activeTab === 'occupancy' && (
            <>
              <div className="flex justify-between items-center mb-6">
                <h3 className="font-heading text-base font-semibold text-slate-900">Monthly Occupancy Rate</h3>
                <button onClick={handleExportOccupancy} className="btn btn-ghost flex items-center gap-2 py-1.5 px-3 text-xs border border-slate-200 bg-white">
                  <Download className="w-4 h-4 text-slate-500" strokeWidth={1.5}/>
                  <span>Export CSV</span>
                </button>
              </div>

              {viewMode === 'chart' ? (
                <>
                  <LineChart 
                    data={occupancy} 
                    valueKey="occupancyPct" 
                    labelKey="month" 
                    color="#10b981" 
                    prefix="" 
                    selectedLabel={selectedMonthName}
                    onPointClick={setSelectedMonthName}
                  />

                  {/* Selected Month Detail Card */}
                  {selectedMonthName && (() => {
                    const selectedData = occupancy.find(o => o.month === selectedMonthName);
                    if (!selectedData) return null;
                    const vacantBeds = Math.max(0, selectedData.totalBeds - selectedData.occupiedBeds);
                    return (
                      <div className="card mt-8 bg-slate-50 border border-slate-200 rounded-xl p-6">
                        <div className="flex justify-between items-center mb-4 border-b border-slate-200 pb-3">
                          <div className="flex items-center gap-2">
                            <Bed className="w-5 h-5 text-emerald-500" strokeWidth={1.5}/>
                            <h4 className="font-heading text-sm font-semibold text-slate-900">
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
              ) : (
                /* Print-friendly Occupancy Table */
                <div className="overflow-x-auto">
                  <table className="w-full text-xs text-left border-collapse">
                    <thead>
                      <tr className="bg-slate-50 border-b border-slate-200 text-slate-500 font-semibold uppercase tracking-wider">
                        <th className="py-3 px-4">Month</th>
                        <th className="py-3 px-4 text-center">Total Beds</th>
                        <th className="py-3 px-4 text-center">Occupied Beds</th>
                        <th className="py-3 px-4 text-center">Vacant Beds</th>
                        <th className="py-3 px-4 text-right">Occupancy Rate</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100 font-mono">
                      {occupancy.map(o => {
                        const vacant = Math.max(0, o.totalBeds - o.occupiedBeds);
                        return (
                          <tr key={o.month} className="hover:bg-slate-50/80">
                            <td className="py-3 px-4 font-semibold font-sans text-slate-800">{o.month} {year}</td>
                            <td className="py-3 px-4 text-center text-slate-600">{o.totalBeds}</td>
                            <td className="py-3 px-4 text-center text-emerald-600 font-semibold">{o.occupiedBeds}</td>
                            <td className="py-3 px-4 text-center text-slate-500">{vacant}</td>
                            <td className="py-3 px-4 text-right font-bold text-emerald-600">{o.occupancyPct}%</td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </>
          )}

          {/* Guest Turnover Tab */}
          {activeTab === 'turnover' && (
            <>
              <div className="flex justify-between items-center mb-6 border-b border-slate-100 pb-3">
                <h3 className="font-heading text-base font-semibold text-slate-900">Guest Turnover</h3>
                <div className="flex gap-4 text-xs font-semibold">
                  <span className="flex items-center gap-1.5 text-cyan-600">
                    <span className="w-2.5 h-2.5 rounded-full bg-cyan-400"></span>
                    Check-Ins (Total: {turnover.reduce((s, t) => s + Number(t.checkIns||0), 0)})
                  </span>
                  <span className="flex items-center gap-1.5 text-rose-600">
                    <span className="w-2.5 h-2.5 rounded-full bg-rose-400"></span>
                    Check-Outs (Total: {turnover.reduce((s, t) => s + Number(t.checkOuts||0), 0)})
                  </span>
                </div>
              </div>
              <LineChart 
                data={turnover} 
                valueKey="checkIns" 
                valueKey2="checkOuts" 
                labelKey="month" 
                color="#22d3ee" 
                color2="#ef4444" 
                prefix="" 
              />
            </>
          )}

          {/* Payments Tab */}
          {activeTab === 'payments' && payments && (
            <div>
              <h3 className="font-heading text-base font-semibold text-slate-900 mb-4">Payment Summary — {year}</h3>
              <div className="grid-4">
                {[
                  { label:'Total Collected', value:`₹${Number(payments.totalRevenue||0).toLocaleString('en-IN')}`, icon: IndianRupee, iconColor: 'text-blue-500' },
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

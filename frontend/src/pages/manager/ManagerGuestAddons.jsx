import React, { useEffect, useState } from 'react';
import AppLayout from '../../components/AppLayout';
import { managerApi } from '../../api';
import { useSystemConfig } from '../../context/SystemConfigContext';
import { 
  ChefHat, 
  Egg, 
  Shirt, 
  Leaf, 
  Utensils, 
  Search, 
  User, 
  Check, 
  Loader2,
  Coffee,
  Sun,
  Moon,
  Plus,
  Minus,
  Calendar,
  ChevronLeft,
  ChevronRight
} from 'lucide-react';

const TODAY = new Date().toISOString().slice(0, 10);
const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June', 
  'July', 'August', 'September', 'October', 'November', 'December'
];

function DailyRosterCell({ log, day }) {
  if (!log) return (
    <div className="flex items-center justify-center gap-0.5 text-slate-200">
      <span>•</span><span>•</span><span>•</span>
    </div>
  );

  const bOpt = log.breakfast;
  const lOpt = log.lunch;
  const dOpt = log.dinner;
  const hasAddons = (log.omelettes || 0) > 0 || (log.boiledEggs || 0) > 0 || (log.laundry || 0) > 0;

  let positionClass = "left-1/2 -translate-x-1/2";
  if (day !== undefined) {
    if (day <= 5) {
      positionClass = "left-0 translate-x-0";
    } else if (day >= 27) {
      positionClass = "right-0 left-auto translate-x-0";
    }
  }

  return (
    <div className="flex flex-col items-center justify-center gap-0.5 relative group py-1">
      <div className="flex items-center justify-center gap-1">
        <span className={bOpt ? "text-amber-500 font-bold text-sm leading-none" : "text-slate-200 leading-none"} title="Breakfast">●</span>
        <span className={lOpt ? "text-emerald-500 font-bold text-sm leading-none" : "text-slate-200 leading-none"} title="Lunch">●</span>
        <span className={dOpt ? "text-blue-500 font-bold text-sm leading-none" : "text-slate-200 leading-none"} title="Dinner">●</span>
      </div>
      {hasAddons && (
        <span className="absolute bottom-0 text-[7px] font-bold text-indigo-500 leading-none scale-75">*</span>
      )}
      
      {/* Tooltip on hover */}
      <div className={`absolute bottom-full mb-1 hidden group-hover:block bg-slate-900/95 backdrop-blur-sm text-white text-[9px] rounded-lg px-2.5 py-1.5 z-30 whitespace-nowrap border border-slate-700/50 shadow-lg shadow-slate-900/20 pointer-events-none ${positionClass}`}>
        <div>B: {bOpt ? 'Yes' : 'No'} | L: {lOpt ? 'Yes' : 'No'} | D: {dOpt ? 'Yes' : 'No'}</div>
        {hasAddons && (
          <div className="mt-0.5 border-t border-slate-700 pt-0.5 font-semibold text-indigo-300">
            {log.omelettes > 0 && `Omelettes: ${log.omelettes} `}
            {log.boiledEggs > 0 && `Boiled Eggs: ${log.boiledEggs} `}
            {log.laundry > 0 && `Washing Machine: ${log.laundry} `}
          </div>
        )}
      </div>
    </div>
  );
}

export default function ManagerGuestAddons() {
  const { config } = useSystemConfig();
  const [activeTab, setActiveTab] = useState('daily'); // 'daily' or 'monthly'
  const [date, setDate] = useState(TODAY);
  
  // Roster Month/Year Selector
  const currentYear = new Date().getFullYear();
  const currentMonth = new Date().getMonth() + 1;
  const [month, setMonth] = useState(currentMonth);
  const [year, setYear] = useState(currentYear);

  const [guests, setGuests] = useState([]);
  const [logs, setLogs] = useState({}); // { guestId: { omeletteCount, boiledEggCount, washingMachineCount, isVeg, breakfastOpted, lunchOpted, dinnerOpted } }
  const [monthlyData, setMonthlyData] = useState([]);
  const [saving, setSaving] = useState({});
  const [saved, setSaved] = useState({});
  const [loading, setLoading] = useState(true);
  const [loadingMonthly, setLoadingMonthly] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [expandedGuestId, setExpandedGuestId] = useState(null);

  // Daily logs fetch
  useEffect(() => {
    if (activeTab !== 'daily') return;
    setLoading(true);
    Promise.all([
      managerApi.getGuests(),
      managerApi.getGuestsByDate(date),
    ]).then(([gRes, logRes]) => {
      setGuests(gRes.data || []);
      const logMap = {};
      (logRes.data || []).forEach(item => {
        logMap[item.guestId] = {
          omeletteCount:       item.omeletteCount       ?? 0,
          boiledEggCount:      item.boiledEggCount      ?? 0,
          washingMachineCount: item.washingMachineCount ?? 0,
          isVeg:               item.isVeg               ?? true,
          breakfastOpted:      item.breakfastOpted      ?? false,
          lunchOpted:          item.lunchOpted          ?? false,
          dinnerOpted:         item.dinnerOpted         ?? false,
        };
      });
      setLogs(logMap);
    }).catch(console.error)
    .finally(() => setLoading(false));
  }, [date, activeTab]);

  // Monthly logs fetch
  useEffect(() => {
    if (activeTab !== 'monthly') return;
    setLoadingMonthly(true);
    managerApi.getMonthlyMeals(month, year)
      .then(res => {
        setMonthlyData(res.data || []);
      })
      .catch(console.error)
      .finally(() => setLoadingMonthly(false));
  }, [month, year, activeTab]);

  const getLog = (guestId) => logs[guestId] || { 
    omeletteCount: 0, 
    boiledEggCount: 0, 
    washingMachineCount: 0, 
    isVeg: true,
    breakfastOpted: false,
    lunchOpted: false,
    dinnerOpted: false
  };

  const handleFieldChange = async (guestId, field, value) => {
    // 1. Update the local state logs immediately for responsive UX
    const updatedLog = { ...getLog(guestId), [field]: value };
    setLogs(prev => ({ ...prev, [guestId]: updatedLog }));

    // 2. Perform background auto-save
    setSaving(s => ({ ...s, [guestId]: true }));
    try {
      await managerApi.updateGuestLog(guestId, date, updatedLog);
      setSaved(s => ({ ...s, [guestId]: true }));
      setTimeout(() => setSaved(s => ({ ...s, [guestId]: false })), 1500);

      // Refresh in background silently to sync logs
      managerApi.getGuestsByDate(date).then(logRes => {
        const logMap = {};
        (logRes.data || []).forEach(item => {
          logMap[item.guestId] = {
            omeletteCount:       item.omeletteCount       ?? 0,
            boiledEggCount:      item.boiledEggCount      ?? 0,
            washingMachineCount: item.washingMachineCount ?? 0,
            isVeg:               item.isVeg               ?? true,
            breakfastOpted:      item.breakfastOpted      ?? false,
            lunchOpted:          item.lunchOpted          ?? false,
            dinnerOpted:         item.dinnerOpted         ?? false,
          };
        });
        setLogs(logMap);
      }).catch(console.error);
    } catch (err) {
      alert('Save failed: ' + (err.response?.data?.error || err.message));
    } finally {
      setSaving(s => ({ ...s, [guestId]: false }));
    }
  };

  const filteredGuests = guests.filter(g => 
    g.fullName?.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const filteredMonthly = monthlyData.filter(g =>
    g.guestName?.toLowerCase().includes(searchQuery.toLowerCase())
  );

  // Month navigation helpers
  const handlePrevMonth = () => {
    if (month === 1) {
      setMonth(12);
      setYear(y => y - 1);
    } else {
      setMonth(m => m - 1);
    }
  };

  const handleNextMonth = () => {
    if (month === 12) {
      setMonth(1);
      setYear(y => y + 1);
    } else {
      setMonth(m => m + 1);
    }
  };

  const daysInMonth = new Date(year, month, 0).getDate();

  return (
    <AppLayout>
      <div className="page-header">
        <div>
          <h1 className="page-title flex items-center gap-2">
            <ChefHat className="w-6 h-6 text-primary" strokeWidth={1.5}/>
            <span>Meal &amp; Add-on Tracker</span>
          </h1>
          <p className="page-subtitle">Record and view meal opt-ins, eggs, omelettes, and washing machine services</p>
        </div>

        <div className="flex items-center gap-3">
          <div className="tabs-container" style={{ margin: 0 }}>
            <button 
              onClick={() => setActiveTab('daily')} 
              className={`tab-btn ${activeTab === 'daily' ? 'active' : ''}`}
            >
              Daily Tracker
            </button>
            <button 
              onClick={() => setActiveTab('monthly')} 
              className={`tab-btn ${activeTab === 'monthly' ? 'active' : ''}`}
            >
              Monthly Roster
            </button>
          </div>

          {activeTab === 'daily' && (
            <input 
              type="date" 
              value={date} 
              onChange={e => setDate(e.target.value)}
              className="form-input" 
              style={{ width: 'auto', padding: '0.4rem 0.75rem' }} 
            />
          )}
        </div>
      </div>

      {/* Stats Summary Panel (Compact & Horizontal) */}
      {activeTab === 'daily' && (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-4 mb-6">
          {[
            { icon: ChefHat, label:'Omelettes', val: Object.values(logs).reduce((s,l) => s+(l.omeletteCount||0), 0), bg: 'bg-indigo-50', color: 'text-indigo-600' },
            { icon: Egg, label:'Boiled Eggs', val: Object.values(logs).reduce((s,l) => s+(l.boiledEggCount||0), 0), bg: 'bg-amber-50', color: 'text-amber-600' },
            { icon: Shirt, label:'Washing Machine', val: Object.values(logs).reduce((s,l) => s+(l.washingMachineCount||0), 0), bg: 'bg-blue-50', color: 'text-blue-600' },
            { icon: Leaf, label:'Veg Guests', val: Object.values(logs).filter(l => l.isVeg).length, bg: 'bg-emerald-50', color: 'text-emerald-600' },
            { icon: Utensils, label:'Non-Veg', val: Object.values(logs).filter(l => !l.isVeg).length, bg: 'bg-rose-50', color: 'text-rose-600' },
          ].map(s => {
            const Icon = s.icon;
            return (
              <div key={s.label} className="bg-white rounded-xl border border-slate-200 p-3 flex items-center gap-3 shadow-sm hover:shadow-md hover:border-primary/20 transition-all duration-200">
                <div className={`p-2 rounded-lg ${s.bg}`}>
                  <Icon className={`w-4 h-4 ${s.color}`} />
                </div>
                <div>
                  <div className="text-[9px] font-bold text-slate-400 uppercase tracking-wider">{s.label}</div>
                  <div className="text-base font-extrabold text-slate-900 mt-0.5">{s.val}</div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Tabs Views */}
      {activeTab === 'daily' ? (
        <>
          {/* Guest Search bar */}
          <div className="card mb-6" style={{ padding: '1rem' }}>
            <div className="flex items-center gap-2 max-w-md bg-white">
              <Search className="w-4 h-4 text-slate-400" strokeWidth={1.5}/>
              <input 
                type="text" 
                placeholder="Search guest by name..." 
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
                className="form-input"
              />
            </div>
          </div>

          {loading ? (
            <div className="card text-center py-12 text-slate-400">
              <Loader2 className="w-8 h-8 animate-spin mx-auto text-slate-300 mb-2" strokeWidth={1.5}/>
              <span>Loading daily logs...</span>
            </div>
          ) : guests.length === 0 ? (
            <div className="card text-center py-12 text-slate-400">
              No active guests found. Check in guests to manage logs.
            </div>
          ) : (
            <div className="table-wrap">
              <table className="table-compact w-full border-collapse">
                <thead>
                  <tr className="border-b border-slate-200 bg-slate-50/50">
                    <th className="py-3 px-4 text-left font-semibold text-slate-600 text-xs">Guest &amp; Bed</th>
                    <th className="text-center py-2.5 px-3 min-w-[90px]">
                      <div className="flex flex-col items-center gap-1">
                        <span className="text-slate-500 text-xxs font-semibold uppercase tracking-wider">Breakfast</span>
                        <input 
                          type="checkbox" 
                          disabled 
                          className="h-3 w-3 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500 cursor-not-allowed opacity-50" 
                          title="Bulk actions placeholder"
                        />
                      </div>
                    </th>
                    <th className="text-center py-2.5 px-3 min-w-[90px]">
                      <div className="flex flex-col items-center gap-1">
                        <span className="text-slate-500 text-xxs font-semibold uppercase tracking-wider">Lunch</span>
                        <input 
                          type="checkbox" 
                          disabled 
                          className="h-3 w-3 rounded border-slate-300 text-emerald-600 focus:ring-emerald-500 cursor-not-allowed opacity-50" 
                          title="Bulk actions placeholder"
                        />
                      </div>
                    </th>
                    <th className="text-center py-2.5 px-3 min-w-[90px]">
                      <div className="flex flex-col items-center gap-1">
                        <span className="text-slate-500 text-xxs font-semibold uppercase tracking-wider">Dinner</span>
                        <input 
                          type="checkbox" 
                          disabled 
                          className="h-3 w-3 rounded border-slate-300 text-blue-600 focus:ring-blue-500 cursor-not-allowed opacity-50" 
                          title="Bulk actions placeholder"
                        />
                      </div>
                    </th>
                    <th className="py-3 px-3 text-left font-semibold text-slate-600 text-xs">Omelette (₹{config?.pricing?.omelette ?? 18})</th>
                    <th className="py-3 px-3 text-left font-semibold text-slate-600 text-xs">Boiled Egg (₹{config?.pricing?.boiledEgg ?? 18})</th>
                    <th className="py-3 px-3 text-left font-semibold text-slate-600 text-xs">Washing Machine (₹{config?.pricing?.washingMachine ?? 50})</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredGuests.map(g => {
                    const log = getLog(g.id);
                    const isSaving = saving[g.id];
                    const isSaved = saved[g.id];

                    return (
                      <tr key={g.id} className="hover:bg-slate-50/80 transition-colors border-b border-slate-100">
                        <td className="py-2.5 px-4">
                          <div className="flex items-center gap-2.5">
                            <div className="w-7 h-7 rounded-full bg-slate-100 border border-slate-200 flex items-center justify-center text-slate-600 font-semibold text-xs flex-shrink-0">
                              {g.fullName?.charAt(0).toUpperCase()}
                            </div>
                            <div>
                              <div className="font-semibold text-slate-900 text-xs flex items-center gap-1.5 leading-none">
                                {log.isVeg ? (
                                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" title="Veg" />
                                ) : (
                                  <span className="w-1.5 h-1.5 rounded-full bg-rose-500" title="Non-Veg" />
                                )}
                                <span>{g.fullName}</span>
                                {isSaving && <Loader2 className="w-3 h-3 animate-spin text-slate-400" strokeWidth={1.5} />}
                                {isSaved && <Check className="w-3 h-3 text-emerald-500" strokeWidth={1.5} />}
                              </div>
                              <div className="text-[10px] text-slate-400 font-semibold mt-1">Bed: {g.bedLabel ?? '—'}</div>
                            </div>
                          </div>
                        </td>
                        <td className="text-center">
                          <label className="toggle scale-75">
                            <input
                              type="checkbox"
                              checked={!!log.breakfastOpted}
                              onChange={e => handleFieldChange(g.id, 'breakfastOpted', e.target.checked)}
                            />
                            <span className="toggle-slider" />
                          </label>
                        </td>
                        <td className="text-center">
                          <label className="toggle scale-75">
                            <input
                              type="checkbox"
                              checked={!!log.lunchOpted}
                              onChange={e => handleFieldChange(g.id, 'lunchOpted', e.target.checked)}
                            />
                            <span className="toggle-slider" />
                          </label>
                        </td>
                        <td className="text-center">
                          <label className="toggle scale-75">
                            <input
                              type="checkbox"
                              checked={!!log.dinnerOpted}
                              onChange={e => handleFieldChange(g.id, 'dinnerOpted', e.target.checked)}
                            />
                            <span className="toggle-slider" />
                          </label>
                        </td>
                        <td>
                          <div className="flex items-center gap-1.5 py-1">
                            <button
                              type="button"
                              onClick={() => handleFieldChange(g.id, 'omeletteCount', Math.max(0, log.omeletteCount - 1))}
                              className="w-6 h-6 border border-slate-200 hover:border-slate-300 text-slate-400 hover:text-slate-600 rounded-md flex items-center justify-center hover:bg-slate-100 active:bg-slate-200 transition-colors"
                            >
                              <Minus size={16} strokeWidth={1.5} />
                            </button>
                            <span className="w-6 text-center font-semibold text-xs text-slate-700">{log.omeletteCount}</span>
                            <button
                              type="button"
                              onClick={() => handleFieldChange(g.id, 'omeletteCount', log.omeletteCount + 1)}
                              className="w-6 h-6 border border-slate-200 hover:border-slate-300 text-slate-400 hover:text-slate-600 rounded-md flex items-center justify-center hover:bg-slate-100 active:bg-slate-200 transition-colors"
                            >
                              <Plus size={16} strokeWidth={1.5} />
                            </button>
                          </div>
                        </td>
                        <td>
                          <div className="flex items-center gap-1.5 py-1">
                            <button
                              type="button"
                              onClick={() => handleFieldChange(g.id, 'boiledEggCount', Math.max(0, log.boiledEggCount - 1))}
                              className="w-6 h-6 border border-slate-200 hover:border-slate-300 text-slate-400 hover:text-slate-600 rounded-md flex items-center justify-center hover:bg-slate-100 active:bg-slate-200 transition-colors"
                            >
                              <Minus size={16} strokeWidth={1.5} />
                            </button>
                            <span className="w-6 text-center font-semibold text-xs text-slate-700">{log.boiledEggCount}</span>
                            <button
                              type="button"
                              onClick={() => handleFieldChange(g.id, 'boiledEggCount', log.boiledEggCount + 1)}
                              className="w-6 h-6 border border-slate-200 hover:border-slate-300 text-slate-400 hover:text-slate-600 rounded-md flex items-center justify-center hover:bg-slate-100 active:bg-slate-200 transition-colors"
                            >
                              <Plus size={16} strokeWidth={1.5} />
                            </button>
                          </div>
                        </td>
                        <td>
                          <div className="flex items-center gap-1.5 py-1">
                            <button
                              type="button"
                              onClick={() => handleFieldChange(g.id, 'washingMachineCount', Math.max(0, log.washingMachineCount - 1))}
                              className="w-6 h-6 border border-slate-200 hover:border-slate-300 text-slate-400 hover:text-slate-600 rounded-md flex items-center justify-center hover:bg-slate-100 active:bg-slate-200 transition-colors"
                            >
                              <Minus size={16} strokeWidth={1.5} />
                            </button>
                            <span className="w-6 text-center font-semibold text-xs text-slate-700">{log.washingMachineCount}</span>
                            <button
                              type="button"
                              onClick={() => handleFieldChange(g.id, 'washingMachineCount', log.washingMachineCount + 1)}
                              className="w-6 h-6 border border-slate-200 hover:border-slate-300 text-slate-400 hover:text-slate-600 rounded-md flex items-center justify-center hover:bg-slate-100 active:bg-slate-200 transition-colors"
                            >
                              <Plus size={16} strokeWidth={1.5} />
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </>
      ) : (
        <>
          {/* Monthly Roster Header controls */}
          <div className="card mb-6" style={{ padding: '1rem' }}>
            <div className="flex flex-wrap gap-4 items-center justify-between">
              {/* Search Bar */}
              <div className="flex items-center gap-2 max-w-xs flex-1 bg-white">
                <Search className="w-4 h-4 text-slate-400" strokeWidth={1.5}/>
                <input 
                  type="text" 
                  placeholder="Filter by name..." 
                  value={searchQuery}
                  onChange={e => setSearchQuery(e.target.value)}
                  className="form-input"
                  style={{ padding: '0.35rem 0.65rem' }}
                />
              </div>

              {/* Month Selector */}
              <div className="flex items-center gap-2 bg-slate-100 p-1 rounded-lg">
                <button onClick={handlePrevMonth} className="p-1 rounded hover:bg-white text-slate-600 transition-colors">
                  <ChevronLeft className="w-4 h-4" strokeWidth={1.5}/>
                </button>
                <span className="text-xs font-bold text-slate-700 min-w-[120px] text-center">
                  {MONTHS[month - 1]} {year}
                </span>
                <button onClick={handleNextMonth} className="p-1 rounded hover:bg-white text-slate-600 transition-colors">
                  <ChevronRight className="w-4 h-4" strokeWidth={1.5}/>
                </button>
              </div>

              {/* Roster Legend */}
              <div className="flex gap-4 items-center text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                <div className="flex items-center gap-1">
                  <span className="text-amber-500">●</span> Breakfast
                </div>
                <div className="flex items-center gap-1">
                  <span className="text-emerald-500">●</span> Lunch
                </div>
                <div className="flex items-center gap-1">
                  <span className="text-blue-500">●</span> Dinner
                </div>
                <div className="flex items-center gap-1">
                  <span className="text-indigo-500 font-extrabold">*</span> Add-ons Opted
                </div>
              </div>
            </div>
          </div>

          {loadingMonthly ? (
            <div className="card text-center py-12 text-slate-400">
              <Loader2 className="w-8 h-8 animate-spin mx-auto text-slate-300 mb-2" strokeWidth={1.5}/>
              <span>Loading monthly roster...</span>
            </div>
          ) : monthlyData.length === 0 ? (
            <div className="card text-center py-12 text-slate-400">
              No roster records found.
            </div>
          ) : (
            <div className="table-wrap">
              <table className="min-w-full table-compact">
                <thead>
                  <tr className="bg-slate-50/50">
                    <th className="py-2.5 px-3 text-left font-semibold text-slate-500 uppercase tracking-wider text-xs">Guest Name</th>
                    <th className="text-center py-2.5 font-semibold text-slate-500 uppercase tracking-wider text-xs">Bed</th>
                    <th className="text-center font-semibold text-indigo-600 bg-indigo-50/30 py-2.5 text-xs" title="Breakfast count">Breakfast (B)</th>
                    <th className="text-center font-semibold text-emerald-600 bg-emerald-50/30 py-2.5 text-xs" title="Lunch count">Lunch (L)</th>
                    <th className="text-center font-semibold text-blue-600 bg-blue-50/30 py-2.5 text-xs" title="Dinner count">Dinner (D)</th>
                    <th className="text-center py-2.5 font-semibold text-slate-500 uppercase tracking-wider text-xs">Action</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredMonthly.map(row => {
                    let bCount = 0, lCount = 0, dCount = 0;
                    Object.values(row.days || {}).forEach(dayLog => {
                      if (dayLog.breakfast) bCount++;
                      if (dayLog.lunch) lCount++;
                      if (dayLog.dinner) dCount++;
                    });

                    const isExpanded = expandedGuestId === row.guestId;

                    return (
                      <React.Fragment key={row.guestId}>
                        <tr 
                          className={`hover:bg-slate-50/80 cursor-pointer transition-colors ${isExpanded ? 'bg-indigo-50/20' : ''}`}
                          onClick={() => setExpandedGuestId(isExpanded ? null : row.guestId)}
                        >
                          <td className="py-3 px-3 font-semibold text-slate-900 text-xs">
                            <div className="flex items-center gap-2">
                              <div className="w-6 h-6 rounded-full bg-slate-100 border border-slate-200 flex items-center justify-center text-slate-600 font-bold text-xxs">
                                {row.guestName?.charAt(0).toUpperCase()}
                              </div>
                              <span>{row.guestName}</span>
                            </div>
                          </td>
                          <td className="text-slate-600 font-semibold text-center text-xs">{row.bedLabel || '—'}</td>
                          <td className="text-center font-bold text-indigo-600 bg-indigo-50/5 text-xs">{bCount}</td>
                          <td className="text-center font-bold text-emerald-600 bg-emerald-50/5 text-xs">{lCount}</td>
                          <td className="text-center font-bold text-blue-600 bg-blue-50/5 text-xs">{dCount}</td>
                          <td className="text-center py-2.5">
                            <button
                              type="button"
                              className={`btn text-[10px] py-1 px-2.5 ${isExpanded ? 'btn-secondary' : 'btn-primary'}`}
                              onClick={(e) => {
                                e.stopPropagation();
                                setExpandedGuestId(isExpanded ? null : row.guestId);
                              }}
                            >
                              {isExpanded ? 'Hide Days' : 'View Days'}
                            </button>
                          </td>
                        </tr>
                        {isExpanded && (
                          <tr>
                            <td colSpan={6} className="bg-slate-50/60 p-4 border-b border-slate-200">
                              <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-sm">
                                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-3 border-b border-slate-100 pb-2">
                                  <h4 className="text-xs font-semibold text-slate-900 flex items-center gap-1.5 font-heading">
                                    <Calendar className="w-3.5 h-3.5 text-primary" strokeWidth={1.5}/>
                                    <span>Daily Opt-in Breakdown for {row.guestName}</span>
                                  </h4>
                                  <div className="text-[10px] text-slate-400 font-semibold uppercase tracking-wider">
                                    {MONTHS[month - 1]} {year}
                                  </div>
                                </div>
                                <div className="overflow-x-auto pb-1">
                                  <div className="flex gap-2 min-w-max px-12 pt-14 pb-3">
                                    {Array.from({ length: daysInMonth }, (_, i) => {
                                      const dayNum = i + 1;
                                      const dayStr = `${year}-${String(month).padStart(2, '0')}-${String(dayNum).padStart(2, '0')}`;
                                      const dayLog = row.days?.[dayStr];

                                      return (
                                        <div 
                                          key={dayNum} 
                                          className="flex flex-col items-center border border-slate-100 rounded-lg p-2 bg-slate-50/50 min-w-[50px] shadow-sm hover:border-indigo-200 transition-colors"
                                        >
                                          <span className="text-[9px] font-bold text-slate-400 mb-1">{dayNum}</span>
                                          <div className="h-6 flex items-center justify-center">
                                            <DailyRosterCell log={dayLog} day={dayNum} />
                                          </div>
                                        </div>
                                      );
                                    })}
                                  </div>
                                </div>
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
          )}
        </>
      )}
    </AppLayout>
  );
}

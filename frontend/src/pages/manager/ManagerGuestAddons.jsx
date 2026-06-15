import React, { useEffect, useState, useRef } from 'react';
import AppLayout from '../../components/AppLayout';
import { managerApi } from '../../api';
import { useSystemConfig } from '../../context/SystemConfigContext';
import { useAuth } from '../../context/AuthContext';
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
  ChevronRight,
  ChevronDown,
  ChevronUp
} from 'lucide-react';

const TODAY = new Date().toISOString().slice(0, 10);
const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June', 
  'July', 'August', 'September', 'October', 'November', 'December'
];

function DailyRosterCell({ log, day, offerOmelette = true, offerBoiledEgg = true }) {
  if (!log) return (
    <div className="flex items-center justify-center gap-0.5 text-slate-200">
      <span>•</span><span>•</span><span>•</span>
    </div>
  );

  const bOpt = log.breakfast;
  const lOpt = log.lunch;
  const dOpt = log.dinner;
  const bDisabled = log.breakfastDisabled;
  const lDisabled = log.lunchDisabled;
  const dDisabled = log.dinnerDisabled;

  const hasAddons = (offerOmelette && (log.omelettes || 0) > 0) || 
                    (offerBoiledEgg && (log.boiledEggs || 0) > 0) || 
                    ((log.laundry || 0) > 0);

  let positionClass = "left-1/2 -translate-x-1/2";
  if (day !== undefined) {
    if (day <= 5) {
      positionClass = "left-0 translate-x-0";
    } else if (day >= 27) {
      positionClass = "right-0 left-auto translate-x-0";
    }
  }

  const bClass = bDisabled ? "text-transparent leading-none pointer-events-none" : (bOpt ? "text-amber-500 font-bold text-sm leading-none" : "text-slate-200 leading-none");
  const lClass = lDisabled ? "text-transparent leading-none pointer-events-none" : (lOpt ? "text-emerald-500 font-bold text-sm leading-none" : "text-slate-200 leading-none");
  const dClass = dDisabled ? "text-transparent leading-none pointer-events-none" : (dOpt ? "text-blue-500 font-bold text-sm leading-none" : "text-slate-200 leading-none");

  const bText = bDisabled ? '—' : (bOpt ? 'Yes' : 'No');
  const lText = lDisabled ? '—' : (lOpt ? 'Yes' : 'No');
  const dText = dDisabled ? '—' : (dOpt ? 'Yes' : 'No');

  return (
    <div className="flex flex-col items-center justify-center gap-0.5 relative group py-1">
      <div className="flex items-center justify-center gap-1">
        <span className={bClass} title={bDisabled ? "" : "Breakfast"}>●</span>
        <span className={lClass} title={lDisabled ? "" : "Lunch"}>●</span>
        <span className={dClass} title={dDisabled ? "" : "Dinner"}>●</span>
      </div>
      {hasAddons && (
        <span className="absolute bottom-0 text-[7px] font-bold text-indigo-500 leading-none scale-75">*</span>
      )}
      
      {/* Tooltip on hover */}
      <div className={`absolute bottom-full mb-1 hidden group-hover:block bg-slate-900/95 backdrop-blur-sm text-white text-[9px] rounded-lg px-2.5 py-1.5 z-30 whitespace-nowrap border border-slate-700/50 shadow-lg shadow-slate-900/20 pointer-events-none ${positionClass}`}>
        <div>B: {bText} | L: {lText} | D: {dText}</div>
        {hasAddons && (
          <div className="mt-0.5 border-t border-slate-700 pt-0.5 font-semibold text-indigo-300">
            {offerOmelette && log.omelettes > 0 && `Omelettes: ${log.omelettes} `}
            {offerBoiledEgg && log.boiledEggs > 0 && `Boiled Eggs: ${log.boiledEggs} `}
            {log.laundry > 0 && `Washing Machine: ${log.laundry} `}
          </div>
        )}
      </div>
    </div>
  );
}

export default function ManagerGuestAddons() {
  const { config } = useSystemConfig();
  const { user } = useAuth();
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

  // Building configuration for add-on toggles
  const [offerOmelette, setOfferOmelette] = useState(true);
  const [offerBoiledEgg, setOfferBoiledEgg] = useState(true);

  useEffect(() => {
    managerApi.getPricing()
      .then(res => {
        setOfferOmelette(res.data.offerOmelette ?? true);
        setOfferBoiledEgg(res.data.offerBoiledEgg ?? true);
      })
      .catch(err => {
        console.error("Failed to fetch pricing config for add-on toggles", err);
      });
  }, []);

  // Hybrid Save states
  const [hasUnsavedBulkChanges, setHasUnsavedBulkChanges] = useState(false);
  const [bulkDirtyIds, setBulkDirtyIds] = useState(new Set());
  const [savingGlobal, setSavingGlobal] = useState(false);
  const [toast, setToast] = useState('');

  // Per-guest debounce timers reference
  const debounceTimers = useRef({});

  const showToast = (msg) => {
    setToast(msg);
    setTimeout(() => setToast(''), 2500);
  };

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
          breakfastDisabled:   item.breakfastDisabled   ?? false,
          lunchDisabled:       item.lunchDisabled       ?? false,
          dinnerDisabled:      item.dinnerDisabled      ?? false,
        };
      });
      setLogs(logMap);
      setBulkDirtyIds(new Set());
      setHasUnsavedBulkChanges(false);
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

  // Clear timers and resets dirty changes when date or tab changes
  useEffect(() => {
    Object.values(debounceTimers.current).forEach(clearTimeout);
    debounceTimers.current = {};
    
    setBulkDirtyIds(new Set());
    setHasUnsavedBulkChanges(false);
  }, [date, activeTab]);

  // Clean up timers on unmount
  useEffect(() => {
    return () => {
      Object.values(debounceTimers.current).forEach(clearTimeout);
    };
  }, []);

  const getLog = (guestId) => logs[guestId] || { 
    omeletteCount: 0, 
    boiledEggCount: 0, 
    washingMachineCount: 0, 
    isVeg: true,
    breakfastOpted: false,
    lunchOpted: false,
    dinnerOpted: false,
    breakfastDisabled: false,
    lunchDisabled: false,
    dinnerDisabled: false,
  };

  // 1. Single Edit Handler: Debounced Silent Auto-Save
  const handleFieldChange = (guestId, field, value) => {
    const updatedLog = { ...getLog(guestId), [field]: value };

    // Update local state instantly for snappy UI
    setLogs(prev => ({ 
      ...prev, 
      [guestId]: updatedLog 
    }));

    // State Isolation: If this guest was part of pending bulk edits, remove them
    if (bulkDirtyIds.has(guestId)) {
      setBulkDirtyIds(prev => {
        const next = new Set(prev);
        next.delete(guestId);
        if (next.size === 0) {
          setHasUnsavedBulkChanges(false);
        }
        return next;
      });
    }

    // Trigger debounced save API call
    debouncedSave(guestId, updatedLog);
  };

  const debouncedSave = (guestId, updatedLog) => {
    if (debounceTimers.current[guestId]) {
      clearTimeout(debounceTimers.current[guestId]);
    }

    debounceTimers.current[guestId] = setTimeout(async () => {
      delete debounceTimers.current[guestId];
      setSaving(s => ({ ...s, [guestId]: true }));
      try {
        await managerApi.updateGuestLog(guestId, date, updatedLog);
        setSaved(s => ({ ...s, [guestId]: true }));
        showToast('Auto-saved changes');
        setTimeout(() => setSaved(s => ({ ...s, [guestId]: false })), 1500);
      } catch (err) {
        console.error(err);
        showToast('Auto-save failed');
      } finally {
        setSaving(s => ({ ...s, [guestId]: false }));
      }
    }, 800);
  };

  // 2. Bulk Edit Handler: Stage changes & render float banner
  const handleBulkToggle = (field, checked) => {
    const nextLogs = { ...logs };
    const nextBulkDirty = new Set(bulkDirtyIds);

    filteredGuests.forEach(g => {
      // Clear any pending single debounced saves for these guests to isolate state
      if (debounceTimers.current[g.id]) {
        clearTimeout(debounceTimers.current[g.id]);
        delete debounceTimers.current[g.id];
      }

      nextLogs[g.id] = { ...getLog(g.id), [field]: checked };
      nextBulkDirty.add(g.id);
    });

    setLogs(nextLogs);
    setBulkDirtyIds(nextBulkDirty);
    setHasUnsavedBulkChanges(true);
  };

  const handleSaveChanges = async () => {
    if (bulkDirtyIds.size === 0) return;
    setSavingGlobal(true);
    const idsToSave = Array.from(bulkDirtyIds);

    const newSaving = { ...saving };
    idsToSave.forEach(id => { newSaving[id] = true; });
    setSaving(newSaving);

    try {
      await Promise.all(idsToSave.map(async (guestId) => {
        await managerApi.updateGuestLog(guestId, date, getLog(guestId));
        setSaved(s => ({ ...s, [guestId]: true }));
        setTimeout(() => setSaved(s => ({ ...s, [guestId]: false })), 1500);
      }));

      showToast('Bulk changes saved successfully');
      setBulkDirtyIds(new Set());
      setHasUnsavedBulkChanges(false);

      // Refresh in background to sync state
      const logRes = await managerApi.getGuestsByDate(date);
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
          breakfastDisabled:   item.breakfastDisabled   ?? false,
          lunchDisabled:       item.lunchDisabled       ?? false,
          dinnerDisabled:      item.dinnerDisabled      ?? false,
        };
      });
      setLogs(logMap);
    } catch (err) {
      alert('Save changes failed: ' + (err.response?.data?.error || err.message));
    } finally {
      const clearSaving = { ...saving };
      idsToSave.forEach(id => { delete clearSaving[id]; });
      setSaving(clearSaving);
      setSavingGlobal(false);
    }
  };

  const handleDiscardChanges = () => {
    setBulkDirtyIds(new Set());
    setHasUnsavedBulkChanges(false);
    setLoading(true);
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
          breakfastDisabled:   item.breakfastDisabled   ?? false,
          lunchDisabled:       item.lunchDisabled       ?? false,
          dinnerDisabled:      item.dinnerDisabled      ?? false,
        };
      });
      setLogs(logMap);
    }).catch(console.error)
    .finally(() => setLoading(false));
  };

  const filteredGuests = guests.filter(g => 
    g.fullName?.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const filteredMonthly = monthlyData.filter(g =>
    g.guestName?.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const allBreakfastChecked = filteredGuests.length > 0 && filteredGuests.every(g => !!getLog(g.id).breakfastOpted);
  const allLunchChecked = filteredGuests.length > 0 && filteredGuests.every(g => !!getLog(g.id).lunchOpted);
  const allDinnerChecked = filteredGuests.length > 0 && filteredGuests.every(g => !!getLog(g.id).dinnerOpted);

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
      {/* Toast popup */}
      {toast && (
        <div className="fixed top-4 right-4 z-[9999] bg-slate-900 text-white px-4 py-2.5 rounded-xl shadow-lg text-sm animate-fade-in-up flex items-center gap-2">
          <Check className="w-4 h-4 text-emerald-400" strokeWidth={1.5} /> {toast}
        </div>
      )}

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
            offerOmelette && { icon: ChefHat, label:'Omelettes', val: Object.values(logs).reduce((s,l) => s+(l.omeletteCount||0), 0), bg: 'bg-indigo-50', color: 'text-indigo-600' },
            offerBoiledEgg && { icon: Egg, label:'Boiled Eggs', val: Object.values(logs).reduce((s,l) => s+(l.boiledEggCount||0), 0), bg: 'bg-amber-50', color: 'text-amber-600' },
            { icon: Shirt, label:'Washing Machine', val: Object.values(logs).reduce((s,l) => s+(l.washingMachineCount||0), 0), bg: 'bg-blue-50', color: 'text-blue-600' },
            { icon: Leaf, label:'Veg Guests', val: Object.values(logs).filter(l => l.isVeg).length, bg: 'bg-emerald-50', color: 'text-emerald-600' },
            { icon: Utensils, label:'Non-Veg', val: Object.values(logs).filter(l => !l.isVeg).length, bg: 'bg-rose-50', color: 'text-rose-600' },
          ].filter(Boolean).map(s => {
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
                      <div className="flex flex-col items-center gap-1.5 justify-center">
                        <span className="text-slate-500 text-xxs font-bold uppercase tracking-wider">Breakfast</span>
                        <label className="toggle scale-[0.65] origin-center cursor-pointer">
                          <input 
                            type="checkbox" 
                            checked={allBreakfastChecked} 
                            onChange={(e) => handleBulkToggle('breakfastOpted', e.target.checked)}
                          />
                          <span className="toggle-slider" />
                        </label>
                      </div>
                    </th>
                    <th className="text-center py-2.5 px-3 min-w-[90px]">
                      <div className="flex flex-col items-center gap-1.5 justify-center">
                        <span className="text-slate-500 text-xxs font-bold uppercase tracking-wider">Lunch</span>
                        <label className="toggle scale-[0.65] origin-center cursor-pointer">
                          <input 
                            type="checkbox" 
                            checked={allLunchChecked} 
                            onChange={(e) => handleBulkToggle('lunchOpted', e.target.checked)}
                          />
                          <span className="toggle-slider" />
                        </label>
                      </div>
                    </th>
                    <th className="text-center py-2.5 px-3 min-w-[90px]">
                      <div className="flex flex-col items-center gap-1.5 justify-center">
                        <span className="text-slate-500 text-xxs font-bold uppercase tracking-wider">Dinner</span>
                        <label className="toggle scale-[0.65] origin-center cursor-pointer">
                          <input 
                            type="checkbox" 
                            checked={allDinnerChecked} 
                            onChange={(e) => handleBulkToggle('dinnerOpted', e.target.checked)}
                          />
                          <span className="toggle-slider" />
                        </label>
                      </div>
                    </th>
                    {offerOmelette && (
                      <th className="py-3 px-3 text-left font-semibold text-slate-600 text-xs">Omelette (₹{config?.pricing?.omelette ?? 18})</th>
                    )}
                    {offerBoiledEgg && (
                      <th className="py-3 px-3 text-left font-semibold text-slate-600 text-xs">Boiled Egg (₹{config?.pricing?.boiledEgg ?? 18})</th>
                    )}
                    <th className="py-3 px-3 text-left font-semibold text-slate-600 text-xs">Washing Machine (₹{config?.pricing?.washingMachine ?? 50})</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredGuests.map(g => {
                    const log = getLog(g.id);
                    const isSaving = saving[g.id];
                    const isSaved = saved[g.id];
                    const isBulkDirty = bulkDirtyIds.has(g.id);

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
                                {isBulkDirty && <span className="text-[10px] text-amber-500 font-bold animate-pulse" title="Pending bulk save">*</span>}
                                {isSaving && <Loader2 className="w-3 h-3 animate-spin text-slate-400" strokeWidth={1.5} />}
                                {isSaved && <Check className="w-3 h-3 text-emerald-500" strokeWidth={1.5} />}
                              </div>
                              <div className="text-[10px] text-slate-400 font-semibold mt-1">Bed: {g.bedLabel ?? '—'}</div>
                            </div>
                          </div>
                        </td>
                        <td className="text-center">
                          {(log.breakfastDisabled && !(user?.role === 'PG_MANAGER' || user?.role === 'PG_OWNER')) ? (
                            <span className="text-slate-400 font-semibold text-xs">—</span>
                          ) : (
                            <label className={`toggle scale-75 ${log.breakfastDisabled ? 'ring-2 ring-amber-400 rounded-full' : ''}`} title={log.breakfastDisabled ? "Manager Override Active" : ""}>
                              <input
                                type="checkbox"
                                checked={!!log.breakfastOpted}
                                onChange={e => handleFieldChange(g.id, 'breakfastOpted', e.target.checked)}
                              />
                              <span className="toggle-slider" />
                            </label>
                          )}
                        </td>
                        <td className="text-center">
                          {(log.lunchDisabled && !(user?.role === 'PG_MANAGER' || user?.role === 'PG_OWNER')) ? (
                            <span className="text-slate-400 font-semibold text-xs">—</span>
                          ) : (
                            <label className={`toggle scale-75 ${log.lunchDisabled ? 'ring-2 ring-amber-400 rounded-full' : ''}`} title={log.lunchDisabled ? "Manager Override Active" : ""}>
                              <input
                                type="checkbox"
                                checked={!!log.lunchOpted}
                                onChange={e => handleFieldChange(g.id, 'lunchOpted', e.target.checked)}
                              />
                              <span className="toggle-slider" />
                            </label>
                          )}
                        </td>
                        <td className="text-center">
                          {(log.dinnerDisabled && !(user?.role === 'PG_MANAGER' || user?.role === 'PG_OWNER')) ? (
                            <span className="text-slate-400 font-semibold text-xs">—</span>
                          ) : (
                            <label className={`toggle scale-75 ${log.dinnerDisabled ? 'ring-2 ring-amber-400 rounded-full' : ''}`} title={log.dinnerDisabled ? "Manager Override Active" : ""}>
                              <input
                                type="checkbox"
                                checked={!!log.dinnerOpted}
                                onChange={e => handleFieldChange(g.id, 'dinnerOpted', e.target.checked)}
                              />
                              <span className="toggle-slider" />
                            </label>
                          )}
                        </td>
                        {offerOmelette && (
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
                        )}
                        {offerBoiledEgg && (
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
                        )}
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
                <thead className="sticky top-0 bg-white z-10 border-b border-slate-200">
                  <tr className="bg-white">
                    <th className="py-2.5 px-3 text-left font-semibold text-slate-500 uppercase tracking-wider text-xs bg-white border-b border-slate-200">Guest Name</th>
                    <th className="text-center py-2.5 font-semibold text-slate-500 uppercase tracking-wider text-xs bg-white border-b border-slate-200">Bed</th>
                    <th className="text-center bg-white py-2.5 border-b border-slate-200" title="Breakfast count">
                      <div className="flex items-center justify-center gap-1 uppercase text-xs font-semibold text-slate-500">
                        BREAKFAST (B) <span className="font-normal text-slate-400 text-[10px]">/ {daysInMonth}</span>
                      </div>
                    </th>
                    <th className="text-center bg-white py-2.5 border-b border-slate-200" title="Lunch count">
                      <div className="flex items-center justify-center gap-1 uppercase text-xs font-semibold text-slate-500">
                        LUNCH (L) <span className="font-normal text-slate-400 text-[10px]">/ {daysInMonth}</span>
                      </div>
                    </th>
                    <th className="text-center bg-white py-2.5 border-b border-slate-200" title="Dinner count">
                      <div className="flex items-center justify-center gap-1 uppercase text-xs font-semibold text-slate-500">
                        DINNER (D) <span className="font-normal text-slate-400 text-[10px]">/ {daysInMonth}</span>
                      </div>
                    </th>
                    <th className="text-right py-2.5 px-3 font-semibold text-slate-500 uppercase tracking-wider text-xs bg-white border-b border-slate-200"></th>
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
                          className={`hover:bg-slate-50/80 cursor-pointer transition-colors ${isExpanded ? 'bg-slate-50' : ''}`}
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
                          <td className="text-center font-medium text-slate-700 text-xs">{bCount}</td>
                          <td className="text-center font-medium text-slate-700 text-xs">{lCount}</td>
                          <td className="text-center font-medium text-slate-700 text-xs">{dCount}</td>
                          <td className="py-2.5 px-3">
                            <div className="flex justify-end">
                              {isExpanded ? (
                                <ChevronUp size={20} className="text-slate-400 transition-transform duration-200" strokeWidth={1.5} />
                              ) : (
                                <ChevronDown size={20} className="text-slate-400 transition-transform duration-200" strokeWidth={1.5} />
                              )}
                            </div>
                          </td>
                        </tr>
                        {isExpanded && (
                          <tr className="bg-slate-50/60 border-t border-b border-slate-100">
                            <td colSpan={6} className="p-4">
                              <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-sm">
                                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4 border-b border-slate-100 pb-2">
                                  <h4 className="text-xs font-semibold text-slate-800 flex items-center gap-1.5 font-heading">
                                    <Calendar className="w-3.5 h-3.5 text-primary" strokeWidth={1.5}/>
                                    <span>Daily Opt-in Breakdown for {row.guestName}</span>
                                  </h4>
                                  <div className="text-[10px] text-slate-400 font-semibold uppercase tracking-wider">
                                    {MONTHS[month - 1]} {year}
                                  </div>
                                </div>
                                <div className="grid grid-cols-7 sm:grid-cols-10 gap-2">
                                  {Array.from({ length: daysInMonth }, (_, i) => {
                                    const dayNum = i + 1;
                                    const dayStr = `${year}-${String(month).padStart(2, '0')}-${String(dayNum).padStart(2, '0')}`;
                                    const dayLog = row.days?.[dayStr];
                                    const isOutside = (row.checkInDate && dayStr < row.checkInDate) || 
                                                      (row.actualCheckOutDate && dayStr > row.actualCheckOutDate);

                                    return (
                                      <div 
                                        key={dayNum} 
                                        className={`flex flex-col items-center border rounded-lg p-1.5 transition-colors ${
                                          isOutside 
                                            ? 'bg-slate-100 border-slate-200 opacity-40 cursor-not-allowed select-none' 
                                            : 'bg-slate-50/30 border-slate-200 hover:border-slate-300'
                                        }`}
                                      >
                                        <span className="text-[9px] font-medium text-slate-400 mb-0.5">{dayNum}</span>
                                        <div className="h-5 flex items-center justify-center">
                                          <DailyRosterCell log={isOutside ? null : dayLog} day={dayNum} offerOmelette={offerOmelette} offerBoiledEgg={offerBoiledEgg} />
                                        </div>
                                      </div>
                                    );
                                  })}
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

      {/* Floating Bulk Action Confirmation Bar */}
      {hasUnsavedBulkChanges && bulkDirtyIds.size > 0 && (
        <div className="fixed bottom-6 left-1/2 -translate-x-1/2 bg-slate-900 text-white px-6 py-4 rounded-2xl shadow-2xl flex items-center gap-6 z-50 border border-slate-800 animate-fade-in-up">
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-amber-500 animate-pulse" />
            <span className="text-xs font-semibold text-slate-300">
              Unsaved bulk changes for {bulkDirtyIds.size} guest{bulkDirtyIds.size > 1 ? 's' : ''}
            </span>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={handleDiscardChanges}
              className="text-xs font-bold text-slate-400 hover:text-white px-3 py-1.5 rounded-lg hover:bg-slate-800 transition-colors"
              disabled={savingGlobal}
            >
              Discard
            </button>
            <button
              onClick={handleSaveChanges}
              className="bg-indigo-600 hover:bg-indigo-700 text-white font-bold text-xs px-4 py-1.5 rounded-lg shadow transition-colors flex items-center gap-1.5"
              disabled={savingGlobal}
            >
              {savingGlobal ? (
                <>
                  <Loader2 className="w-3.5 h-3.5 animate-spin" strokeWidth={1.5} />
                  <span>Saving...</span>
                </>
              ) : (
                <>
                  <Check className="w-3.5 h-3.5" strokeWidth={1.5} />
                  <span>Save Changes</span>
                </>
              )}
            </button>
          </div>
        </div>
      )}
    </AppLayout>
  );
}

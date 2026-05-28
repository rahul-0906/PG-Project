import React, { useEffect, useState } from 'react';
import AppLayout from '../../components/AppLayout';
import { guestApi } from '../../api';
import { 
  CalendarDays, 
  Check, 
  CheckCircle2, 
  Utensils, 
  User, 
  Lock, 
  Coffee, 
  Sun, 
  Moon, 
  Info,
  Loader2,
  ChevronLeft,
  ChevronRight
} from 'lucide-react';

const MEAL_CONFIG = [
  { key: 'breakfastOpted', label: 'Breakfast', icon: Coffee, lockKey: 'breakfast', cutoffText: '10:00 PM previous day' },
  { key: 'lunchOpted',     label: 'Lunch',     icon: Sun,    lockKey: 'lunch',     cutoffText: '10:00 PM previous day' },
  { key: 'dinnerOpted',    label: 'Dinner',    icon: Moon,   lockKey: 'dinner',    cutoffText: '02:00 PM same day' },
];

function isLocked(mealKey, date) {
  const now = new Date();
  const logDate = new Date(date);
  if (mealKey === 'dinner') {
    const lock = new Date(logDate); lock.setHours(14, 0, 0, 0);
    return now > lock;
  } else {
    const prevNight = new Date(logDate);
    prevNight.setDate(prevNight.getDate() - 1);
    prevNight.setHours(22, 0, 0, 0);
    return now > prevNight;
  }
}

export default function DailyLog() {
  const [selectedMonth, setSelectedMonth] = useState(() => new Date().toISOString().slice(0, 7));
  const [selectedDate, setSelectedDate] = useState(() => new Date().toISOString().slice(0, 10));
  
  const [log, setLog] = useState({
    breakfastOpted: false, lunchOpted: false, dinnerOpted: false,
    omeletteCount: 0, boiledEggCount: 0, washingMachineCount: 0, isVeg: true
  });
  const [monthlyLogs, setMonthlyLogs] = useState({});
  const [config, setConfig] = useState(null);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [loadingLogs, setLoadingLogs] = useState(false);

  const fetchMonthlyLogs = (monthStr) => {
    setLoadingLogs(true);
    guestApi.getMonthlyLogs(monthStr).then(res => {
      const map = {};
      (res.data || []).forEach(item => {
        map[item.logDate] = item;
      });
      setMonthlyLogs(map);
    }).catch(console.error)
    .finally(() => setLoadingLogs(false));
  };

  useEffect(() => {
    guestApi.getConfig().then(r => setConfig(r.data)).catch(() => {});
  }, []);

  useEffect(() => {
    fetchMonthlyLogs(selectedMonth);
  }, [selectedMonth]);

  useEffect(() => {
    guestApi.getLog(selectedDate).then(r => { 
      if (r.data?.logDate) {
        setLog(r.data);
      } else {
        setLog({
          logDate: selectedDate,
          breakfastOpted: false,
          lunchOpted: false,
          dinnerOpted: false,
          omeletteCount: 0,
          boiledEggCount: 0,
          washingMachineCount: 0,
          isVeg: true
        });
      }
    }).catch(() => {
      setLog({
        logDate: selectedDate,
        breakfastOpted: false,
        lunchOpted: false,
        dinnerOpted: false,
        omeletteCount: 0,
        boiledEggCount: 0,
        washingMachineCount: 0,
        isVeg: true
      });
    });
  }, [selectedDate]);

  const toggle = (key) => {
    if (isLocked(key.replace('Opted', ''), selectedDate)) return;
    setLog(l => ({ ...l, [key]: !l[key] }));
  };

  const save = async () => {
    setSaving(true);
    try {
      await guestApi.updateLog(selectedDate, {
        breakfastOpted: log.breakfastOpted,
        lunchOpted:     log.lunchOpted,
        dinnerOpted:    log.dinnerOpted,
      });
      setSaved(true);
      setTimeout(() => setSaved(false), 2000);
      fetchMonthlyLogs(selectedMonth);
    } catch (err) {
      alert('Save failed: ' + (err.response?.data?.error || err.message));
    } finally {
      setSaving(false);
    }
  };

  const meals = MEAL_CONFIG.filter(m => {
    if (!config) return true;
    if (m.lockKey === 'breakfast' && !config.breakfastEnabled) return false;
    if (m.lockKey === 'lunch'     && !config.lunchEnabled)     return false;
    if (m.lockKey === 'dinner'    && !config.dinnerEnabled)    return false;
    return true;
  });

  const foodIncluded = config?.foodIncludedInRent;

  // Calendar calculations
  const [year, month] = selectedMonth.split('-').map(Number);
  const firstDayIndex = new Date(year, month - 1, 1).getDay();
  const totalDays = new Date(year, month, 0).getDate();
  
  const blanks = Array.from({ length: firstDayIndex }, (_, i) => null);
  const days = Array.from({ length: totalDays }, (_, i) => i + 1);
  const allSlots = [...blanks, ...days];

  const handlePrevMonth = () => {
    const prevDate = new Date(year, month - 2, 1);
    const newMonthStr = prevDate.toISOString().slice(0, 7);
    setSelectedMonth(newMonthStr);
    setSelectedDate(`${newMonthStr}-01`);
  };

  const handleNextMonth = () => {
    const nextDate = new Date(year, month, 1);
    const newMonthStr = nextDate.toISOString().slice(0, 7);
    setSelectedMonth(newMonthStr);
    setSelectedDate(`${newMonthStr}-01`);
  };

  const dayNames = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  const monthNames = [
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December'
  ];

  return (
    <AppLayout>
      {/* Top Banner Header */}
      <div className="bg-white p-4 sm:p-5 rounded-2xl border border-slate-200/80 shadow-sm mb-6 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div className="flex items-center gap-3">
          <div className="p-2.5 bg-primary/10 text-primary rounded-xl">
            <CalendarDays className="w-5 h-5" />
          </div>
          <div>
            <h1 className="text-lg font-black text-slate-900 tracking-tight sm:text-xl">
              Meal Planner
            </h1>
            <p className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider mt-0.5">
              Opt-in / opt-out of daily meals & view add-on services
            </p>
          </div>
        </div>
      </div>

      {/* Main Layout Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Section: Calendar */}
        <div className="lg:col-span-2 flex flex-col gap-6">
          <div className="card shadow-sm border border-slate-200/80 p-4 bg-white rounded-2xl max-w-[500px] mx-auto lg:mx-0">
            {/* Calendar Control Header */}
            <div className="flex items-center justify-between mb-4 border-b border-slate-100 pb-3">
              <h3 className="text-xs font-bold text-slate-800 flex items-center gap-2">
                <span>Monthly Calendar</span>
                {loadingLogs && <Loader2 className="w-3 h-3 animate-spin text-slate-400" />}
              </h3>

              <div className="flex items-center gap-1.5 bg-slate-100 p-0.5 rounded-lg">
                <button 
                  type="button" 
                  onClick={handlePrevMonth} 
                  className="p-1 rounded hover:bg-white text-slate-600 transition-colors"
                >
                  <ChevronLeft className="w-3.5 h-3.5" />
                </button>
                <span className="text-[11px] font-bold text-slate-700 min-w-[100px] text-center select-none">
                  {monthNames[month - 1]} {year}
                </span>
                <button 
                  type="button" 
                  onClick={handleNextMonth} 
                  className="p-1 rounded hover:bg-white text-slate-600 transition-colors"
                >
                  <ChevronRight className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>

            {/* Calendar Grid */}
            <div className="grid grid-cols-7 gap-1">
              {dayNames.map(d => (
                <div key={d} className="text-center text-[9px] font-bold text-slate-400 uppercase tracking-wider py-1">
                  {d}
                </div>
              ))}
              {allSlots.map((dayNum, idx) => {
                if (dayNum === null) {
                  return <div key={`blank-${idx}`} className="aspect-square" />;
                }
                
                const dayStr = `${year}-${String(month).padStart(2, '0')}-${String(dayNum).padStart(2, '0')}`;
                const isSelected = dayStr === selectedDate;
                const isToday = dayStr === new Date().toISOString().slice(0, 10);
                const dayLog = monthlyLogs[dayStr];
                const hasAddons = (dayLog?.omeletteCount || 0) > 0 || (dayLog?.boiledEggCount || 0) > 0 || (dayLog?.washingMachineCount || 0) > 0;

                return (
                  <button
                    key={dayNum}
                    type="button"
                    onClick={() => setSelectedDate(dayStr)}
                    className={`aspect-square p-1 rounded-lg border flex flex-col justify-between items-center transition-all duration-200 ${
                      isSelected 
                        ? 'bg-indigo-50/70 border-primary text-primary font-bold shadow-sm ring-1 ring-primary' 
                        : isToday
                          ? 'bg-slate-50 border-slate-300 text-slate-900 font-bold'
                          : 'bg-white border-slate-100 text-slate-700 hover:border-slate-300'
                    }`}
                  >
                    <span className="text-[10px] font-semibold">{dayNum}</span>
                    
                    {/* Add-on Indicators in middle */}
                    <div className="flex gap-0.5 justify-center h-3 items-center">
                      {dayLog?.omeletteCount > 0 && <span className="text-[8px] leading-none" title={`Omelettes: ${dayLog.omeletteCount}`}>🍳</span>}
                      {dayLog?.boiledEggCount > 0 && <span className="text-[8px] leading-none" title={`Boiled Eggs: ${dayLog.boiledEggCount}`}>🥚</span>}
                      {dayLog?.washingMachineCount > 0 && <span className="text-[8px] leading-none" title={`Washing Machine: ${dayLog.washingMachineCount}`}>🧺</span>}
                    </div>

                    {/* B/L/D Dot Indicators at bottom */}
                    <div className="flex gap-0.5 justify-center pb-0.5">
                      {dayLog?.breakfastOpted && <span className="w-1 h-1 rounded-full bg-amber-500" title="Breakfast" />}
                      {dayLog?.lunchOpted && <span className="w-1 h-1 rounded-full bg-emerald-500" title="Lunch" />}
                      {dayLog?.dinnerOpted && <span className="w-1 h-1 rounded-full bg-blue-500" title="Dinner" />}
                    </div>
                  </button>
                );
              })}
            </div>

            {/* Calendar Legend */}
            <div className="flex flex-wrap gap-4 items-center justify-center text-[10px] font-bold text-slate-400 uppercase tracking-wider mt-6 pt-4 border-t border-slate-100">
              <div className="flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-amber-500" /> Breakfast (B)
              </div>
              <div className="flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" /> Lunch (L)
              </div>
              <div className="flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-blue-500" /> Dinner (D)
              </div>
              <div className="flex items-center gap-1">
                <span>🍳</span> Omelette
              </div>
              <div className="flex items-center gap-1">
                <span>🥚</span> Boiled Egg
              </div>
              <div className="flex items-center gap-1">
                <span>🧺</span> Washing Machine
              </div>
            </div>
          </div>
        </div>

        {/* Right Section: Details Panel */}
        <div className="lg:col-span-1 flex flex-col gap-6">
          <div className="card shadow-sm border border-slate-200/80 p-5 bg-white rounded-2xl flex flex-col justify-between min-h-[400px]">
            <div>
              {/* Selected Date Header */}
              <div className="border-b border-slate-100 pb-3 mb-4 flex items-center justify-between">
                <div>
                  <h3 className="text-slate-800 font-bold text-sm">Meal &amp; Add-on Details</h3>
                  <p className="text-[10px] font-bold text-indigo-600 mt-1 uppercase tracking-wider">
                    {formatSelectedDate(selectedDate)}
                  </p>
                </div>
                {selectedDate === new Date().toISOString().slice(0, 10) && (
                  <span className="bg-indigo-50 text-indigo-700 border border-indigo-100 px-2 py-0.5 rounded-full text-[9px] font-extrabold uppercase tracking-wide">
                    Today
                  </span>
                )}
              </div>

              {/* Food Info Message */}
              {foodIncluded ? (
                <div className="bg-emerald-50/50 border border-emerald-200 rounded-xl p-3.5 mb-6 flex items-center gap-3">
                  <CheckCircle2 className="w-5 h-5 text-emerald-500 flex-shrink-0" />
                  <div>
                    <div className="font-bold text-emerald-800 text-xs">Food Included in Rent</div>
                    <div className="text-[10px] text-emerald-600 mt-0.5 leading-relaxed font-medium">
                      Meals are bundled with your rent. No extra billing will apply.
                    </div>
                  </div>
                </div>
              ) : (
                <div className="bg-amber-50/60 border border-amber-100/60 p-3 rounded-xl mb-4 flex items-start gap-2.5">
                  <Info className="w-3.5 h-3.5 text-amber-600 mt-0.5 flex-shrink-0" />
                  <p className="text-amber-800/90 text-[10px] leading-relaxed font-semibold">
                    Set preferences below &amp; click save. Locked meals cannot be updated after their cutoff times.
                  </p>
                </div>
              )}

              {/* Meal Toggles */}
              <div className="space-y-3.5 mb-6">
                {meals.length === 0 ? (
                  <div className="text-slate-400 text-center py-6 text-xs">
                    No meals are currently enabled.
                  </div>
                ) : (
                  meals.map(meal => {
                    const locked = isLocked(meal.lockKey, selectedDate);
                    const Icon = meal.icon;
                    const isOpted = !!log[meal.key];

                    return (
                      <div 
                        key={meal.key}
                        className={`p-3.5 rounded-xl border flex items-center justify-between transition-all duration-200 ${
                          locked 
                            ? 'bg-slate-50 border-slate-200/50 opacity-80' 
                            : isOpted 
                              ? 'bg-emerald-50/30 border-emerald-500 shadow-sm shadow-emerald-50' 
                              : 'bg-white border-slate-200/80 hover:border-slate-300'
                        }`}
                      >
                        <div className="flex items-center gap-3">
                          <div className={`p-2 rounded-lg ${
                            locked 
                              ? 'bg-slate-200/60 text-slate-500' 
                              : isOpted 
                                ? 'bg-emerald-100/80 text-emerald-600' 
                                : 'bg-slate-100/80 text-slate-500'
                          }`}>
                            <Icon className="w-4 h-4" />
                          </div>
                          <div>
                            <h4 className="font-bold text-slate-800 text-xs">{meal.label}</h4>
                            <span className="text-[9px] text-slate-400 mt-0.5 block font-bold uppercase tracking-wide">
                              Cutoff: {meal.cutoffText}
                            </span>
                          </div>
                        </div>

                        <div className="flex items-center gap-2">
                          {locked ? (
                            <span className="bg-slate-200 border border-slate-300/40 text-slate-600 px-2 py-0.5 rounded-full text-[9px] font-bold flex items-center gap-1">
                              <Lock className="w-2.5 h-2.5" />
                              <span>Locked</span>
                            </span>
                          ) : (
                            <label className="toggle scale-75">
                              <input 
                                type="checkbox" 
                                checked={isOpted} 
                                onChange={() => toggle(meal.key)} 
                                disabled={locked} 
                              />
                              <span className="toggle-slider" />
                            </label>
                          )}
                        </div>
                      </div>
                    );
                  })
                )}
              </div>

              {/* Add-ons Log Grid */}
              <div className="border-t border-slate-100 pt-4">
                <h4 className="text-xs font-bold text-slate-800 mb-3 flex items-center gap-1.5">
                  <Utensils className="w-3.5 h-3.5 text-indigo-500" />
                  <span>Add-ons Opted (Logged by Manager)</span>
                </h4>
                <div className="grid grid-cols-2 gap-3">
                  <div className="bg-slate-50 border border-slate-100 p-2.5 rounded-xl flex flex-col justify-between">
                    <span className="text-[8px] font-bold text-slate-400 uppercase tracking-wider">Diet Type</span>
                    <span className={`text-[11px] font-bold mt-1 ${log.isVeg ? 'text-emerald-600' : 'text-rose-600'}`}>
                      {log.isVeg ? '🟢 Veg' : '🔴 Non-Veg'}
                    </span>
                  </div>
                  <div className="bg-slate-50 border border-slate-100 p-2.5 rounded-xl flex flex-col justify-between">
                    <span className="text-[8px] font-bold text-slate-400 uppercase tracking-wider">Omelettes</span>
                    <span className="text-[11px] font-black text-slate-700 mt-1">
                      {log.omeletteCount || 0} pcs
                    </span>
                  </div>
                  <div className="bg-slate-50 border border-slate-100 p-2.5 rounded-xl flex flex-col justify-between">
                    <span className="text-[8px] font-bold text-slate-400 uppercase tracking-wider">Boiled Eggs</span>
                    <span className="text-[11px] font-black text-slate-700 mt-1">
                      {log.boiledEggCount || 0} pcs
                    </span>
                  </div>
                  <div className="bg-slate-50 border border-slate-100 p-2.5 rounded-xl flex flex-col justify-between">
                    <span className="text-[8px] font-bold text-slate-400 uppercase tracking-wider">Washing Machine</span>
                    <span className="text-[11px] font-black text-slate-700 mt-1">
                      {log.washingMachineCount || 0} use
                    </span>
                  </div>
                </div>
              </div>
            </div>

            {/* Save Button */}
            <div className="pt-6 border-t border-slate-100 mt-6">
              <button 
                id="btn-save-log" 
                className="w-full btn btn-primary py-2.5 rounded-lg text-xs font-bold shadow-md shadow-blue-200 transition-all duration-200 hover:-translate-y-0.5 flex items-center justify-center gap-2" 
                onClick={save} 
                disabled={saving}
              >
                {saving ? (
                  <>
                    <Loader2 className="w-3.5 h-3.5 animate-spin" />
                    <span>Saving...</span>
                  </>
                ) : saved ? (
                  <>
                    <Check className="w-3.5 h-3.5" />
                    <span>Saved Successfully!</span>
                  </>
                ) : (
                  <>
                    <Check className="w-3.5 h-3.5" />
                    <span>Save Meal Plan</span>
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      </div>
    </AppLayout>
  );
}

function formatSelectedDate(dateStr) {
  if (!dateStr) return '';
  return new Date(dateStr).toLocaleDateString('en-IN', { 
    weekday: 'long', 
    year: 'numeric', 
    month: 'long', 
    day: 'numeric' 
  });
}

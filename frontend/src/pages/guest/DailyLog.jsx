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
  Loader2
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
  const [today] = useState(new Date().toISOString().slice(0, 10));
  const [log, setLog] = useState({
    breakfastOpted: false, lunchOpted: false, dinnerOpted: false
  });
  const [config, setConfig] = useState(null);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    guestApi.getLog(today).then(r => { if (r.data?.logDate) setLog(r.data); }).catch(() => {});
    guestApi.getConfig().then(r => setConfig(r.data)).catch(() => {});
  }, [today]);

  const toggle = (key) => {
    if (isLocked(key.replace('Opted', ''), today)) return;
    setLog(l => ({ ...l, [key]: !l[key] }));
  };

  const save = async () => {
    setSaving(true);
    await guestApi.updateLog(today, {
      breakfastOpted: log.breakfastOpted,
      lunchOpted:     log.lunchOpted,
      dinnerOpted:    log.dinnerOpted,
    }).catch(() => {});
    setSaving(false); setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  };

  const meals = MEAL_CONFIG.filter(m => {
    if (!config) return true;
    if (m.lockKey === 'breakfast' && !config.breakfastEnabled) return false;
    if (m.lockKey === 'lunch'     && !config.lunchEnabled)     return false;
    if (m.lockKey === 'dinner'    && !config.dinnerEnabled)    return false;
    return true;
  });

  const foodIncluded = config?.foodIncludedInRent;

  return (
    <AppLayout>
      <div className="bg-white p-4 sm:p-5 rounded-2xl border border-slate-200/80 shadow-sm mb-4 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div className="flex items-center gap-3">
          <div className="p-2.5 bg-primary/10 text-primary rounded-xl">
            <CalendarDays className="w-5 h-5" />
          </div>
          <div>
            <h1 className="text-lg font-black text-slate-900 tracking-tight sm:text-xl">
              Meal Planner
            </h1>
            <p className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider mt-0.5">
              {new Date(today).toLocaleDateString('en-IN', { weekday:'long', year:'numeric', month:'long', day:'numeric' })}
            </p>
          </div>
        </div>
        
        <button 
          id="btn-save-log" 
          className="btn btn-primary px-4 py-2 rounded-lg text-sm font-bold shadow-md shadow-blue-200 transition-all duration-200 hover:-translate-y-0.5 hover:shadow-lg hover:shadow-blue-300 flex items-center gap-2" 
          onClick={save} 
          disabled={saving}
        >
          {saving ? (
            <>
              <Loader2 className="w-4 h-4 animate-spin" />
              <span>Saving...</span>
            </>
          ) : saved ? (
            <>
              <Check className="w-4 h-4" />
              <span>Saved!</span>
            </>
          ) : (
            <>
              <Check className="w-4 h-4" />
              <span>Save Meal Plan</span>
            </>
          )}
        </button>
      </div>

      {foodIncluded ? (
        <div className="card border border-emerald-200 bg-emerald-50/50 mb-6">
          <div className="flex items-center gap-3">
            <CheckCircle2 className="w-6 h-6 text-emerald-500 flex-shrink-0" />
            <div>
              <div className="font-semibold text-emerald-800 text-sm">Food Included in Rent</div>
              <div className="text-xs text-emerald-600/90 mt-0.5">
                Meals are included in your monthly rent — no separate billing.
              </div>
            </div>
          </div>
        </div>
      ) : (
        <div className="card mb-6 overflow-hidden border border-slate-200/60 shadow-lg p-6">
          <div className="flex items-center justify-between border-b border-slate-100 pb-4 mb-4">
            <div className="flex items-center gap-2">
              <div className="p-2 bg-indigo-50 text-indigo-600 rounded-lg">
                <Utensils className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-slate-800 font-bold text-base">Meal Selection</h3>
                <p className="text-slate-400 text-xs mt-0.5">Choose your meals for today. Click save above to store changes.</p>
              </div>
            </div>
          </div>
          
          <div className="flex items-start gap-3 bg-amber-50/60 border border-amber-100/60 p-3 rounded-lg mb-6">
            <Info className="w-4 h-4 text-amber-600 mt-0.5 flex-shrink-0" />
            <p className="text-amber-800/90 text-xs leading-relaxed font-medium">
              Important: Opt in or out before the lockout times shown on each card. Locked choices cannot be updated.
            </p>
          </div>

          {meals.length === 0 && (
            <div className="text-slate-400 text-center py-8 text-sm">
              No meals are currently configured for this PG.
            </div>
          )}

          <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
            {meals.map(meal => {
              const locked = isLocked(meal.lockKey, today);
              const Icon = meal.icon;
              const isOpted = !!log[meal.key];
              
              return (
                <div 
                  key={meal.key} 
                  className={`relative p-4 rounded-xl border-2 transition-all duration-200 flex flex-col justify-between min-h-[130px] ${
                    locked 
                      ? 'bg-slate-50/70 border-slate-200/50 opacity-80' 
                      : isOpted 
                        ? 'bg-emerald-50/40 border-emerald-500 shadow-sm shadow-emerald-100' 
                        : 'bg-white border-slate-200/80 hover:border-slate-300'
                  }`}
                >
                  <div className="flex items-start justify-between">
                    <div className="flex items-center gap-3">
                      <div className={`p-2.5 rounded-lg ${
                        locked 
                          ? 'bg-slate-200/60 text-slate-500' 
                          : isOpted 
                            ? 'bg-emerald-100/80 text-emerald-600' 
                            : 'bg-slate-100/80 text-slate-500'
                      }`}>
                        <Icon className="w-5 h-5" />
                      </div>
                      <div>
                        <h4 className="font-bold text-slate-800 text-sm leading-none">{meal.label}</h4>
                        <span className="text-[10px] text-slate-400 mt-2 block font-semibold uppercase tracking-wide">
                          Cutoff: {meal.cutoffText}
                        </span>
                      </div>
                    </div>
                    
                    <div className="flex items-center">
                      {locked ? (
                        <div className="bg-slate-200/80 border border-slate-300/40 text-slate-600 px-2 py-0.5 rounded-full text-[10px] font-bold flex items-center gap-1">
                          <Lock className="w-2.5 h-2.5" />
                          <span>Locked</span>
                        </div>
                      ) : (
                        <label className="toggle">
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
                  
                  <div className="border-t border-slate-100 pt-2.5 mt-3 flex items-center justify-between">
                    <span className="text-xs font-semibold text-slate-400">Status</span>
                    {locked ? (
                      <span className="text-xs font-bold text-slate-400">Closed</span>
                    ) : isOpted ? (
                      <span className="text-xs font-extrabold text-emerald-600 flex items-center gap-1">
                        <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-ping" />
                        <span>Opted In</span>
                      </span>
                    ) : (
                      <span className="text-xs font-bold text-slate-500">Opted Out</span>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Info card about add-ons */}
      <div className="bg-gradient-to-r from-indigo-50 to-blue-50 border border-indigo-100 rounded-2xl p-5 shadow-sm">
        <div className="flex flex-col gap-4">
          <div className="flex items-start gap-4">
            <div className="p-2.5 bg-indigo-500 text-white rounded-xl shadow-md shadow-indigo-100 flex-shrink-0">
              <User className="w-5 h-5" />
            </div>
            <div>
              <h4 className="font-bold text-slate-800 text-sm">Add-ons &amp; Diet Selection (Managed by PG Manager)</h4>
              <p className="text-xs text-slate-600 mt-1 leading-relaxed max-w-2xl">
                Diet preference, omelette, boiled egg, and washing machine usage are recorded daily by your manager and billed under your monthly invoices.
              </p>
            </div>
          </div>
          
          {/* Add-ons Log Grid for selected date */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <div className="bg-white/80 border border-indigo-100/30 p-3 rounded-xl flex flex-col justify-between">
              <span className="text-[9px] font-bold text-slate-400 uppercase tracking-wider">Diet Type</span>
              <span className={`text-xs font-bold mt-1.5 ${log.isVeg ? 'text-emerald-600' : 'text-rose-600'}`}>
                {log.isVeg ? '🟢 Veg' : '🔴 Non-Veg'}
              </span>
            </div>
            <div className="bg-white/80 border border-indigo-100/30 p-3 rounded-xl flex flex-col justify-between">
              <span className="text-[9px] font-bold text-slate-400 uppercase tracking-wider">Omelettes</span>
              <span className="text-xs font-black text-slate-700 mt-1.5">
                {log.omeletteCount || 0} pcs
              </span>
            </div>
            <div className="bg-white/80 border border-indigo-100/30 p-3 rounded-xl flex flex-col justify-between">
              <span className="text-[9px] font-bold text-slate-400 uppercase tracking-wider">Boiled Eggs</span>
              <span className="text-xs font-black text-slate-700 mt-1.5">
                {log.boiledEggCount || 0} pcs
              </span>
            </div>
            <div className="bg-white/80 border border-indigo-100/30 p-3 rounded-xl flex flex-col justify-between">
              <span className="text-[9px] font-bold text-slate-400 uppercase tracking-wider">Washing Machine</span>
              <span className="text-xs font-black text-slate-700 mt-1.5">
                {log.washingMachineCount || 0} use
              </span>
            </div>
          </div>
        </div>
      </div>
    </AppLayout>
  );
}

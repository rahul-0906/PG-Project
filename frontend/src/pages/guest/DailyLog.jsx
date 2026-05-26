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
  { key: 'breakfastOpted', label: 'Breakfast', icon: Coffee, lockKey: 'breakfast' },
  { key: 'lunchOpted',     label: 'Lunch',     icon: Sun,    lockKey: 'lunch' },
  { key: 'dinnerOpted',    label: 'Dinner',    icon: Moon,   lockKey: 'dinner' },
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
      <div className="page-header">
        <div>
          <h1 className="page-title flex items-center gap-2">
            <CalendarDays className="w-6 h-6 text-primary" />
            <span>Daily Log</span>
          </h1>
          <p className="page-subtitle">
            {new Date(today).toLocaleDateString('en-IN', { weekday:'long', year:'numeric', month:'long', day:'numeric' })}
          </p>
        </div>
        <button 
          id="btn-save-log" 
          className="btn btn-primary flex items-center gap-1.5" 
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
              <span>Save</span>
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
        <div className="card mb-6">
          <div className="flex items-center gap-2 mb-4">
            <Utensils className="w-5 h-5 text-slate-400" />
            <h3 className="text-slate-800 font-bold text-sm">Meal Selection</h3>
          </div>
          <div className="flex items-start gap-2 bg-slate-50 border border-slate-100 p-3 rounded-lg mb-4">
            <Info className="w-4 h-4 text-slate-400 mt-0.5 flex-shrink-0" />
            <p className="text-slate-500 text-xs leading-relaxed">
              Opt in/out before the lockout time. Your selection is billed monthly.
            </p>
          </div>
          {meals.length === 0 && (
            <div className="text-slate-400 text-center py-4 text-sm">
              No meals are currently configured for this PG.
            </div>
          )}
          {meals.map(meal => {
            const locked = isLocked(meal.lockKey, today);
            const Icon = meal.icon;
            return (
              <div key={meal.key} className={`meal-card ${log[meal.key] ? 'opted' : ''} ${locked ? 'locked' : ''} mb-3`}>
                <div className="flex items-center gap-2.5">
                  <Icon className={`w-4 h-4 ${log[meal.key] ? 'text-primary' : 'text-slate-400'}`} />
                  <div>
                    <div className="font-semibold text-slate-800 text-sm">{meal.label}</div>
                    {locked && (
                      <div className="flex items-center gap-1 text-slate-400 text-xxs mt-0.5">
                        <Lock className="w-3 h-3" />
                        <span>Selection window closed</span>
                      </div>
                    )}
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  {locked && <span className="meal-locked-badge">Locked</span>}
                  <label className="toggle">
                    <input type="checkbox" checked={!!log[meal.key]} onChange={() => toggle(meal.key)} disabled={locked} />
                    <span className="toggle-slider" />
                  </label>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Info card about add-ons */}
      <div className="card border border-indigo-100 bg-indigo-50/30">
        <div className="flex items-center gap-3">
          <User className="w-6 h-6 text-indigo-500 flex-shrink-0" />
          <div>
            <div className="font-semibold text-indigo-800 text-sm">Add-ons Managed by Your PG Manager</div>
            <div className="text-xs text-indigo-600/90 mt-0.5 leading-relaxed">
              Egg, omelette, washing machine usage, and veg/non-veg preference are recorded by your PG Manager on a daily basis. Contact your manager if any updates are needed.
            </div>
          </div>
        </div>
      </div>
    </AppLayout>
  );
}

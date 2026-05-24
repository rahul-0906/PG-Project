import React, { useEffect, useState } from 'react';
import Sidebar from '../../components/Sidebar';
import { guestApi } from '../../api';

const MEAL_CONFIG = [
  { key: 'breakfastOpted', label: 'Breakfast 🌅', lockKey: 'breakfast' },
  { key: 'lunchOpted',     label: 'Lunch ☀️',     lockKey: 'lunch' },
  { key: 'dinnerOpted',   label: 'Dinner 🌙',    lockKey: 'dinner' },
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
    // Only send meal selections — add-ons are managed by Manager
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
    <div className="layout">
      <Sidebar />
      <div className="main-content fade-in">
        <div className="page-header">
          <div>
            <h1 className="page-title">Daily Log 📋</h1>
            <p className="page-subtitle">
              {new Date(today).toLocaleDateString('en-IN', { weekday:'long', year:'numeric', month:'long', day:'numeric' })}
            </p>
          </div>
          <button id="btn-save-log" className="btn btn-primary" onClick={save} disabled={saving}>
            {saving ? '⏳ Saving...' : saved ? '✅ Saved!' : '💾 Save'}
          </button>
        </div>

        {foodIncluded ? (
          <div className="card" style={{ background:'rgba(16,185,129,0.08)', border:'1px solid rgba(16,185,129,0.3)', marginBottom:'1.5rem' }}>
            <div style={{ display:'flex', alignItems:'center', gap:'0.75rem' }}>
              <span style={{ fontSize:'1.5rem' }}>✅</span>
              <div>
                <div style={{ fontWeight:700, color:'#10b981' }}>Food Included in Rent</div>
                <div style={{ fontSize:'0.85rem', color:'var(--text-muted)' }}>
                  Meals are included in your monthly rent — no separate billing.
                </div>
              </div>
            </div>
          </div>
        ) : (
          <div className="card" style={{ marginBottom:'1.5rem' }}>
            <h3 style={{ marginBottom:'1rem', fontSize:'1rem', fontWeight:700 }}>🍽️ Meal Selection</h3>
            <p style={{ color:'var(--text-muted)', fontSize:'0.82rem', marginBottom:'1.25rem' }}>
              ℹ️ Opt in/out before the lockout time. Your selection is billed monthly.
            </p>
            {meals.length === 0 && (
              <div style={{ color:'var(--text-muted)', textAlign:'center', padding:'1rem' }}>
                No meals are currently configured for this PG.
              </div>
            )}
            {meals.map(meal => {
              const locked = isLocked(meal.lockKey, today);
              return (
                <div key={meal.key} className={`meal-card ${log[meal.key] ? 'opted' : ''} ${locked ? 'locked' : ''}`} style={{ marginBottom:'0.75rem' }}>
                  <div>
                    <div style={{ fontWeight:600 }}>{meal.label}</div>
                    {locked && <div style={{ fontSize:'0.75rem', color:'var(--text-muted)', marginTop:'0.2rem' }}>🔒 Selection window closed</div>}
                  </div>
                  <div style={{ display:'flex', alignItems:'center', gap:'0.75rem' }}>
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
        <div className="card" style={{ background:'rgba(99,102,241,0.05)', border:'1px solid rgba(99,102,241,0.2)' }}>
          <div style={{ display:'flex', alignItems:'center', gap:'0.75rem' }}>
            <span style={{ fontSize:'1.5rem' }}>👔</span>
            <div>
              <div style={{ fontWeight:700, color:'var(--accent)' }}>Add-ons Managed by Your PG Manager</div>
              <div style={{ fontSize:'0.82rem', color:'var(--text-muted)', marginTop:'0.25rem' }}>
                Egg, omelette, washing machine usage, and veg/non-veg preference are recorded by your PG Manager on a daily basis. Contact your manager if any updates are needed.
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

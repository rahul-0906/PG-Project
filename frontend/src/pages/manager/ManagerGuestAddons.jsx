import React, { useEffect, useState } from 'react';
import Sidebar from '../../components/Sidebar';
import { managerApi } from '../../api';

const TODAY = new Date().toISOString().slice(0, 10);

function AddOnCounter({ label, icon, value, onChange, unit = '' }) {
  return (
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      padding: '0.5rem 0', borderBottom: '1px solid var(--border)'
    }}>
      <div>
        <span style={{ fontSize: '1rem' }}>{icon}</span>
        <span style={{ marginLeft: '0.5rem', color: 'var(--text-primary)', fontWeight: 600, fontSize: '0.85rem' }}>{label}</span>
        {unit && <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem', marginLeft: '0.35rem' }}>{unit}</span>}
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
        <button onClick={() => onChange(Math.max(0, value - 1))}
          style={{ width: 32, height: 32, border: '1px solid var(--border)', borderRadius: 6,
            background: 'var(--bg-main)', color: 'var(--text-primary)', cursor: 'pointer', fontSize: '1rem', fontWeight: 700 }}>
          −
        </button>
        <span style={{ minWidth: 28, textAlign: 'center', fontWeight: 800, color: 'var(--accent)', fontSize: '1rem' }}>{value}</span>
        <button onClick={() => onChange(value + 1)}
          style={{ width: 32, height: 32, border: 'none', borderRadius: 6,
            background: 'var(--accent)', color: '#fff', cursor: 'pointer', fontSize: '1rem', fontWeight: 700 }}>
          +
        </button>
      </div>
    </div>
  );
}

function VegToggle({ isVeg, onChange }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0.5rem 0', borderBottom: '1px solid var(--border)' }}>
      <div>
        <span>{isVeg ? '🥗' : '🍖'}</span>
        <span style={{ marginLeft: '0.5rem', color: 'var(--text-primary)', fontWeight: 600, fontSize: '0.85rem' }}>
          {isVeg ? 'Veg' : 'Non-Veg'}
        </span>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.8rem' }}>
        <span style={{ color: isVeg ? '#10b981' : 'var(--text-muted)', fontWeight: 600 }}>Veg</span>
        <label className="toggle">
          <input type="checkbox" checked={!isVeg} onChange={() => onChange(!isVeg)} />
          <span className="toggle-slider" />
        </label>
        <span style={{ color: !isVeg ? '#ef4444' : 'var(--text-muted)', fontWeight: 600 }}>Non-Veg</span>
      </div>
    </div>
  );
}

export default function ManagerGuestAddons() {
  const [date, setDate] = useState(TODAY);
  const [guests, setGuests] = useState([]);
  const [logs, setLogs] = useState({});        // { guestId: { omeletteCount, boiledEggCount, washingMachineCount, isVeg } }
  const [saving, setSaving] = useState({});
  const [saved, setSaved] = useState({});
  const [loading, setLoading] = useState(true);
  const [config, setConfig] = useState(null);

  useEffect(() => {
    setLoading(true);
    Promise.all([
      managerApi.getGuests(),
      managerApi.getGuestsByDate(date),
    ]).then(([gRes, logRes]) => {
      setGuests(gRes.data || []);
      // Build logs map from response
      const logMap = {};
      (logRes.data || []).forEach(item => {
        logMap[item.guestId] = {
          omeletteCount:      item.omeletteCount      ?? 0,
          boiledEggCount:     item.boiledEggCount     ?? 0,
          washingMachineCount: item.washingMachineCount ?? 0,
          isVeg:              item.isVeg              ?? true,
        };
      });
      setLogs(logMap);
    }).catch(console.error)
    .finally(() => setLoading(false));
  }, [date]);

  const getLog = (guestId) => logs[guestId] || { omeletteCount:0, boiledEggCount:0, washingMachineCount:0, isVeg:true };

  const updateField = (guestId, field, value) => {
    setLogs(prev => ({ ...prev, [guestId]: { ...getLog(guestId), [field]: value } }));
  };

  const saveGuest = async (guestId) => {
    setSaving(s => ({ ...s, [guestId]: true }));
    try {
      await managerApi.updateGuestLog(guestId, date, getLog(guestId));
      setSaved(s => ({ ...s, [guestId]: true }));
      setTimeout(() => setSaved(s => ({ ...s, [guestId]: false })), 2000);
    } catch (err) { alert('Save failed: ' + (err.response?.data?.error || err.message)); }
    finally { setSaving(s => ({ ...s, [guestId]: false })); }
  };

  return (
    <div className="layout">
      <Sidebar />
      <div className="main-content fade-in">
        <div className="page-header">
          <div>
            <h1 className="page-title">Daily Add-ons 🥚</h1>
            <p className="page-subtitle">Record egg, omelette, washing machine &amp; veg preference per guest</p>
          </div>
          <input type="date" value={date} onChange={e => setDate(e.target.value)}
            className="form-input" style={{ width:'auto', padding:'0.4rem 0.75rem' }} />
        </div>

        {/* Summary Bar */}
        <div style={{ display:'grid', gridTemplateColumns:'repeat(auto-fit,minmax(130px,1fr))', gap:'0.75rem', marginBottom:'1.5rem' }}>
          {[
            { icon:'🍳', label:'Omelettes', val: Object.values(logs).reduce((s,l) => s+(l.omeletteCount||0), 0) },
            { icon:'🥚', label:'Boiled Eggs', val: Object.values(logs).reduce((s,l) => s+(l.boiledEggCount||0), 0) },
            { icon:'🫧', label:'WM Uses', val: Object.values(logs).reduce((s,l) => s+(l.washingMachineCount||0), 0) },
            { icon:'🥗', label:'Veg Guests', val: Object.values(logs).filter(l => l.isVeg).length },
            { icon:'🍖', label:'Non-Veg', val: Object.values(logs).filter(l => !l.isVeg).length },
          ].map(s => (
            <div key={s.label} className="stat-card" style={{ padding:'0.75rem', textAlign:'center' }}>
              <div style={{ fontSize:'1.25rem' }}>{s.icon}</div>
              <div style={{ fontSize:'1.5rem', fontWeight:900, color:'var(--accent)' }}>{s.val}</div>
              <div style={{ color:'var(--text-muted)', fontSize:'0.7rem' }}>{s.label}</div>
            </div>
          ))}
        </div>

        {loading ? (
          <div className="card" style={{ textAlign:'center', padding:'3rem', color:'var(--text-muted)' }}>
            ⏳ Loading guest list...
          </div>
        ) : guests.length === 0 ? (
          <div className="card" style={{ textAlign:'center', padding:'3rem', color:'var(--text-muted)' }}>
            No active guests found.
          </div>
        ) : (
          <div style={{ display:'grid', gridTemplateColumns:'repeat(auto-fill,minmax(300px,1fr))', gap:'1rem' }}>
            {guests.map(guest => {
              const log = getLog(guest.id);
              const isSaving = saving[guest.id];
              const isSaved  = saved[guest.id];
              return (
                <div key={guest.id} className="card" style={{ padding:'1rem' }}>
                  {/* Guest header */}
                  <div style={{ display:'flex', alignItems:'center', gap:'0.75rem', marginBottom:'1rem', paddingBottom:'0.75rem', borderBottom:'2px solid var(--border)' }}>
                    <div style={{ width:40, height:40, borderRadius:'50%', background:'linear-gradient(135deg,#6366f1,#8b5cf6)',
                      display:'flex', alignItems:'center', justifyContent:'center', color:'#fff', fontWeight:800, fontSize:'1rem', flexShrink:0 }}>
                      {guest.fullName?.charAt(0).toUpperCase()}
                    </div>
                    <div style={{ flex:1, minWidth:0 }}>
                      <div style={{ fontWeight:700, color:'var(--text-primary)', fontSize:'0.9rem', whiteSpace:'nowrap', overflow:'hidden', textOverflow:'ellipsis' }}>{guest.fullName}</div>
                      <div style={{ fontSize:'0.72rem', color:'var(--text-muted)' }}>Bed: {guest.bed?.bedLabel ?? '—'}</div>
                    </div>
                    <button id={`btn-save-addon-${guest.id?.slice(0,8)}`}
                      onClick={() => saveGuest(guest.id)} disabled={isSaving}
                      className="btn btn-primary" style={{ fontSize:'0.75rem', padding:'0.3rem 0.75rem', flexShrink:0 }}>
                      {isSaving ? '⏳' : isSaved ? '✅' : '💾 Save'}
                    </button>
                  </div>

                  {/* Veg/Non-Veg */}
                  <VegToggle isVeg={log.isVeg} onChange={v => updateField(guest.id, 'isVeg', v)} />

                  {/* Counters */}
                  <AddOnCounter label="Omelette" icon="🍳" value={log.omeletteCount}
                    unit="₹18 each" onChange={v => updateField(guest.id, 'omeletteCount', v)} />
                  <AddOnCounter label="Boiled Egg" icon="🥚" value={log.boiledEggCount}
                    unit="₹18 each" onChange={v => updateField(guest.id, 'boiledEggCount', v)} />
                  <AddOnCounter label="Washing Machine" icon="🫧" value={log.washingMachineCount}
                    unit="₹50/use" onChange={v => updateField(guest.id, 'washingMachineCount', v)} />
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}

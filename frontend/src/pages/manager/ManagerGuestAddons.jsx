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
  const [logs, setLogs] = useState({});        // { guestId: { omeletteCount, boiledEggCount, washingMachineCount, isVeg, breakfastOpted, lunchOpted, dinnerOpted } }
  const [saving, setSaving] = useState({});
  const [saved, setSaved] = useState({});
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedGuestId, setSelectedGuestId] = useState('');

  useEffect(() => {
    setLoading(true);
    Promise.all([
      managerApi.getGuests(),
      managerApi.getGuestsByDate(date),
    ]).then(([gRes, logRes]) => {
      const activeGuests = gRes.data || [];
      setGuests(activeGuests);
      
      // Build logs map from response
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
      
      // Default select first matching guest if none selected or if currently selected is not in guest list
      if (activeGuests.length > 0) {
        const stillActive = activeGuests.some(g => g.id === selectedGuestId);
        if (!stillActive) {
          setSelectedGuestId(activeGuests[0].id);
        }
      } else {
        setSelectedGuestId('');
      }
    }).catch(console.error)
    .finally(() => setLoading(false));
  }, [date]);

  const getLog = (guestId) => logs[guestId] || { 
    omeletteCount: 0, 
    boiledEggCount: 0, 
    washingMachineCount: 0, 
    isVeg: true,
    breakfastOpted: false,
    lunchOpted: false,
    dinnerOpted: false
  };

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

  const selectedGuest = guests.find(g => g.id === selectedGuestId);
  const filteredGuestsForSearch = guests.filter(g => 
    g.fullName?.toLowerCase().includes(searchQuery.toLowerCase())
  );

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

        {/* Search Panel */}
        <div className="card" style={{ marginBottom: '1.5rem', maxWidth: '600px' }}>
          <div style={{ display: 'flex', gap: '1rem', alignItems: 'flex-end', flexWrap: 'wrap' }}>
            <div className="form-group" style={{ margin: 0, flex: 1, minWidth: '200px' }}>
              <label className="form-label">🔍 Find Guest (Search by Name)</label>
              <input 
                type="text" 
                placeholder="Type guest name..." 
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
                className="form-input"
              />
            </div>
            <div className="form-group" style={{ margin: 0, flex: 1, minWidth: '200px' }}>
              <label className="form-label">👤 Select Guest</label>
              <select 
                value={selectedGuestId} 
                onChange={e => setSelectedGuestId(e.target.value)}
                className="form-input"
              >
                <option value="">-- Choose Guest --</option>
                {filteredGuestsForSearch.map(g => (
                  <option key={g.id} value={g.id}>
                    {g.fullName} ({g.bed?.bedLabel ?? 'No Bed'})
                  </option>
                ))}
              </select>
            </div>
          </div>
        </div>

        {loading ? (
          <div className="card" style={{ textAlign:'center', padding:'3rem', color:'var(--text-muted)' }}>
            ⏳ Loading guest list...
          </div>
        ) : guests.length === 0 ? (
          <div className="card" style={{ textAlign:'center', padding:'3rem', color:'var(--text-muted)' }}>
            No active guests found.
          </div>
        ) : selectedGuest ? (
          <div style={{ display:'flex', justifyContent:'center' }}>
            {(() => {
              const guest = selectedGuest;
              const log = getLog(guest.id);
              const isSaving = saving[guest.id];
              const isSaved  = saved[guest.id];
              return (
                <div key={guest.id} className="card" style={{ padding:'1.5rem', width: '100%', maxWidth: '500px' }}>
                  {/* Guest header */}
                  <div style={{ display:'flex', alignItems:'center', gap:'0.75rem', marginBottom:'1.25rem', paddingBottom:'1rem', borderBottom:'2px solid var(--border)' }}>
                    <div style={{ width:48, height:48, borderRadius:'50%', background:'linear-gradient(135deg,#6366f1,#8b5cf6)',
                      display:'flex', alignItems:'center', justifyContent:'center', color:'#fff', fontWeight:800, fontSize:'1.2rem', flexShrink:0 }}>
                      {guest.fullName?.charAt(0).toUpperCase()}
                    </div>
                    <div style={{ flex:1, minWidth:0 }}>
                      <div style={{ fontWeight:800, color:'var(--text-primary)', fontSize:'1.05rem', whiteSpace:'nowrap', overflow:'hidden', textOverflow:'ellipsis' }}>{guest.fullName}</div>
                      <div style={{ fontSize:'0.8rem', color:'var(--text-muted)' }}>Bed: {guest.bed?.bedLabel ?? '—'}</div>
                    </div>
                    <button id={`btn-save-addon-${guest.id?.slice(0,8)}`}
                      onClick={() => saveGuest(guest.id)} disabled={isSaving}
                      className="btn btn-primary" style={{ fontSize:'0.85rem', padding:'0.4rem 1.1rem', flexShrink:0 }}>
                      {isSaving ? '⏳ Saving...' : isSaved ? '✅ Saved' : '💾 Save Changes'}
                    </button>
                  </div>

                  {/* Veg/Non-Veg Preference */}
                  <div style={{ marginBottom: '1rem' }}>
                    <VegToggle isVeg={log.isVeg} onChange={v => updateField(guest.id, 'isVeg', v)} />
                  </div>

                  {/* Meal Options based on Preference */}
                  <div style={{ background: 'var(--bg-main)', padding: '1rem', borderRadius: '8px', marginBottom: '1.25rem', border: '1px solid var(--border)' }}>
                    <div style={{ fontSize:'0.75rem', color:'var(--text-muted)', fontWeight:700, textTransform:'uppercase', letterSpacing:'0.05em', marginBottom:'0.75rem' }}>
                      🍽️ Meal Options ({log.isVeg ? 'Veg 🌱' : 'Non-Veg 🍗'})
                    </div>
                    <div style={{ display:'flex', flexDirection:'column', gap:'0.75rem' }}>
                      {[
                        { key: 'breakfastOpted', label: 'Breakfast 🌅' },
                        { key: 'lunchOpted',     label: 'Lunch ☀️' },
                        { key: 'dinnerOpted',    label: 'Dinner 🌙' }
                      ].map(m => (
                        <div key={m.key} style={{ display:'flex', alignItems:'center', justifyContent:'space-between' }}>
                          <span style={{ fontSize:'0.85rem', fontWeight: 600, color:'var(--text-secondary)' }}>
                            {m.label} ({log.isVeg ? 'Veg' : 'Non-Veg'})
                          </span>
                          <label className="toggle">
                            <input 
                              type="checkbox" 
                              checked={!!log[m.key]} 
                              onChange={e => updateField(guest.id, m.key, e.target.checked)} 
                            />
                            <span className="toggle-slider" />
                          </label>
                        </div>
                      ))}
                    </div>
                  </div>

                  {/* Daily Add-ons Counters */}
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
                    <div style={{ fontSize:'0.75rem', color:'var(--text-muted)', fontWeight:700, textTransform:'uppercase', letterSpacing:'0.05em', marginBottom:'0.5rem', marginTop: '0.25rem' }}>
                      ➕ Daily Add-ons &amp; Services
                    </div>
                    <AddOnCounter label="Omelette" icon="🍳" value={log.omeletteCount}
                      unit="₹18 each" onChange={v => updateField(guest.id, 'omeletteCount', v)} />
                    <AddOnCounter label="Boiled Egg" icon="🥚" value={log.boiledEggCount}
                      unit="₹18 each" onChange={v => updateField(guest.id, 'boiledEggCount', v)} />
                    <AddOnCounter label="Washing Machine" icon="🫧" value={log.washingMachineCount}
                      unit="₹50/use" onChange={v => updateField(guest.id, 'washingMachineCount', v)} />
                  </div>
                </div>
              );
            })()}
          </div>
        ) : (
          <div className="card" style={{ textAlign:'center', padding:'3rem', color:'var(--text-muted)' }}>
            🔍 Please search or select a guest from the panel above.
          </div>
        )}
      </div>
    </div>
  );
}

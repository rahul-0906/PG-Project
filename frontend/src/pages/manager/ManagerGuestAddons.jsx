import React, { useEffect, useState } from 'react';
import AppLayout from '../../components/AppLayout';
import { managerApi } from '../../api';
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
  Plus
} from 'lucide-react';

const TODAY = new Date().toISOString().slice(0, 10);

function AddOnCounter({ label, icon: Icon, value, onChange, unit = '', iconColor = 'text-slate-400' }) {
  return (
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      padding: '0.65rem 0', borderBottom: '1px solid var(--border)'
    }}>
      <div style={{ display: 'flex', alignItems: 'center' }}>
        {Icon && <Icon className={`w-4 h-4 ${iconColor} mr-2`} />}
        <span style={{ color: '#1e293b', fontWeight: 600, fontSize: '0.85rem' }}>{label}</span>
        {unit && <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem', marginLeft: '0.35rem' }}>{unit}</span>}
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
        <button onClick={() => onChange(Math.max(0, value - 1))}
          style={{ width: 28, height: 28, border: '1px solid var(--border)', borderRadius: 6,
            background: '#f8fafc', color: '#475569', cursor: 'pointer', fontSize: '1.1rem', fontWeight: 700,
            display: 'flex', alignItems: 'center', justifyContent: 'center', transition: 'all 0.15s ease' }}>
          −
        </button>
        <span style={{ minWidth: 24, textAlign: 'center', fontWeight: 800, color: 'var(--color-primary)', fontSize: '0.95rem' }}>{value}</span>
        <button onClick={() => onChange(value + 1)}
          style={{ width: 28, height: 28, border: 'none', borderRadius: 6,
            background: 'var(--color-primary)', color: '#fff', cursor: 'pointer', fontSize: '1.1rem', fontWeight: 700,
            display: 'flex', alignItems: 'center', justifyContent: 'center', transition: 'all 0.15s ease' }}>
          +
        </button>
      </div>
    </div>
  );
}

function VegToggle({ isVeg, onChange }) {
  const Icon = isVeg ? Leaf : Utensils;
  const iconColor = isVeg ? 'text-emerald-500' : 'text-rose-500';
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0.25rem 0' }}>
      <div style={{ display: 'flex', alignItems: 'center' }}>
        <Icon className={`w-4 h-4 ${iconColor} mr-2`} />
        <span style={{ color: '#1e293b', fontWeight: 700, fontSize: '0.85rem' }}>
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
      
      // Maintain selected guest if still active, otherwise clear selection
      if (selectedGuestId) {
        const stillActive = activeGuests.some(g => g.id === selectedGuestId);
        if (!stillActive) {
          setSelectedGuestId('');
        }
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
    <AppLayout>
      <div className="page-header">
        <div>
          <h1 className="page-title flex items-center gap-2">
            <ChefHat className="w-6 h-6 text-primary" />
            <span>Meal & Add-on Tracker</span>
          </h1>
          <p className="page-subtitle">Record meal options, egg, omelette, washing machine &amp; veg preference per guest</p>
        </div>
        <input type="date" value={date} onChange={e => setDate(e.target.value)}
          className="form-input" style={{ width:'auto', padding:'0.4rem 0.75rem' }} />
      </div>

      {/* Summary Bar */}
      <div style={{ display:'grid', gridTemplateColumns:'repeat(auto-fit,minmax(130px,1fr))', gap:'0.75rem', marginBottom:'1.5rem' }}>
        {[
          { icon: ChefHat, label:'Omelettes', val: Object.values(logs).reduce((s,l) => s+(l.omeletteCount||0), 0), color: 'text-indigo-500' },
          { icon: Egg, label:'Boiled Eggs', val: Object.values(logs).reduce((s,l) => s+(l.boiledEggCount||0), 0), color: 'text-amber-500' },
          { icon: Shirt, label:'Washing Machine', val: Object.values(logs).reduce((s,l) => s+(l.washingMachineCount||0), 0), color: 'text-blue-500' },
          { icon: Leaf, label:'Veg Guests', val: Object.values(logs).filter(l => l.isVeg).length, color: 'text-emerald-500' },
          { icon: Utensils, label:'Non-Veg', val: Object.values(logs).filter(l => !l.isVeg).length, color: 'text-rose-500' },
        ].map(s => {
          const Icon = s.icon;
          return (
            <div key={s.label} className="stat-card flex flex-col items-center justify-center p-3 text-center">
              <Icon className={`w-5 h-5 ${s.color} mb-1`} />
              <div className="text-xl font-extrabold text-slate-800">{s.val}</div>
              <div className="text-slate-400 text-xxs font-medium uppercase tracking-wider mt-0.5">{s.label}</div>
            </div>
          );
        })}
      </div>

      {/* Search Panel */}
      <div className="card" style={{ marginBottom: '1.5rem', maxWidth: '600px' }}>
        <div style={{ display: 'flex', gap: '1rem', alignItems: 'flex-end', flexWrap: 'wrap' }}>
          <div className="form-group" style={{ margin: 0, flex: 1, minWidth: '200px' }}>
            <label className="form-label flex items-center gap-1.5">
              <Search className="w-4 h-4 text-slate-400" />
              <span>Find Guest (Search by Name)</span>
            </label>
            <input 
              type="text" 
              placeholder="Type guest name..." 
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              className="form-input"
            />
          </div>
          <div className="form-group" style={{ margin: 0, flex: 1, minWidth: '200px' }}>
            <label className="form-label flex items-center gap-1.5">
              <User className="w-4 h-4 text-slate-400" />
              <span>Select Guest</span>
            </label>
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
          <Loader2 className="w-8 h-8 animate-spin mx-auto text-slate-400 mb-2" />
          <span>Loading guest list...</span>
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
              <div key={guest.id} className="card" style={{ padding:'1.5rem', width: '100%', maxWidth: '750px' }}>
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
                    className="btn btn-primary flex items-center gap-1.5" style={{ fontSize:'0.85rem', padding:'0.4rem 1.1rem', flexShrink:0 }}>
                    {isSaving ? (
                      <>
                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                        <span>Saving...</span>
                      </>
                    ) : isSaved ? (
                      <>
                        <Check className="w-3.5 h-3.5" />
                        <span>Saved</span>
                      </>
                    ) : (
                      <>
                        <Check className="w-3.5 h-3.5" />
                        <span>Save Changes</span>
                      </>
                    )}
                  </button>
                </div>

                {/* Standalone Veg Toggle Bar */}
                <div style={{ marginBottom: '1.25rem', background: '#f8fafc', padding: '0.75rem 1.25rem', borderRadius: '8px', border: '1px solid var(--border)' }}>
                  <VegToggle isVeg={log.isVeg} onChange={v => updateField(guest.id, 'isVeg', v)} />
                </div>

                {/* 2-Column Grid */}
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '1.5rem' }}>
                  {/* Left Column: Meal Options Panel */}
                  <div style={{ background: '#f8fafc', padding: '1.25rem', borderRadius: '8px', border: '1px solid var(--border)' }}>
                    <div style={{ fontSize:'0.75rem', color:'var(--text-muted)', fontWeight:700, textTransform:'uppercase', letterSpacing:'0.05em', marginBottom:'0.75rem', display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                      <Utensils className="w-3.5 h-3.5 text-slate-400" />
                      <span>Meal Options ({log.isVeg ? 'Veg' : 'Non-Veg'})</span>
                    </div>
                    <div style={{ display:'flex', flexDirection:'column', gap:'0.75rem' }}>
                      {[
                        { key: 'breakfastOpted', label: 'Breakfast', icon: Coffee },
                        { key: 'lunchOpted',     label: 'Lunch',     icon: Sun },
                        { key: 'dinnerOpted',    label: 'Dinner',    icon: Moon }
                      ].map(m => {
                        const MealIcon = m.icon;
                        return (
                          <div key={m.key} style={{ display:'flex', alignItems:'center', justifyContent:'space-between' }}>
                            <span style={{ fontSize:'0.85rem', fontWeight: 600, color:'var(--text-secondary)', display: 'flex', alignItems: 'center' }}>
                              <MealIcon className="w-3.5 h-3.5 text-slate-400 mr-2" />
                              <span>{m.label} ({log.isVeg ? 'Veg' : 'Non-Veg'})</span>
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
                        );
                      })}
                    </div>
                  </div>

                  {/* Right Column: Daily Add-ons Panel */}
                  <div style={{ background: '#f8fafc', padding: '1.25rem', borderRadius: '8px', border: '1px solid var(--border)', display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
                    <div style={{ fontSize:'0.75rem', color:'var(--text-muted)', fontWeight:700, textTransform:'uppercase', letterSpacing:'0.05em', marginBottom:'0.5rem', display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                      <Plus className="w-3.5 h-3.5 text-slate-400" />
                      <span>Daily Add-ons &amp; Services</span>
                    </div>
                    <AddOnCounter label="Omelette" icon={ChefHat} iconColor="text-indigo-500" value={log.omeletteCount}
                      unit="₹18 each" onChange={v => updateField(guest.id, 'omeletteCount', v)} />
                    <AddOnCounter label="Boiled Egg" icon={Egg} iconColor="text-amber-500" value={log.boiledEggCount}
                      unit="₹18 each" onChange={v => updateField(guest.id, 'boiledEggCount', v)} />
                    <AddOnCounter label="Washing Machine" icon={Shirt} iconColor="text-blue-500" value={log.washingMachineCount}
                      unit="₹50/use" onChange={v => updateField(guest.id, 'washingMachineCount', v)} />
                  </div>
                </div>
              </div>
            );
          })()}
        </div>
      ) : (
        <div className="card" style={{ textAlign:'center', padding:'3rem', color:'var(--text-muted)' }}>
          <Search className="w-8 h-8 mx-auto text-slate-300 mb-2" />
          <span>Please search or select a guest from the panel above.</span>
        </div>
      )}
    </AppLayout>
  );
}

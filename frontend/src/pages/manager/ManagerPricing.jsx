import React, { useEffect, useState, useCallback } from 'react';
import AppLayout from '../../components/AppLayout';
import { managerApi, ownerApi } from '../../api';
import { useAuth } from '../../context/AuthContext';
import { Tag, Check, Pencil, X, Loader2, ChefHat, Bed, Building2, RefreshCcw, Clock, Calendar } from 'lucide-react';

const FOOD_KEYS = [
  { key: 'breakfast',      label: 'Breakfast',       icon: '🍳' },
  { key: 'lunch',          label: 'Lunch',            icon: '🍱' },
  { key: 'dinner',         label: 'Dinner',           icon: '🍛' },
  { key: 'omelette',       label: 'Omelette',         icon: '🥚' },
  { key: 'boiled_egg',     label: 'Boiled Egg',       icon: '🥚' },
  { key: 'washing_machine',label: 'Washing Machine',  icon: '🫧' },
];

const SHARING_LABELS = { 1: 'Single', 2: 'Double', 3: 'Triple', 4: 'Quad' };

function EditablePrice({ value, onSave, saving }) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState('');

  const start = () => { setDraft(value?.toString() ?? ''); setEditing(true); };
  const cancel = () => setEditing(false);
  const save = async () => {
    const n = parseFloat(draft);
    if (isNaN(n) || n < 0) return;
    await onSave(n);
    setEditing(false);
  };

  if (editing) {
    return (
      <div className="flex items-center gap-1">
        <span className="text-slate-400 text-sm">₹</span>
        <input
          autoFocus
          type="number"
          className="form-input py-1 text-sm w-24"
          value={draft}
          onChange={e => setDraft(e.target.value)}
          onKeyDown={e => { if (e.key === 'Enter') save(); if (e.key === 'Escape') cancel(); }}
          autoComplete="off"
          name="price-edit-draft"
        />
        <button className="p-1 rounded text-emerald-600 hover:bg-emerald-50" onClick={save} disabled={saving}>
          {saving ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Check className="w-3.5 h-3.5" />}
        </button>
        <button className="p-1 rounded text-slate-400 hover:bg-slate-100" onClick={cancel}>
          <X className="w-3.5 h-3.5" />
        </button>
      </div>
    );
  }

  return (
    <button
      className="flex items-center gap-2 group"
      onClick={start}
    >
      <span className="font-semibold text-slate-800">₹{parseFloat(value || 0).toFixed(2)}</span>
      <Pencil className="w-3 h-3 text-slate-300 group-hover:text-indigo-500 transition-colors" />
    </button>
  );
}

export default function ManagerPricing() {
  const [pricingData, setPricingData] = useState(null);
  const [buildings, setBuildings] = useState([]);
  const [selectedBuildingId, setSelectedBuildingId] = useState('');
  const [loading, setLoading] = useState(true);
  const [savingKey, setSavingKey] = useState('');
  const [savingRoom, setSavingRoom] = useState('');
  const [savingSharing, setSavingSharing] = useState('');
  const [toast, setToast] = useState('');
  const [schedulerEnabled, setSchedulerEnabled] = useState(false);
  const [savingScheduler, setSavingScheduler] = useState(false);

  const { user } = useAuth();

  const showToast = (msg) => {
    setToast(msg);
    setTimeout(() => setToast(''), 2500);
  };

  const loadBuildings = async () => {
    try {
      const res = await ownerApi.getBranches();
      setBuildings(res.data);
      if (res.data.length > 0 && !selectedBuildingId) {
        setSelectedBuildingId(res.data[0].id);
      }
    } catch { /* ignore */ }
  };

  const loadPricing = useCallback(async () => {
    setLoading(true);
    try {
      const res = await managerApi.getPricing(selectedBuildingId || undefined);
      setPricingData(res.data);
      setSchedulerEnabled(res.data.billingSchedulerEnabled ?? false);
      if (user?.role === 'PG_MANAGER' && res.data?.buildings?.length > 0) {
        setBuildings(res.data.buildings);
        if (!selectedBuildingId) {
          setSelectedBuildingId(res.data.buildings[0].id);
        }
      }
    } catch { /* ignore */ }
    finally { setLoading(false); }
  }, [selectedBuildingId, user]);

  useEffect(() => {
    if (user?.role === 'PG_MANAGER') {
      loadPricing();
    } else {
      loadBuildings();
    }
  }, [user]);

  useEffect(() => {
    if (selectedBuildingId && user?.role !== 'PG_MANAGER') {
      loadPricing();
    }
  }, [selectedBuildingId, loadPricing, user]);

  const handleFoodPriceUpdate = async (key, value) => {
    setSavingKey(key);
    try {
      await managerApi.updateFoodPrice(key, value, selectedBuildingId);
      await loadPricing();
      showToast('Price updated successfully');
    } catch { showToast('Failed to update price'); }
    finally { setSavingKey(''); }
  };

  const handleRoomRentUpdate = async (roomId, baseRent) => {
    setSavingRoom(roomId);
    try {
      await managerApi.updateRoomRent(roomId, baseRent);
      await loadPricing();
      showToast('Room rent updated');
    } catch { showToast('Failed to update room rent'); }
    finally { setSavingRoom(''); }
  };

  const handleSharingRentUpdate = async (sharingType, baseRent) => {
    setSavingSharing(sharingType);
    try {
      await managerApi.updateSharingRent(sharingType, baseRent, selectedBuildingId);
      await loadPricing();
      showToast('Sharing type rent updated');
    } catch { showToast('Failed to update sharing type rent'); }
    finally { setSavingSharing(''); }
  };

  const handleToggleScheduler = async (e) => {
    const newVal = e.target.checked;
    setSavingScheduler(true);
    try {
      await managerApi.updateFoodPrice('billing_scheduler_enabled', newVal ? 1 : 0, selectedBuildingId);
      setSchedulerEnabled(newVal);
      showToast(newVal ? 'Automatic billing scheduler enabled' : 'Automatic billing scheduler disabled');
    } catch {
      showToast('Failed to update billing scheduler status');
    } finally {
      setSavingScheduler(false);
    }
  };

  const foodPricing = pricingData?.foodPricing || {};
  const bldgData = pricingData?.buildings || [];

  return (
    <AppLayout>
      <div className="page-header">
        <div>
          <h1 className="page-title flex items-center gap-2">
            <Tag className="w-6 h-6 text-primary" />
            <span>Pricing Manager</span>
          </h1>
          <p className="page-subtitle">Manage room rents and food/addon prices per building</p>
        </div>
        <div className="flex items-center gap-2">
          <Building2 className="w-4 h-4 text-slate-400" />
          {buildings.length <= 1 ? (
            <span className="font-semibold text-slate-700 text-sm mr-2">
              {buildings[0]?.name || 'Loading...'}
            </span>
          ) : (
            <select
              id="pricing-building-select"
              className="form-input py-1.5 text-sm cursor-pointer"
              value={selectedBuildingId}
              onChange={e => setSelectedBuildingId(e.target.value)}
            >
              {buildings.map(b => (
                <option key={b.id} value={b.id}>{b.name}</option>
              ))}
            </select>
          )}
          <button className="btn btn-ghost p-1.5" onClick={loadPricing} title="Refresh">
            <RefreshCcw className="w-4 h-4 text-slate-400" />
          </button>
        </div>
      </div>

      {toast && (
        <div className="fixed top-4 right-4 z-50 bg-emerald-600 text-white px-4 py-2.5 rounded-xl shadow-lg text-sm animate-fade-in-up flex items-center gap-2">
          <Check className="w-4 h-4" /> {toast}
        </div>
      )}

      {loading ? (
        <div className="flex justify-center items-center py-20">
          <Loader2 className="w-8 h-8 text-indigo-500 animate-spin" />
        </div>
      ) : (
        <div className="flex flex-col gap-6">
          {/* Food & Addon Pricing */}
          <div className="card">
            <h3 className="text-base font-bold text-slate-800 mb-4 flex items-center gap-2">
              <ChefHat className="w-5 h-5 text-orange-500" />
              Food & Addon Pricing
            </h3>
            <div className="grid grid-cols-2 gap-3" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))' }}>
              {FOOD_KEYS.map(({ key, label, icon }) => (
                <div key={key} className="flex items-center justify-between p-3.5 rounded-xl border border-slate-100 bg-slate-50 hover:bg-white hover:border-slate-200 transition-all">
                  <div className="flex items-center gap-2">
                    <span className="text-xl">{icon}</span>
                    <span className="text-sm font-medium text-slate-700">{label}</span>
                  </div>
                  <EditablePrice
                    value={foodPricing[key] ?? 0}
                    onSave={(v) => handleFoodPriceUpdate(key, v)}
                    saving={savingKey === key}
                  />
                </div>
              ))}
            </div>
            <p className="text-xs text-slate-400 mt-3">Click any price to edit it. Changes are saved immediately.</p>
          </div>

          {/* Automatic Billing Settings */}
          <div className="card">
            <h3 className="text-base font-bold text-slate-800 mb-2 flex items-center gap-2">
              <Clock className="w-5 h-5 text-indigo-500" />
              Automatic Billing Scheduler
            </h3>
            <p className="text-sm text-slate-500 mb-4">
              When enabled, the monthly billing cron job automatically generates and sends invoices to all active guests on the 1st of every month. Disable this if you prefer to manually generate and verify invoices (e.g., on the 1st or 2nd of the month).
            </p>
            <div className="flex items-center justify-between p-3.5 rounded-xl border border-slate-100 bg-slate-50 hover:bg-white hover:border-slate-200 transition-all">
              <div className="flex items-center gap-2">
                <span className="text-xl">📅</span>
                <div className="flex flex-col">
                  <span className="text-sm font-semibold text-slate-700">Monthly Billing Cron</span>
                  <span className="text-xs text-slate-400">Runs at midnight on the 1st of every month</span>
                </div>
              </div>
              <div className="flex items-center gap-2">
                {savingScheduler ? (
                  <Loader2 className="w-4 h-4 animate-spin text-indigo-600" />
                ) : (
                  <label className="relative inline-flex items-center cursor-pointer">
                    <input
                      id="toggle-scheduler"
                      type="checkbox"
                      className="sr-only peer"
                      checked={schedulerEnabled}
                      onChange={handleToggleScheduler}
                    />
                    <div className="w-11 h-6 bg-slate-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-indigo-600"></div>
                  </label>
                )}
                <span className="text-xs font-semibold text-slate-600 w-16 text-right">
                  {schedulerEnabled ? 'Enabled' : 'Disabled'}
                </span>
              </div>
            </div>
          </div>

          {/* Room Rents by Sharing Type */}
          <div className="card">
            <h3 className="text-base font-bold text-slate-800 mb-4 flex items-center gap-2">
              <Bed className="w-5 h-5 text-blue-500" />
              Room Rents by Sharing Type (Global for Building)
            </h3>
            <div className="grid grid-cols-2 gap-3" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))' }}>
              {Object.keys(SHARING_LABELS).map(sharingType => {
                const bldg = bldgData.find(b => b.id === selectedBuildingId);
                const rooms = [];
                bldg?.floors?.forEach(f => {
                  f.blocks?.forEach(bl => {
                    bl.rooms?.forEach(r => {
                      if (r.sharingType === parseInt(sharingType)) rooms.push(r);
                    });
                  });
                  f.standaloneRooms?.forEach(r => {
                    if (r.sharingType === parseInt(sharingType)) rooms.push(r);
                  });
                });
                
                if (rooms.length === 0) return null;
                const baseRent = rooms[0]?.baseRent ?? 0;
                
                return (
                  <div key={sharingType} className="flex items-center justify-between p-3.5 rounded-xl border border-slate-100 bg-slate-50 hover:bg-white hover:border-slate-200 transition-all">
                    <div className="flex items-center gap-2">
                      <span className="text-xl">🛏️</span>
                      <div className="flex flex-col">
                        <span className="text-sm font-semibold text-slate-700">{SHARING_LABELS[sharingType]} Sharing</span>
                        <span className="text-[10px] text-slate-400 font-medium">{rooms.length} rooms</span>
                      </div>
                    </div>
                    <EditablePrice
                      value={baseRent}
                      onSave={(v) => handleSharingRentUpdate(parseInt(sharingType), v)}
                      saving={savingSharing === parseInt(sharingType)}
                    />
                  </div>
                );
              })}
            </div>
            <p className="text-xs text-slate-400 mt-3">Click any price to edit. Changes apply building-wide immediately.</p>
          </div>
        </div>
      )}
    </AppLayout>
  );
}

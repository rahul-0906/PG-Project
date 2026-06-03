import React, { useEffect, useState, useCallback } from 'react';
import AppLayout from '../../components/AppLayout';
import { managerApi, ownerApi } from '../../api';
import { useAuth } from '../../context/AuthContext';
import { Tag, Check, Pencil, X, Loader2, ChefHat, Bed, Building, RefreshCcw, Clock, Calendar, Settings } from 'lucide-react';

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
          {saving ? <Loader2 className="w-3.5 h-3.5 animate-spin" strokeWidth={1.5} /> : <Check className="w-3.5 h-3.5" strokeWidth={1.5} />}
        </button>
        <button className="p-1 rounded text-slate-400 hover:bg-slate-100" onClick={cancel}>
          <X className="w-3.5 h-3.5" strokeWidth={1.5} />
        </button>
      </div>
    );
  }

  return (
    <button
      className="flex items-center gap-2 group"
      onClick={start}
    >
      <span className="font-semibold text-slate-900">₹{parseFloat(value || 0).toFixed(2)}</span>
      <Pencil className="w-3 h-3 text-slate-300 group-hover:text-primary transition-colors" strokeWidth={1.5} />
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

  // New building configuration states
  const [foodIncludedInRent, setFoodIncludedInRent] = useState(false);
  const [allowMealCancellations, setAllowMealCancellations] = useState(true);
  const [ebSplitMethod, setEbSplitMethod] = useState('EQUAL_SPLIT');
  const [breakfastCutoffTime, setBreakfastCutoffTime] = useState('22:00');
  const [dinnerCutoffTime, setDinnerCutoffTime] = useState('14:00');
  const [isPreviousDay, setIsPreviousDay] = useState(true);
  const [allowedPaymentModes, setAllowedPaymentModes] = useState('BOTH');
  const [savingConfig, setSavingConfig] = useState(false);

  // Local state for configuration edits
  const [localFoodIncluded, setLocalFoodIncluded] = useState(false);
  const [localMealCancellations, setLocalMealCancellations] = useState(true);
  const [localEbSplitMethod, setLocalEbSplitMethod] = useState('EQUAL_SPLIT');
  const [localBreakfastTime, setLocalBreakfastTime] = useState('22:00');
  const [localDinnerTime, setLocalDinnerTime] = useState('14:00');
  const [localIsPreviousDay, setLocalIsPreviousDay] = useState(true);
  const [localAllowedPaymentModes, setLocalAllowedPaymentModes] = useState('BOTH');

  // Sync local states when API data is loaded
  useEffect(() => {
    setLocalFoodIncluded(foodIncludedInRent);
    setLocalMealCancellations(allowMealCancellations);
    setLocalEbSplitMethod(ebSplitMethod);
    setLocalBreakfastTime(breakfastCutoffTime);
    setLocalDinnerTime(dinnerCutoffTime);
    setLocalIsPreviousDay(isPreviousDay);
    setLocalAllowedPaymentModes(allowedPaymentModes);
  }, [foodIncludedInRent, allowMealCancellations, ebSplitMethod, breakfastCutoffTime, dinnerCutoffTime, isPreviousDay, allowedPaymentModes]);

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
      setFoodIncludedInRent(res.data.foodIncludedInRent ?? false);
      setAllowMealCancellations(res.data.allowMealCancellations ?? true);
      setEbSplitMethod(res.data.ebSplitMethod ?? 'EQUAL_SPLIT');
      setBreakfastCutoffTime(res.data.breakfastCutoffTime ? res.data.breakfastCutoffTime.substring(0, 5) : '22:00');
      setDinnerCutoffTime(res.data.dinnerCutoffTime ? res.data.dinnerCutoffTime.substring(0, 5) : '14:00');
      setIsPreviousDay(res.data.isPreviousDay ?? true);
      setAllowedPaymentModes(res.data.allowedPaymentModes ?? 'BOTH');

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

  const handleSharingRentUpdate = async (sharingType, baseRent, floorId) => {
    const key = `${floorId}_${sharingType}`;
    setSavingSharing(key);
    try {
      await managerApi.updateSharingRent(sharingType, baseRent, selectedBuildingId, floorId);
      await loadPricing();
      showToast('Sharing type rent updated for floor');
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

  const handleSaveConfig = async () => {
    setSavingConfig(true);
    try {
      const payload = {
        foodIncludedInRent: localFoodIncluded,
        allowMealCancellations: localMealCancellations,
        ebSplitMethod: localEbSplitMethod,
        breakfastCutoffTime: localBreakfastTime,
        dinnerCutoffTime: localDinnerTime,
        isPreviousDay: localIsPreviousDay,
        allowedPaymentModes: localAllowedPaymentModes
      };

      await managerApi.updateBuildingConfig(payload, selectedBuildingId);
      
      // Force a re-fetch of the data immediately
      await loadPricing();
      
      showToast('Building rules saved successfully');
    } catch (err) {
      console.error(err);
      showToast('Failed to save building rules');
    } finally {
      setSavingConfig(false);
    }
  };

  const foodPricing = pricingData?.foodPricing || {};
  const bldgData = pricingData?.buildings || [];

  return (
    <AppLayout>
      <div className="page-header">
        <div>
          <h1 className="page-title flex items-center gap-2">
            <Tag className="w-6 h-6 text-primary" strokeWidth={1.5} />
            <span>Pricing Manager</span>
          </h1>
          <p className="page-subtitle">Manage room rents and food/addon prices per building</p>
        </div>
        <div className="flex items-center gap-2">
          <Building className="w-4 h-4 text-slate-400" strokeWidth={1.5} />
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
            <RefreshCcw className="w-4 h-4 text-slate-400" strokeWidth={1.5} />
          </button>
        </div>
      </div>

      {toast && (
        <div className="fixed top-4 right-4 z-[9999] bg-green-600 text-white px-4 py-2.5 rounded-xl shadow-lg text-sm animate-fade-in-up flex items-center gap-2">
          <Check className="w-4 h-4" strokeWidth={1.5} /> {toast}
        </div>
      )}

      {loading ? (
        <div className="flex justify-center items-center py-20">
          <Loader2 className="w-8 h-8 text-primary animate-spin" strokeWidth={1.5} />
        </div>
      ) : (
        <div className="flex flex-col gap-6">
          {/* Building Rules & Config */}
          <div className="card" style={{ padding: '1.25rem' }}>
            <h3 className="font-heading text-base font-semibold text-slate-900 mb-2 flex items-center gap-2">
              <Settings className="w-5 h-5 text-primary" strokeWidth={1.5} />
              Building Rules & Config
            </h3>
            <p className="text-sm text-slate-500 mb-4">
              Configure food inclusion rules, cancellation eligibility, and utility split methods specifically for this building.
            </p>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              {/* Food Included */}
              <div className="flex flex-col justify-between p-4 rounded-xl border border-slate-100 bg-slate-50 hover:bg-white hover:border-slate-200 transition-all">
                <div className="mb-4">
                  <div className="flex items-center gap-2 mb-1">
                    <span className="text-lg">🍽️</span>
                    <span className="text-sm font-semibold text-slate-700">Food Included in Rent</span>
                  </div>
                  <span className="text-xs text-slate-400">If enabled, meals are included in the base rent dynamically.</span>
                </div>
                <div className="flex items-center justify-between mt-auto pt-2">
                  <span className="text-xs font-semibold text-slate-600">
                    {localFoodIncluded ? 'Included' : 'Billed Separately'}
                  </span>
                  <button
                    type="button"
                    onClick={() => setLocalFoodIncluded(prev => !prev)}
                    className="relative inline-flex items-center focus:outline-none"
                  >
                    <div className={`w-11 h-6 rounded-full transition-colors relative ${localFoodIncluded ? 'bg-primary' : 'bg-slate-200'}`}>
                      <div className={`absolute top-[2px] left-[2px] bg-white border border-slate-300 rounded-full h-5 w-5 transition-transform ${localFoodIncluded ? 'translate-x-5' : 'translate-x-0'}`}></div>
                    </div>
                  </button>
                </div>
              </div>

              {/* Allow Meal Cancellations */}
              <div className="flex flex-col justify-between p-4 rounded-xl border border-slate-100 bg-slate-50 hover:bg-white hover:border-slate-200 transition-all">
                <div className="mb-4">
                  <div className="flex items-center gap-2 mb-1">
                    <span className="text-lg">❌</span>
                    <span className="text-sm font-semibold text-slate-700">Allow Meal Cancellations</span>
                  </div>
                  <span className="text-xs text-slate-400">Enables guests to cancel meals for daily food refunds.</span>
                </div>
                <div className="flex items-center justify-between mt-auto pt-2">
                  <span className="text-xs font-semibold text-slate-600">
                    {localMealCancellations ? 'Allowed' : 'Disabled'}
                  </span>
                  <button
                    type="button"
                    onClick={() => setLocalMealCancellations(prev => !prev)}
                    className="relative inline-flex items-center focus:outline-none"
                  >
                    <div className={`w-11 h-6 rounded-full transition-colors relative ${localMealCancellations ? 'bg-primary' : 'bg-slate-200'}`}>
                      <div className={`absolute top-[2px] left-[2px] bg-white border border-slate-300 rounded-full h-5 w-5 transition-transform ${localMealCancellations ? 'translate-x-5' : 'translate-x-0'}`}></div>
                    </div>
                  </button>
                </div>
              </div>

              {/* EB Split Method */}
              <div className="flex flex-col justify-between p-4 rounded-xl border border-slate-100 bg-slate-50 hover:bg-white hover:border-slate-200 transition-all">
                <div className="mb-4">
                  <div className="flex items-center gap-2 mb-1">
                    <span className="text-lg">⚡</span>
                    <span className="text-sm font-semibold text-slate-700">Electricity Bill Split</span>
                  </div>
                  <span className="text-xs text-slate-400">Method used to divide the building's monthly electricity bill.</span>
                </div>
                <div className="mt-auto pt-2">
                  <select
                    className="form-input w-full py-1.5 text-xs cursor-pointer text-slate-700 font-semibold bg-white border-slate-200 rounded-lg"
                    value={localEbSplitMethod}
                    onChange={(e) => setLocalEbSplitMethod(e.target.value)}
                  >
                    <option value="EQUAL_SPLIT">Equal Split (per active guest)</option>
                    <option value="PER_BED">Fixed Rate Per Bed</option>
                    <option value="METER_BASED">Sub-meter Reading Based</option>
                    <option value="MANAGER_MANUAL">Manager Manual Entry</option>
                  </select>
                </div>
              </div>

              {/* Allowed Payment Modes */}
              <div className="flex flex-col justify-between p-4 rounded-xl border border-slate-100 bg-slate-50 hover:bg-white hover:border-slate-200 transition-all">
                <div className="mb-4">
                  <div className="flex items-center gap-2 mb-1">
                    <span className="text-lg">💳</span>
                    <span className="text-sm font-semibold text-slate-700">Allowed Payment Modes</span>
                  </div>
                  <span className="text-xs text-slate-400">Set payment options allowed for guest monthly invoices.</span>
                </div>
                <div className="mt-auto pt-2">
                  <select
                    id="payment-mode-select"
                    className="form-input w-full py-1.5 text-xs cursor-pointer text-slate-700 font-semibold bg-white border-slate-200 rounded-lg"
                    value={localAllowedPaymentModes}
                    onChange={(e) => setLocalAllowedPaymentModes(e.target.value)}
                  >
                    <option value="BOTH">Cash & Online (Both)</option>
                    <option value="CASH_ONLY">Cash Only</option>
                  </select>
                </div>
              </div>
            </div>

            {/* Cut-off Settings */}
            <div className="border-t border-slate-150 pt-5 mt-5">
              <h4 className="font-heading text-sm font-semibold text-slate-900 mb-3 flex items-center gap-2">
                <span>🕒</span> Meal Cut-off Settings
              </h4>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-slate-700">
                {/* Breakfast Cut-off */}
                <div className="flex flex-col justify-between p-4 rounded-xl border border-slate-100 bg-slate-50 hover:bg-white hover:border-slate-200 transition-all">
                  <div>
                    <span className="text-xs font-semibold text-slate-700 block mb-1">Breakfast & Lunch Cut-off Time</span>
                    <span className="text-[10px] text-slate-400">Lockout time for guests to change breakfast & lunch options.</span>
                  </div>
                  <div className="mt-3">
                    <input
                      type="time"
                      className="form-input w-full py-1.5 text-xs cursor-pointer"
                      value={localBreakfastTime}
                      onChange={(e) => setLocalBreakfastTime(e.target.value)}
                    />
                  </div>
                </div>

                {/* Dinner Cut-off */}
                <div className="flex flex-col justify-between p-4 rounded-xl border border-slate-100 bg-slate-50 hover:bg-white hover:border-slate-200 transition-all">
                  <div>
                    <span className="text-xs font-semibold text-slate-700 block mb-1">Dinner Cut-off Time</span>
                    <span className="text-[10px] text-slate-400">Lockout time for guests to change dinner options.</span>
                  </div>
                  <div className="mt-3">
                    <input
                      type="time"
                      className="form-input w-full py-1.5 text-xs cursor-pointer"
                      value={localDinnerTime}
                      onChange={(e) => setLocalDinnerTime(e.target.value)}
                    />
                  </div>
                </div>

                {/* Previous Day Flag */}
                <div className="flex flex-col justify-between p-4 rounded-xl border border-slate-100 bg-slate-50 hover:bg-white hover:border-slate-200 transition-all">
                  <div>
                    <span className="text-xs font-semibold text-slate-700 block mb-1">Breakfast/Lunch Lockout Day</span>
                    <span className="text-[10px] text-slate-400">Specify if breakfast/lunch is locked the previous day or same day.</span>
                  </div>
                  <div className="mt-3">
                    <select
                      id="lockout-day-select"
                      className="form-input w-full py-1.5 text-xs cursor-pointer"
                      value={localIsPreviousDay ? 'true' : 'false'}
                      onChange={(e) => setLocalIsPreviousDay(e.target.value === 'true')}
                    >
                      <option value="true">Previous Day</option>
                      <option value="false">Same Day</option>
                    </select>
                  </div>
                </div>
              </div>
            </div>

            {/* Save Config Rules Action Row */}
            <div className="flex justify-end mt-5 pt-4 border-t border-slate-150">
              <button
                type="button"
                className="btn btn-primary flex items-center gap-2 text-xs py-1.5 px-4 font-semibold"
                onClick={handleSaveConfig}
                disabled={savingConfig}
              >
                {savingConfig ? (
                  <>
                    <Loader2 className="w-3.5 h-3.5 animate-spin" strokeWidth={1.5} />
                    <span>Saving...</span>
                  </>
                ) : (
                  <>
                    <Check className="w-3.5 h-3.5" strokeWidth={1.5} />
                    <span>Save Rules</span>
                  </>
                )}
              </button>
            </div>
          </div>

          {/* Food & Addon Pricing */}
          <div className="card" style={{ padding: '1.25rem' }}>
            <h3 className="font-heading text-base font-semibold text-slate-900 mb-4 flex items-center gap-2">
              <ChefHat className="w-5 h-5 text-primary" strokeWidth={1.5} />
              Food & Addon Pricing
            </h3>
            <div className="grid grid-cols-2 gap-3" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))' }}>
              {FOOD_KEYS.filter(({ key }) => {
                if (localFoodIncluded && ['breakfast', 'lunch', 'dinner'].includes(key)) return false;
                return true;
              }).map(({ key, label, icon }) => (
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
            <p className="text-xs text-slate-400 mt-3 font-medium">Click any price to edit it. Changes are saved immediately.</p>
          </div>

          {/* Automatic Billing Settings */}
          <div className="card" style={{ padding: '1.25rem' }}>
            <h3 className="font-heading text-base font-semibold text-slate-900 mb-2 flex items-center gap-2">
              <Clock className="w-5 h-5 text-primary" strokeWidth={1.5} />
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
                  <Loader2 className="w-4 h-4 animate-spin text-primary" strokeWidth={1.5} />
                ) : (
                  <label className="relative inline-flex items-center cursor-pointer">
                    <input
                      id="toggle-scheduler"
                      type="checkbox"
                      className="sr-only peer"
                      checked={schedulerEnabled}
                      onChange={handleToggleScheduler}
                    />
                    <div className="w-11 h-6 bg-slate-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary"></div>
                  </label>
                )}
                <span className="text-xs font-semibold text-slate-600 w-16 text-right">
                  {schedulerEnabled ? 'Enabled' : 'Disabled'}
                </span>
              </div>
            </div>
          </div>

          {/* Room Rents by Sharing Type (Grouped by Floor) */}
          <div className="card" style={{ padding: '1.25rem' }}>
            <h3 className="font-heading text-base font-semibold text-slate-900 mb-2 flex items-center gap-2">
              <Bed className="w-5 h-5 text-primary" strokeWidth={1.5} />
              Room Rents by Floor & Sharing Type
            </h3>
            <p className="text-sm text-slate-500 mb-4">
              Manage room rents categorized by floor and sharing type.
            </p>
            <div className="flex flex-col gap-5">
              {(() => {
                const bldg = bldgData.find(b => b.id === selectedBuildingId);
                if (!bldg || !bldg.floors || bldg.floors.length === 0) {
                  return <p className="text-slate-400 text-xs font-medium">No floors configured for this building.</p>;
                }
                return bldg.floors.map(floor => {
                  const floorRooms = [];
                  floor.blocks?.forEach(bl => {
                    bl.rooms?.forEach(r => floorRooms.push(r));
                  });
                  floor.standaloneRooms?.forEach(r => floorRooms.push(r));

                  if (floorRooms.length === 0) return null;

                  const sharingTypesOnFloor = [1, 2, 3, 4].filter(st =>
                    floorRooms.some(r => r.sharingType === st)
                  );

                  return (
                    <div key={floor.id} className="border border-slate-100 bg-slate-50/50 p-4 rounded-xl">
                      <h4 className="font-heading text-xs font-bold text-slate-800 mb-3 uppercase tracking-wider flex items-center gap-1.5">
                        <span className="w-1.5 h-3.5 bg-primary rounded-full"></span>
                        <span>{floor.name || `Floor ${floor.id}`}</span>
                      </h4>
                      <div className="grid grid-cols-2 gap-3" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))' }}>
                        {sharingTypesOnFloor.map(sharingType => {
                          const roomsOfSt = floorRooms.filter(r => r.sharingType === sharingType);
                          const baseRent = roomsOfSt[0]?.baseRent ?? 0;
                          const key = `${floor.id}_${sharingType}`;

                          return (
                            <div key={sharingType} className="flex items-center justify-between p-3.5 rounded-xl border border-slate-100 bg-white hover:shadow-sm hover:border-slate-200 transition-all">
                              <div className="flex items-center gap-2">
                                <span className="text-xl">🛏️</span>
                                <div className="flex flex-col">
                                  <span className="text-xs font-semibold text-slate-700">{SHARING_LABELS[sharingType]} Sharing</span>
                                  <span className="text-[10px] text-slate-400 font-medium">{roomsOfSt.length} rooms</span>
                                </div>
                              </div>
                              <EditablePrice
                                value={baseRent}
                                onSave={(v) => handleSharingRentUpdate(sharingType, v, floor.id)}
                                saving={savingSharing === key}
                              />
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  );
                });
              })()}
            </div>
            <p className="text-xs text-slate-400 mt-4 font-medium">Click any price to edit. Changes apply only to the selected floor's rooms of that sharing type.</p>
          </div>
        </div>
      )}
    </AppLayout>
  );
}

import React, { useState, useEffect } from 'react';
import AppLayout from '../../components/AppLayout';
import { managerApi, ownerApi } from '../../api';
import { useAuth } from '../../context/AuthContext';
import { 
  Zap, 
  CheckCircle2, 
  X, 
  Loader2,
  Plus,
  Building2,
  AlertCircle
} from 'lucide-react';

export default function ManagerEbBill() {
  const { user } = useAuth();
  const [mode, setMode] = useState('EQUAL_SPLIT'); // EQUAL_SPLIT or METER_BASED
  const [buildings, setBuildings] = useState([]);
  const [selectedBuildingId, setSelectedBuildingId] = useState('');
  const [ebSplitMethod, setEbSplitMethod] = useState('');
  const [loadingConfig, setLoadingConfig] = useState(false);
  const [loadingBuildings, setLoadingBuildings] = useState(true);

  const [form, setForm] = useState({ blockId:'', totalAmount:'', periodStart:'', periodEnd:'', ratePerUnit:'' });
  const [readings, setReadings] = useState([]);
  const [result, setResult] = useState(null);
  const [saving, setSaving] = useState(false);
  const [floors, setFloors] = useState([]);
  const [selectedFloorId, setSelectedFloorId] = useState('');
  const [loadingFloors, setLoadingFloors] = useState(false);
  const [blocks, setBlocks] = useState([]);
  const [loadingBlocks, setLoadingBlocks] = useState(false);
  const [toast, setToast] = useState(null);

  const showToast = (message, type = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 3000);
  };

  useEffect(() => {
    const fetchBuildings = async () => {
      setLoadingBuildings(true);
      try {
        let res;
        if (user?.role === 'PG_MANAGER') {
          res = await managerApi.getAssignedBuildings();
        } else {
          res = await ownerApi.getBranches();
        }
        setBuildings(res.data || []);
        if (res.data && res.data.length > 0) {
          setSelectedBuildingId(res.data[0].id);
        }
      } catch (err) {
        console.error('Failed to fetch buildings', err);
      } finally {
        setLoadingBuildings(false);
      }
    };
    if (user) {
      fetchBuildings();
    }
  }, [user]);

  useEffect(() => {
    if (selectedBuildingId) {
      const fetchConfig = async () => {
        setLoadingConfig(true);
        try {
          const res = await managerApi.getPricing(selectedBuildingId);
          const splitMethod = res.data.ebSplitMethod || 'EQUAL_SPLIT';
          setEbSplitMethod(splitMethod);
          if (splitMethod === 'EQUAL_SPLIT' || splitMethod === 'METER_BASED') {
            setMode(splitMethod);
          } else {
            setMode(splitMethod);
          }
        } catch (err) {
          console.error('Failed to fetch pricing config', err);
        } finally {
          setLoadingConfig(false);
        }
      };
      fetchConfig();
    }
  }, [selectedBuildingId]);

  useEffect(() => {
    if (selectedBuildingId) {
      const fetchFloors = async () => {
        setLoadingFloors(true);
        try {
          const res = await managerApi.getFloorsByBuilding(selectedBuildingId);
          const floorsList = res.data || [];
          setFloors(floorsList);
          if (floorsList.length > 0) {
            setSelectedFloorId(floorsList[0].id.toString());
          } else {
            setSelectedFloorId('');
            setFloors([]);
            setBlocks([]);
            setForm(f => ({ ...f, blockId: '' }));
          }
        } catch (err) {
          console.error('Failed to fetch floors', err);
          setFloors([]);
          setSelectedFloorId('');
          setBlocks([]);
          setForm(f => ({ ...f, blockId: '' }));
        } finally {
          setLoadingFloors(false);
        }
      };
      fetchFloors();
    } else {
      setFloors([]);
      setSelectedFloorId('');
      setBlocks([]);
      setForm(f => ({ ...f, blockId: '' }));
    }
  }, [selectedBuildingId]);

  useEffect(() => {
    if (selectedFloorId) {
      const fetchBlocks = async () => {
        setLoadingBlocks(true);
        try {
          const res = await managerApi.getBlocksByFloor(selectedFloorId);
          const blocksList = res.data || [];
          setBlocks(blocksList);
          if (blocksList.length > 0) {
            setForm(f => ({ ...f, blockId: blocksList[0].id.toString() }));
          } else {
            setForm(f => ({ ...f, blockId: '' }));
          }
        } catch (err) {
          console.error('Failed to fetch blocks', err);
          setBlocks([]);
          setForm(f => ({ ...f, blockId: '' }));
        } finally {
          setLoadingBlocks(false);
        }
      };
      fetchBlocks();
    } else {
      setBlocks([]);
      setForm(f => ({ ...f, blockId: '' }));
    }
  }, [selectedFloorId]);

  const addReadingRow = () => {
    setReadings([...readings, { guestId: '', previousReading: '', currentReading: '' }]);
  };

  const updateReading = (index, field, value) => {
    const newReadings = [...readings];
    newReadings[index][field] = value;
    setReadings(newReadings);
  };

  const removeReadingRow = (index) => {
    setReadings(readings.filter((_, i) => i !== index));
  };

  const submit = async (e) => {
    e.preventDefault(); 
    setSaving(true);
    try {
      let res;
      if (mode === 'EQUAL_SPLIT') {
        res = await managerApi.recordEbBill({
          blockId: form.blockId, 
          totalAmount: form.totalAmount, 
          periodStart: form.periodStart, 
          periodEnd: form.periodEnd
        });
      } else {
        res = await managerApi.recordMeterBasedEbBill({
          blockId: form.blockId, 
          ratePerUnit: form.ratePerUnit, 
          periodStart: form.periodStart, 
          periodEnd: form.periodEnd,
          readings: readings
        });
      }
      setResult(res.data);
      showToast('Electricity bill recorded and split successfully!');
    } catch(err) { 
      showToast(err.response?.data?.error || 'Failed to save electricity bill', 'error'); 
    }
    setSaving(false);
  };

  const isFormEditable = ebSplitMethod === 'EQUAL_SPLIT' || ebSplitMethod === 'METER_BASED';

  return (
    <AppLayout>
      {toast && (
        <div className={`fixed top-4 right-4 z-[9999] px-4 py-3 rounded-xl shadow-xl text-sm font-semibold flex items-center gap-2.5 transition-all duration-300 animate-fade-in-up border ${
          toast.type === 'error' 
            ? 'bg-rose-50 border-rose-200 text-rose-800 shadow-rose-100/50' 
            : 'bg-emerald-50 border-emerald-200 text-emerald-800 shadow-emerald-100/50'
        }`}>
          {toast.type === 'error' ? (
            <AlertCircle className="w-4 h-4 text-rose-600" />
          ) : (
            <CheckCircle2 className="w-4 h-4 text-emerald-600" />
          )}
          <span>{toast.message}</span>
          <button onClick={() => setToast(null)} className="ml-2 hover:opacity-80 focus:outline-none">
            <X className="w-3.5 h-3.5 text-slate-400" />
          </button>
        </div>
      )}
      <div className="page-header">
        <div>
          <h1 className="page-title flex items-center gap-2">
            <Zap className="w-6 h-6 text-primary" />
            <span>EB Bill</span>
          </h1>
          <p className="page-subtitle">Record electricity bills per block</p>
        </div>
        <div className="flex items-center gap-2">
          <Building2 className="w-4 h-4 text-slate-400" />
          {loadingBuildings ? (
            <span className="text-xs text-slate-400">Loading...</span>
          ) : buildings.length <= 1 ? (
            <span className="font-semibold text-slate-700 text-sm mr-2">
              {buildings[0]?.name || 'No assigned buildings'}
            </span>
          ) : (
            <select
              id="eb-building-select"
              className="form-input py-1.5 text-sm cursor-pointer"
              value={selectedBuildingId}
              onChange={e => setSelectedBuildingId(e.target.value)}
            >
              {buildings.map(b => (
                <option key={b.id} value={b.id}>{b.name}</option>
              ))}
            </select>
          )}
        </div>
      </div>

      {loadingBuildings || loadingConfig ? (
        <div className="flex justify-center items-center py-20">
          <Loader2 className="w-8 h-8 text-indigo-500 animate-spin" />
        </div>
      ) : !isFormEditable ? (
        <div className="card p-6 flex flex-col gap-4 max-w-xl border border-slate-200 bg-white rounded-xl">
          <div className="flex gap-4">
            <div className="w-10 h-10 rounded-xl bg-slate-50 text-slate-600 border border-slate-200 flex items-center justify-center flex-shrink-0 shadow-sm">
              <Zap className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-heading text-sm font-semibold text-slate-900 mb-1">
                EB Split Method is {ebSplitMethod === 'PER_BED' ? 'Fixed Rate Per Bed' : 'Manager Manual Entry'}
              </h3>
              <p className="text-xs text-slate-500 leading-relaxed">
                {ebSplitMethod === 'PER_BED' 
                  ? "Electricity charges are calculated at a fixed rate per bed and will be automatically included in the monthly invoice. Manual recording is disabled."
                  : "Electricity charges are manually calculated and entered by the manager during billing setup. Manual recording is disabled."}
              </p>
            </div>
          </div>
        </div>
      ) : (
        <div className="fade-in">
          <div className="mb-4 bg-slate-50 border border-slate-200 rounded-xl p-3.5 text-xs text-slate-500 font-medium flex items-center gap-2">
            <span className="text-sm">⚡</span>
            <span>Billing split method is locked to <strong className="text-slate-800">{ebSplitMethod === 'EQUAL_SPLIT' ? 'Equal Split' : 'Sub-Meter Based'}</strong> based on building configuration.</span>
          </div>

          <div className="card max-w-3xl border border-slate-200 bg-white rounded-xl">
            <form onSubmit={submit}>
              <div className="grid grid-cols-2 gap-4">
                <div className="form-group">
                  <label className="form-label">Floor</label>
                  {loadingFloors ? (
                    <div className="flex items-center gap-2 py-2.5 text-slate-400 text-xs font-semibold">
                      <Loader2 className="w-3.5 h-3.5 animate-spin text-primary" />
                      <span>Loading floors...</span>
                    </div>
                  ) : floors.length === 0 ? (
                    <div className="text-xs text-rose-500 font-semibold py-2.5">
                      No floors found
                    </div>
                  ) : (
                    <select
                      id="eb-floor-select"
                      className="form-input cursor-pointer"
                      value={selectedFloorId}
                      onChange={e => setSelectedFloorId(e.target.value)}
                      required
                    >
                      {floors.map(fl => (
                        <option key={fl.id} value={fl.id}>
                          {fl.floorLabel || `Floor ${fl.floorNumber}`}
                        </option>
                      ))}
                    </select>
                  )}
                </div>

                <div className="form-group">
                  <label className="form-label">Block</label>
                  {loadingBlocks ? (
                    <div className="flex items-center gap-2 py-2.5 text-slate-400 text-xs font-semibold">
                      <Loader2 className="w-3.5 h-3.5 animate-spin text-primary" />
                      <span>Loading blocks...</span>
                    </div>
                  ) : blocks.length === 0 ? (
                    <div className="text-xs text-rose-500 font-semibold py-2.5">
                      No blocks found
                    </div>
                  ) : (
                    <select
                      id="eb-block-select"
                      className="form-input cursor-pointer"
                      value={form.blockId}
                      onChange={e => setForm(f => ({ ...f, blockId: e.target.value }))}
                      required
                    >
                      {blocks.map(b => (
                        <option key={b.id} value={b.id}>
                          {b.name || `Block ${b.id}`}
                        </option>
                      ))}
                    </select>
                  )}
                </div>
                {mode === 'EQUAL_SPLIT' ? (
                  <div className="form-group">
                    <label className="form-label">Total EB Amount (₹)</label>
                    <input 
                      type="number" 
                      step="0.01" 
                      className="form-input" 
                      value={form.totalAmount} 
                      onChange={e => setForm(f => ({ ...f, totalAmount: e.target.value }))} 
                      placeholder="e.g. 3600" 
                      autoComplete="off" 
                      name="eb-total-amount" 
                      required 
                    />
                  </div>
                ) : (
                  <div className="form-group">
                    <label className="form-label">Rate Per Unit (₹/kWh)</label>
                    <input 
                      type="number" 
                      step="0.01" 
                      className="form-input" 
                      value={form.ratePerUnit} 
                      onChange={e => setForm(f => ({ ...f, ratePerUnit: e.target.value }))} 
                      placeholder="e.g. 8.50" 
                      autoComplete="off" 
                      name="eb-rate-per-unit" 
                      required 
                    />
                  </div>
                )}
              </div>
              
              <div className="grid-2">
                <div className="form-group">
                  <label className="form-label">Period Start</label>
                  <input 
                    type="date" 
                    className="form-input" 
                    value={form.periodStart} 
                    onChange={e => setForm(f => ({ ...f, periodStart: e.target.value }))} 
                    required 
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Period End</label>
                  <input 
                    type="date" 
                    className="form-input" 
                    value={form.periodEnd} 
                    onChange={e => setForm(f => ({ ...f, periodEnd: e.target.value }))} 
                    required 
                  />
                </div>
              </div>

              {mode === 'METER_BASED' && (
                <div className="mt-4 mb-6 bg-slate-50 border border-slate-200 p-4 rounded-xl">
                  <div className="flex justify-between items-center mb-4">
                    <h4 className="font-heading text-sm font-semibold text-slate-900">Guest Meter Readings</h4>
                    <button 
                      type="button" 
                      className="btn btn-secondary flex items-center gap-1 py-1 px-3 text-xs" 
                      onClick={addReadingRow}
                    >
                      <Plus className="w-3.5 h-3.5" />
                      <span>Add Guest</span>
                    </button>
                  </div>
                  {readings.length === 0 && <p className="text-slate-500 text-xs">No readings added.</p>}
                  {readings.map((r, i) => (
                    <div key={i} style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr auto', gap: '0.5rem', marginBottom: '0.5rem', alignItems: 'end' }}>
                      <div>
                        <label className="form-label">Guest ID</label>
                        <input 
                          className="form-input" 
                          value={r.guestId} 
                          onChange={e => updateReading(i, 'guestId', e.target.value)} 
                          required 
                        />
                      </div>
                      <div>
                        <label className="form-label">Previous</label>
                        <input 
                          type="number" 
                          step="0.01" 
                          className="form-input" 
                          value={r.previousReading} 
                          onChange={e => updateReading(i, 'previousReading', e.target.value)} 
                          required 
                        />
                      </div>
                      <div>
                        <label className="form-label">Current</label>
                        <input 
                          type="number" 
                          step="0.01" 
                          className="form-input" 
                          value={r.currentReading} 
                          onChange={e => updateReading(i, 'currentReading', e.target.value)} 
                          required 
                        />
                      </div>
                      <button 
                        type="button" 
                        className="btn btn-ghost text-red-600 hover:text-red-700 hover:bg-red-50/50 p-2 border-0 shadow-none" 
                        onClick={() => removeReadingRow(i)}
                      >
                        <X className="w-4 h-4" />
                      </button>
                    </div>
                  ))}
                </div>
              )}

              <button type="submit" className="btn btn-primary flex items-center gap-1.5" disabled={saving}>
                {saving ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    <span>Processing...</span>
                  </>
                ) : (
                  <>
                    <Zap className="w-4 h-4" />
                    <span>Split & Save</span>
                  </>
                )}
              </button>
            </form>
             {result && (
              <div className="mt-6 p-4 bg-green-100 text-green-800 border border-green-200 rounded-xl">
                <div className="font-semibold mb-2 flex items-center gap-2 text-green-900 text-sm">
                  <CheckCircle2 className="w-5 h-5 text-green-700" />
                  <span>EB Bill Recorded</span>
                </div>
                <div className="text-xs text-green-700">Bill ID: {result.id}</div>
                <div className="text-xs text-green-700">Total: ₹{result.totalAmount} split among {result.guestShares?.length ?? 0} guests</div>
              </div>
            )}
          </div>
        </div>
      )}
    </AppLayout>
  );
}

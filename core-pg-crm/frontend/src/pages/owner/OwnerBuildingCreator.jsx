import React, { useState, useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import AppLayout from '../../components/AppLayout';
import { ownerApi } from '../../api';
import {
  Building, Plus, Trash2, ChevronRight, ChevronLeft,
  CheckCircle2, Layers, LayoutGrid, Bed, AlertCircle, Loader2,
  ArrowLeft, Save, MapPin, Pencil
} from 'lucide-react';

const SHARING_LABELS = { 1: 'Single', 2: 'Double', 3: 'Triple', 4: 'Quad' };
const SHARING_COLORS = {
  1: 'bg-violet-50 text-violet-700 border-violet-100',
  2: 'bg-blue-50 text-blue-700 border-blue-100',
  3: 'bg-emerald-50 text-emerald-700 border-emerald-100',
  4: 'bg-amber-50 text-amber-700 border-amber-100',
};

const uid = () => Math.random().toString(36).slice(2, 8);

function makeFloor(number) {
  return {
    _id: uid(),
    number,
    label: number === 0 ? 'Ground Floor' : `${number}${['st','nd','rd'][number-1]||'th'} Floor`,
    rooms: [],
    blocks: [],
  };
}
function makeBlock(name = 'Block A') {
  return { _id: uid(), name, roomConfigs: [makeRoomConfig()] };
}
function makeRoomConfig(sharing = 2) {
  return { _id: uid(), sharing, count: 1, baseRent: '', roomNumbers: [''], isAc: true, bedLabels: '' };
}

// ── Step components for Creator ───────────────────────────────────

function Step1({ data, onChange }) {
  return (
    <div className="flex flex-col gap-5">
      <div>
        <h2 className="font-heading text-lg font-semibold text-slate-900 mb-1">Building Details</h2>
        <p className="text-sm text-slate-500">Give your new building a name and address.</p>
      </div>
      <div className="form-group">
        <label className="form-label">Building Name *</label>
        <input
          id="building-name"
          className="form-input"
          placeholder="e.g. Building B, East Block"
          value={data.name}
          onChange={e => onChange({ ...data, name: e.target.value })}
          required
        />
      </div>
      <div className="form-group">
        <label className="form-label">Address</label>
        <input
          id="building-address"
          className="form-input"
          placeholder="e.g. 456 Side Street, Bengaluru"
          value={data.address}
          onChange={e => onChange({ ...data, address: e.target.value })}
        />
      </div>
    </div>
  );
}

function Step2({ floors, onChange }) {
  const addFloor = () => {
    const nums = floors.map(f => f.number);
    const next = nums.length ? Math.max(...nums) + 1 : 0;
    onChange([...floors, makeFloor(next)]);
  };

  const removeFloor = (id) => onChange(floors.filter(f => f._id !== id));

  const updateFloor = (id, field, val) =>
    onChange(floors.map(f => f._id === id ? { ...f, [field]: val } : f));

  return (
    <div className="flex flex-col gap-5">
      <div>
        <h2 className="font-heading text-lg font-semibold text-slate-900 mb-1">Define Floors</h2>
        <p className="text-sm text-slate-500">Add all floors in your building. You can rename each floor label.</p>
      </div>
      <div className="flex flex-col gap-3">
        {floors.map(floor => (
          <div key={floor._id} className="flex items-center gap-3 p-3 rounded-xl border border-slate-200 bg-slate-50">
            <div className="w-10 h-10 rounded-lg bg-indigo-100 flex items-center justify-center flex-shrink-0">
              <span className="text-indigo-700 font-bold text-sm">{floor.number === 0 ? 'G' : floor.number}</span>
            </div>
            <input
              className="form-input flex-1"
              value={floor.label}
              onChange={e => updateFloor(floor._id, 'label', e.target.value)}
              placeholder="Floor label"
            />
            <button
              type="button"
              className="p-1.5 rounded-lg text-red-400 hover:text-red-600 hover:bg-red-50"
              onClick={() => removeFloor(floor._id)}
            >
              <Trash2 className="w-4 h-4" strokeWidth={1.5}/>
            </button>
          </div>
        ))}
        {floors.length === 0 && (
          <p className="text-slate-400 text-sm text-center py-4">No floors yet. Add at least one floor.</p>
        )}
      </div>
      <button
        type="button"
        className="btn btn-ghost border border-dashed border-slate-300 flex items-center gap-2 justify-center"
        onClick={addFloor}
      >
        <Plus className="w-4 h-4" strokeWidth={1.5}/> Add Floor
      </button>
    </div>
  );
}

function RoomConfigRow({ rc, onUpdate, onRemove, floorNumber, blockName }) {
  const placeholders = [];
  const blockCode = blockName ? (blockName.split(' ').pop() || 'A') : '';
  for (let i = 0; i < rc.count; i++) {
    if (blockName) {
      placeholders.push(`${floorNumber}${blockCode}-${rc.sharing}S${rc.count > 1 ? (i + 1) : ''}`);
    } else {
      const prefix = floorNumber === 0 ? 'G' : String(floorNumber);
      placeholders.push(`${prefix}-${String(i + 1).padStart(2, '0')}`);
    }
  }

  const roomNumbers = rc.roomNumbers || [];

  const handleRoomNumberChange = (index, val) => {
    const updated = [...roomNumbers];
    while (updated.length < rc.count) updated.push('');
    updated[index] = val;
    onUpdate({ ...rc, roomNumbers: updated });
  };

  return (
    <div className="flex flex-col gap-2 p-2.5 rounded-xl bg-white border border-slate-200">
      <div className="flex items-center gap-2">
        <select
          className="form-input py-1 text-xs flex-shrink-0 w-28"
          value={rc.sharing}
          onChange={e => onUpdate({ ...rc, sharing: parseInt(e.target.value) })}
        >
          {[1, 2, 3, 4].map(s => (
            <option key={s} value={s}>{SHARING_LABELS[s]}</option>
          ))}
        </select>
        <input
          type="text"
          className="form-input py-1 text-xs w-20 flex-shrink-0"
          placeholder="Count"
          value={rc.count}
          onChange={e => {
            const valStr = e.target.value.replace(/[^0-9]/g, '');
            if (valStr === '') {
              onUpdate({ ...rc, count: '', roomNumbers: [] });
              return;
            }
            const newCount = parseInt(valStr) || 1;
            const updatedRooms = [...roomNumbers];
            if (updatedRooms.length < newCount) {
              while (updatedRooms.length < newCount) updatedRooms.push('');
            } else {
              updatedRooms.splice(newCount);
            }
            onUpdate({ ...rc, count: newCount, roomNumbers: updatedRooms });
          }}
        />
        <div className="flex items-center gap-1 flex-1">
          <span className="text-slate-400 text-xs">₹</span>
          <input
            type="text"
            className="form-input py-1 text-xs"
            placeholder="Base rent/bed"
            value={rc.baseRent}
            onChange={e => {
              const clean = e.target.value.replace(/[^0-9]/g, '');
              onUpdate({ ...rc, baseRent: clean });
            }}
            autoComplete="off"
            name={`base-rent-${rc._id}`}
            id={`base-rent-${rc._id}`}
          />
        </div>
        <label className="flex items-center gap-1.5 cursor-pointer select-none py-1 px-2 rounded-lg bg-slate-50 border border-slate-200">
          <input
            type="checkbox"
            className="rounded border-slate-300 text-indigo-600 focus:ring-indigo-500 w-3.5 h-3.5"
            checked={rc.isAc === false}
            onChange={e => onUpdate({ ...rc, isAc: !e.target.checked })}
          />
          <span className="text-[10px] font-bold uppercase tracking-wider text-slate-500">Non-AC Room</span>
        </label>
        <button type="button" className="p-1 text-red-400 hover:text-red-600" onClick={onRemove}>
          <Trash2 className="w-3.5 h-3.5" strokeWidth={1.5}/>
        </button>
      </div>

      <div className="border-t border-slate-100 pt-2 mt-1">
        <div className="flex flex-col gap-2">
          <div>
            <div className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1 flex justify-between">
              <span>Bed Labels (comma-separated)</span>
              <span className="text-slate-300 normal-case font-normal">(Optional — e.g. Lower, Upper)</span>
            </div>
            <input
              type="text"
              className="form-input py-1 px-2 text-[11px] w-full"
              placeholder={Array.from({ length: rc.sharing }).map((_, i) => `B${i + 1}`).join(', ')}
              value={rc.bedLabels || ''}
              onChange={e => onUpdate({ ...rc, bedLabels: e.target.value })}
            />
          </div>
          <div>
            <div className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5 flex justify-between">
              <span>Room Numbers</span>
              <span className="text-slate-300 normal-case font-normal">(Optional — defaults shown as placeholders)</span>
            </div>
            <div className="flex flex-wrap gap-2">
              {Array.from({ length: rc.count }).map((_, idx) => (
                <div key={idx} className="flex items-center gap-1">
                  {rc.count > 1 && <span className="text-[10px] text-slate-400 font-medium">#{idx + 1}:</span>}
                  <input
                    type="text"
                    className="form-input py-0.5 px-2 text-[11px] w-24 font-semibold"
                    placeholder={placeholders[idx]}
                    value={roomNumbers[idx] || ''}
                    onChange={e => handleRoomNumberChange(idx, e.target.value)}
                  />
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function Step3({ floors, onChange }) {
  const updateFloor = (id, updated) =>
    onChange(floors.map(f => f._id === id ? updated : f));

  const [blockModalFloorId, setBlockModalFloorId] = useState(null);
  const [newBlockName, setNewBlockName] = useState('');

  const addBlock = (floorId) => {
    setBlockModalFloorId(floorId);
    setNewBlockName('');
  };

  const confirmAddBlock = () => {
    if (!newBlockName.trim()) return;
    const floor = floors.find(f => f._id === blockModalFloorId);
    updateFloor(blockModalFloorId, { ...floor, blocks: [...floor.blocks, makeBlock(newBlockName.trim())] });
    setBlockModalFloorId(null);
    setNewBlockName('');
  };

  const removeBlock = (floorId, blockId) => {
    const floor = floors.find(f => f._id === floorId);
    updateFloor(floorId, { ...floor, blocks: floor.blocks.filter(b => b._id !== blockId) });
  };

  const updateBlock = (floorId, blockId, updated) => {
    const floor = floors.find(f => f._id === floorId);
    updateFloor(floorId, { ...floor, blocks: floor.blocks.map(b => b._id === blockId ? updated : b) });
  };

  const addStandaloneRoom = (floorId) => {
    const floor = floors.find(f => f._id === floorId);
    updateFloor(floorId, { ...floor, rooms: [...floor.rooms, makeRoomConfig()] });
  };

  const updateStandaloneRoom = (floorId, rcId, updated) => {
    const floor = floors.find(f => f._id === floorId);
    updateFloor(floorId, { ...floor, rooms: floor.rooms.map(r => r._id === rcId ? updated : r) });
  };

  const removeStandaloneRoom = (floorId, rcId) => {
    const floor = floors.find(f => f._id === floorId);
    updateFloor(floorId, { ...floor, rooms: floor.rooms.filter(r => r._id !== rcId) });
  };

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h2 className="font-heading text-lg font-semibold text-slate-900 mb-1">Configure Rooms</h2>
        <p className="text-sm text-slate-500">For each floor, add blocks with rooms or standalone rooms directly on the floor.</p>
      </div>

      {floors.map(floor => (
        <div key={floor._id} className="rounded-2xl border border-slate-200 bg-slate-50 overflow-hidden">
          <div className="bg-indigo-600 px-4 py-2.5 flex items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <Layers className="w-4 h-4 text-indigo-200" strokeWidth={1.5}/>
              <span className="text-white font-semibold text-sm">{floor.label}</span>
            </div>
            <button
              type="button"
              className="p-1 rounded text-indigo-200 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
              onClick={() => onChange(floors.filter(f => f._id !== floor._id))}
              title="Delete Floor"
            >
              <Trash2 className="w-4 h-4" strokeWidth={1.5}/>
            </button>
          </div>

          <div className="p-4 flex flex-col gap-4">
            {/* Blocks */}
            {floor.blocks.map(block => (
              <div key={block._id} className="rounded-xl border border-slate-200 bg-white overflow-hidden">
                <div className="bg-slate-100 px-3 py-2 flex items-center justify-between">
                  <input
                    className="bg-transparent text-sm font-semibold text-slate-700 border-none outline-none font-sans"
                    value={block.name}
                    onChange={e => updateBlock(floor._id, block._id, { ...block, name: e.target.value })}
                  />
                  <button type="button" className="text-red-400 hover:text-red-600 p-1"
                    onClick={() => removeBlock(floor._id, block._id)}>
                    <Trash2 className="w-3.5 h-3.5" strokeWidth={1.5}/>
                  </button>
                </div>
                <div className="p-3 flex flex-col gap-2">
                  <div className="grid grid-cols-4 text-xs text-slate-400 font-medium px-2 mb-1">
                    <span>Sharing</span><span>Count</span><span>Rent/Bed</span><span></span>
                  </div>
                  {block.roomConfigs.map(rc => (
                    <RoomConfigRow
                      key={rc._id}
                      rc={rc}
                      floorNumber={floor.number}
                      blockName={block.name}
                      onUpdate={updated => updateBlock(floor._id, block._id, {
                        ...block,
                        roomConfigs: block.roomConfigs.map(r => r._id === rc._id ? updated : r)
                      })}
                      onRemove={() => updateBlock(floor._id, block._id, {
                        ...block,
                        roomConfigs: block.roomConfigs.filter(r => r._id !== rc._id)
                      })}
                    />
                  ))}
                  <button type="button"
                    className="text-xs text-indigo-600 hover:text-indigo-800 flex items-center gap-1 mt-1 font-semibold"
                    onClick={() => updateBlock(floor._id, block._id, {
                      ...block, roomConfigs: [...block.roomConfigs, makeRoomConfig()]
                    })}>
                    <Plus className="w-3 h-3" strokeWidth={1.5}/> Add room type
                  </button>
                </div>
              </div>
            ))}

            {/* Standalone rooms */}
            {floor.rooms.length > 0 && (
              <div className="rounded-xl border border-slate-200 bg-white p-3">
                <p className="text-xs font-semibold text-slate-500 mb-2">Standalone Rooms (no block)</p>
                <div className="grid grid-cols-4 text-xs text-slate-400 font-medium px-2 mb-1">
                  <span>Sharing</span><span>Count</span><span>Rent/Bed</span><span></span>
                </div>
                {floor.rooms.map(rc => (
                  <RoomConfigRow
                    key={rc._id}
                    rc={rc}
                    floorNumber={floor.number}
                    blockName={null}
                    onUpdate={updated => updateStandaloneRoom(floor._id, rc._id, updated)}
                    onRemove={() => removeStandaloneRoom(floor._id, rc._id)}
                  />
                ))}
              </div>
            )}

            <div className="flex gap-2 mt-1">
              <button type="button"
                className="btn btn-ghost border border-dashed border-slate-300 text-xs flex items-center gap-1 py-1 px-3 shadow-none bg-transparent hover:bg-slate-100"
                onClick={() => addBlock(floor._id)}>
                <Plus className="w-3.5 h-3.5" strokeWidth={1.5}/> Add Block
              </button>
              <button type="button"
                className="btn btn-ghost border border-dashed border-slate-300 text-xs flex items-center gap-1 py-1 px-3 shadow-none bg-transparent hover:bg-slate-100"
                onClick={() => addStandaloneRoom(floor._id)}>
                <Plus className="w-3.5 h-3.5" strokeWidth={1.5}/> Add Standalone Room
              </button>
            </div>
          </div>
        </div>
      ))}

      {blockModalFloorId && (
        <div className="fixed inset-0 bg-slate-900/55 backdrop-blur-[2px] flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl p-5 shadow-xl border border-slate-100 w-full max-w-sm mx-4 animate-in fade-in zoom-in duration-200">
            <h3 className="text-sm font-bold text-slate-800 mb-1">Enter Block Name</h3>
            <p className="text-xs text-slate-500 mb-4">(e.g. Block A, Wing B)</p>
            <input
              type="text"
              className="form-input w-full mb-4 font-semibold text-xs py-2"
              placeholder="e.g. Block A"
              value={newBlockName}
              onChange={e => setNewBlockName(e.target.value)}
              autoFocus
              onKeyDown={e => {
                if (e.key === 'Enter') {
                  confirmAddBlock();
                }
              }}
            />
            <div className="flex justify-end gap-2 text-xs">
              <button
                type="button"
                className="btn btn-ghost py-1 px-3"
                onClick={() => {
                  setBlockModalFloorId(null);
                  setNewBlockName('');
                }}
              >
                Cancel
              </button>
              <button
                type="button"
                className="btn btn-primary py-1 px-3"
                onClick={confirmAddBlock}
                disabled={!newBlockName.trim()}
              >
                OK
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function Step4Pricing({ data, onChange, isEditMode = false }) {
  const FOOD_ITEMS = [
    { key: 'breakfastPrice',      label: 'Breakfast Price (₹)',       placeholder: '60' },
    { key: 'lunchPrice',          label: 'Lunch Price (₹)',            placeholder: '65' },
    { key: 'dinnerPrice',         label: 'Dinner Price (₹)',           placeholder: '60' },
    { key: 'omelettePrice',       label: 'Omelette Price (₹)',         placeholder: '18' },
    { key: 'boiledEggPrice',      label: 'Boiled Egg Price (₹)',       placeholder: '18' },
    { key: 'washingMachinePrice', label: 'Washing Machine Price (₹)',  placeholder: '50' }
  ];

  const [enablePremiumSurcharges, setEnablePremiumSurcharges] = useState(
    data.foodIncludedInRent && (parseFloat(data.omelettePrice || 0) > 0 || parseFloat(data.boiledEggPrice || 0) > 0)
  );

  useEffect(() => {
    if (!data.foodIncludedInRent) {
      setEnablePremiumSurcharges(false);
    } else {
      setEnablePremiumSurcharges(parseFloat(data.omelettePrice || 0) > 0 || parseFloat(data.boiledEggPrice || 0) > 0);
    }
  }, [data.foodIncludedInRent, data.omelettePrice, data.boiledEggPrice]);

  return (
    <div className="flex flex-col gap-5">
      <div>
        <h2 className="font-heading text-lg font-semibold text-slate-900 mb-1">Pricing &amp; Rules Config</h2>
        <p className="text-sm text-slate-500">Configure default prices for meals/add-ons and key business rules for this building.</p>
      </div>

      <div className="p-4 rounded-xl border border-slate-200 bg-slate-50 space-y-3.5">
        <h3 className="text-xs font-bold text-slate-500 uppercase tracking-wider flex items-center gap-1.5">
          <span>⚙️</span> Business Rules
        </h3>
        <label className={`flex items-center gap-2.5 text-xs font-semibold text-slate-700 ${isEditMode ? 'cursor-not-allowed' : 'cursor-pointer'}`}>
          <input
            type="checkbox"
            checked={data.foodIncludedInRent}
            disabled={isEditMode}
            onChange={e => {
              const checked = e.target.checked;
              if (checked) {
                onChange({
                  ...data,
                  foodIncludedInRent: checked,
                  breakfastPrice: '0',
                  lunchPrice: '0',
                  dinnerPrice: '0'
                });
              } else {
                onChange({
                  ...data,
                  foodIncludedInRent: checked
                });
              }
            }}
          />
          <span>Food Included in Rent (Guests won't be charged extra for daily meals)</span>
        </label>
        {data.foodIncludedInRent && (
          <label className={`flex items-center gap-2.5 text-xs font-semibold text-slate-700 pl-6 cursor-pointer`}>
            <input
              type="checkbox"
              checked={enablePremiumSurcharges}
              disabled={isEditMode}
              onChange={e => {
                const checked = e.target.checked;
                setEnablePremiumSurcharges(checked);
                if (!checked) {
                  onChange({
                    ...data,
                    omelettePrice: '0',
                    boiledEggPrice: '0'
                  });
                }
              }}
            />
            <span>Enable Premium Add-on Surcharges</span>
          </label>
        )}
        <label className={`flex items-center gap-2.5 text-xs font-semibold text-slate-700 ${isEditMode ? 'cursor-not-allowed' : 'cursor-pointer'}`}>
          <input
            type="checkbox"
            checked={data.allowMealCancellations}
            disabled={isEditMode}
            onChange={e => onChange({ ...data, allowMealCancellations: e.target.checked })}
          />
          <span>Allow Meal Cancellations (Guests can cancel meals up to lockout time)</span>
        </label>
        <div className="form-group mb-0">
          <label className="form-label text-xs">EB Split Method</label>
          <select
            className={`form-input text-xs py-1 ${isEditMode ? 'bg-slate-50 text-slate-500 cursor-not-allowed' : ''}`}
            value={data.ebSplitMethod || 'EQUAL_SPLIT'}
            disabled={isEditMode}
            onChange={e => onChange({ ...data, ebSplitMethod: e.target.value })}
          >
            <option value="EQUAL_SPLIT">Equal Split</option>
            <option value="PER_BED">Per Bed Rate</option>
            <option value="METER_BASED">Meter-based split</option>
            <option value="MANAGER_MANUAL">Manager Manual</option>
          </select>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4">
        {FOOD_ITEMS.filter(({ key }) => {
          if (data.foodIncludedInRent && ['breakfastPrice', 'lunchPrice', 'dinnerPrice'].includes(key)) {
            return false;
          }
          if (data.foodIncludedInRent && ['omelettePrice', 'boiledEggPrice'].includes(key) && !enablePremiumSurcharges) {
            return false;
          }
          return true;
        }).map(({ key, label, placeholder }) => (
          <div key={key} className="form-group mb-0">
            <label className="form-label text-xs">{label}</label>
            <input
              type="text"
              className={`form-input text-xs py-1.5 ${isEditMode ? 'bg-slate-50 text-slate-500 cursor-not-allowed' : ''}`}
              placeholder={placeholder}
              value={data[key]}
              disabled={isEditMode}
              onChange={e => {
                const clean = e.target.value.replace(/[^0-9.]/g, '');
                onChange({ ...data, [key]: clean });
              }}
            />
          </div>
        ))}
      </div>
    </div>
  );
}

function Step4({ building, floors }) {
  let totalRooms = 0, totalBeds = 0, totalBlocks = 0;
  floors.forEach(f => {
    totalBlocks += f.blocks.length;
    f.rooms.forEach(r => { totalRooms += r.count; totalBeds += r.count * r.sharing; });
    f.blocks.forEach(b => b.roomConfigs.forEach(r => { totalRooms += r.count; totalBeds += r.count * r.sharing; }));
  });

  return (
    <div className="flex flex-col gap-5">
      <div>
        <h2 className="font-heading text-lg font-semibold text-slate-900 mb-1">Review &amp; Create</h2>
        <p className="text-sm text-slate-500">Confirm the configuration before creating the building.</p>
      </div>

      <div className="rounded-2xl border border-indigo-200 bg-indigo-50 p-5">
        <div className="flex items-center gap-3 mb-4">
          <Building className="w-6 h-6 text-indigo-600" strokeWidth={1.5}/>
          <div>
            <div className="font-bold text-slate-800 text-base">{building.name}</div>
            {building.address && <div className="text-xs text-slate-500">{building.address}</div>}
          </div>
        </div>
        <div className="grid grid-cols-4 gap-3">
          {[
            { label: 'Floors', value: floors.length, icon: Layers },
            { label: 'Blocks', value: totalBlocks, icon: LayoutGrid },
            { label: 'Rooms', value: totalRooms, icon: LayoutGrid },
            { label: 'Beds', value: totalBeds, icon: Bed },
          ].map(({ label, value, icon: Icon }) => (
            <div key={label} className="bg-white rounded-xl p-3 text-center shadow-sm">
              <div className="text-2xl font-black text-indigo-700">{value}</div>
              <div className="text-xs text-slate-500 mt-0.5">{label}</div>
            </div>
          ))}
        </div>
      </div>

      {/* Pricing & Rules Summary Section */}
      <div className="rounded-xl border border-slate-200 p-4 bg-slate-50">
        <div className="font-bold text-slate-700 text-xs uppercase tracking-wider mb-2.5">
          Pricing &amp; Rules Summary
        </div>
        <div className="grid grid-cols-2 gap-y-2 gap-x-4 text-xs text-slate-600">
          <div><strong className="text-slate-500">Food Included:</strong> {building.foodIncludedInRent ? 'Yes' : 'No'}</div>
          <div><strong className="text-slate-500">Allow Meal Cancellations:</strong> {building.allowMealCancellations ? 'Yes' : 'No'}</div>
          <div><strong className="text-slate-500">EB Split Method:</strong> {building.ebSplitMethod}</div>
          <div><strong className="text-slate-500">Breakfast Price:</strong> ₹{building.breakfastPrice}</div>
          <div><strong className="text-slate-500">Lunch Price:</strong> ₹{building.lunchPrice}</div>
          <div><strong className="text-slate-500">Dinner Price:</strong> ₹{building.dinnerPrice}</div>
          <div><strong className="text-slate-500">Omelette Price:</strong> ₹{building.omelettePrice}</div>
          <div><strong className="text-slate-500">Boiled Egg Price:</strong> ₹{building.boiledEggPrice}</div>
          <div><strong className="text-slate-500">Washing Machine:</strong> ₹{building.washingMachinePrice}</div>
        </div>
      </div>

      <div className="flex flex-col gap-3">
        {floors.map(floor => (
          <div key={floor._id} className="rounded-xl border border-slate-200 p-3 bg-white">
            <div className="font-semibold text-slate-700 text-sm mb-2 flex items-center gap-1.5">
              <Layers className="w-3.5 h-3.5 text-slate-400" strokeWidth={1.5}/> {floor.label}
            </div>
            {floor.blocks.map(block => (
              <div key={block._id} className="ml-4 mb-2">
                <span className="text-xs font-semibold text-slate-500">{block.name}: </span>
                <span className="text-xs text-slate-500">
                  {block.roomConfigs.map(r => {
                    const customText = r.roomNumbers && r.roomNumbers.some(n => n)
                      ? ` [Rooms: ${r.roomNumbers.map((n, idx) => n || `Auto(#${idx+1})`).join(', ')}]`
                      : '';
                    return `${r.count}× ${SHARING_LABELS[r.sharing]} (₹${r.baseRent}/bed)${customText}`;
                  }).join(', ')}
                </span>
              </div>
            ))}
            {floor.rooms.map(r => {
              const customText = r.roomNumbers && r.roomNumbers.some(n => n)
                ? ` [Rooms: ${r.roomNumbers.map((n, idx) => n || `Auto(#${idx+1})`).join(', ')}]`
                : '';
              return (
                <div key={r._id} className="ml-4 text-xs text-slate-500">
                  Standalone: {r.count}× {SHARING_LABELS[r.sharing]} (₹{r.baseRent}/bed){customText}
                </div>
              );
            })}
          </div>
        ))}
      </div>
    </div>
  );
}

// ── Building Module Root Component ────────────────────────────────

export default function OwnerBuildingCreator() {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const editParam = searchParams.get('edit');
  const [mode, setMode] = useState('list'); // 'list' | 'create' | 'edit'
  const [buildings, setBuildings] = useState([]);
  const [loadingList, setLoadingList] = useState(true);

  useEffect(() => {
    if (editParam) {
      handleStartEdit(editParam);
    } else {
      setMode('list');
    }
  }, [editParam]);

  // Wizard state (create mode)
  const [step, setStep] = useState(1);
  const [building, setBuilding] = useState({
    name: '',
    address: '',
    foodIncludedInRent: false,
    allowMealCancellations: true,
    breakfastPrice: '0',
    lunchPrice: '0',
    dinnerPrice: '0',
    omelettePrice: '0',
    boiledEggPrice: '0',
    washingMachinePrice: '0',
    ebSplitMethod: 'EQUAL_SPLIT'
  });
  const [floors, setFloors] = useState([makeFloor(0)]);
  const [saving, setSaving] = useState(false);
  const [result, setResult] = useState(null);

  // Editor state (edit mode)
  const [editBuildingId, setEditBuildingId] = useState('');
  const [editData, setEditData] = useState(null); // BuildingEditRequest structure
  const [loadingEdit, setLoadingEdit] = useState(false);
  const [savingEdit, setSavingEdit] = useState(false);

  // Deletion state
  const [buildingToDelete, setBuildingToDelete] = useState(null);
  const [deleteConfirmInput, setDeleteConfirmInput] = useState('');
  const [deletingId, setDeletingId] = useState('');

  const [error, setError] = useState('');

  // ── Loading Buildings List ───────────────────────────────────────
  const fetchBuildings = async () => {
    setLoadingList(true);
    try {
      const res = await ownerApi.getBranches();
      const fullList = await Promise.all(
        res.data.map(async (b) => {
          try {
            const layoutRes = await ownerApi.getBuildingLayout(b.id);
            let rooms = 0, beds = 0, blocks = 0;
            layoutRes.data.floors.forEach(f => {
              blocks += f.blocks.length;
              f.rooms.forEach(r => { rooms++; beds += r.sharing; });
              f.blocks.forEach(bl => bl.rooms.forEach(r => { rooms++; beds += r.sharing; }));
            });
            return {
              ...b,
              totalFloors: layoutRes.data.floors.length,
              totalBlocks: blocks,
              totalRooms: rooms,
              totalBeds: beds
            };
          } catch {
            return { ...b, totalFloors: 0, totalBlocks: 0, totalRooms: 0, totalBeds: 0 };
          }
        })
      );
      setBuildings(fullList);
    } catch (err) {
      setError('Failed to fetch buildings.');
    } finally {
      setLoadingList(false);
    }
  };

  const handleDeleteBuilding = async () => {
    if (!buildingToDelete) return;
    if (deleteConfirmInput.trim().toLowerCase() !== buildingToDelete.name.trim().toLowerCase()) {
      alert("Please type the building name exactly to confirm deletion.");
      return;
    }
    setDeletingId(buildingToDelete.id);
    setError('');
    try {
      await ownerApi.deleteBuilding(buildingToDelete.id);
      setBuildingToDelete(null);
      setDeleteConfirmInput('');
      fetchBuildings();
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to delete building');
    } finally {
      setDeletingId('');
    }
  };

  useEffect(() => {
    if (mode === 'list') {
      fetchBuildings();
    }
  }, [mode]);

  useEffect(() => {
    // Auto-cascade block deletes on 0 room configs (empty blocks) in wizard mode
    let changed = false;
    const cleanedFloors = floors.map(f => {
      const activeBlocks = f.blocks.filter(b => b.roomConfigs && b.roomConfigs.length > 0);
      if (activeBlocks.length !== f.blocks.length) {
        changed = true;
        return { ...f, blocks: activeBlocks };
      }
      return f;
    });
    if (changed) {
      setFloors(cleanedFloors);
    }
  }, [floors]);

  useEffect(() => {
    // Auto-cascade block deletes on 0 rooms (empty blocks) in layout edit mode
    if (!editData || !editData.floors) return;
    let changed = false;
    const cleanedFloors = editData.floors.map(f => {
      const activeBlocks = f.blocks.filter(b => b.rooms && b.rooms.length > 0);
      if (activeBlocks.length !== f.blocks.length) {
        changed = true;
        return { ...f, blocks: activeBlocks };
      }
      return f;
    });
    if (changed) {
      setEditData(prev => ({ ...prev, floors: cleanedFloors }));
    }
  }, [editData?.floors]);

  // ── Layout Editor Handlers ───────────────────────────────────────
  const handleStartEdit = async (bldId) => {
    setEditBuildingId(bldId);
    setMode('edit');
    setLoadingEdit(true);
    setError('');
    try {
      const res = await ownerApi.getBuildingLayout(bldId);
      const data = res.data;
      
      // Ensure defaults for pricing/rules configs
      data.foodIncludedInRent = data.foodIncludedInRent ?? false;
      data.allowMealCancellations = data.allowMealCancellations ?? true;
      data.breakfastPrice = data.breakfastPrice !== undefined && data.breakfastPrice !== null ? String(data.breakfastPrice) : '0';
      data.lunchPrice = data.lunchPrice !== undefined && data.lunchPrice !== null ? String(data.lunchPrice) : '0';
      data.dinnerPrice = data.dinnerPrice !== undefined && data.dinnerPrice !== null ? String(data.dinnerPrice) : '0';
      data.omelettePrice = data.omelettePrice !== undefined && data.omelettePrice !== null ? String(data.omelettePrice) : '0';
      data.boiledEggPrice = data.boiledEggPrice !== undefined && data.boiledEggPrice !== null ? String(data.boiledEggPrice) : '0';
      data.washingMachinePrice = data.washingMachinePrice !== undefined && data.washingMachinePrice !== null ? String(data.washingMachinePrice) : '0';
      data.ebSplitMethod = data.ebSplitMethod || 'EQUAL_SPLIT';

      data.floors = data.floors.map(f => ({
        ...f,
        _tempId: uid(),
        rooms: f.rooms.map(r => ({ ...r, _tempId: uid() })),
        blocks: f.blocks.map(bl => ({
          ...bl,
          _tempId: uid(),
          rooms: bl.rooms.map(r => ({ ...r, _tempId: uid() }))
        }))
      }));
      setEditData(data);
    } catch (err) {
      setError('Failed to load building layout details.');
    } finally {
      setLoadingEdit(false);
    }
  };

  // Layout edits helper updates
  const updateBuildingField = (field, val) => {
    setEditData(prev => ({ ...prev, [field]: val }));
  };

  const addEditFloor = () => {
    const nums = editData.floors.map(f => f.number);
    const next = nums.length ? Math.max(...nums) + 1 : 0;
    const newFloor = {
      id: '',
      _tempId: uid(),
      number: next,
      label: next === 0 ? 'Ground Floor' : `${next}${['st','nd','rd'][next-1]||'th'} Floor`,
      rooms: [],
      blocks: []
    };
    setEditData(prev => ({ ...prev, floors: [...prev.floors, newFloor] }));
  };

  const deleteEditFloor = (id, tempId) => {
    const floor = editData.floors.find(f => id ? f.id === id : f._tempId === tempId);
    let hasOccupied = false;
    floor.rooms.forEach(r => { if (r.occupiedBedsCount > 0) hasOccupied = true; });
    floor.blocks.forEach(b => b.rooms.forEach(r => { if (r.occupiedBedsCount > 0) hasOccupied = true; }));

    if (hasOccupied) {
      alert("Cannot delete floor because it contains rooms with active guests checked in.");
      return;
    }

    setEditData(prev => ({
      ...prev,
      floors: prev.floors.filter(f => id ? f.id !== id : f._tempId !== tempId)
    }));
  };

  const addEditBlock = (floorId, floorTempId) => {
    setEditData(prev => ({
      ...prev,
      floors: prev.floors.map(f => {
        const match = floorId ? f.id === floorId : f._tempId === floorTempId;
        if (!match) return f;
        const letter = String.fromCharCode(65 + f.blocks.length);
        const nextRoomNum = `${f.number}${letter}-2S1`;
        const newBlock = {
          id: '',
          _tempId: uid(),
          name: `Block ${letter}`,
          rooms: [
            {
              id: '',
              _tempId: uid(),
              roomNumber: nextRoomNum,
              sharing: 2,
              baseRent: 8000,
              isAc: true,
              occupiedBedsCount: 0
            }
          ]
        };
        return { ...f, blocks: [...f.blocks, newBlock] };
      })
    }));
  };

  const deleteEditBlock = (floorId, floorTempId, blockId, blockTempId) => {
    const floor = editData.floors.find(f => floorId ? f.id === floorId : f._tempId === floorTempId);
    const block = floor.blocks.find(b => blockId ? b.id === blockId : b._tempId === blockTempId);
    const hasOccupied = block.rooms.some(r => r.occupiedBedsCount > 0);
    if (hasOccupied) {
      alert("Cannot delete block because it contains rooms with active guests checked in.");
      return;
    }

    setEditData(prev => ({
      ...prev,
      floors: prev.floors.map(f => {
        const match = floorId ? f.id === floorId : f._tempId === floorTempId;
        if (!match) return f;
        return {
          ...f,
          blocks: f.blocks.filter(b => blockId ? b.id !== blockId : b._tempId !== blockTempId)
        };
      })
    }));
  };

  const addEditRoom = (floorId, floorTempId, blockId, blockTempId) => {
    const newRoom = {
      id: '',
      _tempId: uid(),
      roomNumber: '',
      sharing: 2,
      baseRent: 8000,
      isAc: true,
      occupiedBedsCount: 0
    };

    setEditData(prev => ({
      ...prev,
      floors: prev.floors.map(f => {
        const matchFloor = floorId ? f.id === floorId : f._tempId === floorTempId;
        if (!matchFloor) return f;

        if (blockId || blockTempId) {
          return {
            ...f,
            blocks: f.blocks.map(b => {
              const matchBlock = blockId ? b.id === blockId : b._tempId === blockTempId;
              if (!matchBlock) return b;
              const blockCode = b.name.split(' ').pop() || 'A';
              const nextRoomNum = `${f.number}${blockCode}-${newRoom.sharing}S${b.rooms.length + 1}`;
              return { ...b, rooms: [...b.rooms, { ...newRoom, roomNumber: nextRoomNum }] };
            })
          };
        } else {
          const prefix = f.number === 0 ? 'G' : String(f.number);
          const nextRoomNum = `${prefix}-${String(f.rooms.length + 1).padStart(2, '0')}`;
          return { ...f, rooms: [...f.rooms, { ...newRoom, roomNumber: nextRoomNum }] };
        }
      })
    }));
  };

  const deleteEditRoom = (floorId, floorTempId, blockId, blockTempId, roomId, roomTempId) => {
    const floor = editData.floors.find(f => floorId ? f.id === floorId : f._tempId === floorTempId);
    let room;
    if (blockId || blockTempId) {
      const block = floor.blocks.find(b => blockId ? b.id === blockId : b._tempId === blockTempId);
      room = block.rooms.find(r => roomId ? r.id === roomId : r._tempId === roomTempId);
    } else {
      room = floor.rooms.find(r => roomId ? r.id === roomId : r._tempId === roomTempId);
    }

    if (room && room.occupiedBedsCount > 0) {
      alert(`Cannot delete room ${room.roomNumber} because it currently contains active guests.`);
      return;
    }

    setEditData(prev => ({
      ...prev,
      floors: prev.floors.map(f => {
        const matchFloor = floorId ? f.id === floorId : f._tempId === floorTempId;
        if (!matchFloor) return f;

        if (blockId || blockTempId) {
          return {
            ...f,
            blocks: f.blocks.map(b => {
              const matchBlock = blockId ? b.id === blockId : b._tempId === blockTempId;
              if (!matchBlock) return b;
              return {
                ...b,
                rooms: b.rooms.filter(r => roomId ? r.id !== roomId : r._tempId !== roomTempId)
              };
            })
          };
        } else {
          return {
            ...f,
            rooms: f.rooms.filter(r => roomId ? r.id !== roomId : r._tempId !== roomTempId)
          };
        }
      })
    }));
  };

  const updateEditRoomField = (floorId, floorTempId, blockId, blockTempId, roomId, roomTempId, field, val) => {
    setEditData(prev => ({
      ...prev,
      floors: prev.floors.map(f => {
        const matchFloor = floorId ? f.id === floorId : f._tempId === floorTempId;
        if (!matchFloor) return f;

        if (blockId || blockTempId) {
          return {
            ...f,
            blocks: f.blocks.map(b => {
              const matchBlock = blockId ? b.id === blockId : b._tempId === blockTempId;
              if (!matchBlock) return b;
              return {
                ...b,
                rooms: b.rooms.map(r => {
                  const matchRoom = roomId ? r.id === roomId : r._tempId === roomTempId;
                  if (!matchRoom) return r;
                  if (field === 'sharing' && r.occupiedBedsCount > val) {
                    alert(`Cannot decrease capacity to ${val}. Room has ${r.occupiedBedsCount} active guests checked in.`);
                    return r;
                  }
                  return { ...r, [field]: val };
                })
              };
            })
          };
        } else {
          return {
            ...f,
            rooms: f.rooms.map(r => {
              const matchRoom = roomId ? r.id === roomId : r._tempId === roomTempId;
              if (!matchRoom) return r;
              if (field === 'sharing' && r.occupiedBedsCount > val) {
                alert(`Cannot decrease capacity to ${val}. Room has ${r.occupiedBedsCount} active guests checked in.`);
                return r;
              }
              return { ...r, [field]: val };
            })
          };
        }
      })
    }));
  };

  const handleSaveEdit = async () => {
    setSavingEdit(true);
    setError('');
    try {
      const pricingKeys = ['breakfastPrice', 'lunchPrice', 'dinnerPrice', 'omelettePrice', 'boiledEggPrice', 'washingMachinePrice'];
      const hasInvalidPrice = pricingKeys.some(k => {
        if (editData.foodIncludedInRent && ['breakfastPrice', 'lunchPrice', 'dinnerPrice'].includes(k)) {
          return false;
        }
        const val = editData[k];
        return val === undefined || val === null || val.toString().trim() === '' || isNaN(parseFloat(val)) || parseFloat(val) < 0;
      });
      if (hasInvalidPrice) {
        setError('Please enter valid, non-negative values for all enabled pricing fields.');
        setSavingEdit(false);
        return;
      }

      const payload = {
        name: editData.name.trim(),
        address: editData.address.trim(),
        foodIncludedInRent: editData.foodIncludedInRent,
        allowMealCancellations: editData.allowMealCancellations,
        breakfastPrice: parseFloat(editData.breakfastPrice) || 0,
        lunchPrice: parseFloat(editData.lunchPrice) || 0,
        dinnerPrice: parseFloat(editData.dinnerPrice) || 0,
        omelettePrice: parseFloat(editData.omelettePrice) || 0,
        boiledEggPrice: parseFloat(editData.boiledEggPrice) || 0,
        washingMachinePrice: parseFloat(editData.washingMachinePrice) || 0,
        ebSplitMethod: editData.ebSplitMethod,
        breakfastCutoffTime: editData.breakfastCutoffTime,
        dinnerCutoffTime: editData.dinnerCutoffTime,
        isPreviousDay: editData.isPreviousDay,
        floors: editData.floors.map(f => ({
          id: f.id || null,
          number: f.number,
          label: f.label.trim(),
          rooms: f.rooms.map(r => ({
            id: r.id || null,
            roomNumber: r.roomNumber.trim(),
            sharing: r.sharing,
            baseRent: parseFloat(r.baseRent) || 0,
            isAc: r.isAc || false
          })),
          blocks: f.blocks.map(b => ({
            id: b.id || null,
            name: b.name.trim(),
            rooms: b.rooms.map(r => ({
              id: r.id || null,
              roomNumber: r.roomNumber.trim(),
              sharing: r.sharing,
              baseRent: parseFloat(r.baseRent) || 0,
              isAc: r.isAc || false
            }))
          }))
        }))
      };

      await ownerApi.updateBuilding(editBuildingId, payload);
      setSearchParams({});
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to update building layout');
    } finally {
      setSavingEdit(false);
    }
  };

  // ── Creator Wizard Handlers ─────────────────────────────────────
  const canNext = () => {
    if (step === 1) {
      const pricingKeys = ['breakfastPrice', 'lunchPrice', 'dinnerPrice', 'omelettePrice', 'boiledEggPrice', 'washingMachinePrice'];
      return pricingKeys.every(k => {
        if (building.foodIncludedInRent && ['breakfastPrice', 'lunchPrice', 'dinnerPrice'].includes(k)) {
          return true;
        }
        const val = building[k];
        return val !== undefined && val !== null && val.toString().trim() !== '' && !isNaN(parseFloat(val)) && parseFloat(val) >= 0;
      });
    }
    if (step === 2) return building.name.trim().length > 0 && building.address !== undefined && building.address !== null && building.address.trim().length > 0;
    if (step === 3) {
      return floors.length > 0 && floors.every(f => f.label && f.label.trim().length > 0);
    }
    if (step === 4) {
      let totalRooms = 0;
      let hasInvalidRoom = false;
      let hasInvalidBlock = false;

      for (const f of floors) {
        // Standalone rooms
        for (const r of f.rooms) {
          totalRooms += (parseInt(r.count) || 0);
          if (!r.baseRent || parseFloat(r.baseRent) <= 0 || !r.count || parseInt(r.count) < 1) {
            hasInvalidRoom = true;
          }
        }
        // Block rooms
        for (const b of f.blocks) {
          if (!b.name || !b.name.trim()) {
            hasInvalidBlock = true;
          }
          if (b.roomConfigs.length === 0) {
            hasInvalidBlock = true;
          }
          for (const r of b.roomConfigs) {
            totalRooms += (parseInt(r.count) || 0);
            if (!r.baseRent || parseFloat(r.baseRent) <= 0 || !r.count || parseInt(r.count) < 1) {
              hasInvalidRoom = true;
            }
          }
        }
      }
      return totalRooms > 0 && !hasInvalidRoom && !hasInvalidBlock;
    }
    return true;
  };

  const handleCreate = async () => {
    setSaving(true);
    setError('');
    try {
      const payload = {
        name: building.name.trim(),
        address: building.address.trim(),
        foodIncludedInRent: building.foodIncludedInRent,
        allowMealCancellations: building.allowMealCancellations,
        breakfastPrice: parseFloat(building.breakfastPrice) || 0,
        lunchPrice: parseFloat(building.lunchPrice) || 0,
        dinnerPrice: parseFloat(building.dinnerPrice) || 0,
        omelettePrice: parseFloat(building.omelettePrice) || 0,
        boiledEggPrice: parseFloat(building.boiledEggPrice) || 0,
        washingMachinePrice: parseFloat(building.washingMachinePrice) || 0,
        ebSplitMethod: building.ebSplitMethod,
        floors: floors.map(f => ({
          number: f.number,
          label: f.label,
          rooms: f.rooms.map(r => {
            const labels = r.bedLabels
              ? r.bedLabels.split(',').map(s => s.trim()).filter(Boolean)
              : [];
            const beds = Array.from({ length: r.sharing }).map((_, idx) => ({
              bedLabel: labels[idx] || `B${idx + 1}`
            }));
            return {
              sharing: r.sharing,
              count: parseInt(r.count) || 1,
              baseRent: parseFloat(r.baseRent) || 0,
              roomNumbers: r.roomNumbers || [],
              isAc: r.isAc || false,
              beds
            };
          }),
          blocks: f.blocks.map(b => ({
            name: b.name,
            roomConfigs: b.roomConfigs.map(r => {
              const labels = r.bedLabels
                ? r.bedLabels.split(',').map(s => s.trim()).filter(Boolean)
                : [];
              const beds = Array.from({ length: r.sharing }).map((_, idx) => ({
                bedLabel: labels[idx] || `B${idx + 1}`
              }));
              return {
                sharing: r.sharing,
                count: parseInt(r.count) || 1,
                baseRent: parseFloat(r.baseRent) || 0,
                roomNumbers: r.roomNumbers || [],
                isAc: r.isAc || false,
                beds
              };
            }),
          })),
        })),
      };
      const res = await ownerApi.createBuilding(payload);
      setResult(res.data);
      setStep(6);
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to create building');
    } finally {
      setSaving(false);
    }
  };

  const resetWizard = () => {
    setStep(1);
    setBuilding({
      name: '',
      address: '',
      foodIncludedInRent: false,
      allowMealCancellations: true,
      breakfastPrice: '0',
      lunchPrice: '0',
      dinnerPrice: '0',
      omelettePrice: '0',
      boiledEggPrice: '0',
      washingMachinePrice: '0',
      ebSplitMethod: 'EQUAL_SPLIT'
    });
    setFloors([makeFloor(0)]);
    setResult(null);
    setError('');
  };

  const STEPS = ['Pricing & Rules', 'Building Info', 'Floors', 'Rooms', 'Review'];

  return (
    <AppLayout>
      {mode === 'list' && (
        <div className="fade-in">
          <div className="page-header">
            <div>
              <h1 className="page-title flex items-center gap-2">
                <Building className="w-6 h-6 text-primary" strokeWidth={1.5}/>
                <span>Buildings Module</span>
              </h1>
              <p className="page-subtitle">Configure structures, floors, room types, and base rents</p>
            </div>
            <button
              className="btn btn-primary flex items-center gap-1"
              onClick={() => { resetWizard(); setMode('create'); }}
            >
              <Plus className="w-4 h-4" strokeWidth={1.5}/> Create Building
            </button>
          </div>

          {loadingList ? (
            <div className="flex justify-center items-center py-20">
              <Loader2 className="w-8 h-8 text-indigo-500 animate-spin" strokeWidth={1.5}/>
            </div>
          ) : (
            <div className="grid-2">
              {buildings.map(b => (
                <div key={b.id} className="card flex flex-col justify-between hover:shadow-md transition-shadow">
                  <div>
                    <div className="flex items-center gap-2.5 mb-2">
                      <div className="w-8 h-8 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center flex-shrink-0">
                        <Building className="w-4 h-4" strokeWidth={1.5}/>
                      </div>
                      <div>
                        <h3 className="font-heading font-semibold text-slate-900 text-sm">{b.name}</h3>
                        <p className="text-slate-400 text-xs flex items-center gap-1 mt-0.5">
                          <MapPin className="w-3.5 h-3.5" strokeWidth={1.5}/>
                          {b.address || 'No address added'}
                        </p>
                      </div>
                    </div>

                    <div className="grid grid-cols-4 gap-2 border-t border-slate-100 pt-3.5 mt-3.5 mb-4">
                      {[
                        { label: 'Floors', val: b.totalFloors },
                        { label: 'Blocks', val: b.totalBlocks },
                        { label: 'Rooms', val: b.totalRooms },
                        { label: 'Beds', val: b.totalBeds }
                      ].map(stat => (
                        <div key={stat.label} className="bg-slate-50 border border-slate-100 rounded-lg p-2 text-center">
                          <div className="text-sm font-extrabold text-indigo-700">{stat.val}</div>
                          <div className="text-[10px] text-slate-400 font-bold uppercase tracking-wide mt-0.5">{stat.label}</div>
                        </div>
                      ))}
                    </div>
                  </div>

                  <div className="flex gap-2 border-t border-slate-100 pt-3">
                    <button
                      className="btn btn-ghost py-1.5 px-3 text-xs flex items-center gap-1.5 flex-1 justify-center"
                      onClick={() => setSearchParams({ edit: b.id })}
                    >
                      <Pencil className="w-3.5 h-3.5" strokeWidth={1.5}/>
                      <span>Edit Layout</span>
                    </button>
                    <button
                      className="btn btn-ghost text-red-600 hover:text-red-700 hover:bg-red-50/50 py-1.5 px-3 text-xs flex items-center gap-1.5 flex-1 justify-center"
                      onClick={() => {
                        setBuildingToDelete(b);
                        setDeleteConfirmInput('');
                      }}
                    >
                      <Trash2 className="w-3.5 h-3.5 text-red-500" strokeWidth={1.5}/>
                      <span>Delete</span>
                    </button>
                  </div>
                </div>
              ))}
              {buildings.length === 0 && (
                <div className="col-span-2 text-center py-12 text-slate-400 text-sm">
                  No buildings configured. Click "+ Create Building" to set up your first property.
                </div>
              )}
            </div>
          )}

          {buildingToDelete && (
            <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-[2px] flex items-center justify-center z-50">
              <div className="bg-white rounded-2xl p-6 shadow-xl border border-slate-200 w-full max-w-md mx-4 animate-in fade-in zoom-in duration-200">
                <h3 className="text-base font-extrabold text-rose-600 mb-2 flex items-center gap-2">
                  <Trash2 className="w-5 h-5" strokeWidth={1.5}/>
                  <span>Delete Building?</span>
                </h3>
                <div className="text-xs text-slate-500 mb-4 leading-relaxed">
                  This action is <strong className="text-slate-800">irreversible</strong>. Deleting <strong className="text-slate-800">"{buildingToDelete.name}"</strong> will:
                  <ul className="list-disc pl-4 mt-2 space-y-1">
                    <li>Permanently delete all floors, rooms, and beds.</li>
                    <li>Disassociate and checkout all active guests in this building.</li>
                    <li>Unassign any managers linked to this building.</li>
                  </ul>
                </div>
                <div className="form-group mb-4">
                  <label className="form-label text-xs font-bold text-slate-600" style={{ textTransform: 'none' }}>
                    To confirm, type <span className="text-rose-600 font-extrabold">"{buildingToDelete.name}"</span> below:
                  </label>
                  <input
                    type="text"
                    className="form-input text-xs py-2 w-full mt-1 border-rose-200 focus:border-rose-500 focus:ring-rose-500"
                    placeholder="Type building name exactly"
                    value={deleteConfirmInput}
                    onChange={e => setDeleteConfirmInput(e.target.value)}
                    autoFocus
                    onKeyDown={e => {
                      if (e.key === 'Enter' && deleteConfirmInput.trim().toLowerCase() === buildingToDelete.name.trim().toLowerCase()) {
                        handleDeleteBuilding();
                      }
                    }}
                  />
                </div>
                <div className="flex justify-end gap-2 text-xs">
                  <button
                    type="button"
                    className="btn btn-ghost py-2 px-4 font-semibold"
                    onClick={() => {
                      setBuildingToDelete(null);
                      setDeleteConfirmInput('');
                    }}
                    disabled={deletingId !== ''}
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    className="btn bg-rose-600 hover:bg-rose-700 text-white border border-rose-600 hover:border-rose-700 py-2 px-4 font-semibold shadow-sm flex items-center gap-1.5 disabled:opacity-50"
                    onClick={handleDeleteBuilding}
                    disabled={deleteConfirmInput.trim().toLowerCase() !== buildingToDelete.name.trim().toLowerCase() || deletingId !== ''}
                  >
                    {deletingId ? (
                      <>
                        <Loader2 className="w-3.5 h-3.5 animate-spin" strokeWidth={1.5}/>
                        <span>Deleting...</span>
                      </>
                    ) : (
                      <>
                        <Trash2 className="w-3.5 h-3.5" strokeWidth={1.5}/>
                        <span>Confirm Delete</span>
                      </>
                    )}
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {mode === 'create' && (
        <div className="fade-in">
          <div className="page-header">
            <div className="flex items-center gap-3">
              <button className="p-1.5 rounded-lg border border-slate-200 bg-white hover:bg-slate-50" onClick={() => setMode('list')}>
                <ArrowLeft className="w-4 h-4 text-slate-500" strokeWidth={1.5}/>
              </button>
              <div>
                <h1 className="page-title flex items-center gap-2">
                  <Building className="w-6 h-6 text-primary" strokeWidth={1.5}/>
                  <span>Create New Building</span>
                </h1>
                <p className="page-subtitle">Configure new building layout structures</p>
              </div>
            </div>
          </div>

          {step <= 5 && (
            <div className="flex items-center gap-0 mb-8 max-w-2xl mx-auto">
              {STEPS.map((label, idx) => {
                const num = idx + 1;
                const active = step === num;
                const done = step > num;
                return (
                  <React.Fragment key={num}>
                    <div className="flex items-center gap-2">
                      <div className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold transition-colors ${
                        done ? 'bg-emerald-500 text-white' : active ? 'bg-indigo-600 text-white' : 'bg-slate-200 text-slate-500'
                      }`}>
                        {done ? <CheckCircle2 className="w-4 h-4" strokeWidth={1.5}/> : num}
                      </div>
                      <span className={`text-xs font-medium ${active ? 'text-indigo-700' : done ? 'text-emerald-700' : 'text-slate-400'}`}>
                        {label}
                      </span>
                    </div>
                    {idx < STEPS.length - 1 && (
                      <div className={`flex-1 h-0.5 mx-3 ${done ? 'bg-emerald-300' : 'bg-slate-200'}`} />
                    )}
                  </React.Fragment>
                );
              })}
            </div>
          )}

          <div className="card max-w-2xl mx-auto">
            {step === 1 && <Step4Pricing data={building} onChange={setBuilding} />}
            {step === 2 && <Step1 data={building} onChange={setBuilding} />}
            {step === 3 && <Step2 floors={floors} onChange={setFloors} />}
            {step === 4 && <Step3 floors={floors} onChange={setFloors} />}
            {step === 5 && <Step4 building={building} floors={floors} />}
            {step === 6 && result && (
              <div className="text-center py-6">
                <CheckCircle2 className="w-14 h-14 text-emerald-500 mx-auto mb-4" strokeWidth={1.5}/>
                <h2 className="font-heading text-xl font-semibold text-slate-900 mb-1">Building Created!</h2>
                <p className="text-slate-500 text-sm mb-6">"{result.buildingName}" is now ready to use</p>
                <div className="grid grid-cols-4 gap-3 mb-6">
                  {[
                    ['Floors', result.totalFloors],
                    ['Blocks', result.totalBlocks],
                    ['Rooms', result.totalRooms],
                    ['Beds', result.totalBeds],
                  ].map(([label, val]) => (
                    <div key={label} className="bg-emerald-50 border border-emerald-100 rounded-xl p-3 text-center">
                      <div className="text-2xl font-black text-emerald-700">{val}</div>
                      <div className="text-xs text-slate-500">{label}</div>
                    </div>
                  ))}
                </div>
                <div className="flex gap-3 justify-center flex-wrap">
                  <button className="btn btn-primary" onClick={() => navigate('/owner/dashboard', { state: { openAddManager: true, buildingId: result.buildingId } })}>
                    Assign / Create Manager
                  </button>
                  <button className="btn btn-secondary border border-slate-200" onClick={resetWizard}>
                    <Plus className="w-4 h-4" strokeWidth={1.5}/> Create Another
                  </button>
                  <button className="btn btn-ghost" onClick={() => setMode('list')}>Back to Buildings</button>
                </div>
              </div>
            )}

            {error && (
              <div className="mt-4 flex items-center gap-2 p-3 rounded-xl bg-red-50 border border-red-200 text-red-700 text-sm">
                <AlertCircle className="w-4 h-4 flex-shrink-0" strokeWidth={1.5}/>
                <span>{error}</span>
              </div>
            )}

            {step <= 5 && (
              <div className="flex justify-between mt-8 pt-5 border-t border-slate-100">
                {step > 1 ? (
                  <button className="btn btn-ghost flex items-center gap-1" onClick={() => setStep(s => s - 1)}>
                    <ChevronLeft className="w-4 h-4" strokeWidth={1.5}/> Back
                  </button>
                ) : <div />}
                {step < 5 ? (
                  <button
                    className="btn btn-primary flex items-center gap-1"
                    disabled={!canNext()}
                    onClick={() => setStep(s => s + 1)}
                  >
                    Next <ChevronRight className="w-4 h-4" strokeWidth={1.5}/>
                  </button>
                ) : (
                  <button
                    className="btn btn-primary flex items-center gap-2"
                    disabled={saving}
                    onClick={handleCreate}
                  >
                    {saving ? <Loader2 className="w-4 h-4 animate-spin" strokeWidth={1.5}/> : <Building className="w-4 h-4" strokeWidth={1.5}/>}
                    {saving ? 'Creating...' : 'Create Building'}
                  </button>
                )}
              </div>
            )}
          </div>
        </div>
      )}

      {mode === 'edit' && (
        <div className="fade-in">
          <div className="page-header">
            <div className="flex items-center gap-3">
              <button className="p-1.5 rounded-lg border border-slate-200 bg-white hover:bg-slate-50" onClick={() => setSearchParams({})}>
                <ArrowLeft className="w-4 h-4 text-slate-500" strokeWidth={1.5}/>
              </button>
              <div>
                <h1 className="page-title flex items-center gap-2">
                  <Building className="w-6 h-6 text-primary" strokeWidth={1.5}/>
                  <span>Edit Layout: {editData?.name || 'Loading...'}</span>
                </h1>
                <p className="page-subtitle">Add or remove rooms/blocks, and update rent or bed structures</p>
              </div>
            </div>
            {editData && (
              <button
                className="btn btn-primary flex items-center gap-2"
                onClick={handleSaveEdit}
                disabled={savingEdit}
              >
                {savingEdit ? <Loader2 className="w-4 h-4 animate-spin" strokeWidth={1.5}/> : <Save className="w-4 h-4" strokeWidth={1.5}/>}
                <span>{savingEdit ? 'Saving...' : 'Save Changes'}</span>
              </button>
            )}
          </div>

          {loadingEdit ? (
            <div className="flex justify-center items-center py-20">
              <Loader2 className="w-8 h-8 text-indigo-500 animate-spin" strokeWidth={1.5}/>
            </div>
          ) : editData ? (
            <div className="grid grid-cols-3 gap-6">
              {/* Left pane: Details and Floor Management */}
              <div className="col-span-1 flex flex-col gap-5">
                <div className="card">
                  <h3 className="font-heading text-sm font-semibold text-slate-900 mb-3">Building Details</h3>
                  <div className="form-group">
                    <label className="form-label">Building Name</label>
                    <input
                      className="form-input"
                      value={editData.name}
                      onChange={e => updateBuildingField('name', e.target.value)}
                    />
                  </div>
                  <div className="form-group mb-0">
                    <label className="form-label">Address</label>
                    <input
                      className="form-input"
                      value={editData.address}
                      onChange={e => updateBuildingField('address', e.target.value)}
                    />
                  </div>
                </div>

                <div className="card">
                  <Step4Pricing data={editData} onChange={setEditData} isEditMode={true} />
                </div>

                <div className="card">
                  <div className="flex justify-between items-center mb-3">
                    <h3 className="font-heading text-sm font-semibold text-slate-900">Floors List</h3>
                    <button
                      className="btn btn-ghost py-1 px-2.5 text-xs flex items-center gap-1 shadow-none bg-transparent hover:bg-slate-100 font-semibold"
                      onClick={addEditFloor}
                    >
                      <Plus className="w-3.5 h-3.5" strokeWidth={1.5}/> Add Floor
                    </button>
                  </div>
                  <div className="flex flex-col gap-2">
                    {editData.floors.map(floor => {
                      let totalRooms = floor.rooms.length;
                      floor.blocks.forEach(b => totalRooms += b.rooms.length);

                      return (
                        <div key={floor.id || floor._tempId} className="flex items-center gap-2 p-2 rounded-lg border border-slate-100 bg-slate-50">
                          <div className="w-7 h-7 rounded bg-indigo-100 flex items-center justify-center font-bold text-indigo-700 text-xs">
                            {floor.number}
                          </div>
                          <input
                            className="form-input flex-1 py-1 text-xs"
                            value={floor.label}
                            onChange={e => {
                              const updated = editData.floors.map(f =>
                                (floor.id ? f.id === floor.id : f._tempId === floor._tempId)
                                  ? { ...f, label: e.target.value } : f
                              );
                              setEditData(prev => ({ ...prev, floors: updated }));
                            }}
                          />
                          <span className="text-[10px] bg-slate-200/60 text-slate-500 font-semibold px-1.5 py-0.5 rounded">
                            {totalRooms} Rooms
                          </span>
                          <button
                            type="button"
                            className="p-1 text-red-400 hover:text-red-600 hover:bg-red-50 rounded"
                            onClick={() => deleteEditFloor(floor.id, floor._tempId)}
                          >
                            <Trash2 className="w-3.5 h-3.5" strokeWidth={1.5}/>
                          </button>
                        </div>
                      );
                    })}
                  </div>
                </div>
              </div>

              {/* Right pane: Detailed Layout Config */}
              <div className="col-span-2 flex flex-col gap-5">
                {editData.floors.map(floor => (
                  <div key={floor.id || floor._tempId} className="rounded-2xl border border-slate-200 bg-slate-50 overflow-hidden">
                    <div className="bg-indigo-600 px-4 py-2.5 flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <Layers className="w-4 h-4 text-indigo-200" strokeWidth={1.5}/>
                        <span className="text-white font-semibold text-sm">{floor.label}</span>
                      </div>
                      <div className="flex gap-2">
                        <button
                          type="button"
                          className="text-xs bg-indigo-700/80 text-indigo-100 hover:bg-indigo-700 py-1 px-2.5 rounded font-semibold border border-indigo-500/50 flex items-center gap-1"
                          onClick={() => addEditBlock(floor.id, floor._tempId)}
                        >
                          <Plus className="w-3.5 h-3.5" strokeWidth={1.5}/> Add Block
                        </button>
                        <button
                          type="button"
                          className="text-xs bg-indigo-700/80 text-indigo-100 hover:bg-indigo-700 py-1 px-2.5 rounded font-semibold border border-indigo-500/50 flex items-center gap-1"
                          onClick={() => addEditRoom(floor.id, floor._tempId, null, null)}
                        >
                          <Plus className="w-3.5 h-3.5" strokeWidth={1.5}/> Standalone Room
                        </button>
                      </div>
                    </div>

                    <div className="p-4 flex flex-col gap-4">
                      {/* Blocks */}
                      {floor.blocks.map(block => (
                        <div key={block.id || block._tempId} className="rounded-xl border border-slate-200 bg-white overflow-hidden">
                          <div className="bg-slate-100 px-3 py-2 flex items-center justify-between">
                            <input
                              className="bg-transparent text-sm font-semibold text-slate-700 border-none outline-none font-sans"
                              value={block.name}
                              onChange={e => {
                                const updated = editData.floors.map(f => {
                                  const matchF = floor.id ? f.id === floor.id : f._tempId === floor._tempId;
                                  if (!matchF) return f;
                                  return {
                                    ...f,
                                    blocks: f.blocks.map(b =>
                                      (block.id ? b.id === block.id : b._tempId === block._tempId)
                                        ? { ...b, name: e.target.value } : b
                                    )
                                  };
                                });
                                setEditData(prev => ({ ...prev, floors: updated }));
                              }}
                            />
                            <button type="button" className="text-red-400 hover:text-red-600 p-1"
                              onClick={() => deleteEditBlock(floor.id, floor._tempId, block.id, block._tempId)}>
                              <Trash2 className="w-3.5 h-3.5" strokeWidth={1.5}/>
                            </button>
                          </div>

                          <div className="p-3 flex flex-col gap-2">
                            <div className="grid grid-cols-6 text-xs text-slate-400 font-medium px-2 mb-0.5">
                              <span>Room No.</span>
                              <span>Sharing</span>
                              <span>Rent/Bed</span>
                              <span className="text-center">Non-AC</span>
                              <span className="text-center">Guests</span>
                              <span></span>
                            </div>

                            {block.rooms.map(room => (
                              <div key={room.id || room._tempId} className="grid grid-cols-6 gap-2 items-center p-2 rounded-lg bg-slate-50 border border-slate-100 text-xs">
                                <input
                                  className="form-input py-1 px-2 text-xs"
                                  value={room.roomNumber}
                                  onChange={e => updateEditRoomField(floor.id, floor._tempId, block.id, block._tempId, room.id, room._tempId, 'roomNumber', e.target.value)}
                                  id={`edit-room-number-block-${room.id || room._tempId}`}
                                  name={`edit-room-number-block-${room.id || room._tempId}`}
                                />
                                <select
                                  className={`form-input py-1 px-2 text-xs font-semibold ${room.occupiedBedsCount > 0 ? 'bg-slate-100 text-slate-500 cursor-not-allowed' : ''}`}
                                  value={room.sharing}
                                  onChange={e => updateEditRoomField(floor.id, floor._tempId, block.id, block._tempId, room.id, room._tempId, 'sharing', parseInt(e.target.value))}
                                  id={`edit-sharing-block-${room.id || room._tempId}`}
                                  name={`edit-sharing-block-${room.id || room._tempId}`}
                                  disabled={room.occupiedBedsCount > 0}
                                >
                                  {[1, 2, 3, 4].map(s => (
                                    <option key={s} value={s}>{SHARING_LABELS[s]}</option>
                                  ))}
                                </select>
                                <div className="flex items-center gap-1">
                                  <span className="text-slate-400 text-xs">₹</span>
                                  <input
                                    type="text"
                                    className="form-input py-1 px-2 text-xs"
                                    value={room.baseRent}
                                    onChange={e => {
                                      const clean = e.target.value.replace(/[^0-9]/g, '');
                                      updateEditRoomField(floor.id, floor._tempId, block.id, block._tempId, room.id, room._tempId, 'baseRent', clean);
                                    }}
                                    autoComplete="off"
                                    name={`edit-base-rent-block-${room.id || room._tempId}`}
                                    id={`edit-base-rent-block-${room.id || room._tempId}`}
                                  />
                                </div>
                                <div className="text-center">
                                  <input
                                    type="checkbox"
                                    className="rounded border-slate-300 text-indigo-600 focus:ring-indigo-500 w-3.5 h-3.5"
                                    checked={room.isAc === false}
                                    onChange={e => updateEditRoomField(floor.id, floor._tempId, block.id, block._tempId, room.id, room._tempId, 'isAc', !e.target.checked)}
                                    id={`edit-isAc-block-${room.id || room._tempId}`}
                                    name={`edit-isAc-block-${room.id || room._tempId}`}
                                  />
                                </div>
                                <div className="text-center">
                                  {room.occupiedBedsCount > 0 ? (
                                    <span className="inline-flex px-1.5 py-0.5 rounded-full text-[10px] font-bold bg-indigo-50 text-indigo-600 border border-indigo-100">
                                      {room.occupiedBedsCount} Occupied
                                    </span>
                                  ) : (
                                    <span className="text-slate-400 text-[10px] font-semibold">0</span>
                                  )}
                                </div>
                                <div className="flex justify-end">
                                  <button
                                    type="button"
                                    className="p-1 text-red-400 hover:text-red-600 disabled:opacity-30 rounded hover:bg-red-50"
                                    onClick={() => deleteEditRoom(floor.id, floor._tempId, block.id, block._tempId, room.id, room._tempId)}
                                    disabled={room.occupiedBedsCount > 0}
                                    title={room.occupiedBedsCount > 0 ? 'Cannot delete occupied room' : ''}
                                  >
                                    <Trash2 className="w-3.5 h-3.5" strokeWidth={1.5}/>
                                  </button>
                                </div>
                              </div>
                            ))}

                            <button
                              type="button"
                              className="text-xs text-indigo-600 hover:text-indigo-800 flex items-center gap-1 mt-1 font-semibold"
                              onClick={() => addEditRoom(floor.id, floor._tempId, block.id, block._tempId)}
                            >
                              <Plus className="w-3 h-3" strokeWidth={1.5}/> Add Room to Block
                            </button>
                          </div>
                        </div>
                      ))}

                      {/* Standalone rooms */}
                      {floor.rooms.length > 0 && (
                        <div className="rounded-xl border border-slate-200 bg-white p-3">
                          <p className="text-xs font-semibold text-slate-500 mb-2">Standalone Rooms (no block)</p>
                          <div className="grid grid-cols-6 text-xs text-slate-400 font-medium px-2 mb-0.5">
                            <span>Room No.</span>
                            <span>Sharing</span>
                            <span>Rent/Bed</span>
                            <span className="text-center">Non-AC</span>
                            <span className="text-center">Guests</span>
                            <span></span>
                          </div>

                          {floor.rooms.map(room => (
                            <div key={room.id || room._tempId} className="grid grid-cols-6 gap-2 items-center p-2 rounded-lg bg-slate-50 border border-slate-100 text-xs">
                              <input
                                className="form-input py-1 px-2 text-xs"
                                value={room.roomNumber}
                                onChange={e => updateEditRoomField(floor.id, floor._tempId, null, null, room.id, room._tempId, 'roomNumber', e.target.value)}
                                id={`edit-room-number-standalone-${room.id || room._tempId}`}
                                name={`edit-room-number-standalone-${room.id || room._tempId}`}
                              />
                              <select
                                className={`form-input py-1 px-2 text-xs font-semibold ${room.occupiedBedsCount > 0 ? 'bg-slate-100 text-slate-500 cursor-not-allowed' : ''}`}
                                value={room.sharing}
                                onChange={e => updateEditRoomField(floor.id, floor._tempId, null, null, room.id, room._tempId, 'sharing', parseInt(e.target.value))}
                                id={`edit-sharing-standalone-${room.id || room._tempId}`}
                                name={`edit-sharing-standalone-${room.id || room._tempId}`}
                                disabled={room.occupiedBedsCount > 0}
                              >
                                  {[1, 2, 3, 4].map(s => (
                                    <option key={s} value={s}>{SHARING_LABELS[s]}</option>
                                  ))}
                              </select>
                              <div className="flex items-center gap-1">
                                <span className="text-slate-400 text-xs">₹</span>
                                <input
                                  type="text"
                                  className="form-input py-1 px-2 text-xs"
                                  value={room.baseRent}
                                  onChange={e => {
                                    const clean = e.target.value.replace(/[^0-9]/g, '');
                                    updateEditRoomField(floor.id, floor._tempId, null, null, room.id, room._tempId, 'baseRent', clean);
                                  }}
                                  autoComplete="off"
                                  name={`edit-base-rent-standalone-${room.id || room._tempId}`}
                                  id={`edit-base-rent-standalone-${room.id || room._tempId}`}
                                />
                              </div>
                              <div className="text-center">
                                <input
                                  type="checkbox"
                                  className="rounded border-slate-300 text-indigo-600 focus:ring-indigo-500 w-3.5 h-3.5"
                                  checked={room.isAc === false}
                                  onChange={e => updateEditRoomField(floor.id, floor._tempId, null, null, room.id, room._tempId, 'isAc', !e.target.checked)}
                                  id={`edit-isAc-standalone-${room.id || room._tempId}`}
                                  name={`edit-isAc-standalone-${room.id || room._tempId}`}
                                />
                              </div>
                              <div className="text-center">
                                {room.occupiedBedsCount > 0 ? (
                                  <span className="inline-flex px-1.5 py-0.5 rounded-full text-[10px] font-bold bg-indigo-50 text-indigo-600 border border-indigo-100">
                                    {room.occupiedBedsCount} Occupied
                                  </span>
                                ) : (
                                  <span className="text-slate-400 text-[10px] font-semibold">0</span>
                                )}
                              </div>
                              <div className="flex justify-end">
                                <button
                                  type="button"
                                  className="p-1 text-red-400 hover:text-red-600 disabled:opacity-30 rounded hover:bg-red-50"
                                  onClick={() => deleteEditRoom(floor.id, floor._tempId, null, null, room.id, room._tempId)}
                                  disabled={room.occupiedBedsCount > 0}
                                  title={room.occupiedBedsCount > 0 ? 'Cannot delete occupied room' : ''}
                                >
                                  <Trash2 className="w-3.5 h-3.5" strokeWidth={1.5}/>
                                </button>
                              </div>
                            </div>
                          ))}
                        </div>
                      )}

                      {floor.rooms.length === 0 && floor.blocks.length === 0 && (
                        <p className="text-center text-slate-400 text-xs py-4">No rooms on this floor. Click Add Block or Standalone Room to start adding rooms.</p>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <div className="text-center py-20 text-slate-400">Layout structure is empty.</div>
          )}

          {error && (
            <div className="mt-5 flex items-center gap-2 p-3 rounded-xl bg-red-50 border border-red-200 text-red-700 text-sm max-w-2xl mx-auto">
              <AlertCircle className="w-4 h-4 flex-shrink-0" strokeWidth={1.5}/>
              <span>{error}</span>
            </div>
          )}
        </div>
      )}
    </AppLayout>
  );
}

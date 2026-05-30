import React, { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import AppLayout from '../../components/AppLayout';
import { managerApi } from '../../api';
import { 
  Users, 
  X, 
  Plus, 
  MapPin, 
  Layers, 
  Home, 
  Utensils, 
  Leaf, 
  Search, 
  Edit2, 
  LogOut, 
  AlertTriangle, 
  Loader2 
} from 'lucide-react';

export default function ManagerGuests() {
  const [guests, setGuests] = useState([]);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ 
    bedId:'', 
    fullName:'', 
    email:'', 
    phone:'', 
    whatsappNumber:'', 
    advanceDeposit:'', 
    checkInDate: new Date().toISOString().slice(0,10),
    isVeg: true,
    foodOptedIn: true,
    breakfastOpted: true,
    lunchOpted: true,
    dinnerOpted: true
  });
  const [saving, setSaving] = useState(false);
  const [vacantBeds, setVacantBeds] = useState([]);
  const [selectedBedInfo, setSelectedBedInfo] = useState(null);
  const [loadingBeds, setLoadingBeds] = useState(false);
  
  // Search & Filter state for Guest Main Page
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedFloor, setSelectedFloor] = useState('All');
  
  // Bed selection floor tab state
  const [activeFloorTab, setActiveFloorTab] = useState('');

  // Editing guest states
  const [editingGuest, setEditingGuest] = useState(null);
  const [editForm, setEditForm] = useState({ fullName: '', email: '', phone: '', whatsappNumber: '', advanceDeposit: '', kycStatus: 'PENDING' });
  const [updating, setUpdating] = useState(false);

  // Checkout notice states
  const [confirmCheckoutGuest, setConfirmCheckoutGuest] = useState(null);
  const [checkoutLoading, setCheckoutLoading] = useState(false);

  const startEdit = (g) => {
    setEditingGuest(g);
    setEditForm({
      fullName: g.fullName || '',
      email: g.email || '',
      phone: g.phone || '',
      whatsappNumber: g.whatsappNumber || '',
      advanceDeposit: g.advanceDeposit || 0,
      kycStatus: g.kycStatus || 'PENDING'
    });
  };

  const handleUpdate = async (e) => {
    e.preventDefault();
    setUpdating(true);
    try {
      await managerApi.updateGuest(editingGuest.id, editForm);
      setEditingGuest(null);
      managerApi.getGuests().then(r => setGuests(r.data));
    } catch (err) {
      alert(err.response?.data?.error || 'Failed to update guest details');
    } finally {
      setUpdating(false);
    }
  };

  useEffect(() => { managerApi.getGuests().then(r => setGuests(r.data)).catch(() => {}); }, []);

  useEffect(() => {
    if (showForm) {
      setLoadingBeds(true);
      managerApi.getAllBeds()
        .then(r => {
          setVacantBeds(r.data || []);
        })
        .catch(console.error)
        .finally(() => setLoadingBeds(false));
    } else {
      resetForm();
    }
  }, [showForm]);

  const resetForm = () => {
    setForm({ 
      bedId:'', 
      fullName:'', 
      email:'', 
      phone:'', 
      whatsappNumber:'', 
      advanceDeposit:'', 
      checkInDate: new Date().toISOString().slice(0,10),
      isVeg: true,
      foodOptedIn: true,
      breakfastOpted: true,
      lunchOpted: true,
      dinnerOpted: true
    });
    setSelectedBedInfo(null);
  };

  const checkIn = async (e) => {
    e.preventDefault(); setSaving(true);
    try {
      await managerApi.checkIn(form);
      setShowForm(false);
      resetForm();
      managerApi.getGuests().then(r => setGuests(r.data));
    } catch (err) {
      alert(err.response?.data?.error || 'Failed');
    } finally {
      setSaving(false);
    }
  };

  const initiateCheckout = (g) => {
    setConfirmCheckoutGuest(g);
  };

  const handleConfirmCheckout = async () => {
    if (!confirmCheckoutGuest) return;
    setCheckoutLoading(true);
    try {
      await managerApi.initiateCheckout(confirmCheckoutGuest.id);
      setConfirmCheckoutGuest(null);
      managerApi.getGuests().then(r => setGuests(r.data));
    } catch (err) {
      alert(err.response?.data?.error || 'Failed to initiate checkout');
    } finally {
      setCheckoutLoading(false);
    }
  };

  // Group vacant beds by Floor -> Block -> Room
  const groupedBeds = {};
  vacantBeds.forEach(bed => {
    const floorLabel = bed.room?.floor?.floorLabel || `Floor ${bed.room?.floor?.floorNumber}` || 'Other';
    const blockName = bed.room?.block?.name || 'No Block';
    const roomNumber = bed.room?.roomNumber || 'Other';
    
    if (!groupedBeds[floorLabel]) groupedBeds[floorLabel] = {};
    if (!groupedBeds[floorLabel][blockName]) groupedBeds[floorLabel][blockName] = {};
    if (!groupedBeds[floorLabel][blockName][roomNumber]) groupedBeds[floorLabel][blockName][roomNumber] = [];
    
    groupedBeds[floorLabel][blockName][roomNumber].push(bed);
  });

  const floors = Object.keys(groupedBeds);
  const currentFloor = activeFloorTab || (floors.length > 0 ? floors[0] : '');

  // Extract unique floors of active guests for filtering
  const guestFloors = ['All', ...new Set(guests.map(g => 
    g.floorName || 'Other'
  ).filter(Boolean))];

  // Filter guest list
  const filteredGuests = guests.filter(g => {
    const searchMatch = 
      g.fullName?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      g.email?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      g.phone?.includes(searchQuery);

    const floorLabel = g.floorName || 'Other';
    const floorMatch = selectedFloor === 'All' || floorLabel === selectedFloor;

    return searchMatch && floorMatch;
  });

  return (
    <AppLayout>
      <div className="page-header">
        <div>
          <h1 className="page-title flex items-center gap-2">
            <Users className="w-6 h-6 text-primary" />
            <span>Guests</span>
          </h1>
          <p className="page-subtitle">{filteredGuests.length} showing ({guests.length} total active)</p>
        </div>
        <button className="btn btn-primary flex items-center gap-1.5" onClick={() => setShowForm(!showForm)}>
          {showForm ? <X className="w-4 h-4" /> : <Plus className="w-4 h-4" />}
          <span>{showForm ? 'Cancel' : 'Check In'}</span>
        </button>
      </div>
      {showForm && (
        <div className="card" style={{ marginBottom: '1.5rem' }}>
          <h3 style={{ marginBottom:'1rem', fontWeight:700 }}>New Guest Check-In</h3>
          
          {/* Step 1: Bed Selection */}
          <div style={{ marginBottom: '1.5rem', borderBottom: '1px solid var(--border)', paddingBottom: '1.5rem' }}>
            <h4 style={{ marginBottom: '0.75rem', color: 'var(--text-secondary)', fontSize: '0.9rem', fontWeight: 600 }}>
              1. Select an Available Bed
            </h4>
            {loadingBeds ? (
              <div className="flex items-center gap-2 text-slate-400 text-sm">
                <Loader2 className="w-4 h-4 animate-spin" />
                <span>Loading available beds...</span>
              </div>
            ) : vacantBeds.length === 0 ? (
              <div className="flex items-center gap-1.5 text-rose-500 text-sm">
                <AlertTriangle className="w-4 h-4" />
                <span>No beds configured in the building.</span>
              </div>
            ) : !vacantBeds.some(bed => bed.status === 'VACANT') ? (
              <div className="flex items-center gap-1.5 text-rose-500 text-sm">
                <AlertTriangle className="w-4 h-4" />
                <span>No vacant beds available in the building.</span>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                {/* Horizontal Floor Selection Tabs */}
                {/* Horizontal Floor Selection Tabs & Legend */}
                <div className="flex flex-col gap-3">
                  <div className="flex gap-2 flex-wrap">
                    {floors.map(f => {
                      const isActive = currentFloor === f;
                      return (
                        <button
                          key={f}
                          type="button"
                          onClick={() => setActiveFloorTab(f)}
                          className={`btn px-4 py-1.5 text-xs flex items-center gap-1.5 rounded-lg border transition-all ${
                            isActive 
                              ? 'bg-primary text-white font-medium border-primary' 
                              : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'
                          }`}
                        >
                          <MapPin className="w-3.5 h-3.5" />
                          <span>{f}</span>
                        </button>
                      );
                    })}
                  </div>

                  {/* Status Legend */}
                  <div className="flex gap-4 text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-2 flex-wrap">
                    <div className="flex items-center gap-1.5">
                      <span className="w-2.5 h-2.5 rounded-full bg-green-500 inline-block"></span>
                      <span>Vacant (Selectable)</span>
                    </div>
                    <div className="flex items-center gap-1.5">
                      <span className="w-2.5 h-2.5 rounded-full bg-slate-400 inline-block"></span>
                      <span>Occupied</span>
                    </div>
                    <div className="flex items-center gap-1.5">
                      <span className="w-2.5 h-2.5 rounded-full bg-yellow-500 inline-block"></span>
                      <span>Notice Period (Leaving Soon)</span>
                    </div>
                  </div>
                </div>

                {currentFloor && groupedBeds[currentFloor] && (
                  <div className="bg-slate-50 p-4 rounded-xl border border-slate-200">
                    <div className="flex items-center gap-1 font-bold text-sm text-primary mb-3">
                      <MapPin className="w-4 h-4" />
                      <span>{currentFloor}</span>
                    </div>
                    
                    <div className="flex flex-col gap-4">
                      {Object.entries(groupedBeds[currentFloor]).map(([blockName, rooms]) => (
                        <div key={blockName} className="flex flex-col gap-2 pl-3 border-l-2 border-slate-200">
                          <div className="flex items-center gap-1 text-slate-400 font-bold text-xs uppercase tracking-wider mb-1">
                            <Layers className="w-3.5 h-3.5" />
                            <span>{blockName}</span>
                          </div>
                          
                          <div className="flex flex-wrap gap-4 w-full">
                            {Object.entries(rooms).map(([roomNum, beds]) => (
                              <div key={roomNum} className="bg-white p-3.5 rounded-xl border border-slate-200 flex flex-col gap-2 min-w-[220px] flex-grow md:flex-initial shadow-sm hover:border-slate-300 transition-colors">
                                <div className="flex flex-col gap-0.5">
                                  <span className="flex items-center gap-1 font-bold text-xs text-slate-700">
                                    <Home className="w-3.5 h-3.5 text-slate-400" />
                                    <span>Room {roomNum}</span>
                                  </span>
                                  <span className="text-[11px] text-slate-500 font-semibold" style={{ paddingLeft: '1.25rem' }}>
                                    {beds[0]?.room?.sharingType ? `${beds[0].room.sharingType}-Sharing` : 'Unknown'}
                                  </span>
                                </div>
                                <div className="flex flex-wrap gap-1.5 mt-1">
                                  {beds.map(bed => {
                                    const occupant = guests.find(g => g.bedId === bed.id);
                                    const isNoticePeriod = occupant && occupant.noticeDate;
                                    const isOccupied = bed.status === 'OCCUPIED' || (occupant && !isNoticePeriod);
                                    const isVacant = !isOccupied && !isNoticePeriod;

                                    const isSelected = form.bedId === bed.id;

                                    let btnClasses = "px-2.5 py-1 rounded-md border text-[11px] transition-all font-bold ";
                                    let isDisabled = false;

                                    if (isVacant) {
                                      if (isSelected) {
                                        btnClasses += "bg-primary border-primary text-white shadow-sm cursor-pointer";
                                      } else {
                                        btnClasses += "bg-green-50 border-green-200 text-green-700 hover:bg-green-100 hover:shadow-sm cursor-pointer";
                                      }
                                    } else if (isNoticePeriod) {
                                      btnClasses += "bg-yellow-50 border-yellow-200 text-yellow-700 cursor-not-allowed opacity-90";
                                      isDisabled = true;
                                    } else {
                                      // Occupied
                                      btnClasses += "bg-slate-100 border-slate-200 text-slate-400 cursor-not-allowed opacity-75";
                                      isDisabled = true;
                                    }

                                    return (
                                      <div key={bed.id} className="relative group">
                                        <button
                                          type="button"
                                          disabled={isDisabled}
                                          onClick={() => {
                                            if (isVacant) {
                                              setForm(f => ({ ...f, bedId: bed.id }));
                                              setSelectedBedInfo(bed);
                                            }
                                          }}
                                          className={btnClasses}
                                        >
                                          {bed.bedLabel}
                                        </button>
                                        {isNoticePeriod && occupant && (
                                          <div className="absolute bottom-full mb-1.5 hidden group-hover:block bg-slate-900/95 backdrop-blur-sm text-white text-[10px] rounded-lg px-2.5 py-1.5 z-30 whitespace-nowrap border border-slate-700/50 shadow-lg shadow-slate-900/20 pointer-events-none left-1/2 -translate-x-1/2 text-left">
                                            <div className="font-extrabold text-yellow-400 mb-0.5">Notice Period</div>
                                            <div className="font-bold">{occupant.fullName}</div>
                                            <div className="text-[9px] text-slate-300 mt-0.5">
                                              Notice: {occupant.noticeDate || '—'}<br />
                                              Exit: {occupant.exitDate || '—'}
                                            </div>
                                          </div>
                                        )}
                                        {isOccupied && occupant && (
                                          <div className="absolute bottom-full mb-1.5 hidden group-hover:block bg-slate-900/95 backdrop-blur-sm text-white text-[10px] rounded-lg px-2.5 py-1.5 z-30 whitespace-nowrap border border-slate-700/50 shadow-lg shadow-slate-900/20 pointer-events-none left-1/2 -translate-x-1/2 text-left">
                                            <div className="font-extrabold text-slate-300 mb-0.5">Occupied</div>
                                            <div className="font-bold">{occupant.fullName}</div>
                                            <div className="text-[9px] text-slate-400 mt-0.5">
                                              Checked In: {occupant.checkInDate || '—'}
                                            </div>
                                          </div>
                                        )}
                                      </div>
                                    );
                                  })}
                                </div>
                              </div>
                            ))}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
          
          {/* Step 2: Guest Details Form */}
          {form.bedId && selectedBedInfo && (
            <form onSubmit={checkIn} className="fade-in-up">
              <h4 style={{ marginBottom: '1rem', color: 'var(--text-secondary)', fontSize: '0.9rem', fontWeight: 600 }}>
                2. Guest Details for Bed <span style={{ color: 'var(--success)' }}>{selectedBedInfo.bedLabel}</span> (Room {selectedBedInfo.room?.roomNumber}, Rent: ₹{selectedBedInfo.room?.baseRent})
              </h4>
              <div className="grid-3">
                {[['fullName','Full Name'],['email','Email ID'],['phone','Phone'],['whatsappNumber','WhatsApp Number'],['advanceDeposit','Advance Deposit (₹)']].map(([key, label]) => (
                  <div key={key} className="form-group">
                    <label className="form-label">{label}</label>
                    <input className="form-input" value={form[key]} onChange={e => setForm(f=>({...f,[key]:e.target.value}))} required={key === 'fullName' || key === 'email' || key === 'phone'} />
                  </div>
                ))}
                <div className="form-group">
                  <label className="form-label">Check-In Date</label>
                  <input type="date" className="form-input" value={form.checkInDate} onChange={e => setForm(f=>({...f,checkInDate:e.target.value}))} required />
                </div>
              </div>

              {/* Meal Preferences & Selection */}
              <div style={{ background: 'var(--bg-secondary)', padding: '1rem', borderRadius: '10px', border: '1px solid var(--border)', marginTop: '1rem', marginBottom: '1.25rem' }}>
                <div className="flex items-center gap-1.5 font-bold text-xs text-primary mb-3 uppercase tracking-wider">
                  <Utensils className="w-4 h-4" />
                  <span>Meal Preferences &amp; Selections</span>
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem' }}>
                  {/* Veg / Non Veg */}
                  <div className="form-group" style={{ margin: 0 }}>
                    <label className="form-label" style={{ display: 'block', marginBottom: '0.4rem' }}>Food Preference</label>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.85rem' }}>
                      <span className="flex items-center gap-1 font-semibold" style={{ color: form.isVeg ? '#10b981' : 'var(--text-muted)' }}>
                        <Leaf className="w-3.5 h-3.5" />
                        <span>Veg</span>
                      </span>
                      <label className="toggle">
                        <input type="checkbox" checked={!form.isVeg} onChange={() => setForm(f => ({ ...f, isVeg: !f.isVeg }))} />
                        <span className="toggle-slider" />
                      </label>
                      <span className="flex items-center gap-1 font-semibold" style={{ color: !form.isVeg ? '#ef4444' : 'var(--text-muted)' }}>
                        <Utensils className="w-3.5 h-3.5" />
                        <span>Non-Veg</span>
                      </span>
                    </div>
                  </div>
                  
                  {/* Meal Choices */}
                  <div className="form-group" style={{ margin: 0 }}>
                    <label className="form-label" style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', cursor: 'pointer', marginBottom: '0.4rem' }}>
                      <input 
                        type="checkbox"
                        id="opt-in-food-master"
                        checked={form.foodOptedIn}
                        onChange={e => {
                          const checked = e.target.checked;
                          setForm(f => ({
                            ...f,
                            foodOptedIn: checked,
                            breakfastOpted: checked,
                            lunchOpted: checked,
                            dinnerOpted: checked
                          }));
                        }}
                      />
                      <span className="font-semibold text-slate-700">Opt-in for Food</span>
                    </label>
                    
                    {form.foodOptedIn && (
                      <div className="flex gap-4 mt-2 pl-5 animate-fade-in">
                        {[
                          { key: 'breakfastOpted', label: 'Breakfast', id: 'check-breakfast' },
                          { key: 'lunchOpted',     label: 'Lunch', id: 'check-lunch' },
                          { key: 'dinnerOpted',    label: 'Dinner', id: 'check-dinner' }
                        ].map(m => (
                          <label key={m.key} className="flex items-center gap-1.5 text-xs font-medium text-slate-600 cursor-pointer hover:text-slate-900 transition-colors">
                            <input 
                              type="checkbox" 
                              id={m.id}
                              checked={form[m.key]} 
                              onChange={e => setForm(f => ({ ...f, [m.key]: e.target.checked }))} 
                            />
                            <span>{m.label}</span>
                          </label>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              </div>

              <button type="submit" className="btn btn-primary" style={{ marginTop: '0.5rem' }} disabled={saving}>{saving?'Saving...':'Confirm Check-In'}</button>
            </form>
          )}
        </div>
      )}

      {/* Filter Toolbar for Guests List */}
      <div className="card" style={{ marginBottom: '1.5rem', padding: '1rem' }}>
        <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', alignItems: 'center' }}>
          <div className="form-group" style={{ margin: 0, flex: 1, minWidth: '200px' }}>
            <label className="form-label flex items-center gap-1.5">
              <Search className="w-4 h-4 text-slate-400" />
              <span>Search Guest (Name / Email / Phone)</span>
            </label>
            <input
              type="text"
              placeholder="Search..."
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              className="form-input"
            />
          </div>
          <div className="form-group" style={{ margin: 0, width: '200px' }}>
            <label className="form-label flex items-center gap-1.5">
              <MapPin className="w-4 h-4 text-slate-400" />
              <span>Filter by Floor</span>
            </label>
            <select
              value={selectedFloor}
              onChange={e => setSelectedFloor(e.target.value)}
              className="form-input"
            >
              {guestFloors.map(floor => (
                <option key={floor} value={floor}>{floor}</option>
              ))}
            </select>
          </div>
        </div>
      </div>

      <div className="card">
        <div className="table-wrap">
          <table>
            <thead><tr><th>Name</th><th>Bed</th><th>Check-In</th><th>KYC</th><th>Actions</th></tr></thead>
            <tbody>
              {filteredGuests.map(g => (
                <tr key={g.id}>
                  <td style={{fontWeight:600}}>{g.fullName}<div style={{fontSize:'0.75rem',color:'var(--text-muted)'}}>{g.email}</div></td>
                  <td><span className="badge badge-accent">{g.bedLabel ?? 'N/A'}</span></td>
                  <td style={{color:'var(--text-muted)'}}>{g.checkInDate}</td>
                  <td><span className={`badge ${g.kycStatus==='VERIFIED'?'badge-success':g.kycStatus==='REJECTED'?'badge-danger':'badge-warning'}`}>{g.kycStatus}</span></td>
                  <td>
                    <div style={{ display: 'flex', gap: '0.4rem' }}>
                      <button className="btn btn-primary flex items-center gap-1" style={{ fontSize: '0.8rem', padding: '0.3rem 0.6rem' }} onClick={() => startEdit(g)}>
                        <Edit2 className="w-3.5 h-3.5" />
                        <span>Edit</span>
                      </button>
                      <button className="btn btn-ghost flex items-center gap-1" style={{ fontSize: '0.8rem', padding: '0.3rem 0.6rem' }} onClick={() => initiateCheckout(g)}>
                        <LogOut className="w-3.5 h-3.5 text-slate-400" />
                        <span>Notice</span>
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {filteredGuests.length === 0 && (
                <tr>
                  <td colSpan={5} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '2rem' }}>
                    No active guests match the search filters.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Edit Guest Modal */}
      {editingGuest && createPortal(
        <div className="modal-overlay">
          <div className="modal-content card fade-in-up" style={{ maxWidth: 500, width: '100%' }}>
            <h3 style={{ marginBottom: '1.25rem', fontWeight: 800, color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Edit2 className="w-5 h-5 text-primary" />
              <span>Edit Guest Details</span>
            </h3>
            <form onSubmit={handleUpdate}>
              <div className="form-group">
                <label className="form-label">Full Name</label>
                <input className="form-input" value={editForm.fullName} onChange={e => setEditForm(f => ({ ...f, fullName: e.target.value }))} required />
              </div>
              <div className="form-group">
                <label className="form-label">Email ID</label>
                <input type="email" className="form-input" value={editForm.email} onChange={e => setEditForm(f => ({ ...f, email: e.target.value }))} required />
              </div>
              <div className="form-group">
                <label className="form-label">Phone</label>
                <input className="form-input" value={editForm.phone} onChange={e => setEditForm(f => ({ ...f, phone: e.target.value }))} required />
              </div>
              <div className="form-group">
                <label className="form-label">WhatsApp Number</label>
                <input className="form-input" value={editForm.whatsappNumber} onChange={e => setEditForm(f => ({ ...f, whatsappNumber: e.target.value }))} />
              </div>
              <div className="form-group">
                <label className="form-label">Advance Deposit (₹)</label>
                <input type="number" className="form-input" value={editForm.advanceDeposit} onChange={e => setEditForm(f => ({ ...f, advanceDeposit: e.target.value }))} required />
              </div>
              <div className="form-group">
                <label className="form-label">KYC Status</label>
                <select className="form-input" value={editForm.kycStatus} onChange={e => setEditForm(f => ({ ...f, kycStatus: e.target.value }))}>
                  <option value="PENDING">PENDING</option>
                  <option value="VERIFIED">VERIFIED</option>
                  <option value="REJECTED">REJECTED</option>
                </select>
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem', marginTop: '1.75rem' }}>
                <button type="button" className="btn btn-ghost" onClick={() => setEditingGuest(null)}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={updating}>{updating ? 'Saving...' : 'Save Changes'}</button>
              </div>
            </form>
          </div>
        </div>,
        document.body
      )}

      {/* Checkout Confirmation Modal */}
      {confirmCheckoutGuest && createPortal(
        <div className="modal-overlay">
          <div className="modal-content card fade-in-up" style={{ maxWidth: 450, width: '100%' }}>
            <h3 style={{ marginBottom: '1.25rem', fontWeight: 800, color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
              <div className="p-2 bg-amber-50 rounded-lg text-amber-500 flex items-center justify-center">
                <AlertTriangle className="w-5 h-5" />
              </div>
              <span>Initiate Checkout Notice</span>
            </h3>
            <div style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', marginBottom: '1.75rem', lineHeight: '1.5' }}>
              Are you sure you want to initiate the checkout notice for <strong style={{ color: 'var(--text-primary)' }}>{confirmCheckoutGuest.fullName}</strong>?
              <p style={{ marginTop: '0.5rem', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                This will trigger the notice period sequence according to the configured building rules.
              </p>
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem' }}>
              <button 
                type="button" 
                className="btn btn-ghost" 
                onClick={() => setConfirmCheckoutGuest(null)}
                disabled={checkoutLoading}
              >
                Cancel
              </button>
              <button 
                type="button" 
                className="btn btn-primary" 
                style={{ backgroundColor: '#f59e0b', borderColor: '#f59e0b', color: 'white' }} 
                onClick={handleConfirmCheckout} 
                disabled={checkoutLoading}
              >
                {checkoutLoading ? 'Processing...' : 'Confirm Notice'}
              </button>
            </div>
          </div>
        </div>,
        document.body
      )}
    </AppLayout>
  );
}

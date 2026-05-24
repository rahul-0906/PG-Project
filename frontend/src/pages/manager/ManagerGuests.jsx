import React, { useEffect, useState } from 'react';
import Sidebar from '../../components/Sidebar';
import { managerApi } from '../../api';

export default function ManagerGuests() {
  const [guests, setGuests] = useState([]);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ bedId:'', fullName:'', email:'', phone:'', whatsappNumber:'', advanceDeposit:'', checkInDate: new Date().toISOString().slice(0,10) });
  const [saving, setSaving] = useState(false);
  const [vacantBeds, setVacantBeds] = useState([]);
  const [selectedBedInfo, setSelectedBedInfo] = useState(null);
  const [loadingBeds, setLoadingBeds] = useState(false);

  useEffect(() => { managerApi.getGuests().then(r => setGuests(r.data)).catch(() => {}); }, []);

  useEffect(() => {
    if (showForm) {
      setLoadingBeds(true);
      managerApi.getVacantBeds()
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
    setForm({ bedId:'', fullName:'', email:'', phone:'', whatsappNumber:'', advanceDeposit:'', checkInDate: new Date().toISOString().slice(0,10) });
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

  const initiateCheckout = async (id) => {
    if (!window.confirm('Initiate checkout notice?')) return;
    await managerApi.initiateCheckout(id).catch(err => alert(err.response?.data?.error));
    managerApi.getGuests().then(r => setGuests(r.data));
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

  return (
    <div className="layout">
      <Sidebar />
      <div className="main-content fade-in">
        <div className="page-header">
          <div><h1 className="page-title">Guests 👥</h1><p className="page-subtitle">{guests.length} active</p></div>
          <button className="btn btn-primary" onClick={() => setShowForm(!showForm)}>{showForm ? '✕ Cancel' : '+ Check In'}</button>
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
                <div style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>⏳ Loading available beds...</div>
              ) : vacantBeds.length === 0 ? (
                <div style={{ color: 'var(--danger)', fontSize: '0.85rem' }}>⚠️ No vacant beds available in the building.</div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                  {Object.entries(groupedBeds).map(([floor, blocks]) => (
                    <div key={floor} style={{ background: 'var(--bg-primary)', padding: '1rem', borderRadius: '10px', border: '1px solid var(--border)' }}>
                      <div style={{ fontWeight: 700, fontSize: '0.85rem', color: 'var(--accent)', marginBottom: '0.75rem' }}>📍 {floor}</div>
                      
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                        {Object.entries(blocks).map(([blockName, rooms]) => (
                          <div key={blockName} style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', paddingLeft: '0.5rem', borderLeft: '2px solid var(--border)' }}>
                            <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', fontWeight: 600 }}>🧱 {blockName}</div>
                            
                            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.75rem' }}>
                              {Object.entries(rooms).map(([roomNum, beds]) => (
                                <div key={roomNum} style={{ background: 'var(--bg-secondary)', padding: '0.5rem 0.75rem', borderRadius: '8px', border: '1px solid var(--border)', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                                  <span style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--text-muted)' }}>🚪 {roomNum}:</span>
                                  <div style={{ display: 'flex', gap: '0.35rem' }}>
                                    {beds.map(bed => {
                                      const isSelected = form.bedId === bed.id;
                                      return (
                                        <button
                                          key={bed.id}
                                          type="button"
                                          onClick={() => {
                                            setForm(f => ({ ...f, bedId: bed.id }));
                                            setSelectedBedInfo(bed);
                                          }}
                                          style={{
                                            padding: '0.2rem 0.5rem',
                                            borderRadius: '4px',
                                            border: isSelected ? '1px solid var(--accent)' : '1px solid var(--border)',
                                            background: isSelected ? 'var(--accent)' : 'var(--bg-card)',
                                            color: isSelected ? '#fff' : 'var(--text-primary)',
                                            fontSize: '0.75rem',
                                            fontWeight: 700,
                                            cursor: 'pointer',
                                            transition: 'all 0.15s'
                                          }}
                                        >
                                          {bed.bedLabel}
                                        </button>
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
                  ))}
                </div>
              )}
            </div>
            
            {/* Step 2: Guest Details Form */}
            {form.bedId && selectedBedInfo && (
              <form onSubmit={checkIn} className="fade-in">
                <h4 style={{ marginBottom: '1rem', color: 'var(--text-secondary)', fontSize: '0.9rem', fontWeight: 600 }}>
                  2. Guest Details for Bed <span style={{ color: 'var(--success)' }}>{selectedBedInfo.bedLabel}</span> (Room {selectedBedInfo.room?.roomNumber}, Rent: ₹{selectedBedInfo.room?.baseRent})
                </h4>
                <div className="grid-3">
                  {[['fullName','Full Name'],['email','Email'],['phone','Phone'],['whatsappNumber','WhatsApp Number'],['advanceDeposit','Advance Deposit (₹)']].map(([key, label]) => (
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
                <button type="submit" className="btn btn-primary" style={{ marginTop: '1rem' }} disabled={saving}>{saving?'Saving...':'Confirm Check-In'}</button>
              </form>
            )}
          </div>
        )}
        <div className="card">
          <div className="table-wrap">
            <table>
              <thead><tr><th>Name</th><th>Bed</th><th>Check-In</th><th>KYC</th><th>Actions</th></tr></thead>
              <tbody>
                {guests.map(g => (
                  <tr key={g.id}>
                    <td style={{fontWeight:600}}>{g.fullName}<div style={{fontSize:'0.75rem',color:'var(--text-muted)'}}>{g.email}</div></td>
                    <td><span className="badge badge-accent">{g.bed?.bedLabel ?? 'N/A'}</span></td>
                    <td style={{color:'var(--text-muted)'}}>{g.checkInDate}</td>
                    <td><span className={`badge ${g.kycStatus==='VERIFIED'?'badge-success':g.kycStatus==='REJECTED'?'badge-danger':'badge-warning'}`}>{g.kycStatus}</span></td>
                    <td><button className="btn btn-ghost" style={{fontSize:'0.8rem',padding:'0.3rem 0.6rem'}} onClick={()=>initiateCheckout(g.id)}>🚪 Notice</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
}

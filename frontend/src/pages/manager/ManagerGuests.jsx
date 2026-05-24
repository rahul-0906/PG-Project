import React, { useEffect, useState } from 'react';
import Sidebar from '../../components/Sidebar';
import { managerApi } from '../../api';

export default function ManagerGuests() {
  const [guests, setGuests] = useState([]);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ bedId:'', fullName:'', email:'', phone:'', whatsappNumber:'', advanceDeposit:'', checkInDate: new Date().toISOString().slice(0,10) });
  const [saving, setSaving] = useState(false);

  useEffect(() => { managerApi.getGuests().then(r => setGuests(r.data)).catch(() => {}); }, []);

  const checkIn = async (e) => {
    e.preventDefault(); setSaving(true);
    await managerApi.checkIn(form).catch(err => alert(err.response?.data?.error || 'Failed'));
    setSaving(false); setShowForm(false);
    managerApi.getGuests().then(r => setGuests(r.data));
  };

  const initiateCheckout = async (id) => {
    if (!window.confirm('Initiate checkout notice?')) return;
    await managerApi.initiateCheckout(id).catch(err => alert(err.response?.data?.error));
    managerApi.getGuests().then(r => setGuests(r.data));
  };

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
            <form onSubmit={checkIn}>
              <div className="grid-3">
                {[['bedId','Bed ID'],['fullName','Full Name'],['email','Email'],['phone','Phone'],['whatsappNumber','WhatsApp Number'],['advanceDeposit','Advance Deposit (₹)']].map(([key, label]) => (
                  <div key={key} className="form-group">
                    <label className="form-label">{label}</label>
                    <input className="form-input" value={form[key]} onChange={e => setForm(f=>({...f,[key]:e.target.value}))} />
                  </div>
                ))}
                <div className="form-group">
                  <label className="form-label">Check-In Date</label>
                  <input type="date" className="form-input" value={form.checkInDate} onChange={e => setForm(f=>({...f,checkInDate:e.target.value}))} />
                </div>
              </div>
              <button type="submit" className="btn btn-primary" disabled={saving}>{saving?'Saving...':'Confirm Check-In'}</button>
            </form>
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

import React, { useEffect, useState } from 'react';
import Sidebar from '../../components/Sidebar';
import { ownerApi } from '../../api';

export default function OwnerDashboard() {
  const [data, setData] = useState(null);
  const [branches, setBranches] = useState([]);

  const [managers, setManagers] = useState([]);
  const [showManagerModal, setShowManagerModal] = useState(false);
  const [managerForm, setManagerForm] = useState({ fullName: '', email: '', branchId: '' });
  const [savingManager, setSavingManager] = useState(false);

  useEffect(() => {
    ownerApi.getDashboard().then(r => setData(r.data)).catch(()=>{});
    ownerApi.getBranches().then(r => setBranches(r.data)).catch(()=>{});
    ownerApi.getManagers().then(r => setManagers(r.data)).catch(()=>{});
  }, []);

  const saveManager = async (e) => {
    e.preventDefault();
    setSavingManager(true);
    try {
      await ownerApi.createManager(managerForm);
      setShowManagerModal(false);
      setManagerForm({ fullName: '', email: '', branchId: '' });
      ownerApi.getManagers().then(r => setManagers(r.data));
    } catch (err) { alert('Failed to create manager'); }
    finally { setSavingManager(false); }
  };

  return (
    <div className="layout">
      <Sidebar />
      <div className="main-content fade-in">
        <div className="page-header">
          <div><h1 className="page-title">Owner Dashboard 🏢</h1><p className="page-subtitle">Your property at a glance</p></div>
        </div>
        <div className="grid-4" style={{ marginBottom:'1.5rem' }}>
          {[
            { label:'🛏️ Total Beds', val: data?.totalBeds ?? '—' },
            { label:'✅ Occupied', val: data?.occupiedBeds ?? '—' },
            { label:'🟢 Vacant', val: data?.vacantBeds ?? '—' },
            { label:'👥 Active Guests', val: data?.activeGuests ?? '—' },
          ].map(s => (
            <div key={s.label} className="stat-card"><div className="stat-value">{s.val}</div><div className="stat-label">{s.label}</div></div>
          ))}
        </div>
        
        <div className="grid-2">
          <div className="card">
            <h3 style={{ marginBottom:'1rem', fontWeight:700 }}>🏗️ Branches ({branches.length})</h3>
            {branches.map(b => (
              <div key={b.id} style={{ padding:'0.875rem', borderRadius:10, background:'var(--bg-secondary)', marginBottom:'0.6rem', display:'flex', justifyContent:'space-between', alignItems:'center' }}>
                <div><div style={{fontWeight:600}}>{b.name}</div><div style={{fontSize:'0.8rem',color:'var(--text-muted)'}}>{b.address}</div></div>
                <span className="badge badge-success">Active</span>
              </div>
            ))}
            {branches.length === 0 && <p style={{color:'var(--text-muted)', fontSize:'0.85rem'}}>No branches configured yet.</p>}
          </div>

          <div className="card">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom:'1rem' }}>
              <h3 style={{ fontWeight:700, margin: 0 }}>👔 Managers ({managers.length})</h3>
              <button className="btn btn-primary" style={{ padding: '0.4rem 0.8rem', fontSize: '0.85rem' }} onClick={() => setShowManagerModal(true)}>+ Add Manager</button>
            </div>
            {managers.map(m => (
              <div key={m.id} style={{ padding:'0.875rem', borderRadius:10, background:'var(--bg-secondary)', marginBottom:'0.6rem', display:'flex', justifyContent:'space-between', alignItems:'center' }}>
                <div>
                  <div style={{fontWeight:600}}>{m.fullName}</div>
                  <div style={{fontSize:'0.8rem',color:'var(--text-muted)'}}>{m.email} • {branches.find(b => b.id === m.branchId)?.name || 'Unknown Branch'}</div>
                </div>
                <span className="badge badge-info">Active</span>
              </div>
            ))}
            {managers.length === 0 && <p style={{color:'var(--text-muted)', fontSize:'0.85rem'}}>No managers added yet.</p>}
          </div>
        </div>

        {showManagerModal && (
          <div className="modal-overlay">
            <div className="modal-content card fade-in" style={{ maxWidth: 500 }}>
              <h3 style={{ marginBottom: '1rem', fontWeight: 700 }}>Create Manager</h3>
              <form onSubmit={saveManager}>
                <div className="form-group">
                  <label className="form-label">Full Name</label>
                  <input className="form-input" value={managerForm.fullName} onChange={e => setManagerForm(f => ({...f, fullName: e.target.value}))} required />
                </div>
                <div className="form-group">
                  <label className="form-label">Email</label>
                  <input type="email" className="form-input" value={managerForm.email} onChange={e => setManagerForm(f => ({...f, email: e.target.value}))} required />
                </div>
                <div className="form-group">
                  <label className="form-label">Assign to Branch</label>
                  <select className="form-input" value={managerForm.branchId} onChange={e => setManagerForm(f => ({...f, branchId: e.target.value}))} required>
                    <option value="">-- Select Branch --</option>
                    {branches.map(b => (
                      <option key={b.id} value={b.id}>{b.name}</option>
                    ))}
                  </select>
                </div>
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem', marginTop: '1.5rem' }}>
                  <button type="button" className="btn btn-ghost" onClick={() => setShowManagerModal(false)}>Cancel</button>
                  <button type="submit" className="btn btn-primary" disabled={savingManager}>{savingManager ? 'Creating...' : 'Create Manager'}</button>
                </div>
              </form>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

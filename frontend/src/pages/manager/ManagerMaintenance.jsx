import React, { useEffect, useState } from 'react';
import Sidebar from '../../components/Sidebar';
import { managerApi } from '../../api';

export default function ManagerMaintenance() {
  const [tickets, setTickets] = useState([]);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ location:'', description:'', priority:'MEDIUM' });

  const refresh = () => managerApi.getMaintenanceTickets().then(r => setTickets(r.data)).catch(()=>{});
  useEffect(() => { refresh(); }, []);

  const create = async (e) => {
    e.preventDefault();
    await managerApi.createTicket(form).catch(err => alert(err.response?.data?.error));
    setShowForm(false); setForm({ location:'', description:'', priority:'MEDIUM' }); refresh();
  };

  const resolve = async (id) => {
    await managerApi.resolveTicket(id).catch(err => alert(err.response?.data?.error));
    refresh();
  };

  const priorityColors = { LOW:'badge-info', MEDIUM:'badge-warning', HIGH:'badge-danger' };
  const statusColors = { OPEN:'badge-danger', IN_PROGRESS:'badge-warning', RESOLVED:'badge-success' };

  return (
    <div className="layout">
      <Sidebar />
      <div className="main-content fade-in">
        <div className="page-header">
          <div><h1 className="page-title">Maintenance 🔧</h1><p className="page-subtitle">{tickets.filter(t=>t.status!=='RESOLVED').length} open tickets</p></div>
          <button className="btn btn-primary" onClick={()=>setShowForm(!showForm)}>{showForm?'✕ Cancel':'+ New Ticket'}</button>
        </div>
        {showForm && (
          <div className="card" style={{ marginBottom:'1.5rem', maxWidth:500 }}>
            <form onSubmit={create}>
              <div className="form-group"><label className="form-label">Location</label><input className="form-input" value={form.location} onChange={e=>setForm(f=>({...f,location:e.target.value}))} placeholder="e.g. Block A, Room 101" required /></div>
              <div className="form-group"><label className="form-label">Description</label><textarea className="form-input" rows={3} value={form.description} onChange={e=>setForm(f=>({...f,description:e.target.value}))} required /></div>
              <div className="form-group"><label className="form-label">Priority</label>
                <select className="form-input" value={form.priority} onChange={e=>setForm(f=>({...f,priority:e.target.value}))}>
                  <option>LOW</option><option>MEDIUM</option><option>HIGH</option>
                </select>
              </div>
              <button type="submit" className="btn btn-primary">Create Ticket</button>
            </form>
          </div>
        )}
        <div className="card">
          <div className="table-wrap">
            <table>
              <thead><tr><th>Location</th><th>Description</th><th>Priority</th><th>Status</th><th>Action</th></tr></thead>
              <tbody>
                {tickets.map(t => (
                  <tr key={t.id}>
                    <td style={{fontWeight:600}}>{t.location}</td>
                    <td style={{color:'var(--text-secondary)', maxWidth:240}}>{t.description}</td>
                    <td><span className={`badge ${priorityColors[t.priority]}`}>{t.priority}</span></td>
                    <td><span className={`badge ${statusColors[t.status]}`}>{t.status}</span></td>
                    <td>{t.status!=='RESOLVED' && <button className="btn btn-success" style={{fontSize:'0.8rem',padding:'0.3rem 0.7rem'}} onClick={()=>resolve(t.id)}>✅ Resolve</button>}</td>
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

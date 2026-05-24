import React, { useEffect, useState } from 'react';
import Sidebar from '../../components/Sidebar';
import { managerApi } from '../../api';

export default function ManagerMaintenance() {
  const [tickets, setTickets] = useState([]);

  const refresh = () => managerApi.getMaintenanceTickets().then(r => setTickets(r.data)).catch(()=>{});
  useEffect(() => { refresh(); }, []);

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
        </div>
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

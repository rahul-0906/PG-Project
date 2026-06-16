import React, { useEffect, useState } from 'react';
import AppLayout from '../../components/AppLayout';
import { managerApi } from '../../api';
import { Wrench, Check } from 'lucide-react';

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
    <AppLayout>
      <div className="page-header">
        <div>
          <h1 className="page-title flex items-center gap-2">
            <Wrench className="w-6 h-6 text-primary" strokeWidth={1.5}/>
            <span>Maintenance Desk</span>
          </h1>
          <p className="page-subtitle">{tickets.filter(t=>t.status!=='RESOLVED').length} open tickets</p>
        </div>
      </div>
      <div className="card" style={{ padding: '1.25rem' }}>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Location</th>
                <th>Description</th>
                <th>Priority</th>
                <th>Status</th>
                <th className="text-right">Action</th>
              </tr>
            </thead>
            <tbody>
              {tickets.map(t => (
                <tr key={t.id}>
                  <td className="font-semibold text-slate-900">{t.location}</td>
                  <td className="text-slate-600 font-normal max-w-[240px] truncate" title={t.description}>{t.description}</td>
                  <td><span className={`badge ${priorityColors[t.priority]}`}>{t.priority}</span></td>
                  <td><span className={`badge ${statusColors[t.status]}`}>{t.status}</span></td>
                  <td>
                    <div className="flex justify-end">
                      {t.status!=='RESOLVED' && (
                        <button className="btn btn-success flex items-center gap-1 py-1 px-2.5 text-xxs font-semibold" onClick={()=>resolve(t.id)}>
                          <Check className="w-3.5 h-3.5" strokeWidth={1.5}/>
                          <span>Resolve</span>
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
              {tickets.length === 0 && (
                <tr>
                  <td colSpan={5} className="text-center text-slate-400 py-8 font-medium">
                    No tickets found.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </AppLayout>
  );
}

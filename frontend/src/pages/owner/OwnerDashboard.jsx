import React, { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { Link } from 'react-router-dom';
import AppLayout from '../../components/AppLayout';
import { ownerApi } from '../../api';
import { useAuth } from '../../context/AuthContext';
import { 
  LayoutGrid, 
  Bed, 
  CheckCircle2, 
  Circle, 
  Users, 
  GitBranch, 
  UserCheck, 
  Plus, 
  UserPlus,
  Pencil,
  User as UserIcon
} from 'lucide-react';

function StatCard({ label, value, icon: Icon, iconBg = 'bg-slate-50', iconColor = 'text-slate-500' }) {
  return (
    <div className="bg-white rounded-xl border border-slate-200/80 p-3.5 flex items-center gap-3.5 shadow-sm hover:shadow-md hover:border-primary/30 transition-all duration-200">
      {Icon && (
        <div className={`p-2 rounded-lg ${iconBg} ${iconColor} flex items-center justify-center`}>
          <Icon className="w-4 h-4" />
        </div>
      )}
      <div>
        <div className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">{label}</div>
        <div className="text-xl font-black text-slate-900 tracking-tight mt-0.5">{value}</div>
      </div>
    </div>
  );
}

export default function OwnerDashboard() {
  const { user } = useAuth();
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
    <AppLayout>
      {/* Premium Welcome Banner */}
      <div className="relative overflow-hidden bg-gradient-to-br from-indigo-600 via-blue-600 to-violet-700 rounded-2xl p-4 sm:p-5 shadow-md shadow-indigo-100/60 mb-6 text-white">
        <div className="absolute right-0 top-0 translate-x-12 -translate-y-12 w-48 h-48 bg-white/5 rounded-full blur-3xl pointer-events-none" />
        <div className="absolute left-1/3 bottom-0 translate-y-1/2 w-36 h-36 bg-indigo-500/10 rounded-full blur-2xl pointer-events-none" />
        
        <div className="relative z-10 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
          <div className="flex items-center gap-3 sm:gap-4">
            <div className="p-2 sm:p-2.5 bg-white/10 backdrop-blur-md rounded-xl border border-white/10 shadow-inner">
              <UserIcon className="w-5 h-5 sm:w-6 sm:h-6 text-indigo-100" />
            </div>
            <div>
              <span className="text-indigo-200 text-[10px] sm:text-xs font-semibold tracking-wide uppercase">Owner Portal</span>
              <h1 className="text-lg sm:text-xl font-extrabold tracking-tight mt-0.5">
                Welcome back, {user?.fullName || 'PG Owner'}
              </h1>
              <div className="flex flex-wrap gap-2 mt-2">
                <span className="bg-white/10 backdrop-blur-md px-2.5 py-0.5 rounded-full text-[9px] font-bold uppercase tracking-wider border border-white/5">
                  Branches: {branches.length}
                </span>
                <span className="bg-white/10 backdrop-blur-md px-2.5 py-0.5 rounded-full text-[9px] font-bold uppercase tracking-wider border border-white/5">
                  Managers: {managers.length}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div className="page-header">
        <div>
          <h1 className="page-title flex items-center gap-2">
            <LayoutGrid className="w-6 h-6 text-primary" />
            <span>Owner Dashboard</span>
          </h1>
          <p className="page-subtitle">Your property at a glance</p>
        </div>
      </div>

      <div className="grid-4" style={{ marginBottom:'1.5rem' }}>
        <StatCard label="Total Beds" value={data?.totalBeds ?? '—'} icon={Bed} iconBg="bg-blue-50" iconColor="text-blue-500" />
        <StatCard label="Occupied" value={data?.occupiedBeds ?? '—'} icon={CheckCircle2} iconBg="bg-emerald-50" iconColor="text-emerald-500" />
        <StatCard label="Vacant" value={data?.vacantBeds ?? '—'} icon={Circle} iconBg="bg-slate-50" iconColor="text-slate-400" />
        <StatCard label="Active Guests" value={data?.activeGuests ?? '—'} icon={Users} iconBg="bg-violet-50" iconColor="text-violet-500" />
      </div>
      
      <div className="grid-2">
        <div className="card">
          <h3 className="text-slate-800 font-bold mb-4 flex items-center gap-2">
            <GitBranch className="w-5 h-5 text-slate-400" />
            <span>Branches ({branches.length})</span>
          </h3>
          {branches.map(b => (
            <div key={b.id} className="p-3.5 rounded-xl bg-slate-50 border border-slate-100 mb-2 flex justify-between items-center">
              <div>
                <div className="font-semibold text-sm text-slate-800">{b.name}</div>
                <div className="text-xs text-slate-400 mt-0.5">{b.address}</div>
              </div>
              <div className="flex items-center gap-2">
                <Link
                  to={`/owner/buildings?edit=${b.id}`}
                  className="btn btn-ghost p-1.5 rounded-lg hover:bg-slate-200 text-slate-600 hover:text-indigo-600 transition-colors flex items-center gap-1 text-xs"
                  title="Edit Layout & Details"
                >
                  <Pencil className="w-3.5 h-3.5" />
                  <span>Edit Layout</span>
                </Link>
                <span className="badge badge-success">Active</span>
              </div>
            </div>
          ))}
          {branches.length === 0 && <p className="text-slate-400 text-xs mt-2">No branches configured yet.</p>}
        </div>

        <div className="card">
          <div className="flex justify-between items-center mb-4">
            <h3 className="text-slate-800 font-bold flex items-center gap-2">
              <UserCheck className="w-5 h-5 text-slate-400" />
              <span>Managers ({managers.length})</span>
            </h3>
            <button className="btn btn-primary flex items-center gap-1 px-2.5 py-1 text-xs" onClick={() => setShowManagerModal(true)}>
              <Plus className="w-3.5 h-3.5" />
              <span>Add Manager</span>
            </button>
          </div>
          {managers.map(m => (
            <div key={m.id} className="p-3.5 rounded-xl bg-slate-50 border border-slate-100 mb-2 flex justify-between items-center">
              <div>
                <div className="font-semibold text-sm text-slate-800">{m.fullName}</div>
                <div className="text-xs text-slate-400 mt-0.5">{m.email} • {branches.find(b => b.id === m.branchId)?.name || 'Unknown Branch'}</div>
              </div>
              <span className="badge badge-info">Active</span>
            </div>
          ))}
          {managers.length === 0 && <p className="text-slate-400 text-xs mt-2">No managers added yet.</p>}
        </div>
      </div>

      {showManagerModal && createPortal(
        <div className="modal-overlay">
          <div className="modal-content card fade-in-up" style={{ maxWidth: 500 }}>
            <h3 className="text-base font-bold text-slate-900 mb-4 flex items-center gap-2">
              <UserPlus className="w-5 h-5 text-primary" />
              <span>Create Manager</span>
            </h3>
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
        </div>,
        document.body
      )}
    </AppLayout>
  );
}

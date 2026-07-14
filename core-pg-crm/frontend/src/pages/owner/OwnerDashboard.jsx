import React, { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import AppLayout from '../../components/AppLayout';
import { ownerApi } from '../../api';
import { useAuth } from '../../context/AuthContext';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
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
  Trash2,
  User as UserIcon
} from 'lucide-react';

function StatCard({ label, value, icon: Icon, iconColor = 'text-slate-500' }) {
  return (
    <div className="bg-white rounded-xl border border-slate-200 p-3.5 flex items-center gap-3.5 shadow-sm hover:shadow-md hover:border-primary/30 transition-all duration-200">
      {Icon && (
        <div className={`${iconColor} flex items-center justify-center`}>
          <Icon className="w-5 h-5" strokeWidth={1.5} />
        </div>
      )}
      <div>
        <div className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">{label}</div>
        <div className="text-xl font-extrabold text-slate-900 tracking-tight mt-0.5">{value}</div>
      </div>
    </div>
  );
}

export default function OwnerDashboard() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const location = useLocation();
  const navigate = useNavigate();
  
  const [showManagerModal, setShowManagerModal] = useState(false);
  const [managerForm, setManagerForm] = useState({ fullName: '', email: '', branchIds: [] });
  const [editingManager, setEditingManager] = useState(null);

  useEffect(() => {
    if (location.state?.openAddManager && location.state?.buildingId) {
      setManagerForm({
        fullName: '',
        email: '',
        branchIds: [location.state.buildingId]
      });
      setEditingManager(null);
      setShowManagerModal(true);
      // Clear the location state to prevent repeating on refresh
      navigate(location.pathname, { replace: true, state: {} });
    }
  }, [location.state, location.pathname, navigate]);

  const { data } = useQuery({
    queryKey: ['ownerDashboard'],
    queryFn: () => ownerApi.getDashboard().then(r => r.data),
  });

  const { data: branches = [] } = useQuery({
    queryKey: ['ownerBranches'],
    queryFn: () => ownerApi.getBranches().then(r => r.data),
  });

  const { data: managers = [] } = useQuery({
    queryKey: ['ownerManagers'],
    queryFn: () => ownerApi.getManagers().then(r => r.data),
  });

  const saveManagerMutation = useMutation({
    mutationFn: (payload) => {
      if (editingManager) {
        return ownerApi.updateManager(editingManager.id, payload);
      } else {
        return ownerApi.createManager(payload);
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ownerManagers'] });
      setShowManagerModal(false);
      setManagerForm({ fullName: '', email: '', branchIds: [] });
      setEditingManager(null);
    },
    onError: () => {
      alert(editingManager ? 'Failed to update manager' : 'Failed to create manager');
    }
  });

  const savingManager = saveManagerMutation.isPending;

  const deleteManagerMutation = useMutation({
    mutationFn: (id) => ownerApi.deleteManager(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ownerManagers'] });
    },
    onError: () => {
      alert('Failed to delete manager');
    }
  });

  const handleDeleteManager = (m) => {
    if (window.confirm(`Are you sure you want to delete manager "${m.fullName}"? This action cannot be undone.`)) {
      deleteManagerMutation.mutate(m.id);
    }
  };

  const openEditManager = (mgr) => {
    setEditingManager(mgr);
    setManagerForm({
      fullName: mgr.fullName || '',
      email: mgr.email || '',
      branchIds: mgr.branchId ? mgr.branchId.split(',') : [],
    });
    setShowManagerModal(true);
  };

  const openCreateManager = () => {
    setEditingManager(null);
    setManagerForm({ fullName: '', email: '', branchIds: [] });
    setShowManagerModal(true);
  };

  const getManagerBranchNames = (m) => {
    if (!m.branchId) return 'No branch assigned';
    const ids = m.branchId.split(',');
    return ids.map(id => branches.find(b => b.id === id)?.name || 'Unknown').filter(Boolean).join(', ');
  };

  const saveManager = (e) => {
    e.preventDefault();
    if (managerForm.branchIds.length === 0) return;
    saveManagerMutation.mutate({
      fullName: managerForm.fullName,
      email: managerForm.email,
      branchId: managerForm.branchIds.join(','),
    });
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
              <UserIcon className="w-5 h-5 sm:w-6 sm:h-6 text-indigo-100" strokeWidth={1.5} />
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
            <LayoutGrid className="w-6 h-6 text-primary" strokeWidth={1.5} />
            <span>Owner Dashboard</span>
          </h1>
          <p className="page-subtitle">Your property at a glance</p>
        </div>
      </div>

      <div className="grid-4" style={{ marginBottom:'1.5rem' }}>
        <StatCard label="Total Beds" value={data?.totalBeds ?? '—'} icon={Bed} iconColor="text-blue-500" />
        <StatCard label="Occupied" value={data?.occupiedBeds ?? '—'} icon={CheckCircle2} iconColor="text-emerald-500" />
        <StatCard label="Vacant" value={data?.vacantBeds ?? '—'} icon={Circle} iconColor="text-slate-400" />
        <StatCard label="Active Guests" value={data?.activeGuests ?? '—'} icon={Users} iconColor="text-violet-500" />
      </div>
      
      <div className="grid-2">
        <div className="card" style={{ padding: '1.25rem' }}>
          <div className="flex justify-between items-center mb-4">
            <h3 className="font-heading text-base font-semibold text-slate-900 flex items-center gap-2">
              <GitBranch className="w-5 h-5 text-slate-400" strokeWidth={1.5} />
              <span>Branches ({branches.length})</span>
            </h3>
            <Link
              to="/owner/buildings?create=true"
              className="btn btn-primary py-1.5 px-3 flex items-center gap-2 text-xs"
            >
              <Plus className="w-3.5 h-3.5" strokeWidth={1.5} />
              <span>Create Building</span>
            </Link>
          </div>
          {branches.map(b => (
            <div key={b.id} className="p-3.5 rounded-xl bg-slate-50 border border-slate-100 mb-2 flex justify-between items-center">
              <div>
                <div className="font-semibold text-sm text-slate-900">{b.name}</div>
                <div className="text-xs text-slate-500 font-normal mt-0.5">{b.address}</div>
              </div>
              <div className="flex items-center gap-2">
                <Link
                  to={`/owner/buildings?edit=${b.id}`}
                  className="btn btn-ghost p-1.5 rounded-lg hover:bg-slate-200 text-slate-600 hover:text-indigo-600 transition-colors flex items-center gap-2 text-xs"
                  title="Edit Layout & Details"
                >
                  <Pencil className="w-3.5 h-3.5" strokeWidth={1.5} />
                  <span>Edit Layout</span>
                </Link>
                <span className="badge badge-success">Active</span>
              </div>
            </div>
          ))}
          {branches.length === 0 && <p className="text-slate-400 text-xs mt-2 font-medium">No branches configured yet.</p>}
        </div>

        <div className="card" style={{ padding: '1.25rem' }}>
          <div className="flex justify-between items-center mb-4">
            <h3 className="font-heading text-base font-semibold text-slate-900 flex items-center gap-2">
              <UserCheck className="w-5 h-5 text-slate-400" strokeWidth={1.5} />
              <span>Managers ({managers.length})</span>
            </h3>
            <button 
              className="btn btn-primary flex items-center gap-2 px-2.5 py-1 text-xs font-semibold disabled:opacity-50 disabled:cursor-not-allowed" 
              onClick={openCreateManager}
              disabled={branches.length === 0}
              title={branches.length === 0 ? "Create a branch first before adding a manager" : "Add a new manager"}
            >
              <Plus className="w-3.5 h-3.5" strokeWidth={1.5} />
              <span>Add Manager</span>
            </button>
          </div>
          {managers.map(m => (
            <div key={m.id} className="p-3.5 rounded-xl bg-slate-50 border border-slate-100 mb-2 flex justify-between items-center">
              <div>
                <div className="font-semibold text-sm text-slate-900">{m.fullName}</div>
                <div className="text-xs text-slate-500 font-normal mt-0.5">
                  {m.email} • <span className="font-semibold text-slate-600">{getManagerBranchNames(m)}</span>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => openEditManager(m)}
                  className="btn btn-ghost p-1.5 rounded-lg hover:bg-slate-200 text-slate-600 hover:text-indigo-600 transition-colors flex items-center gap-2 text-xs"
                  title="Edit Manager"
                >
                  <Pencil className="w-3.5 h-3.5" strokeWidth={1.5} />
                  <span>Edit</span>
                </button>
                <button
                  type="button"
                  onClick={() => handleDeleteManager(m)}
                  className="btn btn-ghost p-1.5 rounded-lg hover:bg-rose-50 text-rose-600 hover:text-rose-700 transition-colors flex items-center gap-2 text-xs"
                  title="Delete Manager"
                >
                  <Trash2 className="w-3.5 h-3.5" strokeWidth={1.5} />
                  <span>Delete</span>
                </button>
                <span className="badge badge-info">Active</span>
              </div>
            </div>
          ))}
          {managers.length === 0 && <p className="text-slate-400 text-xs mt-2 font-medium">No managers added yet.</p>}
        </div>
      </div>

      {showManagerModal && createPortal(
        <div className="modal-overlay">
          <div className="modal-content card fade-in-up" style={{ maxWidth: 500 }}>
            <h3 className="font-heading text-base font-semibold text-slate-900 mb-4 flex items-center gap-2">
              <UserPlus className="w-5 h-5 text-primary" strokeWidth={1.5} />
              <span>{editingManager ? 'Edit Manager' : 'Create Manager'}</span>
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
                <label className="form-label">Assign to Branches *</label>
                <div className="grid grid-cols-2 gap-2 mt-1 border border-slate-200 rounded-lg p-3 bg-slate-50/50 max-h-40 overflow-y-auto">
                  {branches.map(b => (
                    <label key={b.id} className="flex items-center gap-2 cursor-pointer hover:bg-slate-100/50 p-1 rounded transition-colors text-xs font-semibold text-slate-700">
                      <input 
                        type="checkbox" 
                        checked={managerForm.branchIds.includes(b.id)} 
                        onChange={e => {
                          const checked = e.target.checked;
                          setManagerForm(f => {
                            const updated = checked 
                              ? [...f.branchIds, b.id] 
                              : f.branchIds.filter(id => id !== b.id);
                            return { ...f, branchIds: updated };
                          });
                        }}
                        className="rounded border-slate-300 text-primary focus:ring-primary"
                      />
                      <span>{b.name}</span>
                    </label>
                  ))}
                </div>
                {managerForm.branchIds.length === 0 && (
                  <p className="text-[10px] text-rose-500 font-semibold mt-1">Please select at least one branch.</p>
                )}
              </div>
              <div className="flex justify-end gap-2 mt-6">
                <button type="button" className="btn btn-ghost" onClick={() => setShowManagerModal(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={savingManager || managerForm.branchIds.length === 0}>
                  {savingManager ? 'Saving...' : editingManager ? 'Save Changes' : 'Create Manager'}
                </button>
              </div>
            </form>
          </div>
        </div>,
        document.body
      )}
    </AppLayout>
  );
}

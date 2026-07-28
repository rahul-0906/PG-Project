import React, { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import AppLayout from '../../components/AppLayout';
import { superAdminApi, ownerApi, managerApi } from '../../api';
import { useAuth } from '../../context/AuthContext';
import { 
  ShieldAlert, Users, DollarSign, Activity, FileText, 
  RefreshCw, Lock, Building, Bed, CheckCircle2, Search, Key,
  GitBranch, UserPlus, AlertCircle, ShieldCheck, FileSpreadsheet, ChevronRight, Trash2
} from 'lucide-react';
import BulkImportModal from '../manager/BulkImportModal';

function StatCard({ label, value, subtext, icon: Icon, iconColor = 'text-primary' }) {
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
        {subtext && <div className="text-[11px] text-slate-500 font-medium mt-0.5">{subtext}</div>}
      </div>
    </div>
  );
}

export default function SuperAdminDashboard() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const activeTab = searchParams.get('tab') || 'overview';
  const queryClient = useQueryClient();
  const [isBulkModalOpen, setIsBulkModalOpen] = useState(false);
  const [selectedBuildingForImport, setSelectedBuildingForImport] = useState('');
  const [selectedBranchForGuests, setSelectedBranchForGuests] = useState('ALL');
  const [guestSearchQuery, setGuestSearchQuery] = useState('');

  // Real DB Overview Summary
  const { data: summary, isLoading: isSummaryLoading, refetch: refetchSummary } = useQuery({
    queryKey: ['superAdminSummary'],
    queryFn: () => superAdminApi.getSummary().then(res => res.data),
  });

  // Real DB Branches/Buildings
  const { data: branches = [] } = useQuery({
    queryKey: ['ownerBranches'],
    queryFn: () => ownerApi.getBranches().then(r => r.data),
  });

  // Branch Guests Query
  const { data: guestsList = [], isLoading: isGuestsLoading } = useQuery({
    queryKey: ['superAdminGuestsList', selectedBranchForGuests],
    queryFn: () => managerApi.getGuests(selectedBranchForGuests === 'ALL' ? null : selectedBranchForGuests).then(r => r.data || []),
    enabled: activeTab === 'guests'
  });

  // Real DB Registered Users
  const { data: users = [], isLoading: isUsersLoading, refetch: refetchUsers } = useQuery({
    queryKey: ['superAdminUsers'],
    queryFn: () => superAdminApi.getUsers().then(res => res.data),
    enabled: activeTab === 'users' || activeTab === 'overview' || activeTab === 'provision',
  });

  // Real DB Audit Logs
  const [auditSearch, setAuditSearch] = useState('');
  const [auditPage, setAuditPage] = useState(0);
  const { data: auditData, isLoading: isAuditLoading } = useQuery({
    queryKey: ['superAdminAuditLogs', auditPage, auditSearch],
    queryFn: () => superAdminApi.getAuditLogs(auditPage, 15, auditSearch).then(res => res.data),
    enabled: activeTab === 'audit',
  });

  // PG Owner Provisioning Form State
  const [ownerForm, setOwnerForm] = useState({
    email: '',
    password: '',
    fullName: '',
    phone: '',
  });
  const [ownerMsg, setOwnerMsg] = useState(null);

  const createOwnerMutation = useMutation({
    mutationFn: (data) => superAdminApi.createPgOwner(data),
    onSuccess: (res) => {
      setOwnerMsg({ type: 'success', text: `PG Owner profile '${res.data.email}' provisioned successfully!` });
      setOwnerForm({ email: '', password: '', fullName: '', phone: '' });
      refetchUsers();
      refetchSummary();
    },
    onError: (err) => {
      const errorMsg = err.response?.data?.message || err.response?.data?.error || (typeof err.response?.data === 'string' ? err.response?.data : null) || err.message || 'Failed to provision PG Owner account.';
      setOwnerMsg({ type: 'error', text: errorMsg });
    }
  });

  const deleteUserMutation = useMutation({
    mutationFn: (userId) => superAdminApi.deleteUser(userId),
    onSuccess: () => {
      refetchUsers();
      refetchSummary();
    },
    onError: (err) => {
      const msg = err.response?.data?.message || err.response?.data?.error || err.message || 'Failed to delete user account.';
      alert(msg);
    }
  });

  // Building Creation Form State
  const [bldForm, setBldForm] = useState({
    name: '',
    address: '',
    totalFloors: 3,
    roomsPerFloor: 4,
    bedsPerRoom: 2,
    rentPerBed: 6000,
  });
  const [bldMsg, setBldMsg] = useState(null);

  const createBuildingMutation = useMutation({
    mutationFn: (data) => superAdminApi.createBuilding(data),
    onSuccess: (res) => {
      setBldMsg({ type: 'success', text: `Building '${res.data?.building?.name || bldForm.name}' created successfully with generated floors, rooms & beds!` });
      setBldForm({ name: '', address: '', totalFloors: 3, roomsPerFloor: 4, bedsPerRoom: 2, rentPerBed: 6000 });
      queryClient.invalidateQueries(['ownerBranches']);
      refetchSummary();
    },
    onError: (err) => {
      setBldMsg({ type: 'error', text: err.response?.data?.message || err.response?.data?.error || err.message || 'Failed to create building.' });
    }
  });

  // Financial Override State
  const [overrideForm, setOverrideForm] = useState({
    guestId: '',
    invoiceId: '',
    overrideType: 'WAIVE_RENT',
    amount: '',
    reason: '',
  });
  const [overrideMessage, setOverrideMessage] = useState(null);

  const overrideMutation = useMutation({
    mutationFn: (data) => superAdminApi.applyFinancialOverride(data),
    onSuccess: (res) => {
      setOverrideMessage({ type: 'success', text: res.data.message || 'Financial override committed to database!' });
      setOverrideForm({ guestId: '', invoiceId: '', overrideType: 'WAIVE_RENT', amount: '', reason: '' });
      refetchSummary();
    },
    onError: (err) => {
      setOverrideMessage({ type: 'error', text: err.response?.data?.message || err.message || 'Failed to apply override.' });
    }
  });

  const handleOverrideSubmit = (e) => {
    e.preventDefault();
    setOverrideMessage(null);
    overrideMutation.mutate({
      ...overrideForm,
      amount: parseFloat(overrideForm.amount) || 0,
    });
  };

  // Role Edit Modal State
  const [editingUser, setEditingUser] = useState(null);
  const [roleForm, setRoleForm] = useState({ newRole: 'PG_MANAGER', branchId: '' });

  const roleMutation = useMutation({
    mutationFn: (data) => superAdminApi.updateUserRole(data.userId, data.newRole, data.branchId),
    onSuccess: () => {
      setEditingUser(null);
      refetchUsers();
      refetchSummary();
    }
  });

  // PII Anonymization State
  const [anonymizeGuestId, setAnonymizeGuestId] = useState('');
  const [anonymizeMsg, setAnonymizeMsg] = useState(null);

  const anonymizeMutation = useMutation({
    mutationFn: (id) => superAdminApi.anonymizeGuest(id),
    onSuccess: (res) => {
      setAnonymizeMsg({ type: 'success', text: res.data.message });
      setAnonymizeGuestId('');
    },
    onError: (err) => {
      setAnonymizeMsg({ type: 'error', text: err.response?.data?.message || 'Failed to anonymize guest PII.' });
    }
  });

  return (
    <AppLayout>
      {/* TAB 1: SYSTEM OVERVIEW (Dashboard Tab Only) */}
      {activeTab === 'overview' && (
        <div className="space-y-6">
          {/* Premium Welcome Banner */}
          <div className="relative overflow-hidden bg-gradient-to-br from-indigo-600 via-blue-600 to-violet-700 rounded-2xl p-4 sm:p-5 shadow-md shadow-indigo-100/60 mb-6 text-white">
            <div className="absolute right-0 top-0 translate-x-12 -translate-y-12 w-48 h-48 bg-white/5 rounded-full blur-3xl pointer-events-none" />
            <div className="absolute left-1/3 bottom-0 translate-y-1/2 w-36 h-36 bg-indigo-500/10 rounded-full blur-2xl pointer-events-none" />
            
            <div className="relative z-10 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
              <div className="flex items-center gap-3 sm:gap-4">
                <div className="p-2 sm:p-2.5 bg-white/10 backdrop-blur-md rounded-xl border border-white/10 shadow-inner">
                  <ShieldAlert className="w-5 h-5 sm:w-6 sm:h-6 text-rose-200" strokeWidth={1.5} />
                </div>
                <div>
                  <span className="text-indigo-200 text-[10px] sm:text-xs font-semibold tracking-wide uppercase">Super Admin Portal</span>
                  <h1 className="text-lg sm:text-xl font-extrabold tracking-tight mt-0.5">
                    Master Provisioning & Control Center
                  </h1>
                  <div className="flex flex-wrap gap-2 mt-2">
                    <span className="bg-white/10 backdrop-blur-md px-2.5 py-0.5 rounded-full text-[9px] font-bold uppercase tracking-wider border border-white/5">
                      Buildings: {summary?.totalBuildings || branches.length || 0}
                    </span>
                    <span className="bg-white/10 backdrop-blur-md px-2.5 py-0.5 rounded-full text-[9px] font-bold uppercase tracking-wider border border-white/5">
                      Beds: {summary?.totalBeds || 0}
                    </span>
                    <span className="bg-white/10 backdrop-blur-md px-2.5 py-0.5 rounded-full text-[9px] font-bold uppercase tracking-wider border border-white/5">
                      Accounts: {users.length || summary?.totalGuests || 0}
                    </span>
                  </div>
                </div>
              </div>

              <button
                onClick={() => { refetchSummary(); refetchUsers(); }}
                className="flex items-center gap-2 px-3.5 py-2 bg-white/10 hover:bg-white/20 text-white text-xs font-semibold rounded-xl border border-white/10 backdrop-blur-md shadow-sm transition"
              >
                <RefreshCw className="w-3.5 h-3.5" />
                Refresh DB Values
              </button>
            </div>
          </div>

          {/* Page Title & Subtitle */}
          <div className="page-header mb-4">
            <div>
              <h1 className="page-title flex items-center gap-2">
                <ShieldAlert className="w-6 h-6 text-rose-600" strokeWidth={1.5} />
                <span>Super Admin Dashboard</span>
              </h1>
              <p className="page-subtitle">Provision PG_OWNER accounts and view system metrics</p>
            </div>
          </div>

          {/* Primary DB Stat Cards */}
          <div className="grid-4 mb-6">
            <StatCard
              label="Total Infrastructure"
              value={summary?.totalBuildings || 0}
              subtext={`${summary?.totalRooms || 0} Rooms / ${summary?.totalBeds || 0} Beds`}
              icon={Building}
              iconColor="text-blue-500"
            />
            <StatCard
              label="Bed Occupancy"
              value={`${summary?.occupancyRate || 0}%`}
              subtext={`${summary?.occupiedBeds || 0} Occupied / ${summary?.vacantBeds || 0} Vacant`}
              icon={Bed}
              iconColor="text-emerald-500"
            />
            <StatCard
              label="Revenue Collected"
              value={`₹${summary?.totalRevenueCollected?.toLocaleString('en-IN') || 0}`}
              subtext="Real Database Total Paid"
              icon={CheckCircle2}
              iconColor="text-emerald-500"
            />
            <StatCard
              label="Pending Rent Arrears"
              value={`₹${summary?.totalPendingArrears?.toLocaleString('en-IN') || 0}`}
              subtext="Generated & Overdue Invoices"
              icon={DollarSign}
              iconColor="text-amber-500"
            />
          </div>
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Managed Buildings Overview */}
            <div className="card p-5">
              <div className="flex justify-between items-center mb-4">
                <h3 className="font-heading text-base font-semibold text-slate-900 flex items-center gap-2">
                  <GitBranch className="w-5 h-5 text-slate-400" strokeWidth={1.5} />
                  <span>Managed Buildings / Branches ({branches.length})</span>
                </h3>
              </div>

              <div className="space-y-3">
                {branches.length === 0 ? (
                  <div className="p-6 text-center text-slate-400 text-xs">No buildings created yet</div>
                ) : (
                  branches.map(b => (
                    <div key={b.id} className="p-3.5 rounded-lg border border-slate-200 bg-slate-50/50 hover:bg-slate-50 transition flex justify-between items-center">
                      <div>
                        <div className="font-bold text-slate-900 text-sm">{b.name}</div>
                        <div className="text-xs text-slate-500">{b.address || 'Address not provided'}</div>
                      </div>
                      <div className="text-right">
                        <span className="px-2.5 py-1 text-xs font-bold rounded-full bg-blue-50 text-blue-700 border border-blue-200">
                          Active Branch
                        </span>
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>

            {/* Account Role Breakdown */}
            <div className="card p-5">
              <h3 className="font-heading text-base font-semibold text-slate-900 flex items-center gap-2 mb-4">
                <Users className="w-5 h-5 text-slate-400" strokeWidth={1.5} />
                <span>User Roles Breakdown</span>
              </h3>
              <div className="grid grid-cols-2 gap-3">
                {Object.entries(summary?.userCountByRole || {}).map(([role, count]) => (
                  <div key={role} className="bg-slate-50 p-4 rounded-xl border border-slate-200">
                    <div className="text-[11px] font-bold uppercase tracking-wider text-slate-500">{role}</div>
                    <div className="text-2xl font-extrabold text-slate-900 mt-1">{count}</div>
                  </div>
                ))}
              </div>

              <div className="mt-4 pt-4 border-t border-slate-100 flex items-center justify-between text-xs">
                <span className="text-slate-500">Super Admin Mode</span>
                <span className="font-bold text-indigo-600">● PROVISIONING PERMITTED</span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* TAB: PROVISION PG OWNER PROFILE */}
      {activeTab === 'provision' && (
        <div className="max-w-2xl card p-6 space-y-4">
          <div>
            <h3 className="font-heading text-base font-semibold text-slate-900 flex items-center gap-2">
              <UserPlus className="w-5 h-5 text-primary" strokeWidth={1.5} />
              <span>Provision PG_OWNER Profile</span>
            </h3>
            <p className="text-xs text-slate-500 mt-0.5">As Super Admin, create new PG Owner accounts. PG Owners can then log in to configure their buildings, rooms, and manager accounts.</p>
          </div>

          {ownerMsg && (
            <div className={`p-3.5 rounded-lg text-xs font-semibold ${
              ownerMsg.type === 'success' ? 'bg-emerald-50 border border-emerald-200 text-emerald-700' : 'bg-rose-50 border border-rose-200 text-rose-700'
            }`}>
              {ownerMsg.text}
            </div>
          )}

          <form onSubmit={(e) => { e.preventDefault(); createOwnerMutation.mutate(ownerForm); }} className="space-y-4">
            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1">PG Owner Email Address *</label>
              <input
                type="email"
                required
                value={ownerForm.email}
                onChange={(e) => setOwnerForm({ ...ownerForm, email: e.target.value })}
                placeholder="e.g. owner@pgbrand.com"
                className="w-full bg-slate-50 border border-slate-300 rounded-lg p-2.5 text-sm text-slate-900 focus:outline-none focus:border-primary"
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1">Full Legal Name *</label>
              <input
                type="text"
                required
                value={ownerForm.fullName}
                onChange={(e) => setOwnerForm({ ...ownerForm, fullName: e.target.value })}
                placeholder="e.g. Rajesh Kumar"
                className="w-full bg-slate-50 border border-slate-300 rounded-lg p-2.5 text-sm text-slate-900 focus:outline-none focus:border-primary"
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Temporary Password *</label>
                <input
                  type="password"
                  required
                  value={ownerForm.password}
                  onChange={(e) => setOwnerForm({ ...ownerForm, password: e.target.value })}
                  placeholder="Temporary password"
                  className="w-full bg-slate-50 border border-slate-300 rounded-lg p-2.5 text-sm text-slate-900 focus:outline-none focus:border-primary"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Phone Number</label>
                <input
                  type="text"
                  value={ownerForm.phone}
                  onChange={(e) => setOwnerForm({ ...ownerForm, phone: e.target.value })}
                  placeholder="+919876543210"
                  className="w-full bg-slate-50 border border-slate-300 rounded-lg p-2.5 text-sm text-slate-900 focus:outline-none focus:border-primary"
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={createOwnerMutation.isPending}
              className="w-full py-2.5 bg-primary hover:bg-primary/90 text-white font-bold text-sm rounded-lg shadow-sm transition"
            >
              {createOwnerMutation.isPending ? 'Provisioning Account...' : 'Provision PG Owner Profile'}
            </button>
          </form>
        </div>
      )}

      {/* TAB: CREATE BUILDING / BRANCH */}
      {activeTab === 'create_building' && (
        <div className="max-w-2xl card p-6 space-y-5">
          <div>
            <h3 className="font-heading text-base font-semibold text-slate-900 flex items-center gap-2">
              <Building className="w-5 h-5 text-indigo-600" strokeWidth={1.5} />
              <span>Create Building / Branch Infrastructure</span>
            </h3>
            <p className="text-xs text-slate-500 mt-0.5">As Super Admin, launch the Visual Interactive Wizard (identical to PG Owner setup) to design and provision property layouts.</p>
          </div>

          {/* Interactive Visual Building Builder Card */}
          <div className="p-5 bg-gradient-to-br from-indigo-50 to-blue-50/60 border border-indigo-200/80 rounded-2xl flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 shadow-sm">
            <div>
              <div className="font-bold text-indigo-950 text-sm flex items-center gap-2">
                <Building className="w-4.5 h-4.5 text-indigo-600" />
                <span>Interactive Visual Building Builder Wizard</span>
              </div>
              <div className="text-xs text-indigo-800/80 mt-1 max-w-md">Launch the full step-by-step Floor, Block & Room Sharing visual wizard to configure buildings, floor maps, and bed rates.</div>
            </div>
            <button
              type="button"
              onClick={() => navigate('/owner/buildings')}
              className="px-5 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-bold transition flex items-center gap-2 shadow-sm whitespace-nowrap"
            >
              <span>Launch Visual Builder</span>
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}

      {/* TAB: BRANCH GUESTS DIRECTORY */}
      {activeTab === 'guests' && (
        <div className="card p-6 space-y-6">
          <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 border-b border-slate-200 pb-4">
            <div>
              <h3 className="font-heading text-base font-semibold text-slate-900 flex items-center gap-2">
                <Users className="w-5 h-5 text-indigo-600" strokeWidth={1.5} />
                <span>Enterprise Branch Guest Directory</span>
              </h3>
              <p className="text-xs text-slate-500 mt-0.5">View and filter active guest profiles, bed assignments, and financial statuses based on property branches.</p>
            </div>
          </div>

          {/* Filter Bar */}
          <div className="flex flex-wrap gap-4 items-center bg-slate-50 p-3.5 rounded-xl border border-slate-200/80">
            <div className="flex-1 min-w-[240px]">
              <label className="block text-[11px] font-bold text-slate-600 uppercase tracking-wider mb-1">Search Resident</label>
              <div className="relative">
                <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
                <input
                  type="text"
                  placeholder="Search by name, phone, or email..."
                  value={guestSearchQuery}
                  onChange={(e) => setGuestSearchQuery(e.target.value)}
                  className="w-full bg-white border border-slate-300 rounded-lg pl-9 pr-3 py-2 text-xs text-slate-900 focus:outline-none focus:border-primary font-medium"
                />
              </div>
            </div>

            <div className="w-[260px]">
              <label className="block text-[11px] font-bold text-slate-600 uppercase tracking-wider mb-1 flex items-center gap-1">
                <Building className="w-3.5 h-3.5 text-indigo-600" />
                <span>Filter by Branch / Building</span>
              </label>
              <select
                value={selectedBranchForGuests}
                onChange={(e) => setSelectedBranchForGuests(e.target.value)}
                className="w-full bg-white border border-slate-300 rounded-lg p-2 text-xs font-semibold text-slate-800 focus:outline-none focus:border-primary"
              >
                <option value="ALL">All Property Branches ({branches.length})</option>
                {branches.map(b => (
                  <option key={b.id} value={b.id}>{b.name}</option>
                ))}
              </select>
            </div>
          </div>

          {/* Table */}
          {isGuestsLoading ? (
            <div className="p-8 text-center text-slate-400 text-xs font-medium">Loading branch guest profiles...</div>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Branch / Property</th>
                    <th>Resident Name</th>
                    <th>Bed / Room</th>
                    <th>Monthly Rent</th>
                    <th>Security Deposit</th>
                    <th>Check-In Date</th>
                    <th>KYC Status</th>
                  </tr>
                </thead>
                <tbody>
                  {guestsList
                    .filter(g => {
                      if (!guestSearchQuery) return true;
                      const q = guestSearchQuery.toLowerCase();
                      return g.fullName?.toLowerCase().includes(q) || g.phone?.includes(q) || g.email?.toLowerCase().includes(q);
                    })
                    .map(g => (
                      <tr key={g.id}>
                        <td>
                          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-semibold bg-indigo-50 text-indigo-900 border border-indigo-200/80">
                            <Building className="w-3.5 h-3.5 text-indigo-600" />
                            <span>{g.buildingName || 'Main Branch'}</span>
                          </span>
                        </td>
                        <td className="font-semibold text-slate-900">
                          <div>{g.fullName}</div>
                          <div className="text-xs text-slate-500 font-normal">{g.phone || g.email}</div>
                        </td>
                        <td>
                          <span className="badge badge-accent border border-violet-100">
                            {g.assignedBeds || g.bedLabel || 'Unassigned'} {g.roomNumber ? `(Room ${g.roomNumber})` : ''}
                          </span>
                        </td>
                        <td className="font-semibold text-slate-900">
                          ₹{g.baseRent ? Number(g.baseRent).toLocaleString('en-IN') : '0'}
                        </td>
                        <td className="text-slate-600 font-medium">
                          ₹{g.advanceDeposit ? Number(g.advanceDeposit).toLocaleString('en-IN') : '0'}
                        </td>
                        <td className="text-slate-500 font-normal">
                          {g.checkInDate || '—'}
                        </td>
                        <td>
                          <span className={`badge text-[10px] font-bold ${
                            g.kycStatus === 'VERIFIED' ? 'bg-emerald-50 text-emerald-700 border-emerald-200' : 'bg-amber-50 text-amber-700 border-amber-200'
                          }`}>
                            {g.kycStatus || 'PENDING'}
                          </span>
                        </td>
                      </tr>
                    ))}
                  {guestsList.length === 0 && (
                    <tr>
                      <td colSpan="7" className="text-center py-8 text-slate-400 text-xs">
                        No active guests found for the selected branch filter.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* TAB: BULK EXCEL MIGRATION */}
      {activeTab === 'bulk_import' && (
        <div className="max-w-2xl card p-6 space-y-4">
          <div>
            <h3 className="font-heading text-base font-semibold text-slate-900 flex items-center gap-2">
              <FileSpreadsheet className="w-5 h-5 text-emerald-600" strokeWidth={1.5} />
              <span>Bulk Excel / CSV Initial Onboarding</span>
            </h3>
            <p className="text-xs text-slate-500 mt-0.5">Upload legacy spreadsheet data (.xlsx, .csv) to bulk import rooms, beds, guests, opening rent arrears, and EB sub-meter readings in 1-Click.</p>
          </div>

          <div className="space-y-4">
            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1">Select Target Building / Branch *</label>
              <select
                value={selectedBuildingForImport}
                onChange={(e) => {
                  setSelectedBuildingForImport(e.target.value);
                  sessionStorage.setItem('selectedBranchId', e.target.value);
                }}
                className="w-full bg-slate-50 border border-slate-300 rounded-lg p-2.5 text-sm text-slate-900 focus:outline-none focus:border-primary font-medium"
              >
                <option value="">-- Choose a Building / Branch --</option>
                {branches.map(b => (
                  <option key={b.id} value={b.id}>{b.name} ({b.address || 'Default'})</option>
                ))}
              </select>
            </div>

            <button
              disabled={!selectedBuildingForImport}
              onClick={() => setIsBulkModalOpen(true)}
              className={`w-full py-3 rounded-xl font-bold text-sm flex items-center justify-center gap-2 shadow-sm transition ${
                selectedBuildingForImport
                  ? 'bg-emerald-600 hover:bg-emerald-700 text-white'
                  : 'bg-slate-100 text-slate-400 cursor-not-allowed'
              }`}
            >
              <FileSpreadsheet className="w-4 h-4" />
              <span>Launch 1-Click Excel Importer</span>
            </button>
          </div>
        </div>
      )}

      {/* TAB 2: USER & RBAC CONTROLS */}
      {activeTab === 'users' && (
        <div className="card p-5 space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="font-heading text-base font-semibold text-slate-900 flex items-center gap-2">
              <Users className="w-5 h-5 text-slate-400" strokeWidth={1.5} />
              <span>System User Accounts ({users.length})</span>
            </h3>
          </div>

          <div className="p-3 bg-amber-50 border border-amber-200 rounded-lg flex items-center gap-2.5 text-xs text-amber-800">
            <AlertCircle className="w-4 h-4 text-amber-600 flex-shrink-0" />
            <span><strong>Notice:</strong> Data modifications or role edits require explicit permission granted by the PG Owner.</span>
          </div>

          {isUsersLoading ? (
            <div className="p-8 text-center text-slate-400">Loading user accounts...</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-slate-700">
                <thead className="bg-slate-50 text-xs uppercase text-slate-500 font-bold border-b border-slate-200">
                  <tr>
                    <th className="p-3">User</th>
                    <th className="p-3">Role</th>
                    <th className="p-3">Branch ID(s)</th>
                    <th className="p-3">Super Admin Permission</th>
                    <th className="p-3">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {users.map(u => (
                    <tr key={u.id} className="hover:bg-slate-50/50">
                      <td className="p-3 font-medium text-slate-900">
                        <div>{u.email}</div>
                        <div className="text-xs text-slate-500">{u.fullName || 'N/A'}</div>
                      </td>
                      <td className="p-3">
                        <span className={`inline-block px-2.5 py-1 text-xs font-bold rounded-full border ${
                          u.role === 'SUPER_ADMIN' ? 'bg-rose-50 text-rose-700 border-rose-200' :
                          u.role === 'PG_OWNER' ? 'bg-amber-50 text-amber-700 border-amber-200' :
                          u.role === 'PG_MANAGER' ? 'bg-violet-50 text-violet-700 border-violet-200' :
                          'bg-emerald-50 text-emerald-700 border-emerald-200'
                        }`}>
                          {u.role}
                        </span>
                      </td>
                      <td className="p-3 text-xs text-slate-500 font-mono">{u.branchId || 'All Branches'}</td>
                      <td className="p-3">
                        {u.role === 'PG_OWNER' ? (
                          <span className={`text-xs font-bold ${u.allowSuperAdminAccess ? 'text-emerald-600' : 'text-amber-600'}`}>
                            {u.allowSuperAdminAccess ? '✓ Granted' : '🔒 Not Granted'}
                          </span>
                        ) : (
                          <span className="text-xs text-slate-400">—</span>
                        )}
                      </td>
                      <td className="p-3">
                        <div className="flex items-center gap-2">
                          <button
                            onClick={() => { setEditingUser(u); setRoleForm({ newRole: u.role, branchId: u.branchId || '' }); }}
                            className="px-3 py-1 bg-slate-900 hover:bg-slate-800 text-xs font-semibold text-white rounded-lg transition shadow-sm"
                          >
                            Modify Role
                          </button>
                          {u.role !== 'SUPER_ADMIN' && (
                            <button
                              onClick={() => {
                                if (window.confirm(`Are you sure you want to delete user account '${u.email}' (${u.role})?`)) {
                                  deleteUserMutation.mutate(u.id);
                                }
                              }}
                              disabled={deleteUserMutation.isPending}
                              className="px-2.5 py-1 bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200 text-xs font-semibold rounded-lg transition flex items-center gap-1 shadow-2xs"
                              title="Delete Account"
                            >
                              <Trash2 className="w-3.5 h-3.5" />
                              <span>Delete</span>
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* Edit Role Modal */}
          {editingUser && (
            <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-sm flex items-center justify-center p-4 z-50">
              <div className="bg-white border border-slate-200 rounded-xl p-6 max-w-md w-full shadow-xl">
                <h4 className="text-lg font-bold text-slate-900 mb-1">Modify Role & Access Level</h4>
                <p className="text-xs text-slate-500 mb-4">{editingUser.email}</p>

                <div className="space-y-4">
                  <div>
                    <label className="block text-xs font-bold text-slate-700 mb-1">Assigned Role</label>
                    <select
                      value={roleForm.newRole}
                      onChange={(e) => setRoleForm({ ...roleForm, newRole: e.target.value })}
                      className="w-full bg-slate-50 border border-slate-300 rounded-lg p-2.5 text-sm text-slate-900 focus:outline-none focus:border-primary"
                    >
                      <option value="SUPER_ADMIN">SUPER_ADMIN (Full Upper-Level Access)</option>
                      <option value="PG_OWNER">PG_OWNER (All Buildings Access)</option>
                      <option value="PG_MANAGER">PG_MANAGER (Scoped Building Operator)</option>
                      <option value="GUEST">GUEST (Resident Portal)</option>
                    </select>
                  </div>

                  <div>
                    <label className="block text-xs font-bold text-slate-700 mb-1">Branch ID(s) (Comma separated UUIDs for PG_MANAGER)</label>
                    <input
                      type="text"
                      value={roleForm.branchId}
                      onChange={(e) => setRoleForm({ ...roleForm, branchId: e.target.value })}
                      placeholder="e.g. bld-uuid-1,bld-uuid-2"
                      className="w-full bg-slate-50 border border-slate-300 rounded-lg p-2.5 text-sm text-slate-900 focus:outline-none focus:border-primary font-mono"
                    />
                  </div>
                </div>

                <div className="flex justify-end gap-2.5 mt-6">
                  <button
                    onClick={() => setEditingUser(null)}
                    className="px-4 py-2 bg-slate-100 text-slate-700 text-xs font-bold rounded-lg hover:bg-slate-200"
                  >
                    Cancel
                  </button>
                  <button
                    onClick={() => roleMutation.mutate({ userId: editingUser.id, ...roleForm })}
                    disabled={roleMutation.isPending}
                    className="px-4 py-2 bg-slate-900 text-white text-xs font-bold rounded-lg hover:bg-slate-800"
                  >
                    {roleMutation.isPending ? 'Saving...' : 'Save Role Changes'}
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* TAB 3: FINANCIAL OVERRIDES */}
      {activeTab === 'overrides' && (
        <div className="max-w-2xl card p-6 space-y-4">
          <div>
            <h3 className="font-heading text-base font-semibold text-slate-900 flex items-center gap-2">
              <DollarSign className="w-5 h-5 text-slate-400" strokeWidth={1.5} />
              <span>Super Admin Financial Override</span>
            </h3>
            <p className="text-xs text-slate-500 mt-0.5">Execute rent waivers or deposit adjustments. Data modifications require PG Owner permission grant.</p>
          </div>

          {overrideMessage && (
            <div className={`p-3.5 rounded-lg text-xs font-semibold ${
              overrideMessage.type === 'success' ? 'bg-emerald-50 border border-emerald-200 text-emerald-700' : 'bg-rose-50 border border-rose-200 text-rose-700'
            }`}>
              {overrideMessage.text}
            </div>
          )}

          <form onSubmit={handleOverrideSubmit} className="space-y-4">
            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1">Target Guest ID *</label>
              <input
                type="text"
                required
                value={overrideForm.guestId}
                onChange={(e) => setOverrideForm({ ...overrideForm, guestId: e.target.value })}
                placeholder="Enter Guest UUID"
                className="w-full bg-slate-50 border border-slate-300 rounded-lg p-2.5 text-sm text-slate-900 font-mono focus:outline-none focus:border-primary"
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1">Invoice ID (Optional)</label>
              <input
                type="text"
                value={overrideForm.invoiceId}
                onChange={(e) => setOverrideForm({ ...overrideForm, invoiceId: e.target.value })}
                placeholder="Enter Invoice UUID if applicable"
                className="w-full bg-slate-50 border border-slate-300 rounded-lg p-2.5 text-sm text-slate-900 font-mono focus:outline-none focus:border-primary"
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Override Action Type *</label>
                <select
                  value={overrideForm.overrideType}
                  onChange={(e) => setOverrideForm({ ...overrideForm, overrideType: e.target.value })}
                  className="w-full bg-slate-50 border border-slate-300 rounded-lg p-2.5 text-sm text-slate-900 focus:outline-none focus:border-primary"
                >
                  <option value="WAIVE_RENT">WAIVE_RENT (Mark invoice paid & ₹0 balance)</option>
                  <option value="ADJUST_DEPOSIT">ADJUST_DEPOSIT (Modify advance deposit)</option>
                  <option value="CUSTOM_DISCOUNT">CUSTOM_DISCOUNT (Apply manual credit)</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Amount (₹) *</label>
                <input
                  type="number"
                  step="0.01"
                  required
                  value={overrideForm.amount}
                  onChange={(e) => setOverrideForm({ ...overrideForm, amount: e.target.value })}
                  placeholder="0.00"
                  className="w-full bg-slate-50 border border-slate-300 rounded-lg p-2.5 text-sm text-slate-900 focus:outline-none focus:border-primary"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1">Audit Reason / Justification *</label>
              <textarea
                required
                rows={3}
                value={overrideForm.reason}
                onChange={(e) => setOverrideForm({ ...overrideForm, reason: e.target.value })}
                placeholder="Provide explicit justification for this override"
                className="w-full bg-slate-50 border border-slate-300 rounded-lg p-2.5 text-sm text-slate-900 focus:outline-none focus:border-primary"
              />
            </div>

            <button
              type="submit"
              disabled={overrideMutation.isPending}
              className="w-full py-2.5 bg-slate-900 hover:bg-slate-800 text-white font-bold text-sm rounded-lg shadow-sm transition"
            >
              {overrideMutation.isPending ? 'Processing Override...' : 'Execute Financial Override'}
            </button>
          </form>
        </div>
      )}

      {/* TAB 4: AUDIT TRAIL FORENSICS */}
      {activeTab === 'audit' && (
        <div className="card p-5 space-y-4">
          <div className="flex flex-col sm:flex-row items-center justify-between gap-4">
            <h3 className="font-heading text-base font-semibold text-slate-900 flex items-center gap-2">
              <FileText className="w-5 h-5 text-slate-400" strokeWidth={1.5} />
              <span>Database Audit Trail Forensics</span>
            </h3>
            <div className="relative w-full sm:w-72">
              <Search className="w-4 h-4 absolute left-3 top-2.5 text-slate-400" />
              <input
                type="text"
                value={auditSearch}
                onChange={(e) => setAuditSearch(e.target.value)}
                placeholder="Search description, role, entity..."
                className="w-full bg-slate-50 border border-slate-300 rounded-lg pl-9 pr-3 py-1.5 text-xs text-slate-900 focus:outline-none focus:border-primary"
              />
            </div>
          </div>

          {isAuditLoading ? (
            <div className="p-8 text-center text-slate-400">Loading audit log entries...</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-slate-700">
                <thead className="bg-slate-50 text-xs uppercase text-slate-500 font-bold border-b border-slate-200">
                  <tr>
                    <th className="p-3">Timestamp</th>
                    <th className="p-3">Action</th>
                    <th className="p-3">Entity</th>
                    <th className="p-3">Description</th>
                    <th className="p-3">Role</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {(auditData?.content || []).map(log => (
                    <tr key={log.id} className="hover:bg-slate-50/50">
                      <td className="p-3 text-xs text-slate-500 font-mono whitespace-nowrap">
                        {log.timestamp ? new Date(log.timestamp).toLocaleString('en-IN') : 'N/A'}
                      </td>
                      <td className="p-3">
                        <span className="px-2 py-0.5 text-xs font-semibold rounded bg-slate-100 text-slate-800 border border-slate-200">
                          {log.action}
                        </span>
                      </td>
                      <td className="p-3 text-xs text-slate-600 font-mono">{log.entityType} ({log.entityId})</td>
                      <td className="p-3 text-xs text-slate-900 font-medium">{log.description}</td>
                      <td className="p-3 text-xs text-slate-500">{log.actorRole || 'SYSTEM'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* TAB 5: DATA COMPLIANCE & PII ANONYMIZATION */}
      {activeTab === 'compliance' && (
        <div className="max-w-2xl card p-6 space-y-4">
          <div>
            <h3 className="font-heading text-base font-semibold text-slate-900 flex items-center gap-2">
              <Lock className="w-5 h-5 text-slate-400" strokeWidth={1.5} />
              <span>Guest PII Anonymization Execution</span>
            </h3>
            <p className="text-xs text-slate-500 mt-0.5">Scrub names, emails, and phone numbers for checked-out guests. Requires PG Owner permission grant.</p>
          </div>

          {anonymizeMsg && (
            <div className={`p-3.5 rounded-lg text-xs font-semibold ${
              anonymizeMsg.type === 'success' ? 'bg-emerald-50 border border-emerald-200 text-emerald-700' : 'bg-rose-50 border border-rose-200 text-rose-700'
            }`}>
              {anonymizeMsg.text}
            </div>
          )}

          <div className="space-y-4">
            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1">Guest UUID to Anonymize *</label>
              <input
                type="text"
                value={anonymizeGuestId}
                onChange={(e) => setAnonymizeGuestId(e.target.value)}
                placeholder="Enter checked-out Guest UUID"
                className="w-full bg-slate-50 border border-slate-300 rounded-lg p-2.5 text-sm text-slate-900 font-mono focus:outline-none focus:border-primary"
              />
            </div>

            <button
              onClick={() => anonymizeMutation.mutate(anonymizeGuestId)}
              disabled={!anonymizeGuestId || anonymizeMutation.isPending}
              className="w-full py-2.5 bg-amber-600 hover:bg-amber-700 text-white font-bold text-sm rounded-lg shadow-sm transition"
            >
              {anonymizeMutation.isPending ? 'Anonymizing...' : 'Scrub Guest PII & Anonymize'}
            </button>
          </div>
        </div>
      )}

      {/* 1-Click Excel Bulk Import Modal */}
      <BulkImportModal
        isOpen={isBulkModalOpen}
        onClose={() => setIsBulkModalOpen(false)}
        onRefresh={() => {
          refetchSummary();
          refetchUsers();
        }}
      />
    </AppLayout>
  );
}

import React, { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import AppLayout from '../../components/AppLayout';
import { guestApi } from '../../api';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts';
import { 
  User, 
  Edit2, 
  FileText, 
  Bell, 
  ChefHat, 
  Coffee, 
  CheckCircle2, 
  TrendingUp,
  ShieldAlert
} from 'lucide-react';

function StatCard({ label, value, icon: Icon, iconColor = 'text-slate-400', children }) {
  return (
    <div className="stat-card flex flex-col gap-1.5">
      <div className="flex items-center justify-between w-full">
        <span className="stat-label">{label}</span>
        {Icon && <Icon className={`w-4 h-4 ${iconColor}`} />}
      </div>
      {value !== undefined ? <div className="stat-value">{value}</div> : children}
    </div>
  );
}

export default function GuestDashboard() {
  const [data, setData] = useState(null);
  const [invoices, setInvoices] = useState([]);

  const [showProfileModal, setShowProfileModal] = useState(false);
  const [profileForm, setProfileForm] = useState({ fullName: '', phone: '', whatsappNumber: '', vehicleRegistration: '' });
  const [savingProfile, setSavingProfile] = useState(false);

  useEffect(() => {
    guestApi.getDashboard().then(r => setData(r.data)).catch(() => {});
    guestApi.getInvoices().then(r => setInvoices(r.data)).catch(() => {});
  }, []);

  const openProfile = async () => {
    try {
      const res = await guestApi.getProfile();
      setProfileForm({
        fullName: res.data.fullName || '',
        phone: res.data.phone || '',
        whatsappNumber: res.data.whatsappNumber || '',
        vehicleRegistration: res.data.vehicleRegistration || ''
      });
      setShowProfileModal(true);
    } catch (err) { alert('Failed to load profile'); }
  };

  const saveProfile = async (e) => {
    e.preventDefault();
    setSavingProfile(true);
    try {
      await guestApi.updateProfile(profileForm);
      setShowProfileModal(false);
      guestApi.getDashboard().then(r => setData(r.data)); // refresh name
    } catch (err) { alert('Failed to save profile'); }
    finally { setSavingProfile(false); }
  };

  const chartData = invoices.slice(0, 6).reverse().map(inv => ({
    name: `${inv.month}/${inv.year}`,
    rent: inv.lineItems?.find(l => l.type === 'RENT')?.amount || 0,
    food: inv.lineItems?.find(l => l.type === 'FOOD')?.amount || 0,
    eb: inv.lineItems?.find(l => l.type === 'EB')?.amount || 0,
    wm: inv.lineItems?.find(l => l.type === 'LAUNDRY')?.amount || 0,
  }));

  return (
    <AppLayout showRightPanel={true}>
      <div className="page-header">
        <div>
          <h1 className="page-title flex items-center gap-2">
            <User className="w-6 h-6 text-primary" />
            <span>Welcome back, {data?.guestName || '...'}</span>
          </h1>
          <p className="page-subtitle">Bed: {data?.bedLabel} • Checked in: {data?.checkInDate}</p>
        </div>
        <button className="btn btn-ghost flex items-center gap-1.5 text-xs" onClick={openProfile}>
          <Edit2 className="w-3.5 h-3.5" />
          <span>Edit Profile</span>
        </button>
      </div>

      {showProfileModal && createPortal(
        <div className="modal-overlay">
          <div className="modal-content card fade-in-up" style={{ maxWidth: 500 }}>
            <h3 className="text-base font-bold text-slate-900 mb-4 flex items-center gap-2">
              <ShieldAlert className="w-5 h-5 text-primary" />
              <span>Edit Profile</span>
            </h3>
            <form onSubmit={saveProfile}>
              <div className="form-group">
                <label className="form-label">Full Name</label>
                <input className="form-input" value={profileForm.fullName} onChange={e => setProfileForm(f => ({...f, fullName: e.target.value}))} required />
              </div>
              <div className="grid-2">
                <div className="form-group">
                  <label className="form-label">Phone</label>
                  <input className="form-input" value={profileForm.phone} onChange={e => setProfileForm(f => ({...f, phone: e.target.value}))} required />
                </div>
                <div className="form-group">
                  <label className="form-label">WhatsApp Number</label>
                  <input className="form-input" value={profileForm.whatsappNumber} onChange={e => setProfileForm(f => ({...f, whatsappNumber: e.target.value}))} />
                </div>
              </div>
              <div className="form-group">
                <label className="form-label">Vehicle Registration (2-Wheeler)</label>
                <input className="form-input" value={profileForm.vehicleRegistration} onChange={e => setProfileForm(f => ({...f, vehicleRegistration: e.target.value}))} placeholder="e.g. TN-01-AB-1234" />
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem', marginTop: '1.5rem' }}>
                <button type="button" className="btn btn-ghost" onClick={() => setShowProfileModal(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={savingProfile}>{savingProfile ? 'Saving...' : 'Save Changes'}</button>
              </div>
            </form>
          </div>
        </div>,
        document.body
      )}

      <div className="grid-3" style={{ marginBottom: '1.5rem' }}>
        <StatCard label="Total Invoices" value={data?.totalInvoices ?? '—'} icon={FileText} iconColor="text-blue-500" />
        <StatCard label="Unread Notifications" value={data?.unreadNotifications ?? '—'} icon={Bell} iconColor="text-rose-500" />
        <StatCard label="Food Plan" icon={ChefHat} iconColor="text-emerald-500">
          <div className="flex flex-col gap-1.5 mt-1">
            <span className={`badge w-max ${data?.foodIncludedInRent ? 'badge-success' : 'badge-info'}`}>
              {data?.foodIncludedInRent ? 'Food Included' : 'Food À La Carte'}
            </span>
            {data?.allowMealCancellations && (
              <span className="badge badge-accent w-max flex items-center gap-1">
                <CheckCircle2 className="w-3 h-3" />
                <span>Cancellations Allowed</span>
              </span>
            )}
          </div>
        </StatCard>
      </div>

      {chartData.length > 0 && (
        <div className="card">
          <h3 className="text-slate-800 font-bold mb-4 flex items-center gap-2">
            <TrendingUp className="w-5 h-5 text-slate-400" />
            <span>Monthly Spending Trend</span>
          </h3>
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(0,0,0,0.05)" />
              <XAxis dataKey="name" tick={{ fill: '#64748b', fontSize: 12 }} />
              <YAxis tick={{ fill: '#64748b', fontSize: 12 }} />
              <Tooltip contentStyle={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '10px', color: '#0f172a' }} />
              <Bar dataKey="rent" fill="#2563eb" radius={[4,4,0,0]} name="Rent" />
              <Bar dataKey="food" fill="#10b981" radius={[4,4,0,0]} name="Food" />
              <Bar dataKey="eb" fill="#f59e0b" radius={[4,4,0,0]} name="EB" />
              <Bar dataKey="wm" fill="#3b82f6" radius={[4,4,0,0]} name="Laundry" />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </AppLayout>
  );
}

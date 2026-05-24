import React, { useEffect, useState } from 'react';
import Sidebar from '../../components/Sidebar';
import { guestApi } from '../../api';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts';

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
    <div className="layout">
      <Sidebar />
      <div className="main-content fade-in">
        <div className="page-header">
          <div>
            <h1 className="page-title">Welcome back, {data?.guestName || '...'} 👋</h1>
            <p className="page-subtitle">Bed: {data?.bedLabel} • Checked in: {data?.checkInDate}</p>
          </div>
          <button className="btn btn-secondary" onClick={openProfile}>✏️ Edit Profile</button>
        </div>

        {showProfileModal && (
          <div className="modal-overlay">
            <div className="modal-content card fade-in" style={{ maxWidth: 500 }}>
              <h3 style={{ marginBottom: '1rem', fontWeight: 700 }}>Edit Profile</h3>
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
          </div>
        )}

        <div className="grid-3" style={{ marginBottom: '1.5rem' }}>
          <div className="stat-card">
            <div className="stat-value">{data?.totalInvoices ?? '—'}</div>
            <div className="stat-label">📄 Total Invoices</div>
          </div>
          <div className="stat-card">
            <div className="stat-value">{data?.unreadNotifications ?? '—'}</div>
            <div className="stat-label">🔔 Unread Notifications</div>
          </div>
          <div className="stat-card">
            <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', flexWrap: 'wrap' }}>
              <span className={`badge ${data?.foodIncludedInRent ? 'badge-success' : 'badge-info'}`}>
                {data?.foodIncludedInRent ? '🍛 Food Included' : '🍽️ Food À La Carte'}
              </span>
              {data?.allowMealCancellations && <span className="badge badge-accent">✅ Cancellations Allowed</span>}
            </div>
            <div className="stat-label">Food Plan</div>
          </div>
        </div>

        {chartData.length > 0 && (
          <div className="card">
            <h2 style={{ marginBottom: '1.5rem', fontSize: '1.1rem', fontWeight: 700 }}>📈 Monthly Spending Trend</h2>
            <ResponsiveContainer width="100%" height={280}>
              <BarChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(0,0,0,0.05)" />
                <XAxis dataKey="name" tick={{ fill: '#64748b', fontSize: 12 }} />
                <YAxis tick={{ fill: '#64748b', fontSize: 12 }} />
                <Tooltip contentStyle={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '10px', color: '#0f172a' }} />
                <Bar dataKey="rent" fill="#6366f1" radius={[4,4,0,0]} name="Rent" />
                <Bar dataKey="food" fill="#10b981" radius={[4,4,0,0]} name="Food" />
                <Bar dataKey="eb" fill="#f59e0b" radius={[4,4,0,0]} name="EB" />
                <Bar dataKey="wm" fill="#3b82f6" radius={[4,4,0,0]} name="Laundry" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </div>
    </div>
  );
}

import React, { useState } from 'react';
import AppLayout from '../components/AppLayout';
import { useAuth } from '../context/AuthContext';
import api from '../api';
import { ShieldCheck, Lock, CheckCircle2, AlertCircle, Loader2 } from 'lucide-react';

export default function Settings() {
  const { user } = useAuth();
  const [form, setForm] = useState({ current: '', newPass: '', confirm: '' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const handlePasswordChange = async (e) => {
    e.preventDefault();
    if (form.newPass !== form.confirm) {
      setError('New passwords do not match.');
      setSuccess('');
      return;
    }
    if (form.newPass.length < 8) {
      setError('Password must be at least 8 characters.');
      setSuccess('');
      return;
    }

    setLoading(true);
    setError('');
    setSuccess('');

    try {
      await api.post('/auth/change-password', {
        currentPassword: form.current,
        newPassword: form.newPass,
      });
      setSuccess('Password changed successfully.');
      setForm({ current: '', newPass: '', confirm: '' });
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to change password. Please check your current password.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <AppLayout>
      <div className="page-header">
        <div>
          <h1 className="page-title flex items-center gap-2">
            <ShieldCheck className="w-6 h-6 text-primary" />
            <span>Account Settings</span>
          </h1>
          <p className="page-subtitle">Manage your credentials and security preferences</p>
        </div>
      </div>

      <div className="max-w-xl">
        <div className="card shadow-md border border-slate-200/80 p-6 bg-white rounded-2xl">
          <h3 className="text-base font-bold text-slate-800 mb-4 flex items-center gap-2 border-b border-slate-100 pb-3">
            <Lock className="w-4 h-4 text-indigo-500" />
            <span>Change Password</span>
          </h3>

          <form onSubmit={handlePasswordChange} className="space-y-4">
            <div className="form-group">
              <label className="form-label text-slate-500 font-bold text-xs uppercase tracking-wide">Current Password</label>
              <input
                type="password"
                className="form-input"
                placeholder="Enter current password"
                value={form.current}
                onChange={e => setForm(f => ({ ...f, current: e.target.value }))}
                required
              />
            </div>

            <div className="form-group">
              <label className="form-label text-slate-500 font-bold text-xs uppercase tracking-wide">New Password</label>
              <input
                type="password"
                className="form-input"
                placeholder="At least 8 characters"
                value={form.newPass}
                onChange={e => setForm(f => ({ ...f, newPass: e.target.value }))}
                required
              />
            </div>

            <div className="form-group">
              <label className="form-label text-slate-500 font-bold text-xs uppercase tracking-wide">Confirm New Password</label>
              <input
                type="password"
                className="form-input"
                placeholder="Re-enter new password"
                value={form.confirm}
                onChange={e => setForm(f => ({ ...f, confirm: e.target.value }))}
                required
              />
            </div>

            {error && (
              <div className="flex items-center gap-2 text-rose-700 text-xs font-semibold bg-rose-50 border border-rose-100 px-3 py-2.5 rounded-lg">
                <AlertCircle className="w-4 h-4 text-rose-500 flex-shrink-0" />
                <span>{error}</span>
              </div>
            )}

            {success && (
              <div className="flex items-center gap-2 text-emerald-700 text-xs font-semibold bg-emerald-50 border border-emerald-100 px-3 py-2.5 rounded-lg">
                <CheckCircle2 className="w-4 h-4 text-emerald-500 flex-shrink-0" />
                <span>{success}</span>
              </div>
            )}

            <div className="flex justify-end pt-2">
              <button
                type="submit"
                className="btn btn-primary px-4 py-2 rounded-lg text-sm font-bold shadow-md shadow-blue-200 transition-all duration-200 hover:-translate-y-0.5"
                disabled={loading}
              >
                {loading ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    <span>Updating...</span>
                  </>
                ) : (
                  <span>Update Password</span>
                )}
              </button>
            </div>
          </form>
        </div>
      </div>
    </AppLayout>
  );
}

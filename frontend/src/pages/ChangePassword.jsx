import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../api';

export default function ChangePassword() {
  const { user, logout, updateUser } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ current: '', newPass: '', confirm: '' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  React.useEffect(() => {
    if (!user) {
      navigate('/login');
    }
  }, [user, navigate]);

  const handle = async (e) => {
    e.preventDefault();
    if (form.newPass !== form.confirm) { setError('New passwords do not match.'); return; }
    if (form.newPass.length < 8) { setError('Password must be at least 8 characters.'); return; }
    setLoading(true); setError('');
    try {
      await api.post('/auth/change-password', {
        currentPassword: form.current,
        newPassword: form.newPass
      });
      // Update mustChangePassword in context
      updateUser({ mustChangePassword: false });
      // Navigate to role dashboard
      const map = { PLATFORM_ADMIN: '/platform/dashboard', PG_OWNER: '/owner/dashboard',
        PG_MANAGER: '/manager/dashboard', GUEST: '/guest/dashboard' };
      navigate(map[user?.role] || '/');
    } catch (err) {
      setError(err.response?.data?.error || 'Password change failed.');
    } finally { setLoading(false); }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50 p-4">
      <div className="bg-white border border-slate-200 shadow-xl rounded-2xl p-8 max-w-md w-full fade-in-up">
        <div className="text-center mb-6">
          <div className="text-4xl mb-2">🔐</div>
          <h2 className="font-heading text-2xl font-bold tracking-tight text-slate-900">
            Set Your New Password
          </h2>
          <p className="text-slate-500 text-sm mt-2">
            For security, you must change your temporary password before continuing.
          </p>
        </div>

        <div className="bg-amber-50 border border-amber-200 text-amber-800 rounded-lg p-3 mb-6 text-xs">
          ⚠️ This is a one-time step. Your password must be at least 8 characters.
        </div>

        <form onSubmit={handle}>
          <div className="form-group">
            <label className="form-label">Current (Temporary) Password</label>
            <input id="cp-current" type="password" className="form-input"
              value={form.current} onChange={e => setForm(f => ({ ...f, current: e.target.value }))}
              placeholder="Enter your temporary password" required />
          </div>
          <div className="form-group">
            <label className="form-label">New Password</label>
            <input id="cp-new" type="password" className="form-input"
              value={form.newPass} onChange={e => setForm(f => ({ ...f, newPass: e.target.value }))}
              placeholder="At least 8 characters" required />
          </div>
          <div className="form-group">
            <label className="form-label">Confirm New Password</label>
            <input id="cp-confirm" type="password" className="form-input"
              value={form.confirm} onChange={e => setForm(f => ({ ...f, confirm: e.target.value }))}
              placeholder="Re-enter new password" required />
          </div>

          {error && (
            <div className="bg-red-50 border border-red-200 text-red-800 rounded-lg p-3 mb-4 text-xs font-medium">{error}</div>
          )}

          <button id="btn-change-password" type="submit" className="btn btn-primary w-full py-3 justify-center" disabled={loading}>
            {loading ? '⏳ Changing...' : '🔐 Set New Password & Continue'}
          </button>
        </form>

        <button type="button" onClick={() => {
            localStorage.removeItem('accessToken');
            localStorage.removeItem('refreshToken');
            localStorage.removeItem('user');
            logout();
            navigate('/login', { replace: true });
          }}
          className="text-slate-500 hover:text-slate-800 text-xs w-full text-center mt-4 bg-transparent border-0 cursor-pointer">
          ← Back to Login
        </button>
      </div>
    </div>
  );
}

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
    <div style={{
      minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: 'var(--bg-main)', padding: '1rem'
    }}>
      <div className="login-card fade-in-up" style={{ maxWidth: 480, width: '100%' }}>
        <div style={{ textAlign: 'center', marginBottom: '1.5rem' }}>
          <div style={{ fontSize: '2.5rem', marginBottom: '0.5rem' }}>🔐</div>
          <h2 style={{ color: 'var(--text-primary)', margin: 0, fontSize: '1.5rem', fontWeight: 800 }}>
            Set Your New Password
          </h2>
          <p style={{ color: 'var(--text-muted)', margin: '0.5rem 0 0', fontSize: '0.9rem' }}>
            For security, you must change your temporary password before continuing.
          </p>
        </div>

        <div style={{
          background: 'rgba(245,158,11,0.1)', border: '1px solid rgba(245,158,11,0.3)',
          borderRadius: '8px', padding: '0.75rem 1rem', marginBottom: '1.5rem',
          fontSize: '0.85rem', color: '#f59e0b'
        }}>
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
            <div style={{
              color: 'var(--danger)', background: 'rgba(239,68,68,0.1)',
              borderRadius: '8px', padding: '0.75rem', marginBottom: '1rem', fontSize: '0.85rem'
            }}>{error}</div>
          )}

          <button id="btn-change-password" type="submit" className="btn btn-primary"
            style={{ width: '100%', justifyContent: 'center', padding: '0.85rem' }} disabled={loading}>
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
          style={{ background: 'none', border: 'none', color: 'var(--text-muted)',
            width: '100%', marginTop: '1rem', cursor: 'pointer', fontSize: '0.8rem' }}>
          ← Back to Login
        </button>
      </div>
    </div>
  );
}

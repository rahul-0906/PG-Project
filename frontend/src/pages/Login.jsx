import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ email: '', password: '' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handle = async (e) => {
    e.preventDefault();
    setLoading(true); setError('');
    try {
      const role = await login(form.email, form.password);
      const map = { PG_OWNER: '/owner/dashboard',
        PG_MANAGER: '/manager/dashboard', GUEST: '/guest/dashboard' };
      navigate(map[role] || '/');
    } catch (err) {
      setError(err.response?.data?.error || 'Login failed. Please check your credentials.');
    } finally { setLoading(false); }
  };

  // One-click login: directly calls API without waiting for form state update
  const quickLogin = async (email, password) => {
    setForm({ email, password });
    setLoading(true); setError('');
    try {
      const role = await login(email, password);
      const map = { PG_OWNER: '/owner/dashboard',
        PG_MANAGER: '/manager/dashboard', GUEST: '/guest/dashboard' };
      navigate(map[role] || '/');
    } catch (err) {
      setError(err.response?.data?.error || 'Login failed.');
    } finally { setLoading(false); }
  };

  const DEMOS = [
    { label: '🏢 PG Owner',       email: 'owner@pgcrm.com',    pass: 'Owner@123' },
    { label: '👔 PG Manager',     email: 'manager@pgcrm.com',  pass: 'Manager@123' },
    { label: '🛏️ Guest',          email: 'guest@pgcrm.com',    pass: 'Guest@123' },
  ];

  return (
    <div className="login-container">
      <div className="login-card fade-in">
        <div className="login-logo">🏠 PG CRM</div>
        <p className="login-tagline">Single-Tenant PG &amp; Hostel Management</p>

        <form onSubmit={handle}>
          <div className="form-group">
            <label className="form-label">Email Address</label>
            <input id="login-email" type="email" className="form-input" placeholder="your@email.com"
              value={form.email} onChange={e => setForm(f => ({ ...f, email: e.target.value }))} required />
          </div>
          <div className="form-group">
            <label className="form-label">Password</label>
            <input id="login-password" type="password" className="form-input" placeholder="••••••••"
              value={form.password} onChange={e => setForm(f => ({ ...f, password: e.target.value }))} required />
          </div>
          {error && (
            <div style={{ color:'var(--danger)', fontSize:'0.85rem', marginBottom:'1rem',
              padding:'0.75rem', background:'rgba(239,68,68,0.1)', borderRadius:'8px' }}>
              {error}
            </div>
          )}
          <button id="btn-signin" type="submit" className="btn btn-primary"
            style={{ width:'100%', justifyContent:'center', padding:'0.85rem' }} disabled={loading}>
            {loading ? '⏳ Signing in...' : '🔑 Sign In'}
          </button>
        </form>

        <div style={{ marginTop:'1.5rem', padding:'1rem', background:'var(--bg-card)', borderRadius:'10px' }}>
          <p style={{ fontSize:'0.75rem', color:'var(--text-muted)', marginBottom:'0.75rem', fontWeight:600 }}>
            ⚡ QUICK LOGIN (DEMO)
          </p>
          {DEMOS.map(d => (
            <button
              id={`demo-${d.email.split('@')[0].replace('.', '-')}`}
              key={d.email}
              type="button"
              onClick={() => quickLogin(d.email, d.pass)}
              disabled={loading}
              style={{ display:'block', width:'100%', textAlign:'left', background:'none',
                border:'1px solid var(--border)', borderRadius:'8px', color:'var(--text-secondary)',
                padding:'0.5rem 0.75rem', marginBottom:'0.4rem', cursor:'pointer', fontSize:'0.8rem',
                transition:'all 0.2s', opacity: loading ? 0.6 : 1 }}
              onMouseEnter={e => e.currentTarget.style.borderColor = 'var(--accent)'}
              onMouseLeave={e => e.currentTarget.style.borderColor = 'var(--border)'}>
              {d.label} — {d.email}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

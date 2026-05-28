import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useSystemConfig } from '../context/SystemConfigContext';

export default function Login() {
  const { user, login } = useAuth();
  const { config } = useSystemConfig();
  const navigate = useNavigate();
  const [form, setForm] = useState({ email: '', password: '' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // Redirect when user state is set (after login or if already authenticated)
  useEffect(() => {
    if (user) {
      const mustChange = user.mustChangePassword === true || user.mustChangePassword === 'true';
      if (mustChange) {
        navigate('/change-password', { replace: true });
      } else {
        const map = {
          PG_OWNER: '/owner/dashboard',
          PG_MANAGER: '/manager/dashboard',
          GUEST: '/guest/dashboard',
        };
        navigate(map[user.role] || '/', { replace: true });
      }
    }
  }, [user, navigate]);

  const handle = async (e) => {
    e.preventDefault();
    setLoading(true); 
    setError('');
    try {
      await login(form.email, form.password);
      // Navigation is handled by the useEffect above once user state commits
    } catch (err) {
      setError(err.response?.data?.error || 'Login failed. Please check your credentials.');
    } finally {
      setLoading(false);
    }
  };

  const quickLogin = async (email, password) => {
    setForm({ email, password });
    setLoading(true); 
    setError('');
    try {
      await login(email, password);
      // Navigation is handled by the useEffect above once user state commits
    } catch (err) {
      setError(err.response?.data?.error || 'Login failed.');
    } finally {
      setLoading(false);
    }
  };

  const DEMOS = [
    { label: '🏢 PG Owner',       email: 'owner@pgcrm.com',    pass: 'Owner@123' },
    { label: '👔 PG Manager',     email: 'manager@pgcrm.com',  pass: 'Manager@123' },
    { label: '🛏️ Guest',          email: 'guest@pgcrm.com',    pass: 'Guest@123' },
  ];

  return (
    <div className="min-h-screen flex items-center justify-center bg-app p-4">
      <div className="w-full max-w-[400px] bg-surface border border-slate-200 shadow-xl rounded-2xl p-6 sm:p-8 fade-in-up">
        <div className="text-2xl font-extrabold text-slate-900 tracking-tight text-center mb-1">
          🏠 {config?.branding?.name || 'PG CRM'}
        </div>
        <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider text-center mb-6">
          Single-Tenant PG &amp; Hostel Management
        </p>

        <form onSubmit={handle} className="space-y-4">
          <div className="form-group">
            <label className="form-label">Email Address</label>
            <input 
              id="login-email" 
              type="email" 
              className="form-input" 
              placeholder="your@email.com"
              value={form.email} 
              onChange={e => setForm(f => ({ ...f, email: e.target.value }))} 
              required 
            />
          </div>
          <div className="form-group">
            <label className="form-label">Password</label>
            <input 
              id="login-password" 
              type="password" 
              className="form-input" 
              placeholder="••••••••"
              value={form.password} 
              onChange={e => setForm(f => ({ ...f, password: e.target.value }))} 
              required 
            />
          </div>

          {error && (
            <div className="text-rose-700 text-xs font-semibold bg-rose-50 border border-rose-100 px-3 py-2.5 rounded-lg text-center leading-relaxed">
              {error}
            </div>
          )}

          <button 
            id="btn-signin" 
            type="submit" 
            className="w-full btn btn-primary py-2.5 flex justify-center text-sm font-semibold shadow-md"
            disabled={loading}
          >
            {loading ? '⏳ Signing in...' : '🔑 Sign In'}
          </button>
        </form>

        <div className="mt-6 bg-slate-50 border border-slate-100 rounded-xl p-4 space-y-2">
          <p className="text-[9px] font-bold text-slate-400 uppercase tracking-wider mb-2">
            ⚡ QUICK LOGIN (DEMO)
          </p>
          {DEMOS.map(d => (
            <button
              id={`demo-${d.email.split('@')[0].replace('.', '-')}`}
              key={d.email}
              type="button"
              onClick={() => quickLogin(d.email, d.pass)}
              disabled={loading}
              className="w-full flex items-center justify-between text-left bg-white border border-slate-200 rounded-lg px-3 py-2 text-xs font-semibold text-slate-600 hover:text-slate-900 hover:border-primary hover:shadow-sm transition-all duration-150 disabled:opacity-50"
            >
              <span>{d.label}</span>
              <span className="text-[10px] text-slate-400 font-normal">{d.email}</span>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

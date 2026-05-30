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
    <div className="min-h-screen flex items-center justify-center bg-slate-50 p-4">
      <div className="w-full max-w-[400px] bg-white border border-slate-200 shadow-xl rounded-2xl p-6 sm:p-8 fade-in-up">
        <div className="flex flex-col items-center justify-center mb-6">
          <div className="flex items-center justify-center w-12 h-12 rounded-xl bg-gradient-to-br from-indigo-500 to-indigo-700 text-white shadow-md shadow-indigo-100 mb-3">
            <svg className="w-6 h-6 fill-current" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M19 2H5C3.89543 2 3 2.89543 3 4V20C3 21.1046 3.89543 22 5 22H19C20.1046 22 21 21.1046 21 20V4C21 2.89543 20.1046 2 19 2ZM11 6H13V8H11V6ZM11 10H13V12H11V10ZM11 14H13V16H11V14ZM7 6H9V8H7V6ZM7 10H9V12H7V10ZM7 14H9V16H7V14ZM17 18H7V17H17V18ZM17 14H15V16H17V14ZM17 10H15V12H17V10ZM17 6H15V8H17V6Z" />
            </svg>
          </div>
          <h2 className="font-heading text-xl font-semibold tracking-tight text-slate-800">
            {config?.branding?.name || 'PG CRM'}
          </h2>
          <p className="text-xs font-medium uppercase tracking-wider text-slate-500 text-center mt-1">
            Single-Tenant PG &amp; Hostel Management
          </p>
        </div>

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
            <div className="text-red-800 text-xs font-semibold bg-red-100 border border-red-200 px-3 py-2.5 rounded-lg text-center leading-relaxed">
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

        <div className="mt-6 bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-2">
          <p className="text-xs font-medium uppercase tracking-wider text-slate-500 mb-2">
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

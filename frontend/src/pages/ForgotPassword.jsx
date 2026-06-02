import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Mail, Loader2, CheckCircle2, AlertCircle } from 'lucide-react';
import api from '../api';

export default function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const navigate = useNavigate();

  const handleReset = async (e) => {
    e.preventDefault();
    if (!email || !email.trim()) {
      setError('Please enter your email address.');
      return;
    }
    setLoading(true);
    setError('');
    try {
      await api.post('/auth/forgot-password', { email: email.trim() });
      setSuccess(true);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to request password reset. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50 p-4">
      <div className="w-full max-w-[400px] bg-white border border-slate-200 shadow-xl rounded-2xl p-6 sm:p-8 fade-in-up">
        
        <div className="flex flex-col items-center justify-center mb-6">
          <div className="flex items-center justify-center w-12 h-12 rounded-xl bg-gradient-to-br from-indigo-500 to-indigo-700 text-white shadow-md shadow-indigo-100 mb-3">
            <svg className="w-6 h-6 fill-current" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z"/>
            </svg>
          </div>
          <h2 className="font-heading text-xl font-semibold tracking-tight text-slate-800">
            Reset Password
          </h2>
          <p className="text-xs font-medium text-slate-500 text-center mt-2 leading-relaxed">
            Enter your email address and we'll send you a temporary password to access your account.
          </p>
        </div>

        {success ? (
          <div className="space-y-4 animate-fade-in">
            <div className="bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs font-medium rounded-xl p-4 flex items-start gap-3">
              <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0 mt-0.5" />
              <div>
                <p className="font-bold text-[13px] mb-1">Check Your Email</p>
                <p className="leading-relaxed">If this email is registered, we have sent a new temporary password. Please check your inbox and spam folder.</p>
              </div>
            </div>
            <Link
              id="back-to-login-btn"
              to="/login"
              className="w-full btn btn-primary py-2.5 flex justify-center text-sm font-semibold shadow-md"
            >
              🔑 Return to Login
            </Link>
          </div>
        ) : (
          <form onSubmit={handleReset} className="space-y-4">
            <div className="form-group">
              <label className="form-label">Email Address</label>
              <div className="relative">
                <input
                  id="reset-email"
                  type="email"
                  className="form-input pl-9"
                  placeholder="your@email.com"
                  value={email}
                  onChange={e => setEmail(e.target.value)}
                  required
                />
                <Mail className="absolute left-3 top-3 w-4 h-4 text-slate-400" />
              </div>
            </div>

            {error && (
              <div className="bg-rose-50 border border-rose-200 text-rose-800 text-xs font-semibold p-3 rounded-lg flex items-center gap-2">
                <AlertCircle className="w-4 h-4 text-rose-600 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <button
              id="btn-reset-password"
              type="submit"
              className="w-full btn btn-primary py-2.5 flex justify-center text-sm font-semibold shadow-md"
              disabled={loading}
            >
              {loading ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin mr-1.5" />
                  <span>Sending temporary password...</span>
                </>
              ) : '✉️ Send Temporary Password'}
            </button>

            <div className="text-center pt-2">
              <Link 
                id="link-back-login"
                to="/login" 
                className="text-xs font-semibold text-slate-500 hover:text-primary transition-colors inline-flex items-center gap-1"
              >
                ← Back to Login
              </Link>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}

import React, { useState } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate } from 'react-router-dom';
import AppLayout from '../../components/AppLayout';
import { guestApi } from '../../api';
import { useAuth } from '../../context/AuthContext';
import { useSystemConfig } from '../../context/SystemConfigContext';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts';
import { 
  User, 
  Edit2, 
  FileText, 
  Bell, 
  ChefHat, 
  CheckCircle2, 
  TrendingUp,
  ShieldAlert,
  ArrowRight,
  Utensils,
  CreditCard,
  Egg,
  Shirt,
  Wallet
} from 'lucide-react';

function StatCard({ label, value, icon: Icon, iconColor = 'text-slate-500', children }) {
  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5 flex flex-col justify-between transition-all duration-200 hover:border-primary/40 hover:shadow-md min-h-[140px]">
      <div>
        <div className="flex items-center justify-between w-full mb-1">
          <span className="text-xs font-medium uppercase tracking-wider text-slate-500">{label}</span>
          {Icon && (
            <div className={iconColor}>
              <Icon className="w-5 h-5" strokeWidth={1.5} />
            </div>
          )}
        </div>
        {value !== undefined && <div className="text-2xl font-bold text-slate-900 tracking-tight">{value}</div>}
      </div>
      {children}
    </div>
  );
}

export default function GuestDashboard() {
  const navigate = useNavigate();
  const { updateUser } = useAuth();
  const queryClient = useQueryClient();

  const [showProfileModal, setShowProfileModal] = useState(false);
  const [profileForm, setProfileForm] = useState({ fullName: '', email: '', phone: '', whatsappNumber: '', vehicleRegistration: '' });
  const [profileData, setProfileData] = useState(null);
  const [originalEmail, setOriginalEmail] = useState('');

  const [showOtpView, setShowOtpView] = useState(false);
  const [otpCode, setOtpCode] = useState('');
  const [otpError, setOtpError] = useState('');
  const [isRequestingOtp, setIsRequestingOtp] = useState(false);
  const [isVerifyingOtp, setIsVerifyingOtp] = useState(false);

  const { config } = useSystemConfig();
  const [selectedMonth, setSelectedMonth] = useState(new Date().toISOString().slice(0, 7));

  const { data } = useQuery({
    queryKey: ['guestDashboard'],
    queryFn: () => guestApi.getDashboard().then(r => r.data)
  });

  const { data: notifications = [] } = useQuery({
    queryKey: ['guestNotifications'],
    queryFn: () => guestApi.getNotifications().then(r => r.data),
    refetchInterval: 10000
  });

  const unreadNotificationsCount = notifications.filter(n => !n.read).length;

  const { data: invoices = [] } = useQuery({
    queryKey: ['guestInvoices'],
    queryFn: () => guestApi.getInvoices().then(r => r.data)
  });

  const { data: addons = [] } = useQuery({
    queryKey: ['guestAddons'],
    queryFn: () => guestApi.getAddons().then(r => r.data)
  });

  const saveProfileMutation = useMutation({
    mutationFn: (form) => guestApi.updateProfile(form),
    onSuccess: (_, form) => {
      updateUser({ fullName: form.fullName });
      setShowProfileModal(false);
      queryClient.invalidateQueries({ queryKey: ['guestDashboard'] });
    },
    onError: () => {
      alert('Failed to save profile');
    }
  });

  const savingProfile = saveProfileMutation.isPending;

  const openProfile = async () => {
    try {
      const res = await guestApi.getProfile();
      setProfileData(res.data);
      setProfileForm({
        fullName: res.data.fullName || '',
        email: res.data.email || '',
        phone: res.data.phone || '',
        whatsappNumber: res.data.whatsappNumber || '',
        vehicleRegistration: res.data.vehicleRegistration || ''
      });
      setOriginalEmail(res.data.email || '');
      setShowOtpView(false);
      setOtpCode('');
      setOtpError('');
      setShowProfileModal(true);
    } catch (err) { alert('Failed to load profile'); }
  };

  const saveProfile = async (e) => {
    e.preventDefault();
    if (profileForm.email.trim().toLowerCase() !== originalEmail.trim().toLowerCase()) {
      setIsRequestingOtp(true);
      setOtpError('');
      try {
        await guestApi.requestEmailChange(profileForm.email);
        setShowOtpView(true);
      } catch (err) {
        setOtpError(err.response?.data?.error || 'Failed to request verification code.');
      } finally {
        setIsRequestingOtp(false);
      }
    } else {
      saveProfileMutation.mutate(profileForm);
    }
  };

  const handleVerifyOtp = async (e) => {
    e.preventDefault();
    setIsVerifyingOtp(true);
    setOtpError('');
    try {
      await guestApi.verifyEmailChange(profileForm.email, otpCode);
      // Success. Now update other fields.
      await guestApi.updateProfile(profileForm);
      updateUser({ fullName: profileForm.fullName });
      setShowProfileModal(false);
      setShowOtpView(false);
      setOtpCode('');
      queryClient.invalidateQueries({ queryKey: ['guestDashboard'] });
    } catch (err) {
      setOtpError(err.response?.data?.error || 'Invalid verification code. Please try again.');
    } finally {
      setIsVerifyingOtp(false);
    }
  };

  const chartData = invoices.slice(0, 6).reverse().map(inv => ({
    name: `${inv.month}/${inv.year}`,
    rent: inv.lineItems?.find(l => l.type === 'RENT')?.amount || 0,
    food: inv.lineItems?.find(l => l.type === 'FOOD')?.amount || 0,
    eb: inv.lineItems?.find(l => l.type === 'EB')?.amount || 0,
    wm: inv.lineItems?.find(l => l.type === 'LAUNDRY')?.amount || 0,
  }));

  const availableMonths = Array.from(new Set([
    new Date().toISOString().slice(0, 7),
    ...addons.map(a => a.logDate ? a.logDate.slice(0, 7) : '')
  ])).filter(Boolean).sort((a, b) => b.localeCompare(a));

  const formatMonthName = (monthKey) => {
    if (!monthKey) return '';
    const [year, month] = monthKey.split('-');
    const date = new Date(parseInt(year), parseInt(month) - 1, 1);
    return date.toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
  };

  const filteredAddons = addons.filter(a => a.logDate && a.logDate.startsWith(selectedMonth));

  const oPrice = config?.pricing?.omelette ?? 18;
  const ePrice = config?.pricing?.boiledEgg ?? 18;
  const wPrice = config?.pricing?.washingMachine ?? 50;

  let totalOmelettes = 0;
  let totalEggs = 0;
  let totalWashing = 0;
  let totalAddonCost = 0;

  filteredAddons.forEach(a => {
    const oCount = a.omeletteCount || 0;
    const eCount = a.boiledEggCount || 0;
    const wCount = a.washingMachineCount || 0;
    totalOmelettes += oCount;
    totalEggs += eCount;
    totalWashing += wCount;
    totalAddonCost += (oCount * oPrice) + (eCount * ePrice) + (wCount * wPrice);
  });

  return (
    <AppLayout>
      {/* Premium Welcome Banner */}
      <div className="relative overflow-hidden bg-gradient-to-br from-indigo-600 via-blue-600 to-violet-700 rounded-2xl p-4 sm:p-5 shadow-md shadow-indigo-100/60 mb-6 text-white">
        <div className="absolute right-0 top-0 translate-x-12 -translate-y-12 w-48 h-48 bg-white/5 rounded-full blur-3xl pointer-events-none" />
        <div className="absolute left-1/3 bottom-0 translate-y-1/2 w-36 h-36 bg-indigo-500/10 rounded-full blur-2xl pointer-events-none" />
        
        <div className="relative z-10 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
          <div className="flex items-center gap-3 sm:gap-4">
            <div className="p-2 sm:p-2.5 bg-white/10 backdrop-blur-md rounded-xl border border-white/10 shadow-inner">
              <User className="w-5 h-5 sm:w-6 sm:h-6 text-indigo-100" strokeWidth={1.5}/>
            </div>
            <div>
              <span className="text-indigo-200 text-[10px] sm:text-xs font-semibold tracking-wide uppercase">Guest Portal</span>
              <h1 className="text-lg sm:text-xl font-extrabold tracking-tight mt-0.5">
                Welcome back, {data?.guestName || '...'}
              </h1>
              <div className="flex flex-wrap gap-2 mt-2">
                <span className="bg-white/10 backdrop-blur-md px-2.5 py-0.5 rounded-full text-[9px] font-bold uppercase tracking-wider border border-white/5">
                  BED(S): {(() => {
                    if (!data) return '—';
                    const bedsData = data.beds || data.allocatedBeds;
                    if (Array.isArray(bedsData)) return bedsData.join(', ');
                    if (typeof bedsData === 'string' && bedsData.trim() !== '') return bedsData;
                    if (data.bedLabel) return data.bedLabel;
                    if (data.bed && typeof data.bed === 'object') return data.bed.bedLabel || '—';
                    if (data.bed) return data.bed;
                    return '—';
                  })()}
                </span>
                <span className="bg-white/10 backdrop-blur-md px-2.5 py-0.5 rounded-full text-[9px] font-bold uppercase tracking-wider border border-white/5">
                  Checked In: {data?.checkInDate || '—'}
                </span>
              </div>
            </div>
          </div>
          
          <button 
            className="bg-white/10 hover:bg-white/20 backdrop-blur-md text-white border border-white/10 px-3.5 py-1.5 rounded-xl text-xxs font-bold transition-all duration-200 flex items-center gap-1.5 hover:-translate-y-0.5"
            onClick={openProfile}
          >
            <Edit2 className="w-3 h-3" strokeWidth={1.5}/>
            <span>Edit Profile</span>
          </button>
        </div>
      </div>

      {showProfileModal && createPortal(
        <div className="modal-overlay">
          <div className="modal-content card fade-in-up" style={{ maxWidth: showOtpView ? 480 : 720, width: '100%', maxHeight: '90vh', display: 'flex', flexDirection: 'column' }}>
            <h3 className="font-heading text-base font-semibold text-slate-900 mb-4 flex items-center gap-2">
              <ShieldAlert className="w-5 h-5 text-primary" strokeWidth={1.5}/>
              <span>Edit Profile Details</span>
            </h3>
            {showOtpView ? (
              <form onSubmit={handleVerifyOtp} className="flex flex-col">
                <div className="bg-indigo-50 border border-indigo-100 rounded-xl p-4 mb-4 text-xs leading-relaxed text-indigo-700">
                  We sent a 6-digit verification code to <strong>{profileForm.email}</strong>. Please enter the code below to confirm your new email address.
                </div>
                
                <div className="form-group">
                  <label className="form-label">6-Digit Verification Code</label>
                  <input 
                    type="text" 
                    className="form-input text-center text-lg font-black tracking-widest uppercase" 
                    maxLength={6} 
                    value={otpCode} 
                    onChange={e => setOtpCode(e.target.value.replace(/\D/g, ''))} 
                    required 
                    placeholder="123456" 
                  />
                </div>

                {otpError && (
                  <div className="text-rose-500 text-xs font-semibold mt-1 mb-3">
                    <ShieldAlert strokeWidth={1.5} className="w-4 h-4 text-rose-500 inline-block mr-1 align-text-bottom" /> {otpError}
                  </div>
                )}

                <div className="flex justify-end gap-2 mt-6 pt-3 border-t border-slate-100">
                  <button type="button" className="btn btn-ghost" onClick={() => { setShowOtpView(false); setOtpCode(''); }}>Back</button>
                  <button type="submit" className="btn btn-primary" disabled={isVerifyingOtp}>
                    {isVerifyingOtp ? 'Verifying...' : 'Verify & Save'}
                  </button>
                </div>
              </form>
            ) : (
              <form onSubmit={saveProfile} className="flex flex-col flex-1 overflow-hidden">
                <div className="overflow-y-auto flex-1 pr-1" style={{ maxHeight: 'calc(90vh - 150px)' }}>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-6 pb-2">
                    {/* Left Column: Account Profile Details */}
                    <div className="space-y-4">
                      <h4 className="text-xs font-bold text-slate-500 uppercase tracking-wider mb-2">Account Profile Details</h4>
                      <div className="form-group">
                        <label className="form-label">Full Name</label>
                        <input className="form-input" value={profileForm.fullName} onChange={e => setProfileForm(f => ({...f, fullName: e.target.value}))} required />
                      </div>
                      <div className="form-group">
                        <label className="form-label">Email ID</label>
                        <input type="email" className="form-input" value={profileForm.email} onChange={e => setProfileForm(f => ({...f, email: e.target.value}))} required />
                      </div>
                      <div className="form-group">
                        <label className="form-label">Phone</label>
                        <input className="form-input" value={profileForm.phone} onChange={e => setProfileForm(f => ({...f, phone: e.target.value}))} required />
                      </div>
                      <div className="form-group">
                        <label className="form-label">WhatsApp Number</label>
                        <input className="form-input" value={profileForm.whatsappNumber} onChange={e => setProfileForm(f => ({...f, whatsappNumber: e.target.value}))} />
                      </div>
                      <div className="form-group">
                        <label className="form-label">Vehicle Registration (2-Wheeler)</label>
                        <input className="form-input" value={profileForm.vehicleRegistration} onChange={e => setProfileForm(f => ({...f, vehicleRegistration: e.target.value}))} placeholder="e.g. TN-01-AB-1234" />
                      </div>
                    </div>

                    {/* Right Column: Stay Allocation & Meal Preferences */}
                    <div className="space-y-4">
                      {profileData && (
                        <div>
                          <h4 className="text-xs font-bold text-slate-500 uppercase tracking-wider mb-2">Stay &amp; Allocation Info</h4>
                          <div className="bg-slate-50 rounded-xl p-4 border border-slate-200/60 grid grid-cols-1 gap-3.5 text-xs">
                            <div>
                              <span className="text-slate-400 font-medium block">Room Assignment</span>
                              <span className="font-semibold text-slate-700">
                                Bed(s) {(() => {
                                  if (!profileData) return '—';
                                  const bedsData = profileData.beds || profileData.allocatedBeds;
                                  if (Array.isArray(bedsData)) return bedsData.join(', ');
                                  if (typeof bedsData === 'string' && bedsData.trim() !== '') return bedsData;
                                  if (profileData.bedLabel) return profileData.bedLabel;
                                  if (profileData.bed && typeof profileData.bed === 'object') return profileData.bed.bedLabel || '—';
                                  if (profileData.bed) return profileData.bed;
                                  return '—';
                                })()} (Room {profileData.roomNumber || '—'}, {profileData.floorName || '—'})
                              </span>
                            </div>
                            <div>
                              <span className="text-slate-400 font-medium block">Check-in Date</span>
                              <span className="font-semibold text-slate-700">{profileData.checkInDate || '—'}</span>
                            </div>
                            <div>
                              <span className="text-slate-400 font-medium block">Advance Deposit</span>
                              <span className="font-semibold text-slate-700">₹{profileData.advanceDeposit ?? 0}</span>
                            </div>
                            <div>
                              <span className="text-slate-400 font-medium block">KYC Status</span>
                              <span className="mt-1 block">
                                <span className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider ${
                                  profileData.kycStatus === 'VERIFIED' ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' :
                                  profileData.kycStatus === 'REJECTED' ? 'bg-rose-50 text-rose-700 border border-rose-200' :
                                  'bg-amber-50 text-amber-700 border border-amber-200'
                                }`}>
                                  {profileData.kycStatus || 'PENDING'}
                                </span>
                              </span>
                            </div>
                            <div>
                              <span className="text-slate-400 font-medium block">Meal Plan Preferences</span>
                              <span className="font-semibold text-slate-700 block mt-0.5">
                                {profileData.vegPreference ? (
                                  <span className="inline-flex items-center">
                                    <span className="w-2 h-2 rounded-full bg-emerald-500 mr-1.5" /> Veg
                                  </span>
                                ) : (
                                  <span className="inline-flex items-center">
                                    <span className="w-2 h-2 rounded-full bg-rose-500 mr-1.5" /> Non-Veg
                                  </span>
                                )}
                              </span>
                              <span className="text-slate-500 font-medium mt-1 block">
                                Opted: {[
                                  profileData.breakfastPreference && 'Breakfast',
                                  profileData.lunchPreference && 'Lunch',
                                  profileData.dinnerPreference && 'Dinner'
                                ].filter(Boolean).join(', ') || 'None'}
                              </span>
                            </div>
                          </div>
                        </div>
                      )}
                    </div>
                  </div>
                </div>

                {otpError && (
                  <div className="text-rose-500 text-xs font-semibold mt-3 mb-1 px-1">
                    <ShieldAlert strokeWidth={1.5} className="w-4 h-4 text-rose-500 inline-block mr-1 align-text-bottom" /> {otpError}
                  </div>
                )}

                <div className="flex justify-end gap-2 mt-4 pt-3 border-t border-slate-100 bg-white">
                  <button type="button" className="btn btn-ghost" onClick={() => setShowProfileModal(false)}>Cancel</button>
                  <button type="submit" className="btn btn-primary" disabled={savingProfile || isRequestingOtp}>
                    {isRequestingOtp ? 'Requesting code...' : (savingProfile ? 'Saving...' : 'Save Changes')}
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>,
        document.body
      )}

      {/* Grid: Stat Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
        <StatCard 
          label="Total Invoices" 
          value={data?.totalInvoices ?? '0'} 
          icon={FileText} 
          iconColor="text-blue-500"
        >
          <div className="flex flex-col gap-1.5 mt-1">
            <div className="text-[10px] text-slate-400 font-medium">Auto-generated monthly bills</div>
            <div className="flex items-center justify-between mt-auto pt-1 border-t border-slate-50/50">
              <span className="text-[10px] text-slate-400 font-medium">View bills</span>
              <button 
                className="text-xs font-bold text-blue-600 hover:text-blue-700 flex items-center gap-1 transition-colors"
                onClick={() => navigate('/guest/invoices')}
              >
                <span>My Invoices</span>
                <ArrowRight className="w-3 h-3" strokeWidth={1.5}/>
              </button>
            </div>
          </div>
        </StatCard>

        <StatCard 
          label="Unread Notifications" 
          value={unreadNotificationsCount} 
          icon={Bell} 
          iconColor={unreadNotificationsCount > 0 ? "text-rose-500 animate-bounce" : "text-rose-400"}
        >
          <div className="flex flex-col gap-1.5 mt-1">
            <div className="text-[10px] text-slate-400 font-medium">Important manager updates</div>
            <div className="flex items-center justify-between mt-auto pt-1 border-t border-slate-50/50">
              <span className="text-[10px] text-slate-400 font-medium">Status</span>
              <span className={`text-[10px] font-bold uppercase tracking-wider ${unreadNotificationsCount > 0 ? 'text-rose-500 font-extrabold animate-pulse' : 'text-slate-400'}`}>
                {unreadNotificationsCount > 0 ? 'New Updates' : 'Up to date'}
              </span>
            </div>
          </div>
        </StatCard>

        <StatCard 
          label="Food Plan" 
          icon={ChefHat} 
          iconColor="text-emerald-500"
        >
          <div className="flex flex-col gap-2 mt-1">
            <div className="flex items-center gap-2">
              <span className={`badge ${data?.foodIncludedInRent ? 'badge-success' : 'badge-info'}`}>
                {data?.foodIncludedInRent ? 'Food Included' : 'Food À La Carte'}
              </span>
              {data?.allowMealCancellations && (
                <span className="badge badge-accent flex items-center gap-1">
                  <CheckCircle2 className="w-3 h-3" strokeWidth={1.5}/>
                  <span>Cancellations Allowed</span>
                </span>
              )}
            </div>
            <div className="flex items-center justify-between mt-auto pt-1 border-t border-slate-50/50">
              <span className="text-[10px] text-slate-400 font-medium">Manage preferences</span>
              <button 
                className="text-xs font-bold text-emerald-600 hover:text-emerald-700 flex items-center gap-1 transition-colors"
                onClick={() => navigate('/guest/daily-log')}
              >
                <span>Meal Planner</span>
                <ArrowRight className="w-3 h-3" strokeWidth={1.5}/>
              </button>
            </div>
          </div>
        </StatCard>
      </div>

      {/* Grid: Actions & Chart */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 mb-8">
        {/* Quick Actions Panel */}
        <div className="lg:col-span-1 bg-white rounded-xl border border-slate-200 shadow-sm p-6 flex flex-col gap-4">
          <div>
            <h3 className="font-heading text-base font-semibold text-slate-900">Quick Operations</h3>
            <p className="text-slate-500 text-xs mt-1">Common tasks and pages you can access immediately.</p>
          </div>
          
          <div className="flex flex-col gap-3 mt-2">
            <button 
              className="w-full flex items-center justify-between p-4 rounded-xl border border-slate-100 bg-slate-50/50 hover:bg-slate-50 hover:border-slate-200 transition-all duration-200 group text-left"
              onClick={() => navigate('/guest/daily-log')}
            >
              <div className="flex items-center gap-2">
                <Utensils className="w-5 h-5 text-indigo-600" strokeWidth={1.5}/>
                <div>
                  <div className="font-bold text-slate-800 text-sm">Meal Planner</div>
                  <div className="text-slate-400 text-xxs mt-0.5">Opt in/out of meals</div>
                </div>
              </div>
              <ArrowRight className="w-4 h-4 text-slate-400 group-hover:text-slate-600 group-hover:translate-x-0.5 transition-all" strokeWidth={1.5}/>
            </button>

            <button 
              className="w-full flex items-center justify-between p-4 rounded-xl border border-slate-200 bg-slate-50/50 hover:bg-slate-50 hover:border-slate-300 transition-all duration-200 group text-left"
              onClick={() => navigate('/guest/invoices')}
            >
              <div className="flex items-center gap-2">
                <CreditCard className="w-5 h-5 text-blue-600" strokeWidth={1.5}/>
                <div>
                  <div className="font-bold text-slate-800 text-sm">My Invoices</div>
                  <div className="text-slate-500 text-xxs mt-0.5">View & clear invoices</div>
                </div>
              </div>
              <ArrowRight className="w-4 h-4 text-slate-500 group-hover:text-slate-600 group-hover:translate-x-0.5 transition-all" strokeWidth={1.5}/>
            </button>

            <button 
              className="w-full flex items-center justify-between p-4 rounded-xl border border-slate-100 bg-slate-50/50 hover:bg-slate-50 hover:border-slate-200 transition-all duration-200 group text-left"
              onClick={openProfile}
            >
              <div className="flex items-center gap-2">
                <User className="w-5 h-5 text-violet-600" strokeWidth={1.5}/>
                <div>
                  <div className="font-bold text-slate-800 text-sm">Edit Profile</div>
                  <div className="text-slate-400 text-xxs mt-0.5">Change phone & vehicle details</div>
                </div>
              </div>
              <ArrowRight className="w-4 h-4 text-slate-400 group-hover:text-slate-600 group-hover:translate-x-0.5 transition-all" strokeWidth={1.5}/>
            </button>
          </div>
        </div>

        {/* Chart Panel */}
        <div className="lg:col-span-2 bg-white rounded-xl border border-slate-200 shadow-sm p-6">
          <div className="flex items-center justify-between mb-6">
            <div className="flex items-center gap-2">
              <div className="p-2 bg-indigo-50 text-indigo-600 rounded-lg">
                <TrendingUp className="w-4 h-4" strokeWidth={1.5}/>
              </div>
              <div>
                <h3 className="font-heading text-base font-semibold text-slate-900">Monthly Spending Trend</h3>
                <p className="text-slate-500 text-xs mt-1">Breakdown of rent, food, EB, and washing machine bills.</p>
              </div>
            </div>
          </div>
          {chartData.length > 0 ? (
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={chartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }} barGap={3}>
                <defs>
                  <linearGradient id="rentGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#818cf8" stopOpacity={0.9} />
                    <stop offset="100%" stopColor="#4f46e5" stopOpacity={0.9} />
                  </linearGradient>
                  <linearGradient id="foodGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#34d399" stopOpacity={0.9} />
                    <stop offset="100%" stopColor="#10b981" stopOpacity={0.9} />
                  </linearGradient>
                  <linearGradient id="ebGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#fbbf24" stopOpacity={0.9} />
                    <stop offset="100%" stopColor="#d97706" stopOpacity={0.9} />
                  </linearGradient>
                  <linearGradient id="wmGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#60a5fa" stopOpacity={0.9} />
                    <stop offset="100%" stopColor="#2563eb" stopOpacity={0.9} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(148, 163, 184, 0.08)" vertical={false} />
                <XAxis dataKey="name" tick={{ fill: '#94a3b8', fontSize: 10, fontWeight: 500 }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fill: '#94a3b8', fontSize: 10, fontWeight: 500 }} axisLine={false} tickLine={false} />
                <Tooltip 
                  cursor={{ fill: 'rgba(148,163,184,0.03)' }}
                  contentStyle={{ 
                    background: '#ffffff', 
                    border: '1px solid #e2e8f0', 
                    borderRadius: '16px', 
                    boxShadow: '0 10px 25px -5px rgba(0,0,0,0.05), 0 8px 10px -6px rgba(0,0,0,0.05)', 
                    color: '#0f172a', 
                    fontSize: '11px',
                    padding: '10px 12px'
                  }}
                />
                <Bar dataKey="rent" fill="url(#rentGradient)" radius={[3,3,0,0]} name="Rent" maxBarSize={12} />
                <Bar dataKey="food" fill="url(#foodGradient)" radius={[3,3,0,0]} name="Food" maxBarSize={12} />
                <Bar dataKey="eb" fill="url(#ebGradient)" radius={[3,3,0,0]} name="EB" maxBarSize={12} />
                <Bar dataKey="wm" fill="url(#wmGradient)" radius={[3,3,0,0]} name="Laundry" maxBarSize={12} />
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <div className="flex flex-col items-center justify-center h-[260px] text-slate-400 text-sm">
              <TrendingUp className="w-8 h-8 text-slate-300 mb-2" strokeWidth={1.5}/>
              <span>No invoice billing history available yet.</span>
            </div>
          )}
        </div>
      </div>

      {/* Add-on & Service Log Card */}
      <div className="card mb-8">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-5">
          <div className="flex items-center gap-2">
            <div className="p-2 bg-indigo-50 text-indigo-600 rounded-lg">
              <ChefHat className="w-4 h-4" strokeWidth={1.5}/>
            </div>
            <div>
              <h3 className="font-heading text-base font-semibold text-slate-900">Add-on &amp; Service Log</h3>
              <p className="text-slate-500 text-xs mt-1">Summary and daily log of opted services.</p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <span className="text-xs font-medium uppercase tracking-wider text-slate-500">Month:</span>
            <select 
              value={selectedMonth} 
              onChange={e => setSelectedMonth(e.target.value)}
              className="form-input"
              style={{ width: 'auto', padding: '0.35rem 1.5rem 0.35rem 0.65rem', fontSize: '0.8rem' }}
            >
              {availableMonths.map(m => (
                <option key={m} value={m}>{formatMonthName(m)}</option>
              ))}
            </select>
          </div>
        </div>

        {/* Summary Metric Cards */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
          <div className="bg-slate-50 border border-slate-200 p-3.5 rounded-xl flex items-center gap-3">
            <div className="w-8 h-8 rounded-lg bg-amber-50 flex items-center justify-center flex-shrink-0">
              <Utensils strokeWidth={1.5} className="text-amber-600 w-5 h-5" />
            </div>
            <div>
              <div className="text-[9px] font-semibold text-slate-500 uppercase tracking-wider">{config?.rules?.omeletteLabel || 'Omelette'}</div>
              <div className="text-sm font-semibold text-slate-800 mt-0.5">{totalOmelettes} pcs</div>
              <div className="text-[9px] text-slate-500 font-medium">₹{totalOmelettes * oPrice} total</div>
            </div>
          </div>
          <div className="bg-slate-50 border border-slate-200 p-3.5 rounded-xl flex items-center gap-3">
            <div className="w-8 h-8 rounded-lg bg-amber-50 flex items-center justify-center flex-shrink-0">
              <Egg strokeWidth={1.5} className="text-amber-500 w-5 h-5" />
            </div>
            <div>
              <div className="text-[9px] font-semibold text-slate-500 uppercase tracking-wider">{config?.rules?.boiledEggLabel || 'Boiled Egg'}</div>
              <div className="text-sm font-semibold text-slate-800 mt-0.5">{totalEggs} pcs</div>
              <div className="text-[9px] text-slate-500 font-medium">₹{totalEggs * ePrice} total</div>
            </div>
          </div>
          <div className="bg-slate-50 border border-slate-200 p-3.5 rounded-xl flex items-center gap-3">
            <div className="w-8 h-8 rounded-lg bg-blue-50 flex items-center justify-center flex-shrink-0">
              <Shirt strokeWidth={1.5} className="text-blue-500 w-5 h-5" />
            </div>
            <div>
              <div className="text-[9px] font-semibold text-slate-500 uppercase tracking-wider">{config?.rules?.washingMachineLabel || 'Washing Machine'}</div>
              <div className="text-sm font-semibold text-slate-800 mt-0.5">{totalWashing} uses</div>
              <div className="text-[9px] text-slate-500 font-medium">₹{totalWashing * wPrice} total</div>
            </div>
          </div>
          <div className="bg-emerald-50/50 border border-emerald-200 p-3.5 rounded-xl flex items-center gap-3">
            <div className="w-8 h-8 rounded-lg bg-emerald-100/80 flex items-center justify-center flex-shrink-0">
              <Wallet strokeWidth={1.5} className="text-emerald-600 w-5 h-5" />
            </div>
            <div>
              <div className="text-[9px] font-semibold text-emerald-600 uppercase tracking-wider">Monthly Total</div>
              <div className="text-sm font-semibold text-emerald-700 mt-0.5">₹{totalAddonCost}</div>
              <div className="text-[9px] text-emerald-400 font-medium">For {formatMonthName(selectedMonth)}</div>
            </div>
          </div>
        </div>

        {/* Compact Service Log Feed */}
        {filteredAddons.filter(a => ((a.omeletteCount || 0) + (a.boiledEggCount || 0) + (a.washingMachineCount || 0)) > 0).length === 0 ? (
          <div className="text-center py-8 text-slate-400 text-sm flex flex-col items-center justify-center bg-slate-50/50 rounded-xl border border-dashed border-slate-200">
            <ChefHat className="w-8 h-8 text-slate-300 mb-2" strokeWidth={1.5}/>
            <span>No service usage logged for this month.</span>
          </div>
        ) : (
          <div className="flex flex-col gap-2 max-h-[280px] overflow-y-auto pr-1">
            {filteredAddons.map(a => {
              const oCount = a.omeletteCount || 0;
              const eCount = a.boiledEggCount || 0;
              const wCount = a.washingMachineCount || 0;
              const dayTotal = (oCount * oPrice) + (eCount * ePrice) + (wCount * wPrice);
              
              if (dayTotal === 0) return null;

              // Parse log date correctly
              const [y, m, d] = a.logDate.split('-').map(Number);
              const logDateObj = new Date(y, m - 1, d);
              const formattedDayStr = logDateObj.toLocaleDateString('en-IN', {
                day: 'numeric',
                month: 'short',
                weekday: 'short'
              });

              return (
                <div key={a.id} className="flex items-center justify-between p-3 rounded-xl border border-slate-100 hover:border-slate-200 bg-white shadow-sm hover:shadow-md transition-all duration-200">
                  <div className="flex items-center gap-3">
                    <span className="text-xs font-bold text-slate-500 min-w-[90px]">{formattedDayStr}</span>
                    <div className="flex flex-wrap gap-1.5">
                      {oCount > 0 && (
                        <span className="inline-flex items-center text-[10px] font-bold text-amber-700 bg-amber-50 border border-amber-100 px-2.5 py-0.5 rounded-full">
                          <Utensils strokeWidth={1.5} className="text-amber-600 w-3 h-3 mr-1" />
                          <span>{config?.rules?.omeletteLabel || 'Omelette'}</span>
                          <span className="bg-amber-200/60 ml-1.5 px-1 py-0.2 rounded text-[9px] font-black text-amber-800">x{oCount}</span>
                        </span>
                      )}
                      {eCount > 0 && (
                        <span className="inline-flex items-center text-[10px] font-bold text-yellow-700 bg-yellow-50/60 border border-yellow-100/60 px-2.5 py-0.5 rounded-full">
                          <Egg strokeWidth={1.5} className="text-amber-500 w-3 h-3 mr-1" />
                          <span>{config?.rules?.boiledEggLabel || 'Boiled Egg'}</span>
                          <span className="bg-yellow-200/50 ml-1.5 px-1 py-0.2 rounded text-[9px] font-black text-yellow-800">x{eCount}</span>
                        </span>
                      )}
                      {wCount > 0 && (
                        <span className="inline-flex items-center text-[10px] font-bold text-blue-700 bg-blue-50 border border-blue-100 px-2.5 py-0.5 rounded-full">
                          <Shirt strokeWidth={1.5} className="text-blue-500 w-3 h-3 mr-1" />
                          <span>{config?.rules?.washingMachineLabel || 'Washing Machine'}</span>
                          <span className="bg-blue-200/60 ml-1.5 px-1 py-0.2 rounded text-[9px] font-black text-blue-800">x{wCount}</span>
                        </span>
                      )}
                    </div>
                  </div>
                  <div className="text-right font-extrabold text-sm text-indigo-600">
                    ₹{dayTotal}
                  </div>
                </div>
              );
            }).filter(Boolean)}
          </div>
        )}
      </div>
    </AppLayout>
  );
}

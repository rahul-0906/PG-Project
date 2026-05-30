import React, { useEffect, useState } from 'react';
import AppLayout from '../../components/AppLayout';
import { guestApi } from '../../api';
import { 
  Wrench, 
  Plus, 
  Loader2, 
  CheckCircle2, 
  AlertCircle, 
  HelpCircle, 
  Clock, 
  AlertTriangle 
} from 'lucide-react';

const PRIORITY_BADGES = {
  LOW: 'bg-slate-100 text-slate-800 border-slate-200',
  MEDIUM: 'bg-blue-100 text-blue-800 border-blue-200',
  HIGH: 'bg-rose-100 text-rose-800 border-rose-200'
};

const STATUS_BADGES = {
  OPEN: 'bg-red-100 text-red-800 border-red-200',
  IN_PROGRESS: 'bg-indigo-100 text-indigo-800 border-indigo-200',
  RESOLVED: 'bg-green-100 text-green-800 border-green-200'
};

export default function GuestMaintenance() {
  const [tickets, setTickets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [toast, setToast] = useState('');
  
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState('MEDIUM');

  const showToast = (msg) => {
    setToast(msg);
    setTimeout(() => setToast(''), 3000);
  };

  const loadTickets = async () => {
    setLoading(true);
    try {
      const res = await guestApi.getMainMaintenanceTickets();
      setTickets(res.data || []);
    } catch {
      showToast('Failed to load maintenance tickets');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // Note: temporary override so the API method name behaves correctly if there is a mismatch
    const getTickets = guestApi.getMaintenanceTickets || guestApi.getMainMaintenanceTickets;
    if (getTickets) {
      setLoading(true);
      getTickets()
        .then(res => setTickets(res.data || []))
        .catch(() => showToast('Failed to load tickets'))
        .finally(() => setLoading(false));
    }
  }, []);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!title.trim() || !description.trim()) {
      showToast('Please fill out all fields');
      return;
    }
    setSubmitting(true);
    try {
      const createTicket = guestApi.createMaintenanceTicket;
      const res = await createTicket({
        title: title.trim(),
        description: description.trim(),
        priority
      });
      showToast('Ticket submitted successfully');
      setTitle('');
      setDescription('');
      setPriority('MEDIUM');
      // reload
      const getTickets = guestApi.getMaintenanceTickets || guestApi.getMainMaintenanceTickets;
      if (getTickets) {
        const listRes = await getTickets();
        setTickets(listRes.data || []);
      }
    } catch (err) {
      showToast(err.response?.data?.error || 'Failed to submit ticket');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AppLayout>
      <div className="page-header">
        <div>
          <h1 className="page-title flex items-center gap-2">
            <Wrench className="w-6 h-6 text-primary" />
            <span>Maintenance Portal</span>
          </h1>
          <p className="page-subtitle">Submit and track maintenance requests for your room</p>
        </div>
      </div>

      {toast && (
        <div className="fixed top-4 right-4 z-50 bg-slate-900 text-white px-4 py-2.5 rounded-xl shadow-lg text-sm animate-fade-in-up flex items-center gap-2">
          <AlertCircle className="w-4 h-4 text-indigo-400" /> {toast}
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Submit Ticket Form */}
        <div className="lg:col-span-1">
          <div className="card shadow-sm border border-slate-200 bg-white rounded-xl p-6">
            <h3 className="font-heading text-base font-semibold text-slate-900 mb-4 flex items-center gap-2">
              <Plus className="w-5 h-5 text-indigo-500" />
              New Request
            </h3>
            
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="form-group">
                <label className="form-label" htmlFor="ticket-title">Issue Title</label>
                <input
                  id="ticket-title"
                  type="text"
                  className="form-input"
                  placeholder="e.g. Wi-Fi not connecting"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  maxLength={100}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="ticket-description">Describe the Issue</label>
                <textarea
                  id="ticket-description"
                  className="form-input min-h-[100px] resize-none"
                  placeholder="Please describe what is wrong in detail..."
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  maxLength={1000}
                  required
                ></textarea>
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="ticket-priority">Priority Level</label>
                <select
                  id="ticket-priority"
                  className="form-input cursor-pointer"
                  value={priority}
                  onChange={(e) => setPriority(e.target.value)}
                >
                  <option value="LOW">Low (Non-urgent)</option>
                  <option value="MEDIUM">Medium (General repair)</option>
                  <option value="HIGH">High (Urgent issue)</option>
                </select>
              </div>

              <button
                id="btn-submit-ticket"
                type="submit"
                className="w-full btn btn-primary py-2 flex justify-center text-sm font-semibold shadow-sm"
                disabled={submitting}
              >
                {submitting ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin mr-2" />
                    <span>Submitting...</span>
                  </>
                ) : (
                  <span>Submit Ticket</span>
                )}
              </button>
            </form>
          </div>
        </div>

        {/* Tickets History List */}
        <div className="lg:col-span-2">
          <div className="card shadow-sm border border-slate-200 bg-white rounded-xl overflow-hidden p-0">
            <div className="p-5 border-b border-slate-200 flex items-center justify-between">
              <h3 className="font-heading text-base font-semibold text-slate-900 flex items-center gap-2">
                <Clock className="w-5 h-5 text-indigo-500" />
                Ticket History
              </h3>
              <span className="text-xs font-semibold text-slate-500 bg-slate-100 px-2.5 py-0.5 rounded-full border border-slate-200">
                {tickets.length} Ticket{tickets.length !== 1 ? 's' : ''}
              </span>
            </div>

            {loading ? (
              <div className="flex justify-center items-center py-20">
                <Loader2 className="w-8 h-8 text-indigo-500 animate-spin" />
              </div>
            ) : tickets.length === 0 ? (
              <div className="text-center py-20 px-4">
                <HelpCircle className="w-12 h-12 text-slate-300 mx-auto mb-3" />
                <h4 className="text-sm font-bold text-slate-800 mb-1">No requests found</h4>
                <p className="text-xs text-slate-500 max-w-xs mx-auto">You haven't submitted any maintenance requests yet.</p>
              </div>
            ) : (
              <div className="table-wrap">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="bg-slate-50 border-b border-slate-200">
                      <th className="py-3 px-4 text-xs font-medium uppercase tracking-wider text-slate-500">Ticket Info</th>
                      <th className="py-3 px-4 text-xs font-medium uppercase tracking-wider text-slate-500">Location</th>
                      <th className="py-3 px-4 text-xs font-medium uppercase tracking-wider text-slate-500">Priority</th>
                      <th className="py-3 px-4 text-xs font-medium uppercase tracking-wider text-slate-500">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200">
                    {tickets.map((t) => (
                      <tr key={t.id} className="hover:bg-slate-50/50 transition-colors">
                        <td className="py-3.5 px-4 max-w-[280px]">
                          <div className="font-semibold text-slate-900 text-sm truncate">{t.title}</div>
                          <div className="text-xs text-slate-500 mt-1 leading-relaxed line-clamp-2" title={t.description}>
                            {t.description}
                          </div>
                          <div className="text-[10px] text-slate-400 mt-1.5 font-medium">
                            Created: {new Date(t.createdAt).toLocaleDateString('en-IN', {
                              day: '2-digit',
                              month: 'short',
                              year: 'numeric'
                            })}
                            {t.resolvedAt && ` • Resolved: ${new Date(t.resolvedAt).toLocaleDateString('en-IN', {
                              day: '2-digit',
                              month: 'short',
                              year: 'numeric'
                            })}`}
                          </div>
                        </td>
                        <td className="py-3.5 px-4 text-slate-600 text-xs font-medium">
                          {t.location}
                        </td>
                        <td className="py-3.5 px-4">
                          <span className={`inline-flex items-center text-[10px] font-bold px-2 py-0.5 rounded-full border ${PRIORITY_BADGES[t.priority] || ''}`}>
                            {t.priority}
                          </span>
                        </td>
                        <td className="py-3.5 px-4">
                          <span className={`inline-flex items-center text-[10px] font-bold px-2 py-0.5 rounded-full border ${STATUS_BADGES[t.status] || ''}`}>
                            {t.status}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      </div>
    </AppLayout>
  );
}

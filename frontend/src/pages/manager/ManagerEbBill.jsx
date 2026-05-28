import React, { useState } from 'react';
import AppLayout from '../../components/AppLayout';
import { managerApi } from '../../api';
import { 
  Zap, 
  CheckCircle2, 
  X, 
  Loader2,
  Plus
} from 'lucide-react';

export default function ManagerEbBill() {
  const [mode, setMode] = useState('EQUAL_SPLIT'); // EQUAL_SPLIT or METER_BASED
  const [form, setForm] = useState({ blockId:'', totalAmount:'', periodStart:'', periodEnd:'', ratePerUnit:'' });
  const [readings, setReadings] = useState([]);
  const [result, setResult] = useState(null);
  const [saving, setSaving] = useState(false);

  const addReadingRow = () => {
    setReadings([...readings, { guestId: '', previousReading: '', currentReading: '' }]);
  };

  const updateReading = (index, field, value) => {
    const newReadings = [...readings];
    newReadings[index][field] = value;
    setReadings(newReadings);
  };

  const removeReadingRow = (index) => {
    setReadings(readings.filter((_, i) => i !== index));
  };

  const submit = async (e) => {
    e.preventDefault(); setSaving(true);
    try {
      let res;
      if (mode === 'EQUAL_SPLIT') {
        res = await managerApi.recordEbBill({
          blockId: form.blockId, totalAmount: form.totalAmount, periodStart: form.periodStart, periodEnd: form.periodEnd
        });
      } else {
        res = await managerApi.recordMeterBasedEbBill({
          blockId: form.blockId, ratePerUnit: form.ratePerUnit, periodStart: form.periodStart, periodEnd: form.periodEnd,
          readings: readings
        });
      }
      setResult(res.data);
    } catch(err) { alert(err.response?.data?.error || 'Failed'); }
    setSaving(false);
  };

  return (
    <AppLayout>
      <div className="page-header">
        <div>
          <h1 className="page-title flex items-center gap-2">
            <Zap className="w-6 h-6 text-primary" />
            <span>EB Bill</span>
          </h1>
          <p className="page-subtitle">Record electricity bills per block</p>
        </div>
      </div>
      
      <div style={{ display: 'flex', gap: '1rem', marginBottom: '1.5rem' }}>
        <button className={`btn ${mode === 'EQUAL_SPLIT' ? 'btn-primary' : 'btn-secondary'}`} onClick={() => setMode('EQUAL_SPLIT')}>Equal Split</button>
        <button className={`btn ${mode === 'METER_BASED' ? 'btn-primary' : 'btn-secondary'}`} onClick={() => setMode('METER_BASED')}>Sub-Meter Based</button>
      </div>

      <div className="card" style={{ maxWidth: 800 }}>
        <form onSubmit={submit}>
          <div className="grid-2">
            <div className="form-group"><label className="form-label">Block ID</label><input className="form-input" value={form.blockId} onChange={e=>setForm(f=>({...f,blockId:e.target.value}))} placeholder="Enter Block ID" autoComplete="off" name="eb-block-id" required /></div>
            {mode === 'EQUAL_SPLIT' ? (
              <div className="form-group"><label className="form-label">Total EB Amount (₹)</label><input type="number" step="0.01" className="form-input" value={form.totalAmount} onChange={e=>setForm(f=>({...f,totalAmount:e.target.value}))} placeholder="e.g. 3600" autoComplete="off" name="eb-total-amount" required /></div>
            ) : (
              <div className="form-group"><label className="form-label">Rate Per Unit (₹/kWh)</label><input type="number" step="0.01" className="form-input" value={form.ratePerUnit} onChange={e=>setForm(f=>({...f,ratePerUnit:e.target.value}))} placeholder="e.g. 8.50" autoComplete="off" name="eb-rate-per-unit" required /></div>
            )}
          </div>
          
          <div className="grid-2">
            <div className="form-group"><label className="form-label">Period Start</label><input type="date" className="form-input" value={form.periodStart} onChange={e=>setForm(f=>({...f,periodStart:e.target.value}))} required /></div>
            <div className="form-group"><label className="form-label">Period End</label><input type="date" className="form-input" value={form.periodEnd} onChange={e=>setForm(f=>({...f,periodEnd:e.target.value}))} required /></div>
          </div>

          {mode === 'METER_BASED' && (
            <div style={{ marginTop: '1rem', marginBottom: '1.5rem', background: 'var(--bg-secondary)', padding: '1rem', borderRadius: 8 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                <h4 style={{ margin: 0, fontWeight: 600 }}>Guest Meter Readings</h4>
                <button type="button" className="btn btn-secondary flex items-center gap-1" style={{ padding: '0.2rem 0.6rem', fontSize: '0.8rem' }} onClick={addReadingRow}>
                  <Plus className="w-3.5 h-3.5" />
                  <span>Add Guest</span>
                </button>
              </div>
              {readings.length === 0 && <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>No readings added.</p>}
              {readings.map((r, i) => (
                <div key={i} style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr auto', gap: '0.5rem', marginBottom: '0.5rem', alignItems: 'end' }}>
                  <div><label className="form-label" style={{fontSize:'0.75rem'}}>Guest ID</label><input className="form-input" value={r.guestId} onChange={e=>updateReading(i, 'guestId', e.target.value)} required /></div>
                  <div><label className="form-label" style={{fontSize:'0.75rem'}}>Previous</label><input type="number" step="0.01" className="form-input" value={r.previousReading} onChange={e=>updateReading(i, 'previousReading', e.target.value)} required /></div>
                  <div><label className="form-label" style={{fontSize:'0.75rem'}}>Current</label><input type="number" step="0.01" className="form-input" value={r.currentReading} onChange={e=>updateReading(i, 'currentReading', e.target.value)} required /></div>
                  <button type="button" className="btn btn-ghost" style={{ color: 'var(--danger)', padding: '0.5rem' }} onClick={() => removeReadingRow(i)}>
                    <X className="w-4 h-4" />
                  </button>
                </div>
              ))}
            </div>
          )}

          <button type="submit" className="btn btn-primary flex items-center gap-1.5" disabled={saving}>
            {saving ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" />
                <span>Processing...</span>
              </>
            ) : (
              <>
                <Zap className="w-4 h-4" />
                <span>Split & Save</span>
              </>
            )}
          </button>
        </form>
        {result && (
          <div style={{ marginTop:'1.5rem', padding:'1rem', background:'rgba(16,185,129,0.1)', borderRadius:10, border:'1px solid var(--success)' }}>
            <div style={{ color:'var(--success)', fontWeight:700, marginBottom:'0.5rem', display:'flex', alignItems:'center', gap:'0.4rem' }}>
              <CheckCircle2 className="w-5 h-5 text-emerald-500" />
              <span>EB Bill Recorded</span>
            </div>
            <div style={{ fontSize:'0.85rem', color:'var(--text-secondary)' }}>Bill ID: {result.id}</div>
            <div style={{ fontSize:'0.85rem', color:'var(--text-secondary)' }}>Total: ₹{result.totalAmount} split among {result.guestShares?.length ?? 0} guests</div>
          </div>
        )}
      </div>
    </AppLayout>
  );
}

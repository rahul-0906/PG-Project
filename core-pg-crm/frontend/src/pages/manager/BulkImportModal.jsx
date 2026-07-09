import React, { useState } from 'react';
import { managerApi } from '../../api';
import { 
  X, 
  UploadCloud, 
  Download, 
  CheckCircle2, 
  AlertCircle, 
  Loader2,
  FileSpreadsheet
} from 'lucide-react';

export default function BulkImportModal({ isOpen, onClose, onRefresh }) {
  const [file, setFile] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [summary, setSummary] = useState(null);
  const [isDragActive, setIsDragActive] = useState(false);

  if (!isOpen) return null;

  // Generate and download a sample CSV template compatible with Excel
  const handleDownloadTemplate = () => {
    const csvContent = 
      "Room Number,First Name,Last Name,Phone,Monthly Rent,Check-in Date,Opening Rent Arrears,Initial EB Reading,Meal Plan Opt-In\n" +
      "101,John,Doe,9876543210,6500,2026-07-08,1200,150.5,Yes\n" +
      "202,Jane,Smith,9876543211,7000,2026-07-08,0,0,No\n" +
      "305,Aravind,Kumar,9876543212,8500,2026-07-08,4500,320.0,Yes";
    
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.setAttribute("href", url);
    link.setAttribute("download", "pg_guest_import_template.csv");
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const handleDrag = (e) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === "dragenter" || e.type === "dragover") {
      setIsDragActive(true);
    } else if (e.type === "dragleave") {
      setIsDragActive(false);
    }
  };

  const handleDrop = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragActive(false);
    
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      const droppedFile = e.dataTransfer.files[0];
      validateAndSetFile(droppedFile);
    }
  };

  const handleFileChange = (e) => {
    if (e.target.files && e.target.files[0]) {
      validateAndSetFile(e.target.files[0]);
    }
  };

  const validateAndSetFile = (selectedFile) => {
    const extension = selectedFile.name.split('.').pop().toLowerCase();
    if (!['xlsx', 'xls', 'csv'].includes(extension)) {
      setError("Invalid file format. Please upload an Excel (.xlsx, .xls) or CSV (.csv) file.");
      setFile(null);
      return;
    }
    setError(null);
    setFile(selectedFile);
  };

  const handleUpload = async (e) => {
    e.preventDefault();
    if (!file) return;

    setLoading(true);
    setError(null);
    setSummary(null);

    const formData = new FormData();
    formData.append("file", file);

    try {
      const selectedBranchId = sessionStorage.getItem('selectedBranchId');
      
      // Post file to backend using custom headers for multipart
      const response = await managerApi.bulkImport(formData, selectedBranchId);
      
      setSummary(response.data);
      setFile(null);
      if (onRefresh) onRefresh();
    } catch (err) {
      console.error("Bulk import failed:", err);
      const errMsg = err.response?.data?.message || err.response?.data?.error || err.message || "Failed to parse spreadsheet.";
      setError(errMsg);
    } finally {
      setLoading(false);
    }
  };

  const handleReset = () => {
    setFile(null);
    setError(null);
    setSummary(null);
  };

  return (
    <div className="modal-backdrop flex items-center justify-center z-50 p-4 animate-fade-in bg-slate-900/60 backdrop-blur-sm fixed inset-0">
      <div className="bg-white rounded-2xl max-w-lg w-full overflow-hidden shadow-2xl border border-slate-100 flex flex-col max-h-[90vh]">
        
        {/* Modal Header */}
        <div className="flex items-center justify-between px-6 py-5 border-b border-slate-100 bg-slate-50/50">
          <div className="flex items-center gap-2.5">
            <div className="p-2 bg-blue-50 text-blue-600 rounded-xl">
              <FileSpreadsheet className="w-5 h-5" strokeWidth={2} />
            </div>
            <div>
              <h3 className="font-extrabold text-slate-800 text-lg leading-tight">1-Click Excel Bulk Import</h3>
              <p className="text-slate-400 text-xs mt-0.5 font-medium">Onboard guests, rooms, and floor structures instantly</p>
            </div>
          </div>
          <button 
            onClick={onClose} 
            disabled={loading}
            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors"
          >
            <X className="w-5 h-5" strokeWidth={2} />
          </button>
        </div>

        {/* Modal Body */}
        <div className="p-6 overflow-y-auto flex-1 space-y-5">
          
          {error && (
            <div className="p-4 bg-rose-50 border border-rose-100 text-rose-700 rounded-xl flex items-start gap-3 animate-shake">
              <AlertCircle className="w-5 h-5 shrink-0 mt-0.5" />
              <div>
                <span className="font-bold text-sm">Import Error: </span>
                <span className="text-sm font-medium">{error}</span>
              </div>
            </div>
          )}

          {summary ? (
            /* Summary Success Result View */
            <div className="space-y-5 py-2">
              <div className="text-center space-y-2">
                <div className="inline-flex p-3.5 bg-emerald-50 text-emerald-600 rounded-full mb-2">
                  <CheckCircle2 className="w-10 h-10 animate-bounce-short" strokeWidth={1.5} />
                </div>
                <h4 className="text-xl font-black text-slate-800">Import Completed!</h4>
                <p className="text-slate-500 text-sm font-semibold max-w-xs mx-auto">
                  Spreadsheet parsed successfully. Your PG roster is now active.
                </p>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="p-4 rounded-2xl bg-slate-50 border border-slate-100/80 text-center shadow-sm">
                  <div className="text-3xl font-black text-slate-800">{summary.totalImported}</div>
                  <div className="text-slate-400 text-xs font-bold mt-1 uppercase tracking-wider">Guests Active</div>
                </div>
                <div className="p-4 rounded-2xl bg-slate-50 border border-slate-100/80 text-center shadow-sm">
                  <div className="text-3xl font-black text-slate-800">{summary.roomsCreated}</div>
                  <div className="text-slate-400 text-xs font-bold mt-1 uppercase tracking-wider">Rooms Created</div>
                </div>
              </div>

              <div className="p-3 bg-emerald-50/50 border border-emerald-100/60 rounded-xl text-center">
                <span className="text-emerald-700 text-xs font-bold">
                  🎉 Guest login profiles generated under `guest.[phone]@pgcrm.com`
                </span>
              </div>
            </div>
          ) : (
            /* Upload Action View */
            <>
              {/* Instructions and Download Template Card */}
              <div className="p-4 rounded-2xl bg-blue-50/40 border border-blue-100/50 flex items-center justify-between gap-4">
                <div className="space-y-1">
                  <div className="font-extrabold text-blue-900 text-sm">Download Template Sheet</div>
                  <div className="text-blue-700/80 text-xs font-medium max-w-[280px]">
                    Use our standardized Excel structure to prevent column mismatch errors.
                  </div>
                </div>
                <button
                  type="button"
                  onClick={handleDownloadTemplate}
                  className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-xs font-bold flex items-center gap-1.5 transition-all shadow-sm shadow-blue-600/20 active:scale-95"
                >
                  <Download className="w-3.5 h-3.5" strokeWidth={2.5} />
                  <span>Download</span>
                </button>
              </div>

              {/* Drag and Drop Zone */}
              <div 
                onDragEnter={handleDrag}
                onDragOver={handleDrag}
                onDragLeave={handleDrag}
                onDrop={handleDrop}
                className={`border-2 border-dashed rounded-2xl p-8 text-center transition-all flex flex-col items-center justify-center cursor-pointer group ${
                  isDragActive 
                    ? "border-blue-500 bg-blue-50/20 scale-[0.98]" 
                    : file 
                      ? "border-emerald-400 bg-emerald-50/10" 
                      : "border-slate-200 hover:border-slate-300 hover:bg-slate-50/30"
                }`}
              >
                <input 
                  id="excel-file-upload"
                  type="file" 
                  accept=".xlsx, .xls, .csv" 
                  onChange={handleFileChange}
                  className="hidden" 
                />
                <label htmlFor="excel-file-upload" className="cursor-pointer flex flex-col items-center">
                  <div className={`p-4 rounded-2xl mb-4 transition-colors ${
                    file 
                      ? "bg-emerald-50 text-emerald-600" 
                      : "bg-slate-50 text-slate-400 group-hover:bg-slate-100 group-hover:text-slate-600"
                  }`}>
                    {file ? (
                      <CheckCircle2 className="w-8 h-8" strokeWidth={1.5} />
                    ) : (
                      <UploadCloud className="w-8 h-8" strokeWidth={1.5} />
                    )}
                  </div>
                  
                  {file ? (
                    <div className="space-y-1">
                      <div className="font-extrabold text-slate-800 text-sm max-w-[260px] truncate">{file.name}</div>
                      <div className="text-slate-400 text-xs font-bold">{(file.size / 1024).toFixed(1)} KB</div>
                    </div>
                  ) : (
                    <div className="space-y-1">
                      <div className="font-extrabold text-slate-700 text-sm">
                        Drag & Drop or <span className="text-blue-600 underline">Browse File</span>
                      </div>
                      <div className="text-slate-400 text-xs font-medium">Supports .xlsx, .xls, .csv up to 10MB</div>
                    </div>
                  )}
                </label>
              </div>
            </>
          )}

        </div>

        {/* Modal Footer */}
        <div className="px-6 py-4 border-t border-slate-100 bg-slate-50/40 flex items-center justify-end gap-3.5">
          {summary ? (
            <button
              onClick={onClose}
              className="px-5 py-2.5 bg-slate-800 hover:bg-slate-900 text-white rounded-xl text-sm font-bold shadow-sm transition-all"
            >
              Done
            </button>
          ) : (
            <>
              <button
                type="button"
                onClick={file ? handleReset : onClose}
                disabled={loading}
                className="px-5 py-2.5 border border-slate-200 hover:bg-slate-50 text-slate-600 rounded-xl text-sm font-bold transition-all disabled:opacity-50"
              >
                {file ? "Reset" : "Cancel"}
              </button>
              <button
                onClick={handleUpload}
                disabled={!file || loading}
                className="px-5 py-2.5 bg-blue-600 hover:bg-blue-700 disabled:bg-slate-200 disabled:text-slate-400 text-white rounded-xl text-sm font-bold flex items-center gap-2 transition-all shadow-sm shadow-blue-600/10 active:scale-95 disabled:scale-100 disabled:shadow-none"
              >
                {loading && <Loader2 className="w-4 h-4 animate-spin" />}
                <span>{loading ? "Importing Roster..." : "Start Import"}</span>
              </button>
            </>
          )}
        </div>

      </div>
    </div>
  );
}

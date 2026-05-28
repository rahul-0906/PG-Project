import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import Login from './pages/Login';
import ChangePassword from './pages/ChangePassword';
import OwnerDashboard from './pages/owner/OwnerDashboard';
import OwnerBuildingCreator from './pages/owner/OwnerBuildingCreator';
import ManagerDashboard from './pages/manager/ManagerDashboard';
import GuestDashboard from './pages/guest/GuestDashboard';
import DailyLog from './pages/guest/DailyLog';
import GuestInvoices from './pages/guest/GuestInvoices';
import ManagerGuests from './pages/manager/ManagerGuests';
import ManagerEbBill from './pages/manager/ManagerEbBill';
import ManagerMaintenance from './pages/manager/ManagerMaintenance';
import ManagerGuestAddons from './pages/manager/ManagerGuestAddons';
import ManagerPricing from './pages/manager/ManagerPricing';
import ManagerInvoiceGenerator from './pages/manager/ManagerInvoiceGenerator';

import Reports from './pages/Reports';
import AuditLog from './pages/AuditLog';

/**
 * Protects a route:
 * - Redirects to /login if not authenticated
 * - Redirects to /change-password if mustChangePassword=true
 * - Redirects to /unauthorized if wrong role
 */
function PrivateRoute({ children, roles }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" />;

  // Force password change before any other page
  const mustChange = user.mustChangePassword === true || user.mustChangePassword === 'true';
  if (mustChange) return <Navigate to="/change-password" />;

  if (roles && !roles.includes(user.role)) return <Navigate to="/unauthorized" />;
  return children;
}

function RoleRedirect() {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" />;
  const mustChange = user.mustChangePassword === true || user.mustChangePassword === 'true';
  if (mustChange) return <Navigate to="/change-password" />;
  const map = {
    PG_OWNER: '/owner/dashboard',
    PG_MANAGER: '/manager/dashboard',
    GUEST: '/guest/dashboard',
  };
  return <Navigate to={map[user.role] || '/login'} />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<RoleRedirect />} />
      <Route path="/login" element={<Login />} />
      <Route path="/change-password" element={<ChangePassword />} />
      <Route path="/unauthorized" element={<div style={{color:'white',padding:'2rem',textAlign:'center'}}>
        <h2>🚫 Unauthorized</h2><p>You don't have permission to view this page.</p>
      </div>} />


      {/* PG Owner */}
      <Route path="/owner/dashboard" element={
        <PrivateRoute roles={['PG_OWNER']}><OwnerDashboard /></PrivateRoute>} />
      <Route path="/owner/buildings" element={
        <PrivateRoute roles={['PG_OWNER']}><OwnerBuildingCreator /></PrivateRoute>} />
      <Route path="/owner/reports" element={
        <PrivateRoute roles={['PG_OWNER']}><Reports /></PrivateRoute>} />
      <Route path="/owner/audit" element={
        <PrivateRoute roles={['PG_OWNER']}><AuditLog /></PrivateRoute>} />

      {/* PG Manager */}
      <Route path="/manager/dashboard" element={
        <PrivateRoute roles={['PG_MANAGER', 'PG_OWNER']}><ManagerDashboard /></PrivateRoute>} />
      <Route path="/manager/guests" element={
        <PrivateRoute roles={['PG_MANAGER', 'PG_OWNER']}><ManagerGuests /></PrivateRoute>} />
      <Route path="/manager/eb-bill" element={
        <PrivateRoute roles={['PG_MANAGER', 'PG_OWNER']}><ManagerEbBill /></PrivateRoute>} />
      <Route path="/manager/maintenance" element={
        <PrivateRoute roles={['PG_MANAGER', 'PG_OWNER']}><ManagerMaintenance /></PrivateRoute>} />
      <Route path="/manager/addons" element={
        <PrivateRoute roles={['PG_MANAGER', 'PG_OWNER']}><ManagerGuestAddons /></PrivateRoute>} />
      <Route path="/manager/pricing" element={
        <PrivateRoute roles={['PG_MANAGER', 'PG_OWNER']}><ManagerPricing /></PrivateRoute>} />
      <Route path="/manager/invoices" element={
        <PrivateRoute roles={['PG_MANAGER', 'PG_OWNER']}><ManagerInvoiceGenerator /></PrivateRoute>} />
      <Route path="/manager/reports" element={
        <PrivateRoute roles={['PG_MANAGER', 'PG_OWNER']}><Reports /></PrivateRoute>} />
      <Route path="/manager/audit" element={
        <PrivateRoute roles={['PG_MANAGER', 'PG_OWNER']}><AuditLog /></PrivateRoute>} />

      {/* Guest */}
      <Route path="/guest/dashboard" element={
        <PrivateRoute roles={['GUEST']}><GuestDashboard /></PrivateRoute>} />
      <Route path="/guest/daily-log" element={
        <PrivateRoute roles={['GUEST']}><DailyLog /></PrivateRoute>} />
      <Route path="/guest/invoices" element={
        <PrivateRoute roles={['GUEST']}><GuestInvoices /></PrivateRoute>} />
    </Routes>
  );
}

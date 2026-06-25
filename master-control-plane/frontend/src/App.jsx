import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import OnboardingForm from './pages/OnboardingForm';
import SuccessPage from './pages/SuccessPage';
import AdminLayout from './components/AdminLayout';
import AdminDashboard from './pages/AdminDashboard';
import ClientRoster from './pages/ClientRoster';
import BillingDashboard from './pages/BillingDashboard';
import TenantOnboardingWizard from './pages/TenantOnboardingWizard';
import SuperAdminDashboard from './pages/SuperAdminDashboard';
import ClientProvisioningTracker from './pages/ClientProvisioningTracker';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/onboard" replace />} />
        <Route path="/signup" element={<OnboardingForm />} />
        <Route path="/onboard" element={<TenantOnboardingWizard />} />
        <Route path="/provisioning" element={<ClientProvisioningTracker />} />
        <Route path="/success" element={<SuccessPage />} />
        <Route path="/billing" element={<BillingDashboard />} />
        <Route path="/admin" element={<AdminLayout />}>
          <Route index element={<AdminDashboard />} />
          <Route path="clients" element={<ClientRoster />} />
          <Route path="tenants" element={<SuperAdminDashboard />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;

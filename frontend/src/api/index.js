import axios from 'axios';

const api = axios.create({ baseURL: '/api' });

api.interceptors.request.use(config => {
  const token = localStorage.getItem('accessToken');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  r => r,
  async err => {
    if (err.response?.status === 401) {
      const refresh = localStorage.getItem('refreshToken');
      if (refresh) {
        try {
          const res = await axios.post('/api/auth/refresh', { refreshToken: refresh });
          localStorage.setItem('accessToken', res.data.accessToken);
          err.config.headers.Authorization = `Bearer ${res.data.accessToken}`;
          return api(err.config);
        } catch {
          localStorage.clear();
          window.location.href = '/login';
        }
      }
    }
    return Promise.reject(err);
  }
);

export const authApi = {
  login: (email, password) => api.post('/auth/login', { email, password }),
  logout: () => api.post('/auth/logout'),
};

export const systemApi = {
  getConfig: () => axios.get('/api/system/config'), // Unauthenticated — uses raw axios
};




export const ownerApi = {
  getDashboard: () => api.get('/owner/dashboard'),
  getBranches: () => api.get('/owner/branches'),
  getManagers: () => api.get('/owner/managers'),
  createManager: (data) => api.post('/owner/managers', data),
  getConfig: () => api.get('/owner/config'),
  // Building Creator
  createBuilding: (data) => api.post('/owner/buildings', data),
  getBuildingLayout: (id) => api.get(`/owner/buildings/${id}`),
  updateBuilding: (id, data) => api.put(`/owner/buildings/${id}`, data),
};

export const managerApi = {
  getDashboard: () => api.get('/manager/dashboard'),
  getGuests: () => api.get('/manager/guests'),
  checkIn: (data) => api.post('/manager/guests', data),
  updateGuest: (id, data) => api.put(`/manager/guests/${id}`, data),
  initiateCheckout: (id) => api.post(`/manager/guests/${id}/initiate-checkout`),
  confirmCheckout: (id) => api.post(`/manager/guests/${id}/confirm-checkout`),
  recordEbBill: (data) => api.post('/manager/eb-bill', data),
  recordMeterBasedEbBill: (data) => api.post('/manager/eb-bill/meter', data),
  getFoodCount: (date) => api.get(`/manager/food-count/${date}`),
  getMaintenanceTickets: () => api.get('/manager/maintenance'),
  createTicket: (data) => api.post('/manager/maintenance', data),
  resolveTicket: (id) => api.put(`/manager/maintenance/${id}/resolve`),
  getVacancies: () => api.get('/manager/vacancies'),
  getVacantBeds: () => api.get('/inventory/vacant-beds'),
  getAllBeds: () => api.get('/inventory/beds'),
  // Guest add-ons (egg/omelette/veg/WM) — managed by manager
  getGuestLog: (guestId, date) => api.get(`/manager/guest-log/${guestId}/${date}`),
  updateGuestLog: (guestId, date, data) => api.put(`/manager/guest-log/${guestId}/${date}`, data),
  getGuestsByDate: (date) => api.get(`/manager/guests-with-log/${date}`),
  // Pricing Manager
  getPricing: (buildingId) => api.get('/manager/pricing', { params: buildingId ? { buildingId } : {} }),
  updateFoodPrice: (key, value, buildingId) => api.put(`/manager/pricing/${key}`, { value }, { params: buildingId ? { buildingId } : {} }),
  updateRoomRent: (roomId, baseRent) => api.put(`/manager/pricing/rooms/${roomId}/rent`, { baseRent }),
  // Invoice Generator
  previewInvoices: (month, year) => api.get('/manager/invoices/preview', { params: { month, year } }),
  generateInvoice: (guestId, month, year) => api.post('/manager/billing/generate', { guestId, month, year }),
  generateAllInvoices: (month, year) => api.post('/manager/invoices/generate-all', { month, year }),
};

export const guestApi = {
  getProfile: () => api.get('/guest/profile'),
  updateProfile: (data) => api.put('/guest/profile', data),
  getDashboard: () => api.get('/guest/dashboard'),
  getLog: (date) => api.get(`/guest/daily-log/${date}`),
  updateLog: (date, data) => api.put(`/guest/daily-log/${date}`, data),
  getInvoices: () => api.get('/guest/invoices'),
  downloadInvoicePdf: (id) => api.get(`/guest/invoices/${id}/pdf`, { responseType: 'blob' }),
  getNotifications: () => api.get('/guest/notifications'),
  markRead: (id) => api.put(`/guest/notifications/${id}/read`),
  getConfig: () => api.get('/guest/tenant-config'),
};

export default api;

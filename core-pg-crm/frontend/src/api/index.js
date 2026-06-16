import axios from 'axios';

const api = axios.create({ baseURL: '/api' });

api.interceptors.request.use(config => {
  const token = sessionStorage.getItem('accessToken');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  const selectedBranchId = sessionStorage.getItem('selectedBranchId');
  if (selectedBranchId && !config.headers['X-Selected-Branch-Id']) {
    config.headers['X-Selected-Branch-Id'] = selectedBranchId;
  }
  return config;
});

api.interceptors.response.use(
  r => r,
  async err => {
    if (err.response?.status === 401) {
      const refresh = sessionStorage.getItem('refreshToken');
      if (refresh) {
        try {
          const res = await axios.post('/api/auth/refresh', { refreshToken: refresh });
          sessionStorage.setItem('accessToken', res.data.accessToken);
          err.config.headers.Authorization = `Bearer ${res.data.accessToken}`;
          return api(err.config);
        } catch {
          sessionStorage.clear();
          window.location.href = '/login';
        }
      }
    }
    return Promise.reject(err);
  }
);

// ── In-Memory Caching Wrapper ──────────────────────────────────────
const cache = new Map();
const CACHE_TTL_MS = 5000; // 5 seconds Cache Time-to-Live

export const clearApiCache = () => {
  cache.clear();
};


const cachedApi = {
  get: (url, config) => {
    const cacheKey = JSON.stringify({ url, params: config?.params });
    const cachedEntry = cache.get(cacheKey);
    const now = Date.now();

    if (cachedEntry && (now - cachedEntry.timestamp < CACHE_TTL_MS)) {
      return Promise.resolve(cachedEntry.response);
    }

    const requestPromise = api.get(url, config);
    cache.set(cacheKey, { timestamp: now, response: requestPromise });

    return requestPromise.catch(err => {
      cache.delete(cacheKey);
      throw err;
    });
  },
  post: (url, data, config) => {
    cache.clear(); // Invalidate entire cache on write
    return api.post(url, data, config);
  },
  put: (url, data, config) => {
    cache.clear(); // Invalidate entire cache on write
    return api.put(url, data, config);
  },
  delete: (url, config) => {
    cache.clear(); // Invalidate entire cache on write
    return api.delete(url, config);
  }
};

export const authApi = {
  login: (email, password) => cachedApi.post('/auth/login', { email, password }),
  logout: () => cachedApi.post('/auth/logout'),
};

export const systemApi = {
  getConfig: () => {
    const url = '/api/system/config';
    const cacheKey = JSON.stringify({ url });
    const cachedEntry = cache.get(cacheKey);
    const now = Date.now();

    if (cachedEntry && (now - cachedEntry.timestamp < CACHE_TTL_MS)) {
      return Promise.resolve(cachedEntry.response);
    }

    const requestPromise = axios.get(url);
    cache.set(cacheKey, { timestamp: now, response: requestPromise });

    return requestPromise.catch(err => {
      cache.delete(cacheKey);
      throw err;
    });
  }
};

export const ownerApi = {
  getDashboard: () => cachedApi.get('/owner/dashboard'),
  getBranches: () => cachedApi.get('/owner/branches'),
  getManagers: () => cachedApi.get('/owner/managers'),
  createManager: (data) => cachedApi.post('/owner/managers', data),
  updateManager: (id, data) => cachedApi.put(`/owner/managers/${id}`, data),
  deleteManager: (id) => cachedApi.delete(`/owner/managers/${id}`),
  getConfig: () => cachedApi.get('/owner/config'),
  // Building Creator
  createBuilding: (data) => cachedApi.post('/owner/buildings', data),
  getBuildingLayout: (id) => cachedApi.get(`/owner/buildings/${id}`),
  updateBuilding: (id, data) => cachedApi.put(`/owner/buildings/${id}`, data),
  deleteBuilding: (id) => cachedApi.delete(`/owner/buildings/${id}`),
};

export const managerApi = {
  getDashboard: (buildingId) => cachedApi.get('/manager/dashboard', {
    headers: buildingId ? { 'X-Selected-Branch-Id': buildingId } : {}
  }),
  getGuests: () => cachedApi.get('/manager/guests'),
  checkIn: (data) => cachedApi.post('/manager/guests', data),
  updateGuest: (id, data) => cachedApi.put(`/manager/guests/${id}`, data),
  switchBed: (guestId, newBedId) => cachedApi.put(`/manager/guests/${guestId}/switch-bed/${newBedId}`),
  deleteGuest: (id) => cachedApi.delete(`/manager/guests/${id}`),
  initiateCheckout: (id) => cachedApi.post(`/manager/guests/${id}/initiate-checkout`),
  confirmCheckout: (id) => cachedApi.post(`/manager/guests/${id}/confirm-checkout`),
  recordEbBill: (data) => cachedApi.post('/manager/eb-bill', data),
  recordMeterBasedEbBill: (data) => cachedApi.post('/manager/eb-bill/meter', data),
  getFoodCount: (date) => cachedApi.get(`/manager/food-count/${date}`),
  getMaintenanceTickets: () => cachedApi.get('/manager/maintenance'),
  createTicket: (data) => cachedApi.post('/manager/maintenance', data),
  resolveTicket: (id) => cachedApi.put(`/manager/maintenance/${id}/resolve`),
  getVacancies: () => cachedApi.get('/manager/vacancies'),
  getVacantBeds: () => cachedApi.get('/inventory/vacant-beds'),
  getAllBeds: () => cachedApi.get('/inventory/beds'),
  // Guest add-ons (egg/omelette/veg/WM) — managed by manager
  getGuestLog: (guestId, date) => cachedApi.get(`/manager/guest-log/${guestId}/${date}`),
  updateGuestLog: (guestId, date, data) => cachedApi.put(`/manager/guest-log/${guestId}/${date}`, data),
  getGuestsByDate: (date) => cachedApi.get(`/manager/guests-with-log/${date}`),
  // Pricing Manager
  getPricing: (buildingId) => cachedApi.get('/manager/pricing', { params: buildingId ? { buildingId } : {} }),
  updateFoodPrice: (key, value, buildingId) => cachedApi.put(`/manager/pricing/${key}`, { value }, { params: buildingId ? { buildingId } : {} }),
  updateBuildingConfig: (data, buildingId) => cachedApi.put('/manager/pricing/config', data, { params: buildingId ? { buildingId } : {} }),
  updateRoomRent: (roomId, baseRent) => cachedApi.put(`/manager/pricing/rooms/${roomId}/rent`, { baseRent }),
  // Invoice Generator
  previewInvoices: (month, year) => cachedApi.get('/manager/invoices/preview', { params: { month, year } }),
  generateInvoice: (guestId, month, year) => cachedApi.post('/manager/billing/generate', { guestId, month, year }),
  generateAllInvoices: (month, year) => cachedApi.post('/manager/invoices/generate-all', { month, year }),
  getMonthlyMeals: (month, year) => cachedApi.get('/manager/monthly-meals', { params: { month, year } }),
  getAssignedBuildings: () => cachedApi.get('/manager/assigned-buildings'),
  getBlocksByBuilding: (buildingId) => cachedApi.get(`/inventory/buildings/${buildingId}/blocks`),
  getFloorsByBuilding: (buildingId) => cachedApi.get(`/inventory/buildings/${buildingId}/floors`),
  getBlocksByFloor: (floorId) => cachedApi.get(`/inventory/floors/${floorId}/blocks`),
  verifyCash: (id) => cachedApi.post(`/manager/invoices/${id}/verify-cash`),
  getPendingCashInvoices: () => cachedApi.get('/manager/invoices/pending-cash'),
  updateSharingRent: (sharingType, baseRent, buildingId, floorId) => {
    const params = {};
    if (buildingId) params.buildingId = buildingId;
    if (floorId) params.floorId = floorId;
    return cachedApi.put(`/manager/pricing/sharing/${sharingType}/rent`, { baseRent }, { params });
  },
};

export const guestApi = {
  getProfile: () => cachedApi.get('/guest/profile'),
  updateProfile: (data) => cachedApi.put('/guest/profile', data),
  requestEmailChange: (newEmail) => cachedApi.post('/guest/profile/request-email-change', { newEmail }),
  verifyEmailChange: (newEmail, code) => cachedApi.post('/guest/profile/verify-email-change', { newEmail, code }),
  getDashboard: () => cachedApi.get('/guest/dashboard'),
  getLog: (date) => cachedApi.get(`/guest/daily-log/${date}`),
  updateLog: (date, data) => cachedApi.put(`/guest/daily-log/${date}`, data),
  getInvoices: () => cachedApi.get('/guest/invoices'),
  downloadInvoicePdf: (id) => cachedApi.get(`/guest/invoices/${id}/pdf`, { responseType: 'blob' }),
  payCash: (id) => cachedApi.post(`/guest/invoices/${id}/pay-cash`),
  getNotifications: () => cachedApi.get('/guest/notifications'),
  markRead: (id) => cachedApi.put(`/guest/notifications/${id}/read`),
  getConfig: () => cachedApi.get('/guest/tenant-config'),
  getAddons: () => cachedApi.get('/guest/addons'),
  getMonthlyLogs: (yearMonth) => cachedApi.get(`/guest/daily-log/month/${yearMonth}`),
  getMaintenanceTickets: () => cachedApi.get('/guest/maintenance'),
  createMaintenanceTicket: (data) => cachedApi.post('/guest/maintenance', data),
};

export const notificationsApi = {
  getNotifications: () => cachedApi.get('/guest/notifications'),
  markRead: (id) => cachedApi.put(`/guest/notifications/${id}/read`),
  markAllRead: () => cachedApi.put('/guest/notifications/read-all'),
};

export default cachedApi;

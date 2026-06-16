import React, { createContext, useContext, useState, useEffect } from 'react';
import axios from 'axios';

const SystemConfigContext = createContext(null);

const DEFAULT_CONFIG = {
  branding: { name: 'PG CRM', shortTitle: 'PG' },
  rules: {
    foodIncludedInRent: false,
    allowMealCancellations: true,
    breakfastEnabled: true,
    lunchEnabled: true,
    dinnerEnabled: true,
  },
};

export function SystemConfigProvider({ children }) {
  const [config, setConfig] = useState(DEFAULT_CONFIG);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    axios.get('/api/system/config')
      .then(res => {
        setConfig(res.data);
        // Dynamically update browser tab title from branding
        if (res.data?.branding?.name) {
          document.title = res.data.branding.name;
        }
      })
      .catch(() => {
        // Silently fall back to default config — app still works
        document.title = DEFAULT_CONFIG.branding.name;
      })
      .finally(() => setLoading(false));
  }, []);

  return (
    <SystemConfigContext.Provider value={{ config, loading }}>
      {children}
    </SystemConfigContext.Provider>
  );
}

export const useSystemConfig = () => useContext(SystemConfigContext);

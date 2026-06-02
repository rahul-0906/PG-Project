import React, { createContext, useContext, useState, useEffect } from 'react';
import { authApi, clearApiCache } from '../api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const stored = localStorage.getItem('user');
    return stored ? JSON.parse(stored) : null;
  });

  const login = async (email, password) => {
    localStorage.removeItem('selectedBranchId');
    const res = await authApi.login(email, password);
    const data = res.data;
    localStorage.setItem('accessToken', data.accessToken);
    localStorage.setItem('refreshToken', data.refreshToken);
    const userData = {
      userId: data.userId,
      role: data.role,
      fullName: data.fullName,
      branchId: data.branchId,
      mustChangePassword: data.mustChangePassword === 'true' || data.mustChangePassword === true,
      firstLogin: data.firstLogin === 'true' || data.firstLogin === true,
    };
    localStorage.setItem('user', JSON.stringify(userData));

    if (data.role === 'PG_MANAGER' && data.branchId) {
      const branches = data.branchId.split(',');
      if (branches.length > 0 && branches[0]) {
        localStorage.setItem('selectedBranchId', branches[0]);
      }
    }

    setUser(userData);
    return data.role;
  };

  const logout = async () => {
    try {
      await authApi.logout();
    } catch (e) {
      // Ignore network failures for logout request
    }
    clearApiCache();
    localStorage.removeItem('token');
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('user');
    localStorage.removeItem('branchId');
    localStorage.removeItem('selectedBranchId');
    localStorage.clear();
    setUser(null);
  };

  const updateUser = (newData) => {
    setUser(prev => {
      const updated = prev ? { ...prev, ...newData } : null;
      if (updated) {
        localStorage.setItem('user', JSON.stringify(updated));
      }
      return updated;
    });
  };

  return (
    <AuthContext.Provider value={{ user, login, logout, updateUser }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);

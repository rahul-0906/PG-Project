import React, { createContext, useContext, useState, useEffect } from 'react';
import { authApi, clearApiCache } from '../api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const stored = sessionStorage.getItem('user');
    return stored ? JSON.parse(stored) : null;
  });

  const login = async (email, password) => {
    sessionStorage.removeItem('selectedBranchId');
    const res = await authApi.login(email, password);
    if (!res || res.status !== 200 || !res.data) {
      throw new Error('Invalid server response');
    }
    const data = res.data;
    sessionStorage.setItem('accessToken', data.accessToken);
    sessionStorage.setItem('refreshToken', data.refreshToken);
    const userData = {
      userId: data.userId,
      role: data.role,
      fullName: data.fullName,
      branchId: data.branchId,
      mustChangePassword: data.mustChangePassword === 'true' || data.mustChangePassword === true,
      firstLogin: data.firstLogin === 'true' || data.firstLogin === true,
    };
    sessionStorage.setItem('user', JSON.stringify(userData));

    if (data.role === 'PG_MANAGER' && data.branchId) {
      const branches = data.branchId.split(',');
      if (branches.length > 0 && branches[0]) {
        sessionStorage.setItem('selectedBranchId', branches[0]);
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
    sessionStorage.removeItem('token');
    sessionStorage.removeItem('accessToken');
    sessionStorage.removeItem('refreshToken');
    sessionStorage.removeItem('user');
    sessionStorage.removeItem('branchId');
    sessionStorage.removeItem('selectedBranchId');
    sessionStorage.clear();
    setUser(null);
  };

  const updateUser = (newData) => {
    setUser(prev => {
      const updated = prev ? { ...prev, ...newData } : null;
      if (updated) {
        sessionStorage.setItem('user', JSON.stringify(updated));
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

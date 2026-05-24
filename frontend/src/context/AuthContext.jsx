import React, { createContext, useContext, useState, useEffect } from 'react';
import { authApi } from '../api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const stored = localStorage.getItem('user');
    return stored ? JSON.parse(stored) : null;
  });

  const login = async (email, password) => {
    const res = await authApi.login(email, password);
    const data = res.data;
    localStorage.setItem('accessToken', data.accessToken);
    localStorage.setItem('refreshToken', data.refreshToken);
    const userData = {
      userId: data.userId,
      role: data.role,
      mustChangePassword: data.mustChangePassword === 'true' || data.mustChangePassword === true,
      firstLogin: data.firstLogin === 'true' || data.firstLogin === true,
    };
    localStorage.setItem('user', JSON.stringify(userData));
    setUser(userData);
    return data.role;
  };

  const logout = async () => {
    await authApi.logout().catch(() => {});
    localStorage.clear();
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);

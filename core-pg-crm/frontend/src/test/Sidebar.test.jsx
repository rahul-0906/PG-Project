import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import Sidebar from '../components/Sidebar';
import { useAuth } from '../context/AuthContext';

// Mock AuthContext
vi.mock('../context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

// Mock useNavigate
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

describe('Sidebar Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders owner links when logged in as PG_OWNER', () => {
    useAuth.mockReturnValue({
      user: { role: 'PG_OWNER', fullName: 'System Owner' },
      logout: vi.fn(),
    });

    render(
      <MemoryRouter>
        <Sidebar />
      </MemoryRouter>
    );

    // Verify Owner links are rendered
    expect(screen.getByText('Buildings')).toBeInTheDocument();
    expect(screen.getByText('Audit Log')).toBeInTheDocument();
    expect(screen.queryByText('Meal & Add-on Tracker')).not.toBeInTheDocument();
  });

  it('renders manager links when logged in as PG_MANAGER', () => {
    useAuth.mockReturnValue({
      user: { role: 'PG_MANAGER', fullName: 'Branch Manager' },
      logout: vi.fn(),
    });

    render(
      <MemoryRouter>
        <Sidebar />
      </MemoryRouter>
    );

    // Verify Manager links are rendered
    expect(screen.getByText('Guests')).toBeInTheDocument();
    expect(screen.getByText('Meal & Add-on Tracker')).toBeInTheDocument();
    expect(screen.getByText('EB Bill')).toBeInTheDocument();
  });

  it('renders guest links when logged in as GUEST', () => {
    useAuth.mockReturnValue({
      user: { role: 'GUEST', fullName: 'Resident Guest' },
      logout: vi.fn(),
    });

    render(
      <MemoryRouter>
        <Sidebar />
      </MemoryRouter>
    );

    // Verify Guest links are rendered
    expect(screen.getByText('Meal Planner')).toBeInTheDocument();
    expect(screen.queryByText('Buildings')).not.toBeInTheDocument();
  });

  it('handles logout correctly when clicking Logout button', () => {
    const mockLogout = vi.fn();
    useAuth.mockReturnValue({
      user: { role: 'GUEST', fullName: 'Resident Guest' },
      logout: mockLogout,
    });

    // Mock sessionStorage
    const store = {};
    global.sessionStorage = {
      removeItem: vi.fn((key) => delete store[key]),
    };

    render(
      <MemoryRouter>
        <Sidebar />
      </MemoryRouter>
    );

    const logoutButton = screen.getByText('Logout');
    fireEvent.click(logoutButton);

    expect(global.sessionStorage.removeItem).toHaveBeenCalledWith('accessToken');
    expect(global.sessionStorage.removeItem).toHaveBeenCalledWith('refreshToken');
    expect(global.sessionStorage.removeItem).toHaveBeenCalledWith('user');
    expect(mockLogout).toHaveBeenCalled();
    expect(mockNavigate).toHaveBeenCalledWith('/login', { replace: true });
  });
});

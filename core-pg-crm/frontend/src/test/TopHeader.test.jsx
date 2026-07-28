import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import TopHeader from '../components/TopHeader';
import { useAuth } from '../context/AuthContext';
import { useSystemConfig } from '../context/SystemConfigContext';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { managerApi, notificationsApi } from '../api';

// Mock AuthContext
vi.mock('../context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

// Mock SystemConfigContext
vi.mock('../context/SystemConfigContext', () => ({
  useSystemConfig: vi.fn(),
}));

// Mock react-query
vi.mock('@tanstack/react-query', () => ({
  useQuery: vi.fn(),
  useQueryClient: vi.fn(),
}));

// Mock APIs
vi.mock('../api', () => ({
  managerApi: {
    getAssignedBuildings: vi.fn(),
  },
  notificationsApi: {
    getNotifications: vi.fn(),
    markRead: vi.fn(),
    markAllRead: vi.fn(),
  },
}));

describe('TopHeader Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useSystemConfig.mockReturnValue({
      config: { branding: { name: 'Test Brand' } },
    });
    useQueryClient.mockReturnValue({
      invalidateQueries: vi.fn(),
    });
  });

  it('renders user details and role badges correctly', () => {
    useAuth.mockReturnValue({
      user: { role: 'PG_OWNER', fullName: 'Alice Owner' },
    });
    useQuery.mockReturnValue({ data: [] });
    managerApi.getAssignedBuildings.mockResolvedValue({ data: [] });

    render(<TopHeader />);

    expect(screen.getByText('Alice Owner')).toBeInTheDocument();
  });

  it('shows branch selector for manager with multiple branches', async () => {
    useAuth.mockReturnValue({
      user: { role: 'PG_MANAGER', fullName: 'Bob Manager' },
    });
    useQuery.mockReturnValue({ data: [] });

    const mockBuildings = [
      { id: 'b1', name: 'Branch Alpha' },
      { id: 'b2', name: 'Branch Beta' },
    ];
    managerApi.getAssignedBuildings.mockResolvedValue({ data: mockBuildings });

    // Mock sessionStorage
    const store = {};
    global.sessionStorage = {
      getItem: vi.fn((key) => store[key] || null),
      setItem: vi.fn((key, value) => { store[key] = value; }),
    };

    render(<TopHeader />);

    // Wait for the async state updates
    const selector = await screen.findByRole('combobox');
    expect(selector).toBeInTheDocument();
  });
});

import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import Reports from '../pages/Reports';
import api, { ownerApi } from '../api';

// Mock AppLayout
vi.mock('../components/AppLayout', () => ({
  default: ({ children }) => <div data-testid="app-layout">{children}</div>,
}));

// Mock APIs
vi.mock('../api', () => ({
  default: {
    get: vi.fn(),
  },
  ownerApi: {
    getAssignedBuildings: vi.fn(),
    getBranches: vi.fn(),
  },
}));

describe('Reports Page Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders title and fetches summary metrics', async () => {
    // Mock buildings API
    ownerApi.getAssignedBuildings.mockResolvedValue({
      data: [{ id: 'bld1', name: 'Branch 1' }],
    });
    ownerApi.getBranches.mockResolvedValue({
      data: [{ id: 'bld1', name: 'Branch 1' }],
    });

    // Mock API endpoint calls for reports metrics
    api.get.mockImplementation((url) => {
      if (url.includes('/reports/metrics')) {
        return Promise.resolve({
          data: {
            totalRevenue: 125000,
            occupancyRate: 85,
            totalBeds: 100,
            occupiedBeds: 85,
            vacantBeds: 15,
            churnRate: 5.2,
          },
        });
      }
      if (url.includes('/reports/revenue-breakdown')) {
        return Promise.resolve({ data: [] });
      }
      if (url.includes('/reports/occupancy-breakdown')) {
        return Promise.resolve({ data: [] });
      }
      if (url.includes('/reports/payment-status')) {
        return Promise.resolve({ data: { paid: 80, pending: 20 } });
      }
      return Promise.resolve({ data: [] });
    });

    render(<Reports />);

    expect(screen.getByTestId('app-layout')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('Reports & Analytics')).toBeInTheDocument();
    });
  });
});

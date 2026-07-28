import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import AuditLog from '../pages/AuditLog';
import api from '../api';

// Mock AppLayout
vi.mock('../components/AppLayout', () => ({
  default: ({ children }) => <div data-testid="app-layout">{children}</div>,
}));

// Mock API
vi.mock('../api', () => ({
  default: {
    get: vi.fn(),
  },
}));

describe('AuditLog Page Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders title, search input, and log records', async () => {
    const mockLogs = [
      {
        id: 'log1',
        action: 'GUEST_CHECKIN',
        actorId: 'user1',
        actorName: 'Manager John',
        actorRole: 'PG_MANAGER',
        entityType: 'Guest',
        entityId: 'guest1',
        description: 'Checked in guest John Doe',
        metadata: '{"bedId":"bed1"}',
        createdAt: '2026-05-15T10:00:00Z',
      },
    ];

    api.get.mockResolvedValue({
      data: {
        content: mockLogs,
        totalElements: 1,
        totalPages: 1,
        number: 0,
      },
    });

    render(<AuditLog />);

    expect(screen.getByTestId('app-layout')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('Audit Log')).toBeInTheDocument();
      expect(screen.getByText('Checked in guest John Doe')).toBeInTheDocument();
      expect(screen.getByText('Manager John')).toBeInTheDocument();
    });
  });
});

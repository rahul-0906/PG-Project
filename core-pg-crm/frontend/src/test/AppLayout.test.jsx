import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import AppLayout from '../components/AppLayout';

// Mock TopHeader and Sidebar to isolate AppLayout testing
vi.mock('../components/TopHeader', () => ({
  default: () => <div data-testid="mock-top-header">TopHeader</div>,
}));

vi.mock('../components/Sidebar', () => ({
  default: () => <div data-testid="mock-sidebar">Sidebar</div>,
}));

describe('AppLayout Component', () => {
  it('renders top header, sidebar, and child content', () => {
    render(
      <AppLayout>
        <div data-testid="test-child">Main Content Page</div>
      </AppLayout>
    );

    expect(screen.getByTestId('mock-top-header')).toBeInTheDocument();
    expect(screen.getByTestId('mock-sidebar')).toBeInTheDocument();
    expect(screen.getByTestId('test-child')).toBeInTheDocument();
    expect(screen.getByText('Main Content Page')).toBeInTheDocument();
  });
});

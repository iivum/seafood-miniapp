import { type ReactElement } from 'react';
import { render, type RenderOptions } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useAuthStore } from '@/features/auth/store';

interface RenderProvidersOptions extends Omit<RenderOptions, 'wrapper'> {
  route?: string;
  initialEntries?: string[];
  authenticated?: boolean;
}

export function renderWithProviders(ui: ReactElement, options: RenderProvidersOptions = {}) {
  const { route = '/', initialEntries, authenticated = false, ...rest } = options;

  if (authenticated) {
    useAuthStore.setState({
      username: 'admin',
      role: 'ADMIN',
      hydrated: true,
    });
  } else {
    useAuthStore.setState({
      username: null,
      role: null,
      hydrated: true,
    });
  }

  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={initialEntries ?? [route]}>{children}</MemoryRouter>
      </QueryClientProvider>
    );
  }

  return { ...render(ui, { wrapper: Wrapper, ...rest }) };
}

import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClientProvider } from '@tanstack/react-query';
import { AppRouter } from './router';
import { queryClient } from './lib/query-client';
// OD v2 字体子集(Sprint 0 1.19/1.20 落地):Fraunces 衬线 + Inter Tight 正文 + Geist Mono 等宽
// 引入 @fontsource/* 包的 index.css,各 weight 字体文件按需打包
import '@fontsource/fraunces/400.css';
import '@fontsource/fraunces/600.css';
import '@fontsource/inter-tight/400.css';
import '@fontsource/inter-tight/500.css';
import '@fontsource/inter-tight/600.css';
import '@fontsource/geist-mono/400.css';
import './index.css';

const root = document.getElementById('root');
if (!root) {
  throw new Error('Root element not found');
}

createRoot(root).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AppRouter />
    </QueryClientProvider>
  </StrictMode>,
);

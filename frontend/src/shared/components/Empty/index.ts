// Shared `<empty />` component TypeScript type re-exports.
// The runtime lives in `index.js` (WeChat mini-program component).
export interface EmptyProps {
  message: string;
  retryable?: boolean;
  icon?: string;
  retryText?: string;
}

export const EMPTY_DEFAULT_MESSAGE = '暂无数据';

import '@testing-library/jest-dom';

// jsdom chưa có Web Crypto: temporaryPassword() cần crypto.getRandomValues (trình duyệt thật có sẵn).
if (!window.crypto?.getRandomValues) {
  // eslint-disable-next-line global-require
  Object.defineProperty(window, 'crypto', { value: require('crypto').webcrypto, configurable: true });
}

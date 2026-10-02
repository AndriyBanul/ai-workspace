import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

window.matchMedia = window.matchMedia || (() => ({ matches: false, addListener() {}, removeListener() {}, addEventListener() {}, removeEventListener() {} }));
globalThis.ResizeObserver = globalThis.ResizeObserver || class { observe() {} unobserve() {} disconnect() {} };
Object.defineProperty(document, 'fonts', { configurable: true, value: { addEventListener() {}, removeEventListener() {} } });

afterEach(() => {
  cleanup();
  localStorage.clear();
  window.history.replaceState(null, '', window.location.pathname + window.location.search);
});

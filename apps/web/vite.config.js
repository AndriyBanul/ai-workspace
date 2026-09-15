import { defineConfig } from 'vite';

export default defineConfig({
  server: { port: 5173, proxy: { '/api': 'http://127.0.0.1:8080', '/openapi.yaml': 'http://127.0.0.1:8080', '/swagger-ui.html': 'http://127.0.0.1:8080' } },
  build: { outDir: 'dist', sourcemap: false },
  test: { environment: 'jsdom', globals: true, setupFiles: './src/test-setup.js' },
});

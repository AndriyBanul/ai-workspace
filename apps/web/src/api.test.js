import { describe, expect, it } from 'vitest';
import { basicAuth, safeUrl, validateFiles } from './api.js';

describe('api helpers', () => {
  it('builds basic auth and accepts supported files', () => {
    expect(basicAuth('a@b.test', 'secret')).toMatch(/^Basic /);
    expect(validateFiles({ document: { name: 'report.docx', size: 100 } })).toHaveLength(1);
  });
  it('rejects unsupported or oversized files', () => {
    expect(() => validateFiles({ document: { name: 'report.exe', size: 100 } })).toThrow(/not a supported/);
    expect(() => validateFiles({ document: { name: 'big.pdf', size: 26 * 1024 * 1024 } })).toThrow(/25 MB/);
  });
  it('only permits safe web URLs', () => {
    expect(safeUrl('https://example.com')).toBe('https://example.com/');
    expect(safeUrl('javascript:alert(1)')).toBeUndefined();
  });
});

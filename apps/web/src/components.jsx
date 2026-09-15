import React, { useState } from 'react';

export function Icon({ name = 'grid', size = 20 }) {
  const paths = { grid: 'M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z', library: 'M4 3h4v18H4zM11 3h4v18h-4zM18 4l3 16', ask: 'M4 4h16v12H9l-5 4z', studio: 'm12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5z', activity: 'M3 12h4l3-8 4 16 3-8h4', arrow: 'M5 12h14m-6-6 6 6-6 6', plus: 'M12 5v14M5 12h14', file: 'M5 3h9l5 5v13H5zM14 3v6h5', upload: 'M12 16V3m-5 5 5-5 5 5M4 15v6h16v-6', search: 'M21 21l-6-6M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0', user: 'M4 21v-3a8 8 0 0 1 16 0v3M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0', close: 'm6 6 12 12M6 18 18 6' };
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={paths[name] || paths.file}/></svg>;
}
export function Notice({ error }) { return error ? <div role="alert" className="notice">{error.message || error}{error.code && <small>{error.code}</small>}</div> : null; }
export function Badge({ status }) { return <span className={`badge ${['FAILED', 'PARTIALLY_FAILED'].includes(status) ? 'bad' : ['PROCESSED', 'COMPLETED'].includes(status) ? 'good' : ''}`}>{status?.replaceAll('_', ' ')}</span>; }
export function Empty({ title, children }) { return <div className="empty"><div className="empty-icon"><Icon name="library" size={28}/></div><h3>{title}</h3><p>{children}</p></div>; }
export function useAction() {
  const [busy, setBusy] = useState(false), [error, setError] = useState(null);
  const run = async action => { setBusy(true); setError(null); try { return await action(); } catch (e) { if (e.name !== 'AbortError') setError(e); } finally { setBusy(false); } };
  return { busy, error, run };
}
export function Submit({ busy, children, ...props }) { return <button className="primary" disabled={busy} {...props}>{busy ? <><span className="spinner"/>Working…</> : children}</button>; }
export const size = bytes => bytes < 1024 ? `${bytes} B` : bytes < 1024 ** 2 ? `${(bytes / 1024).toFixed(1)} KB` : `${(bytes / 1024 ** 2).toFixed(1)} MB`;
export const date = value => value ? new Date(value).toLocaleString() : '—';

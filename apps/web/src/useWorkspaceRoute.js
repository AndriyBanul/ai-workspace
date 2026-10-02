import { useCallback, useEffect, useState } from 'react';

const views = new Set(['library', 'ask', 'studio', 'activity']);

export function readWorkspaceRoute() {
  const match = /^#\/workspaces\/([^/]+)\/(library|ask|studio|activity)$/.exec(window.location.hash);
  if (match) {
    try { return { workspaceId: decodeURIComponent(match[1]), view: match[2] }; }
    catch { /* Invalid external hash falls back to the workspace list. */ }
  }
  const empty = /^#\/(library|ask|studio|activity)$/.exec(window.location.hash);
  return { workspaceId: '', view: empty?.[1] || 'library' };
}

export function useWorkspaceRoute() {
  const [route, setRoute] = useState(readWorkspaceRoute);
  useEffect(() => {
    const sync = () => setRoute(readWorkspaceRoute());
    window.addEventListener('popstate', sync);
    window.addEventListener('hashchange', sync);
    return () => {
      window.removeEventListener('popstate', sync);
      window.removeEventListener('hashchange', sync);
    };
  }, []);

  /** @type {(workspaceId: string, view: string, replace?: boolean) => void} */
  const navigate = useCallback((workspaceId, view, replace = false) => {
    const targetView = views.has(view) ? view : 'library';
    const hash = workspaceId
      ? `#/workspaces/${encodeURIComponent(workspaceId)}/${targetView}`
      : `#/${targetView}`;
    window.history[replace ? 'replaceState' : 'pushState'](null, '', hash);
    setRoute({ workspaceId: workspaceId || '', view: targetView });
  }, []);
  return { ...route, navigate };
}

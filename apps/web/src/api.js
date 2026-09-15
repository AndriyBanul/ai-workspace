export class ApiError extends Error {
  constructor(message, status, code) { super(message); this.status = status; this.code = code; }
}

export function basicAuth(email, password) {
  return `Basic ${btoa(String.fromCharCode(...new TextEncoder().encode(`${email}:${password}`)))}`;
}

export function createApi(authorization) {
  return async (path, { json, body, binary = false, ...options } = {}) => {
    const headers = { 'X-Requested-With': 'XMLHttpRequest', ...options.headers };
    if (authorization) headers.Authorization = authorization;
    if (json !== undefined) { headers['Content-Type'] = 'application/json'; body = JSON.stringify(json); }
    let response;
    try { response = await fetch(`/api/v1${path}`, { ...options, headers, body }); }
    catch (error) {
      if (error.name === 'AbortError') throw error;
      throw new ApiError('Cannot reach the server. Check your connection and try again.', 0);
    }
    if (!response.ok) {
      let error;
      try { error = await response.json(); } catch { /* A proxy may return HTML. */ }
      throw new ApiError(error?.detail || (response.status === 401 ? 'Sign-in failed or your session is no longer authorized.' : `Request failed (${response.status}).`), response.status, error?.code);
    }
    if (response.status === 204) return null;
    return binary ? response.blob() : response.json();
  };
}

export const formats = {
  document: { title: 'Document', accept: '.txt,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx', hint: 'PDF, Word, Excel, PowerPoint, TXT', endpoint: '/documents/text' },
  audio: { title: 'Audio', accept: '.wav,.mp3,.mp4,.avi', hint: 'WAV, MP3, MP4, AVI', endpoint: '/audio/transcriptions' },
  image: { title: 'Image', accept: '.jpg,.jpeg,.png,.webp', hint: 'JPG, PNG, WebP', endpoint: '/images/descriptions' },
  video: { title: 'Video', accept: '.mp4,.mov,.webm,.mpeg,.mpg,.avi', hint: 'MP4, MOV, WebM, MPEG, AVI', endpoint: '/videos/descriptions' },
};

export function validateFiles(files) {
  const entries = Object.entries(files).filter(([, file]) => file);
  if (!entries.length) throw new Error('Choose at least one file.');
  let total = 0;
  for (const [type, file] of entries) {
    if (!file.size) throw new Error(`${file.name} is empty.`);
    if (!formats[type].accept.split(',').some(ext => file.name.toLowerCase().endsWith(ext))) throw new Error(`${file.name} is not a supported ${type} format.`);
    total += file.size;
  }
  if (total >= 25 * 1024 * 1024) throw new Error('Choose files totaling less than 25 MB per submission.');
  return entries;
}

export function safeUrl(value) { try { const url = new URL(value); return ['https:', 'http:'].includes(url.protocol) ? url.href : undefined; } catch { return undefined; } }
export const terminal = new Set(['COMPLETED', 'PARTIALLY_FAILED', 'FAILED']);

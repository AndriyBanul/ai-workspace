/** @typedef {{ detail?: string, code?: string }} ApiErrorPayload */

export class ApiError extends Error {
  /**
   * @param {string} message
   * @param {number} status
   * @param {string | undefined} [code]
   * @param {number | undefined} [retryAfterSeconds]
   */
  constructor(message, status, code, retryAfterSeconds) {
    super(message);
    this.status = status;
    this.code = code;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

/** @param {string} email @param {string} password */
export function basicAuth(email, password) {
  return `Basic ${btoa(String.fromCharCode(...new TextEncoder().encode(`${email}:${password}`)))}`;
}

/**
 * @param {string | undefined} authorization
 * @param {{ onUnauthorized?: () => void }} [configuration]
 */
export function createApi(authorization, { onUnauthorized } = {}) {
  /** @param {string} path @param {RequestInit & { json?: unknown, binary?: boolean }} [requestOptions] */
  return async (path, { json, body, binary = false, ...options } = {}) => {
    const headers = new Headers(options.headers);
    headers.set('X-Requested-With', 'XMLHttpRequest');
    if (authorization) headers.set('Authorization', authorization);
    if (json !== undefined) { headers.set('Content-Type', 'application/json'); body = JSON.stringify(json); }
    let response;
    try { response = await fetch(`/api/v1${path}`, { cache: 'no-store', ...options, headers, body }); }
    catch (error) {
      if (error instanceof Error && error.name === 'AbortError') throw error;
      throw new ApiError('Cannot reach the server. Check your connection and try again.', 0);
    }
    if (!response.ok) {
      /** @type {ApiErrorPayload | undefined} */
      let error;
      try { error = await response.json(); } catch { /* A proxy may return HTML. */ }
      if (response.status === 401 && authorization) onUnauthorized?.();
      const retryAfter = Number.parseInt(response.headers.get('Retry-After') || '', 10);
      const message = error?.detail || (response.status === 401
        ? 'Sign-in failed or your session is no longer authorized.'
        : response.status === 429
          ? 'Too many requests. Wait a moment and try again.'
          : `Request failed (${response.status}).`);
      const retryHint = response.status === 429 && Number.isFinite(retryAfter)
        ? ` Try again in about ${Math.max(1, Math.ceil(retryAfter / 60))} minute(s).` : '';
      throw new ApiError(message + retryHint, response.status, error?.code,
        Number.isFinite(retryAfter) ? retryAfter : undefined);
    }
    if (response.status === 204) return null;
    return binary ? response.blob() : response.json();
  };
}

/** @type {Record<string, { title: string, accept: string, hint: string, endpoint: string }>} */
export const formats = {
  document: { title: 'Document', accept: '.txt,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx', hint: 'PDF, Word, Excel, PowerPoint, TXT', endpoint: '/documents/text' },
  audio: { title: 'Audio', accept: '.wav,.mp3,.mp4,.avi', hint: 'WAV, MP3, MP4, AVI', endpoint: '/audio/transcriptions' },
  image: { title: 'Image', accept: '.jpg,.jpeg,.png,.webp', hint: 'JPG, PNG, WebP', endpoint: '/images/descriptions' },
  video: { title: 'Video', accept: '.mp4,.mov,.webm,.mpeg,.mpg,.avi', hint: 'MP4, MOV, WebM, MPEG, AVI', endpoint: '/videos/descriptions' },
};

/** @param {Record<string, File | null | undefined>} files */
export function validateFiles(files) {
  const entries = Object.entries(files).filter(([, file]) => file);
  if (!entries.length) throw new Error('Choose at least one file.');
  let total = 0;
  for (const [type, file] of entries) {
    if (!file) continue;
    const format = formats[type];
    if (!format) throw new Error(`Unsupported source type: ${type}.`);
    if (!file.size) throw new Error(`${file.name} is empty.`);
    if (!format.accept.split(',').some(ext => file.name.toLowerCase().endsWith(ext))) throw new Error(`${file.name} is not a supported ${type} format.`);
    total += file.size;
  }
  if (total >= 25 * 1024 * 1024) throw new Error('Choose files totaling less than 25 MB per submission.');
  return entries;
}

/** @param {string | null | undefined} value */
export function safeUrl(value) { if (!value) return undefined; try { const url = new URL(value); return ['https:', 'http:'].includes(url.protocol) ? url.href : undefined; } catch { return undefined; } }
export const terminal = new Set(['COMPLETED', 'PARTIALLY_FAILED', 'FAILED']);

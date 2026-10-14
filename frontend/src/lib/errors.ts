import type { TFunction } from 'i18next';
import { isApiError } from './api';

/** Map an unknown error to a localised message using `errors.<code>` keys. */
export function errorMessage(t: TFunction, e: unknown): string {
  if (isApiError(e)) {
    const key = `errors.${e.code}`;
    const translated = t(key, { defaultValue: '', retryAfterSeconds: e.retryAfterSeconds ?? 0 });
    if (translated) return translated;
    return e.message || t('errors.internal');
  }
  if (e instanceof Error && e.message) return e.message;
  return t('errors.internal');
}

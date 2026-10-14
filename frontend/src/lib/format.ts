import i18n from '@/i18n';

function locale(): string {
  return i18n.language === 'hy' ? 'hy-AM' : 'en-GB';
}

/** Formats an ISO date (YYYY-MM-DD) or timestamp for display in the active language. */
export function formatDate(value: string | null | undefined, opts: Intl.DateTimeFormatOptions = { dateStyle: 'medium' }): string {
  if (!value) return '—';
  const d = /^\d{4}-\d{2}-\d{2}$/.test(value) ? new Date(value + 'T00:00:00') : new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return new Intl.DateTimeFormat(locale(), opts).format(d);
}

export function formatDateTime(value: string | null | undefined): string {
  return formatDate(value, { dateStyle: 'medium', timeStyle: 'short' });
}

export function formatNumber(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—';
  return new Intl.NumberFormat(locale()).format(value);
}

/** 120500 -> "120,500 km" / "120 500 կմ" */
export function formatKm(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—';
  return `${formatNumber(value)} ${i18n.t('units.km')}`;
}

/** 45000 -> "45,000 AMD" (currency formatted for the active language, no decimals). */
export function formatAmount(value: number | null | undefined, currency = 'AMD'): string {
  if (value === null || value === undefined) return '—';
  try {
    return new Intl.NumberFormat(locale(), { style: 'currency', currency, maximumFractionDigits: 0 }).format(value);
  } catch {
    return `${formatNumber(value)} ${currency}`;
  }
}

/** Formats a timestamp in a specific IANA time zone, e.g. for campaign schedules. */
export function formatDateTimeInZone(value: string | null | undefined, timeZone: string): string {
  if (!value) return '—';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  try {
    return new Intl.DateTimeFormat(locale(), { dateStyle: 'medium', timeStyle: 'short', timeZone }).format(d);
  } catch {
    return formatDateTime(value);
  }
}

export function todayIso(): string {
  const d = new Date();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

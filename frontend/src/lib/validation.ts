import { z } from 'zod';

/** 17 characters, A–Z and 0–9, never I, O or Q. */
export const VIN_PATTERN = /^[A-HJ-NPR-Z0-9]{17}$/;

export function normalizeVin(raw: string): string {
  return raw.trim().toUpperCase().replace(/\s+/g, '');
}

/** '' -> null, otherwise a valid normalised VIN. */
export const vinSchema = z
  .string()
  .transform(normalizeVin)
  .refine((v) => v === '' || VIN_PATTERN.test(v), 'validation.vin')
  .transform((v) => (v === '' ? null : v));

/** Accepts local Armenian and international formats; the backend normalises to E.164. */
const PHONE_LOOSE = /^\+?[0-9][0-9\s()-]{5,19}$/;

export const phoneSchema = z
  .string()
  .trim()
  .min(1, 'validation.required')
  .refine((s) => PHONE_LOOSE.test(s), 'validation.phone');

export const requiredText = (max: number) =>
  z.string().trim().min(1, 'validation.required').max(max, `validation.maxLength|{"count":${max}}`);

/** '' -> null */
export const optionalText = (max: number) =>
  z
    .string()
    .trim()
    .max(max, `validation.maxLength|{"count":${max}}`)
    .transform((s) => (s === '' ? null : s));

const INT = /^\d+$/;

/** Required non-negative integer typed as a string in the input. */
export const requiredIntString = z
  .string()
  .trim()
  .min(1, 'validation.required')
  .refine((s) => INT.test(s), 'validation.mileage')
  .transform(Number);

/** Optional non-negative integer typed as a string; '' -> null. */
export const optionalIntString = z
  .string()
  .trim()
  .refine((s) => s === '' || INT.test(s), 'validation.mileage')
  .transform((s) => (s === '' ? null : Number(s)));

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

export const requiredPastOrTodayDate = z
  .string()
  .trim()
  .regex(ISO_DATE, 'validation.date')
  .refine((s) => s <= localIsoToday(), 'validation.dateFuture');

export const optionalDate = z
  .string()
  .trim()
  .refine((s) => s === '' || ISO_DATE.test(s), 'validation.date')
  .transform((s) => (s === '' ? null : s));

export function localIsoToday(): string {
  const d = new Date();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

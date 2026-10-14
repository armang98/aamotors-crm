import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import hy from './hy.json';
import en from './en.json';
import { safeStorageGet, safeStorageSet } from '@/lib/utils';

export const SUPPORTED_LANGUAGES = ['hy', 'en'] as const;
export type Language = (typeof SUPPORTED_LANGUAGES)[number];
export const DEFAULT_LANGUAGE: Language = 'hy';
const STORAGE_KEY = 'aam.lang';

export function isLanguage(v: unknown): v is Language {
  return typeof v === 'string' && (SUPPORTED_LANGUAGES as readonly string[]).includes(v);
}

export function storedLanguage(): Language {
  const v = safeStorageGet(STORAGE_KEY);
  return isLanguage(v) ? v : DEFAULT_LANGUAGE;
}

/** BCP-47 tag for the <html lang> attribute. */
export function htmlLang(l: Language): string {
  return l === 'hy' ? 'hy-AM' : 'en';
}

void i18n.use(initReactI18next).init({
  resources: {
    hy: { translation: hy },
    en: { translation: en },
  },
  lng: storedLanguage(),
  fallbackLng: 'en',
  supportedLngs: [...SUPPORTED_LANGUAGES],
  interpolation: { escapeValue: false },
  returnNull: false,
});

function applyHtmlLang(l: Language) {
  if (typeof document !== 'undefined') document.documentElement.lang = htmlLang(l);
}
applyHtmlLang(storedLanguage());

i18n.on('languageChanged', (lng) => {
  if (isLanguage(lng)) {
    safeStorageSet(STORAGE_KEY, lng);
    applyHtmlLang(lng);
  }
});

export async function setLanguage(l: Language) {
  if (i18n.language !== l) await i18n.changeLanguage(l);
}

export default i18n;

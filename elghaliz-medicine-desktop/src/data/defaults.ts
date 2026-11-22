import type { Category } from './types';

// Default seed medical categories (from DefaultMedicalCategories in MedicalModels.kt).
export const DEFAULT_CATEGORIES: Category[] = [
  {
    id: 'cat_nutrition',
    name: 'Nutrition',
    description: 'كورس التغذية الطبية والعلاجية (Clinical & Medical Nutrition)',
    createdByUid: 'system_elghaliz',
    createdByName: 'د. الغليظ',
    visibility: 'published',
  },
  {
    id: 'cat_microbiology',
    name: 'Microbiology',
    description: 'كورس الأحياء الدقيقة الطبية والمناعة (Medical Microbiology)',
    createdByUid: 'system_elghaliz',
    createdByName: 'د. الغليظ',
    visibility: 'published',
  },
  {
    id: 'cat_pharmacology',
    name: 'Pharmacology',
    description: 'كورس علم الأدوية السريري والعلاجات (Clinical Pharmacology)',
    createdByUid: 'system_elghaliz',
    createdByName: 'د. الغليظ',
    visibility: 'published',
  },
  {
    id: 'cat_pathology',
    name: 'Pathology',
    description: 'كورس علم الأمراض العام والخاص (General & Systemic Pathology)',
    createdByUid: 'system_elghaliz',
    createdByName: 'د. الغليظ',
    visibility: 'published',
  },
  {
    id: 'cat_genetics',
    name: 'Genetics',
    description: 'كورس علم الوراثة الطبية والجزيئية (Medical Genetics)',
    createdByUid: 'system_elghaliz',
    createdByName: 'د. الغليظ',
    visibility: 'published',
  },
];

// Cryptography helpers, ported from SecurityHashUtil (SHA-256 hex via Web Crypto).
export async function sha256Hex(raw: string): Promise<string> {
  const normalized = raw.trim().toLowerCase();
  const data = new TextEncoder().encode(normalized);
  const digest = await crypto.subtle.digest('SHA-256', data);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

export function emailToSafeDocId(raw: string, hash: string): string {
  return `adm_${hash.slice(0, 28)}`;
}

export function formatUserIdCode(uid: string): string {
  if (!uid) return 'ELG-000000';
  const clean = uid.toUpperCase().replace(/[^A-Z0-9]/g, '');
  const suffix = clean.slice(-8).padStart(8, '0');
  return `ELG-${suffix}`;
}

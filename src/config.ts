import type { Category } from './types/models';

export const FIREBASE_CONFIG = {
  apiKey: 'AIzaSyDBbJvLAM-v4dwizcsHslVBUeC-M_PZ8QI',
  authDomain: 'gen-lang-client-0295730039.firebaseapp.com',
  projectId: 'gen-lang-client-0295730039',
  storageBucket: 'gen-lang-client-0295730039.firebasestorage.app',
  messagingSenderId: '933895225355',
  appId: '1:933895225355:android:9e8a1f25102b4427af470e',
};

export const FIRESTORE_DATABASE_ID =
  'ai-studio-android-elghaliz-72f133e1-54f8-4875-8f82-71b60492c2b8';

export const PRIMARY_ADMIN_EMAIL = 'abdallahxh909@gmail.com';

export const CENTRAL_APPS_SCRIPT_URL =
  'https://script.google.com/macros/s/AKfycbz7zLWO49No_RdNVPB2FKX5Bw-RjmSVjvd-6t-l3LK4ecQy5hY9daAgi4XLXfvKWyL2Hg/exec';

export const DRIVE_FILES_API_ENDPOINT = 'https://www.googleapis.com/drive/v3/files';
export const DRIVE_RESUMABLE_UPLOAD_ENDPOINT =
  'https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable';

// 2 MB resumable upload chunk (multiple of 256 KB as required by Google Drive API)
export const RESUMABLE_UPLOAD_CHUNK_BYTES = 2 * 1024 * 1024;

// 4 KB Header Lock (first 4096 bytes encrypted with SHA-256 XOR)
export const VAULT_HEADER_LOCK_BYTES = 4096;
export const VAULT_KEY_PREFIX = 'ElghalizMedicalVault_AES256_Key_v1';

export const PLAYER_SPEEDS = [1, 1.25, 1.5, 2] as const;

export const APP_VERSION = '1.0.0-pwa';

export const DEFAULT_MEDICAL_CATEGORIES: Category[] = [
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

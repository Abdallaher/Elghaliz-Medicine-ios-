// Loads live data from Firestore into the shared store (only invoked in LIVE mode).
import { collection, getDocs, query, where, type DocumentData } from 'firebase/firestore';
import { fbDb } from '../firebase';
import { useDataStore } from '../data/dataStore';
import type { Category, ExamItem, Lecture } from '../data/types';

function ts(v: unknown): number | null {
  const x = v as { toMillis?: () => number } | null;
  return x && typeof x.toMillis === 'function' ? x.toMillis() : null;
}

export async function loadLiveData(_uid: string): Promise<void> {
  if (!fbDb) return;
  const hydrate = useDataStore.getState().hydrate;
  try {
    const [catsSnap, lecsSnap, exsSnap] = await Promise.all([
      getDocs(query(collection(fbDb, 'categories'), where('visibility', '==', 'published'))),
      getDocs(query(collection(fbDb, 'lectures'), where('visibility', '==', 'published'))),
      getDocs(query(collection(fbDb, 'exams'), where('visibility', '==', 'published'))),
    ]);

    const categories: Category[] = catsSnap.docs.map((d) => {
      const x = d.data() as DocumentData;
      return {
        id: d.id,
        name: x.name ?? '',
        description: x.description ?? '',
        createdByUid: x.createdByUid ?? '',
        createdByName: x.createdByName ?? '',
        visibility: x.visibility ?? 'published',
        createdAt: ts(x.createdAt),
      };
    });

    const lectures: Lecture[] = lecsSnap.docs.map((d) => {
      const x = d.data() as DocumentData;
      return {
        id: d.id,
        title: x.title ?? '',
        description: x.description ?? '',
        categoryId: x.categoryId ?? '',
        categoryName: x.categoryName ?? '',
        uploadedByUid: x.uploadedByUid ?? '',
        doctorName: x.doctorName ?? '',
        videoSourceUri: x.videoSourceUri ?? '',
        durationSeconds: x.durationSeconds ?? 120,
        fileSizeMb: x.fileSizeMb ?? 5,
        chunkCount: x.chunkCount ?? 0,
        isRecordedMeeting: !!x.isRecordedMeeting,
        visibility: x.visibility ?? 'published',
        createdAt: ts(x.createdAt),
      };
    });

    const exams: ExamItem[] = exsSnap.docs.map((d) => {
      const x = d.data() as DocumentData;
      return {
        id: d.id,
        title: x.title ?? '',
        description: x.description ?? '',
        categoryId: x.categoryId ?? '',
        categoryName: x.categoryName ?? '',
        uploadedByUid: x.uploadedByUid ?? '',
        doctorName: x.doctorName ?? '',
        pdfFileName: x.pdfFileName ?? '',
        fileSizeKb: x.fileSizeKb ?? 100,
        chunkCount: x.chunkCount ?? 1,
        startTimeMillis: Number(x.startTimeMillis ?? 0),
        endTimeMillis: Number(x.endTimeMillis ?? 0),
        scheduleLabel: x.scheduleLabel ?? '',
        visibility: x.visibility ?? 'published',
        createdAt: ts(x.createdAt),
      };
    });

    hydrate({ categories, lectures, exams });
  } catch (err) {
    // eslint-disable-next-line no-console
    console.error('[liveData] load failed', err);
  }
}

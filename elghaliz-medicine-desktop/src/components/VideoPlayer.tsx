import { useEffect, useRef, useState } from 'react';
import { Download, X } from 'lucide-react';
import type { Lecture } from '../data/types';
import { useDataStore } from '../data/dataStore';
import { useAccess } from '../lib/session';
import { formatSizeMb } from '../lib/format';

const WATERMARK_ANCHORS = [
  { top: '10%', left: '8%' },
  { top: '12%', right: '8%' },
  { bottom: '30%', left: '10%' },
  { bottom: '32%', right: '10%' },
  { top: '46%', left: '44%' },
];

export function VideoPlayer({ lecture, onClose }: { lecture: Lecture; onClose: () => void }) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const { session } = useAccess();
  const userId = session?.user?.uid ?? 'guest';
  const saveProgress = useDataStore((s) => s.saveProgress);
  const startDownload = useDataStore((s) => s.startDownload);
  const downloads = useDataStore((s) => s.data.downloads);
  const downloadTasks = useDataStore((s) => s.downloadTasks);

  const [wmIndex, setWmIndex] = useState(0);
  const lastSave = useRef(0);

  const downloaded = downloads.some((d) => d.lectureId === lecture.id);
  const task = downloadTasks[lecture.id];

  useEffect(() => {
    const t = window.setInterval(() => setWmIndex((i) => (i + 1) % WATERMARK_ANCHORS.length), 5000);
    return () => window.clearInterval(t);
  }, []);

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [onClose]);

  const onTimeUpdate = () => {
    const v = videoRef.current;
    if (!v) return;
    const now = Date.now();
    if (now - lastSave.current < 2000) return;
    lastSave.current = now;
    const completed = v.duration > 10 && v.currentTime >= v.duration - 5;
    saveProgress(userId, lecture.id, completed ? 0 : Math.floor(v.currentTime), completed);
  };

  const anchor = WATERMARK_ANCHORS[wmIndex] as React.CSSProperties;
  const wmText = `${session?.user?.displayName ?? ''} • ${session?.user?.email ?? ''} • DRM-PROTECTED`;

  return (
    <div
      className="player-overlay"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="player-card" onMouseDown={(e) => e.stopPropagation()}>
        <div className="player-head">
          <span className="player-pill">بث محمي مشفر</span>
          <button className="player-close" onClick={onClose} aria-label="إغلاق">
            <X size={20} />
          </button>
        </div>

        <div className="video-wrap">
          <video
            ref={videoRef}
            src={lecture.videoSourceUri}
            controls
            autoPlay
            onTimeUpdate={onTimeUpdate}
            onEnded={() => saveProgress(userId, lecture.id, 0, true)}
            controlsList="nodownload"
          />
          <div className="watermark" style={anchor}>
            {wmText}
          </div>
        </div>

        <div className="player-details">
          <span className="cat-pill" style={{ alignSelf: 'flex-start' }}>
            {lecture.categoryName || 'محاضرة طبية'}
          </span>
          <div className="lecture-title" style={{ fontSize: 17 }}>
            {lecture.title}
          </div>
          <div style={{ fontSize: 13, color: 'var(--dark-text-2)' }}>
            د. {lecture.doctorName || 'Abdallah.surour'} · {formatSizeMb(lecture.fileSizeMb)}
          </div>
          {lecture.description && lecture.description !== 'محاضرة مرئية' && (
            <div style={{ fontSize: 13, color: 'var(--dark-text-2)' }}>{lecture.description}</div>
          )}
          <div style={{ marginTop: 6 }}>
            {downloaded ? (
              <button className="btn btn-soft" disabled style={{ color: 'var(--emerald-primary)' }}>
                تم التنزيل للمشاهدة بدون إنترنت ✓
              </button>
            ) : task && task.status !== 'COMPLETED' && task.status !== 'CANCELLED' ? (
              <button className="btn btn-gold" disabled>
                جاري التنزيل ({task.progressPercent}%)
              </button>
            ) : (
              <button className="btn btn-gold" onClick={() => startDownload(lecture, userId)}>
                <Download size={18} /> تنزيل داخل التطبيق
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

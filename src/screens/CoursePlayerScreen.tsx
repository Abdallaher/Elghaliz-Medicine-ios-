import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowRight,
  BookmarkPlus,
  CheckCircle2,
  Download,
  Lock,
  Maximize,
  Pause,
  Play,
  RotateCcw,
  RotateCw,
  Search,
  ShieldCheck,
  Trash2,
  Unlock,
  AlertTriangle,
  X,
} from 'lucide-react';
import { PLAYER_SPEEDS } from '../config';
import type { Lecture } from '../types/models';
import { useAppStore } from '../store/appStore';
import { resolveOnlinePlaybackUrl } from '../lib/driveVault';
import { openDecryptedOfflineLectureUrl } from '../lib/offlineVault';
import { formatDurationHms, formatLectureCountAr } from '../lib/logic';

const WATERMARK_POSITIONS = [
  'top-3 right-3',
  'top-3 left-3',
  'bottom-14 right-4',
  'bottom-14 left-4',
  'top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2',
];

export const CoursePlayerScreen: React.FC = () => {
  const {
    user,
    isAdminUser,
    activeRole,
    categories,
    lectures,
    openedCourseId,
    initialLectureIdForCourse,
    offlineDownloads,
    downloadTasks,
    playbackPositions,
    completedLectureIds,
    notes,
    closeCoursePlayer,
    startOfflineDownload,
    pauseOfflineDownload,
    resumeOfflineDownload,
    cancelOfflineDownload,
    removeOfflineDownload,
    deleteLecture,
    saveLectureProgress,
    addLectureNote,
    deleteLectureNote,
    pushToast,
  } = useAppStore();

  const isAdminView = isAdminUser && activeRole === 'ADMIN';
  const course = useMemo(
    () => categories.find((c) => c.id === openedCourseId) ?? null,
    [categories, openedCourseId],
  );

  const courseLectures = useMemo(
    () =>
      lectures
        .filter((l) => l.categoryId === openedCourseId)
        .sort((a, b) => (a.createdAt ?? 0) - (b.createdAt ?? 0)),
    [lectures, openedCourseId],
  );

  const [searchQuery, setSearchQuery] = useState('');
  const [innerTab, setInnerTab] = useState<'LECTURES' | 'NOTES'>('LECTURES');
  const [selectedLectureId, setSelectedLectureId] = useState<string | null>(
    initialLectureIdForCourse ?? courseLectures[0]?.id ?? null,
  );

  // Player State
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const playerContainerRef = useRef<HTMLDivElement | null>(null);
  const activeObjectUrlRef = useRef<string | null>(null);

  const [resolvedSrc, setResolvedSrc] = useState<string>('');
  const [isOfflineSource, setIsOfflineSource] = useState(false);
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [playbackRate, setPlaybackRate] = useState<number>(1);
  const [controlsLocked, setControlsLocked] = useState(false);
  const [watermarkSlot, setWatermarkSlot] = useState(0);
  const [noteInput, setNoteInput] = useState('');
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null);

  // Keep selectedLectureId valid when courseLectures change
  useEffect(() => {
    if (initialLectureIdForCourse && courseLectures.some((l) => l.id === initialLectureIdForCourse)) {
      setSelectedLectureId(initialLectureIdForCourse);
    } else if (!selectedLectureId || !courseLectures.some((l) => l.id === selectedLectureId)) {
      setSelectedLectureId(courseLectures[0]?.id ?? null);
    }
  }, [courseLectures, initialLectureIdForCourse, selectedLectureId]);

  const activeLecture: Lecture | null = useMemo(
    () => courseLectures.find((l) => l.id === selectedLectureId) ?? null,
    [courseLectures, selectedLectureId],
  );

  const filteredLectures = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return courseLectures;
    return courseLectures.filter(
      (l) => l.title.toLowerCase().includes(q) || l.description.toLowerCase().includes(q),
    );
  }, [courseLectures, searchQuery]);

  const lectureNotes = useMemo(
    () =>
      notes
        .filter((n) => n.lectureId === activeLecture?.id)
        .sort((a, b) => a.second - b.second),
    [notes, activeLecture?.id],
  );

  // Moving student email watermark shifting every 6 seconds
  useEffect(() => {
    const timer = window.setInterval(() => {
      setWatermarkSlot((prev) => (prev + 1) % WATERMARK_POSITIONS.length);
    }, 6000);
    return () => window.clearInterval(timer);
  }, []);

  // Resolve playback source: prefer encrypted offline IndexedDB (RAM 4KB decrypt) if present, else direct 5TB Drive stream
  useEffect(() => {
    let cancelled = false;

    async function loadSource() {
      if (activeObjectUrlRef.current) {
        URL.revokeObjectURL(activeObjectUrlRef.current);
        activeObjectUrlRef.current = null;
      }
      if (!activeLecture) {
        setResolvedSrc('');
        setIsOfflineSource(false);
        return;
      }

      const offlineMatch = offlineDownloads.find((d) => d.lectureId === activeLecture.id);
      if (offlineMatch) {
        try {
          const unlocked = await openDecryptedOfflineLectureUrl(activeLecture.id);
          if (!cancelled && unlocked) {
            activeObjectUrlRef.current = unlocked.objectUrl;
            setResolvedSrc(unlocked.objectUrl);
            setIsOfflineSource(true);
            return;
          }
        } catch {
          // Fallback to online stream
        }
      }

      try {
        const onlineUrl = await resolveOnlinePlaybackUrl(activeLecture.videoSourceUri);
        if (!cancelled) {
          setResolvedSrc(onlineUrl);
          setIsOfflineSource(false);
        }
      } catch {
        if (!cancelled) {
          setResolvedSrc(activeLecture.videoSourceUri);
          setIsOfflineSource(false);
        }
      }
    }

    void loadSource();
    return () => {
      cancelled = true;
    };
  }, [activeLecture?.id, activeLecture?.videoSourceUri, offlineDownloads]);

  // Revoke object URL on unmount
  useEffect(() => {
    return () => {
      if (activeObjectUrlRef.current) {
        URL.revokeObjectURL(activeObjectUrlRef.current);
      }
    };
  }, []);

  // Restore saved playback position when metadata loads
  const handleLoadedMetadata = () => {
    const video = videoRef.current;
    if (!video || !activeLecture) return;
    const total = Number.isFinite(video.duration) ? video.duration : activeLecture.durationSeconds;
    setDuration(total);
    video.playbackRate = playbackRate;
    const saved = playbackPositions[activeLecture.id] ?? 0;
    if (saved > 2 && total > 0 && saved < total - 5) {
      video.currentTime = saved;
      setCurrentTime(saved);
    } else {
      setCurrentTime(0);
    }
  };

  // Persist watch progress every 5 seconds
  const lastSavedSecRef = useRef<number>(0);
  const handleTimeUpdate = () => {
    const video = videoRef.current;
    if (!video || !activeLecture) return;
    const sec = video.currentTime;
    setCurrentTime(sec);
    const total = Number.isFinite(video.duration) ? video.duration : activeLecture.durationSeconds;
    if (Math.abs(sec - lastSavedSecRef.current) >= 5 || (total > 0 && sec / total >= 0.9)) {
      lastSavedSecRef.current = sec;
      void saveLectureProgress(activeLecture.id, sec, total);
    }
  };

  const togglePlayPause = () => {
    const video = videoRef.current;
    if (!video) return;
    if (video.paused) {
      void video.play().catch(() => {
        pushToast('اضغط زر التشغيل لبدء المحاضرة', 'info');
      });
    } else {
      video.pause();
    }
  };

  const seekBy = (deltaSec: number) => {
    const video = videoRef.current;
    if (!video) return;
    const maxDur = Number.isFinite(video.duration) ? video.duration : duration || 0;
    const next = Math.max(0, Math.min(maxDur || 99999, video.currentTime + deltaSec));
    video.currentTime = next;
    setCurrentTime(next);
  };

  const seekTo = (targetSec: number) => {
    const video = videoRef.current;
    if (!video) return;
    video.currentTime = Math.max(0, targetSec);
    setCurrentTime(targetSec);
  };

  const handleSpeedChange = (speed: number) => {
    setPlaybackRate(speed);
    if (videoRef.current) {
      videoRef.current.playbackRate = speed;
    }
  };

  const handleFullscreen = () => {
    const container = playerContainerRef.current;
    const video = videoRef.current as (HTMLVideoElement & { webkitEnterFullscreen?: () => void }) | null;
    if (document.fullscreenElement) {
      void document.exitFullscreen();
    } else if (container?.requestFullscreen) {
      void container.requestFullscreen();
    } else if (video?.webkitEnterFullscreen) {
      video.webkitEnterFullscreen();
    }
  };

  const handleAddNote = (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeLecture || !noteInput.trim()) return;
    addLectureNote(activeLecture.id, Math.floor(currentTime), noteInput);
    setNoteInput('');
  };

  const activeDownloadsList = Object.values(downloadTasks);

  return (
    <div className="min-h-dvh bg-[#F7F3EA] dark:bg-[#0A2A20] text-[#0F241D] dark:text-[#F4EFE4] pb-10">
      {/* Top Emerald Bar */}
      <header className="sticky top-0 z-30 bg-[#0F3D2E] text-white px-4 pt-[max(0.85rem,env(safe-area-inset-top))] pb-3.5 shadow-md">
        <div className="mx-auto max-w-5xl flex items-center gap-3">
          <button
            type="button"
            onClick={closeCoursePlayer}
            aria-label="العودة إلى الكورسات"
            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl bg-white/10 hover:bg-white/20 text-[#FFFDF8] transition cursor-pointer"
          >
            <ArrowRight className="h-5 w-5" />
          </button>

          <div className="min-w-0 flex-1">
            <h1 className="text-base sm:text-lg font-black truncate">
              {course?.name || 'الكورس الطبي'}
            </h1>
            <p className="text-[11px] text-[#E9F0E8]/80">
              {formatLectureCountAr(courseLectures.length)}
            </p>
          </div>

          {isOfflineSource && (
            <span className="inline-flex items-center gap-1 rounded-xl bg-[#C9A24D] px-2.5 py-1 text-[11px] font-black text-[#0F241D]">
              <ShieldCheck className="h-3.5 w-3.5" />
              <span>تشغيل أوفلاين</span>
            </span>
          )}
        </div>
      </header>

      <div className="mx-auto max-w-5xl px-4 pt-4 space-y-4">
        {/* Video Player Card (iOS Safari playsInline compatible) */}
        <section className="rounded-[26px] overflow-hidden bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] shadow-lg">
          <div
            ref={playerContainerRef}
            className="relative aspect-video w-full bg-[#061812] select-none overflow-hidden"
            onContextMenu={(e) => e.preventDefault()}
          >
            {activeLecture && resolvedSrc ? (
              <video
                ref={videoRef}
                key={activeLecture.id + (isOfflineSource ? '_off' : '_on')}
                src={resolvedSrc}
                playsInline
                webkit-playsinline="true"
                controlsList="nodownload noremoteplayback"
                disablePictureInPicture
                preload="metadata"
                onLoadedMetadata={handleLoadedMetadata}
                onTimeUpdate={handleTimeUpdate}
                onPlay={() => setIsPlaying(true)}
                onPause={() => setIsPlaying(false)}
                onEnded={() => {
                  setIsPlaying(false);
                  if (activeLecture) {
                    void saveLectureProgress(
                      activeLecture.id,
                      duration || activeLecture.durationSeconds,
                      duration || activeLecture.durationSeconds,
                    );
                  }
                }}
                className="h-full w-full object-contain bg-black"
              />
            ) : (
              <div className="flex h-full w-full flex-col items-center justify-center text-[#E9F0E8]/75 p-6 text-center">
                <Play className="h-12 w-12 text-[#C9A24D] mb-2" />
                <p className="text-sm font-bold">
                  {courseLectures.length === 0
                    ? 'لا توجد محاضرات مرفوعة في هذا الكورس بعد'
                    : 'اختر محاضرة من القائمة بالأسفل لبدء المشاهدة'}
                </p>
              </div>
            )}

            {/* Moving Student Email Watermark — shifts every 6 seconds */}
            {user?.email && (
              <div
                aria-hidden="true"
                className={`pointer-events-none absolute z-20 rounded-lg bg-black/35 px-2.5 py-1 text-[11px] font-bold tracking-wide text-white/75 backdrop-blur-[1px] transition-all duration-700 ${WATERMARK_POSITIONS[watermarkSlot]}`}
              >
                {user.email}
              </div>
            )}

            {/* Lock/Unlock Screen Button inside video corner */}
            <button
              type="button"
              onClick={() => setControlsLocked((v) => !v)}
              aria-label={controlsLocked ? 'فتح قفل الشاشة' : 'قفل أزرار المشغل'}
              className="absolute top-3 left-3 z-30 flex h-9 w-9 items-center justify-center rounded-full bg-black/50 text-[#C9A24D] hover:bg-black/70 transition cursor-pointer"
            >
              {controlsLocked ? <Lock className="h-4 w-4" /> : <Unlock className="h-4 w-4" />}
            </button>
          </div>

          {/* Player Controls Deck */}
          <div className="p-4 space-y-3">
            <div className="flex items-start justify-between gap-2">
              <div className="min-w-0">
                <h2 className="text-sm sm:text-base font-black text-[#0F3D2E] dark:text-[#F4EFE4] leading-snug">
                  {activeLecture?.title || 'اختر محاضرة'}
                </h2>
                {activeLecture && (
                  <p className="text-[11px] text-[#546B61] dark:text-[#A5C0B4] mt-0.5">
                    {activeLecture.doctorName || 'د. الغليظ'} •{' '}
                    {formatDurationHms(duration || activeLecture.durationSeconds)}
                  </p>
                )}
              </div>
              <button
                type="button"
                onClick={handleFullscreen}
                aria-label="ملء الشاشة"
                className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-[#E9F0E8] dark:bg-[#0A2A20] text-[#0F3D2E] dark:text-[#C9A24D] hover:bg-[#dce8da] transition cursor-pointer"
              >
                <Maximize className="h-4 w-4" />
              </button>
            </div>

            {!controlsLocked ? (
              <>
                {/* Seek Bar */}
                <div className="space-y-1">
                  <input
                    type="range"
                    aria-label="شريط تقدم الفيديو"
                    min={0}
                    max={Math.max(1, Math.floor(duration || activeLecture?.durationSeconds || 100))}
                    value={Math.min(
                      Math.floor(currentTime),
                      Math.max(1, Math.floor(duration || activeLecture?.durationSeconds || 100)),
                    )}
                    onChange={(e) => seekTo(Number(e.target.value))}
                    className="w-full accent-[#0F3D2E] dark:accent-[#C9A24D] cursor-pointer"
                  />
                  <div className="flex items-center justify-between text-[11px] font-mono font-bold text-[#546B61] dark:text-[#A5C0B4]">
                    <span>{formatDurationHms(currentTime)}</span>
                    <span>{formatDurationHms(duration || activeLecture?.durationSeconds || 0)}</span>
                  </div>
                </div>

                {/* Primary Transport Row: -10s / Play-Pause / +10s + Speeds */}
                <div className="flex flex-wrap items-center justify-between gap-3 pt-1">
                  <div className="flex items-center gap-2.5">
                    <button
                      type="button"
                      onClick={() => seekBy(-10)}
                      aria-label="تأخير 10 ثوان"
                      className="inline-flex items-center gap-1 rounded-2xl bg-[#E9F0E8] dark:bg-[#0A2A20] px-3 py-2 text-xs font-extrabold text-[#0F3D2E] dark:text-[#F4EFE4] hover:bg-[#dce8da] cursor-pointer transition"
                    >
                      <RotateCcw className="h-3.5 w-3.5" />
                      <span>-10ث</span>
                    </button>

                    <button
                      type="button"
                      onClick={togglePlayPause}
                      aria-label={isPlaying ? 'إيقاف مؤقت' : 'تشغيل المحاضرة'}
                      className="flex h-12 w-12 items-center justify-center rounded-full bg-[#0F3D2E] hover:bg-[#1F6B52] text-[#C9A24D] shadow-md cursor-pointer transition"
                    >
                      {isPlaying ? <Pause className="h-5 w-5" /> : <Play className="h-5 w-5 fill-current" />}
                    </button>

                    <button
                      type="button"
                      onClick={() => seekBy(10)}
                      aria-label="تقديم 10 ثوان"
                      className="inline-flex items-center gap-1 rounded-2xl bg-[#E9F0E8] dark:bg-[#0A2A20] px-3 py-2 text-xs font-extrabold text-[#0F3D2E] dark:text-[#F4EFE4] hover:bg-[#dce8da] cursor-pointer transition"
                    >
                      <span>+10ث</span>
                      <RotateCw className="h-3.5 w-3.5" />
                    </button>
                  </div>

                  {/* Playback Speed Buttons: 1x, 1.25x, 1.5x, 2x */}
                  <div className="flex items-center gap-1 rounded-2xl bg-[#E9F0E8] dark:bg-[#0A2A20] p-1">
                    {PLAYER_SPEEDS.map((spd) => (
                      <button
                        key={spd}
                        type="button"
                        onClick={() => handleSpeedChange(spd)}
                        className={`rounded-xl px-2.5 py-1.5 text-xs font-extrabold transition cursor-pointer ${
                          playbackRate === spd
                            ? 'bg-[#0F3D2E] text-[#C9A24D] shadow-sm'
                            : 'text-[#546B61] dark:text-[#A5C0B4] hover:text-[#0F3D2E]'
                        }`}
                      >
                        {spd}x
                      </button>
                    ))}
                  </div>
                </div>
              </>
            ) : (
              <p className="text-center text-xs font-bold text-[#9A7624] dark:text-[#C9A24D] py-1">
                أزرار التحكم مقفلة لمنع اللمس العشوائي — اضغط أيقونة القفل داخل الفيديو لفتحها
              </p>
            )}
          </div>
        </section>

        {/* Keep Screen Awake Notice during active iPhone downloads */}
        {activeDownloadsList.length > 0 && (
          <div className="rounded-2xl bg-[#C9A24D]/20 border border-[#C9A24D]/50 px-4 py-3 flex items-start gap-2.5 text-xs text-[#0F241D] dark:text-[#F4EFE4]">
            <AlertTriangle className="h-4 w-4 text-[#9A7624] dark:text-[#C9A24D] shrink-0 mt-0.5" />
            <p className="font-bold leading-relaxed">
              تنبيه هام لمستخدمي الآيفون: يُرجى إبقاء شاشة التطبيق مفتوحة أثناء تنزيل المحاضرة حتى يكتمل التشفير والحفظ داخل التطبيق.
            </p>
          </div>
        )}

        {/* Two Sub-Tabs below player: "محاضرات الكورس" & "ملاحظاتي" */}
        <div className="grid grid-cols-2 gap-2 rounded-[22px] bg-[#E9F0E8] dark:bg-[#123528] p-1.5 border border-[#EFE8D8] dark:border-[#1E4F3D]">
          <button
            type="button"
            onClick={() => setInnerTab('LECTURES')}
            className={`rounded-2xl py-2.5 text-xs sm:text-sm font-extrabold transition cursor-pointer ${
              innerTab === 'LECTURES'
                ? 'bg-[#0F3D2E] text-[#C9A24D] shadow'
                : 'text-[#546B61] dark:text-[#A5C0B4]'
            }`}
          >
            محاضرات الكورس ({courseLectures.length})
          </button>
          <button
            type="button"
            onClick={() => setInnerTab('NOTES')}
            className={`rounded-2xl py-2.5 text-xs sm:text-sm font-extrabold transition cursor-pointer ${
              innerTab === 'NOTES'
                ? 'bg-[#0F3D2E] text-[#C9A24D] shadow'
                : 'text-[#546B61] dark:text-[#A5C0B4]'
            }`}
          >
            ملاحظاتي ({lectureNotes.length})
          </button>
        </div>

        {/* TAB 1: Course Lectures List */}
        {innerTab === 'LECTURES' && (
          <div className="space-y-3">
            {/* Search bar inside course */}
            <div className="relative">
              <Search className="pointer-events-none absolute right-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-[#546B61]" />
              <input
                type="search"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="ابحث داخل محاضرات هذا الكورس..."
                className="w-full rounded-2xl bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] pr-10 pl-4 py-3 text-xs sm:text-sm focus:outline-none focus:border-[#0F3D2E] dark:focus:border-[#C9A24D]"
              />
            </div>

            {filteredLectures.length === 0 ? (
              <div className="rounded-2xl bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] p-8 text-center text-xs font-bold text-[#546B61] dark:text-[#A5C0B4]">
                لا توجد محاضرات مطابقة للبحث في هذا الكورس.
              </div>
            ) : (
              <div className="space-y-2.5">
                {filteredLectures.map((lec) => {
                  const isCurrent = activeLecture?.id === lec.id;
                  const isCompleted = completedLectureIds.includes(lec.id);
                  const savedSec = playbackPositions[lec.id] ?? 0;
                  const isInProgress = !isCompleted && savedSec > 5;
                  const isDownloaded = offlineDownloads.some((d) => d.lectureId === lec.id);
                  const dlTask = downloadTasks[lec.id];

                  return (
                    <article
                      key={lec.id}
                      className={`rounded-[22px] bg-[#FFFDF8] dark:bg-[#123528] p-4 transition border-2 ${
                        isCurrent
                          ? 'border-[#C9A24D] shadow-md'
                          : 'border-[#EFE8D8] dark:border-[#1E4F3D]'
                      }`}
                    >
                      <div className="flex items-start gap-3">
                        {/* Watch Status Dot */}
                        <div className="pt-1.5 shrink-0">
                          <span
                            title={
                              isCompleted
                                ? 'مكتملة'
                                : isInProgress
                                  ? 'قيد المشاهدة'
                                  : 'لم تبدأ'
                            }
                            className={`block h-3 w-3 rounded-full ${
                              isCompleted
                                ? 'bg-[#1F6B52]'
                                : isInProgress
                                  ? 'bg-[#C9A24D]'
                                  : 'bg-[#D0D7D3] dark:bg-[#2A5847]'
                            }`}
                          />
                        </div>

                        <div className="min-w-0 flex-1">
                          <div className="flex items-start justify-between gap-2">
                            <button
                              type="button"
                              onClick={() => setSelectedLectureId(lec.id)}
                              className="text-right font-extrabold text-sm text-[#0F3D2E] dark:text-[#F4EFE4] hover:underline cursor-pointer"
                            >
                              {lec.title}
                            </button>
                            <span className="shrink-0 text-[11px] font-mono font-bold text-[#546B61] dark:text-[#A5C0B4]">
                              {formatDurationHms(lec.durationSeconds)}
                            </span>
                          </div>

                          {lec.description && (
                            <p className="mt-1 text-xs text-[#546B61] dark:text-[#A5C0B4] line-clamp-2">
                              {lec.description}
                            </p>
                          )}

                          <div className="mt-2.5 flex flex-wrap items-center gap-2 text-[11px]">
                            <span className="font-bold text-[#1F6B52] dark:text-[#C9A24D]">
                              {isCompleted
                                ? 'مكتملة ✓'
                                : isInProgress
                                  ? `قيد المشاهدة (${formatDurationHms(savedSec)})`
                                  : 'لم تبدأ بعد'}
                            </span>
                            {lec.fileSizeMb > 0 && (
                              <span className="text-[#546B61] dark:text-[#A5C0B4]">
                                • {lec.fileSizeMb.toFixed(1)} MB
                              </span>
                            )}
                            {isDownloaded && (
                              <span className="inline-flex items-center gap-1 rounded-lg bg-[#E9F0E8] dark:bg-[#0A2A20] px-2 py-0.5 font-bold text-[#0F3D2E] dark:text-[#4AD1A3]">
                                <CheckCircle2 className="h-3 w-3" />
                                <span>محفوظة أوفلاين (4KB مشفّرة)</span>
                              </span>
                            )}
                          </div>

                          {/* Active Download Progress inside Lecture Card */}
                          {dlTask && (
                            <div className="mt-3 rounded-xl bg-[#E9F0E8]/80 dark:bg-[#0A2A20] p-2.5 space-y-1.5">
                              <div className="flex items-center justify-between text-[11px] font-bold">
                                <span>{dlTask.statusLabel}</span>
                                <span className="font-mono text-[#0F3D2E] dark:text-[#C9A24D]">
                                  {dlTask.progressPercent}%
                                </span>
                              </div>
                              <div className="h-2 w-full overflow-hidden rounded-full bg-black/10 dark:bg-white/10">
                                <div
                                  className="h-full rounded-full bg-[#0F3D2E] dark:bg-[#C9A24D] transition-all"
                                  style={{ width: `${dlTask.progressPercent}%` }}
                                />
                              </div>
                              <div className="flex items-center gap-2 pt-0.5">
                                {dlTask.status === 'DOWNLOADING' ? (
                                  <button
                                    type="button"
                                    onClick={() => pauseOfflineDownload(lec.id)}
                                    className="text-[11px] font-bold text-[#0F3D2E] dark:text-[#C9A24D] cursor-pointer"
                                  >
                                    إيقاف مؤقت
                                  </button>
                                ) : (
                                  <button
                                    type="button"
                                    onClick={() => resumeOfflineDownload(lec.id)}
                                    className="text-[11px] font-bold text-[#0F3D2E] dark:text-[#C9A24D] cursor-pointer"
                                  >
                                    استئناف
                                  </button>
                                )}
                                <button
                                  type="button"
                                  onClick={() => cancelOfflineDownload(lec.id)}
                                  className="inline-flex items-center gap-0.5 text-[11px] font-bold text-[#B33A3A] cursor-pointer"
                                >
                                  <X className="h-3 w-3" />
                                  <span>إلغاء</span>
                                </button>
                              </div>
                            </div>
                          )}

                          {/* Action Buttons Row */}
                          <div className="mt-3 flex flex-wrap items-center gap-2">
                            <button
                              type="button"
                              onClick={() => {
                                if (isCurrent) {
                                  togglePlayPause();
                                } else {
                                  setSelectedLectureId(lec.id);
                                }
                              }}
                              className="inline-flex items-center gap-1.5 rounded-xl bg-[#0F3D2E] hover:bg-[#1F6B52] text-white text-xs font-extrabold px-3.5 py-2 cursor-pointer transition"
                            >
                              {isCurrent && isPlaying ? (
                                <>
                                  <Pause className="h-3.5 w-3.5 text-[#C9A24D]" />
                                  <span>إيقاف مؤقت</span>
                                </>
                              ) : (
                                <>
                                  <Play className="h-3.5 w-3.5 text-[#C9A24D]" />
                                  <span>{isCurrent ? 'تشغيل الآن' : 'مشاهدة'}</span>
                                </>
                              )}
                            </button>

                            {!isDownloaded && !dlTask && (
                              <button
                                type="button"
                                onClick={() => void startOfflineDownload(lec)}
                                className="inline-flex items-center gap-1.5 rounded-xl bg-[#E9F0E8] dark:bg-[#0A2A20] hover:bg-[#dce8da] text-[#0F3D2E] dark:text-[#C9A24D] border border-[#0F3D2E]/15 dark:border-[#C9A24D]/30 text-xs font-bold px-3.5 py-2 cursor-pointer transition"
                              >
                                <Download className="h-3.5 w-3.5" />
                                <span>تنزيل داخل التطبيق</span>
                              </button>
                            )}

                            {isDownloaded && (
                              <button
                                type="button"
                                onClick={() => void removeOfflineDownload(lec.id)}
                                className="inline-flex items-center gap-1 rounded-xl bg-[#B33A3A]/10 text-[#B33A3A] dark:text-red-300 text-xs font-bold px-3 py-2 hover:bg-[#B33A3A]/20 cursor-pointer transition"
                              >
                                <Trash2 className="h-3.5 w-3.5" />
                                <span>حذف التنزيل</span>
                              </button>
                            )}

                            {isAdminView && (
                              confirmDeleteId === lec.id ? (
                                <div className="inline-flex items-center gap-1.5">
                                  <button
                                    type="button"
                                    onClick={() => {
                                      void deleteLecture(lec.id);
                                      setConfirmDeleteId(null);
                                    }}
                                    className="rounded-xl bg-[#B33A3A] text-white text-xs font-bold px-3 py-2 cursor-pointer"
                                  >
                                    تأكيد الحذف
                                  </button>
                                  <button
                                    type="button"
                                    onClick={() => setConfirmDeleteId(null)}
                                    className="rounded-xl bg-[#E9F0E8] dark:bg-[#0A2A20] text-xs font-bold px-2.5 py-2 cursor-pointer"
                                  >
                                    تراجع
                                  </button>
                                </div>
                              ) : (
                                <button
                                  type="button"
                                  onClick={() => setConfirmDeleteId(lec.id)}
                                  className="inline-flex items-center gap-1 rounded-xl bg-[#B33A3A]/10 text-[#B33A3A] dark:text-red-300 text-xs font-bold px-3 py-2 hover:bg-[#B33A3A]/20 cursor-pointer transition"
                                >
                                  <Trash2 className="h-3.5 w-3.5" />
                                  <span>حذف المحاضرة</span>
                                </button>
                              )
                            )}
                          </div>
                        </div>
                      </div>
                    </article>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* TAB 2: Timestamped Lecture Notes */}
        {innerTab === 'NOTES' && (
          <div className="space-y-3.5">
            <form
              onSubmit={handleAddNote}
              className="rounded-[22px] bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] p-4 space-y-3"
            >
              <div className="flex items-center justify-between text-xs">
                <span className="font-extrabold text-[#0F3D2E] dark:text-[#F4EFE4]">
                  إضافة ملاحظة مرتبطة بالوقت الحالي
                </span>
                <span className="rounded-xl bg-[#E9F0E8] dark:bg-[#0A2A20] px-2.5 py-1 font-mono font-bold text-[#0F3D2E] dark:text-[#C9A24D]">
                  {formatDurationHms(currentTime)}
                </span>
              </div>

              <div className="flex gap-2">
                <input
                  type="text"
                  value={noteInput}
                  onChange={(e) => setNoteInput(e.target.value)}
                  placeholder="اكتب ملاحظتك الطبية هنا للرجوع لهذه الثانية لاحقاً..."
                  className="flex-1 rounded-2xl bg-[#F7F3EA] dark:bg-[#0A2A20] border border-[#EFE8D8] dark:border-[#1E4F3D] px-4 py-2.5 text-xs sm:text-sm focus:outline-none focus:border-[#0F3D2E] dark:focus:border-[#C9A24D]"
                />
                <button
                  type="submit"
                  disabled={!noteInput.trim() || !activeLecture}
                  className="inline-flex items-center gap-1.5 rounded-2xl bg-[#0F3D2E] hover:bg-[#1F6B52] text-white px-4 py-2.5 text-xs font-extrabold cursor-pointer disabled:opacity-50"
                >
                  <BookmarkPlus className="h-4 w-4 text-[#C9A24D]" />
                  <span>حفظ</span>
                </button>
              </div>
            </form>

            {lectureNotes.length === 0 ? (
              <div className="rounded-2xl bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] p-8 text-center text-xs font-bold text-[#546B61] dark:text-[#A5C0B4]">
                لا توجد ملاحظات مسجّلة لهذه المحاضرة بعد. أضف ملاحظة وستُربط تلقائياً بلحظة الفيديو الحالية.
              </div>
            ) : (
              <div className="space-y-2.5">
                {lectureNotes.map((note) => (
                  <div
                    key={note.id}
                    className="flex items-start justify-between gap-3 rounded-2xl bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] p-3.5"
                  >
                    <button
                      type="button"
                      onClick={() => seekTo(note.second)}
                      className="flex items-start gap-3 text-right flex-1 cursor-pointer group"
                    >
                      <span className="shrink-0 rounded-xl bg-[#0F3D2E] text-[#C9A24D] px-2.5 py-1 font-mono text-xs font-extrabold group-hover:bg-[#1F6B52]">
                        {formatDurationHms(note.second)}
                      </span>
                      <span className="text-xs sm:text-sm font-semibold leading-relaxed text-[#0F241D] dark:text-[#F4EFE4]">
                        {note.text}
                      </span>
                    </button>
                    <button
                      type="button"
                      onClick={() => deleteLectureNote(note.id)}
                      aria-label="حذف الملاحظة"
                      className="shrink-0 flex h-8 w-8 items-center justify-center rounded-xl text-[#B33A3A] hover:bg-[#B33A3A]/10 cursor-pointer"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

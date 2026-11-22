import { useEffect, useRef, useState } from 'react';
import {
  ChevronLeft,
  ChevronRight,
  Hand,
  Mic,
  MicOff,
  MonitorPlay,
  Radio,
  Send,
  Square,
  Users,
  Video,
} from 'lucide-react';
import type { Meeting } from '../data/types';
import { useDataStore } from '../data/dataStore';
import { useAccess } from '../lib/session';
import { useAppStore } from '../store/appStore';
import { EmptyState, Modal } from '../components/ui';
import { formatMmSs } from '../lib/format';

const SLIDES = ['مقدمة في علم الأدوية السريري', 'آليات عمل الأدوية والمستقبلات', 'حالات سريرية تطبيقية'];

export function MeetingsScreen() {
  const { session, isAdminView } = useAccess();
  const data = useDataStore((s) => s.data);
  const startMeeting = useDataStore((s) => s.startMeeting);
  const joinMeeting = useDataStore((s) => s.joinMeeting);
  const pushToast = useAppStore((s) => s.pushToast);

  const [roomId, setRoomId] = useState<string | null>(null);
  const [showStart, setShowStart] = useState(false);

  const live = data.meetings.filter((m) => m.status === 'live');
  const past = data.meetings.filter((m) => m.status === 'ended');

  if (roomId) {
    return <MeetingRoom meetingId={roomId} onLeave={() => setRoomId(null)} />;
  }

  const enter = (id: string) => {
    joinMeeting(id);
    setRoomId(id);
  };

  return (
    <div className="stack">
      <div className="screen-head">
        <div className="screen-title">غرفة الاجتماعات والبث المباشر</div>
        {isAdminView && (
          <button className="btn btn-gold btn-sm" onClick={() => setShowStart(true)}>
            <Radio size={16} /> بدء اجتماع مباشر
          </button>
        )}
      </div>

      {live.length === 0 && past.length === 0 ? (
        <EmptyState
          icon={<Radio size={30} />}
          title="لا يوجد بث مباشر جارٍ في هذه اللحظة"
          description={
            isAdminView
              ? 'اضغط على زر "بدء اجتماع مباشر" أعلاه لفتح غرفة البث المدمجة بالكاميرا والسلايدات للطلاب.'
              : 'عندما يبدأ الدكتور البث المباشر الداخلي، سيظهر لك زر الانضمام هنا فوراً.'
          }
          action={isAdminView ? <button className="btn btn-gold" onClick={() => setShowStart(true)}>بدء أول اجتماع مباشر</button> : null}
        />
      ) : (
        <>
          {live.length > 0 && (
            <div className="stack">
              <div className="section-title">غرف البث المباشر النشطة الآن ({live.length})</div>
              {live.map((m) => (
                <div key={m.id} className="card meeting-card">
                  <div className="row-between">
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <span className="rec-pill">● بث مباشر</span>
                      <span className="badge badge-green">{m.isRecording ? 'جاري التسجيل' : 'نشط'}</span>
                    </div>
                    <span className="helper">
                      <Users size={14} /> {m.participantCount} متصل • ✋ {m.raisedHandsCount}
                    </span>
                  </div>
                  <div style={{ fontFamily: 'var(--font-head)', fontWeight: 800, fontSize: 16 }}>{m.title}</div>
                  <div className="helper">
                    المحاضر: د. {m.hostName} • الوضع: {m.presentationMode === 'slides' ? 'عرض السلايدات الطبية' : 'كاميرا الدكتور المباشرة'}
                  </div>
                  <button className="btn btn-primary" onClick={() => enter(m.id)}>
                    <Video size={18} color="var(--gold-accent)" /> الانضمام للاجتماع المباشر
                  </button>
                </div>
              ))}
            </div>
          )}
          {past.length > 0 && (
            <div className="stack">
              <div className="section-title">جلسات بث سابقة منتهية</div>
              {past.map((m) => (
                <div key={m.id} className="card meeting-card" style={{ opacity: 0.85 }}>
                  <div className="row-between">
                    <span style={{ fontWeight: 700 }}>{m.title}</span>
                    <span className="badge badge-muted">منتهي</span>
                  </div>
                  <div className="helper">بإشراف: د. {m.hostName}</div>
                </div>
              ))}
            </div>
          )}
        </>
      )}

      <StartMeetingModal
        open={showStart}
        onClose={() => setShowStart(false)}
        onSubmit={(title, mode) => {
          const m = startMeeting({ title, mode, hostName: session?.user?.displayName ?? 'د. المشرف', hostUid: session?.user?.uid ?? 'host' });
          pushToast('تم بدء البث المباشر الداخلي ✓', 'success');
          setShowStart(false);
          enter(m.id);
        }}
      />
    </div>
  );
}

function MeetingRoom({ meetingId, onLeave }: { meetingId: string; onLeave: () => void }) {
  const { session, isAdminView } = useAccess();
  const meeting = useDataStore((s) => s.data.meetings.find((m) => m.id === meetingId));
  const messages = useDataStore((s) => s.data.meetingMessages[meetingId] ?? []);
  const updateMeeting = useDataStore((s) => s.updateMeeting);
  const endMeeting = useDataStore((s) => s.endMeeting);
  const sendMeetingMessage = useDataStore((s) => s.sendMeetingMessage);
  const pushToast = useAppStore((s) => s.pushToast);

  const [text, setText] = useState('');
  const isHost = isAdminView && meeting?.hostUid === (session?.user?.uid ?? '');

  if (!meeting) {
    return (
      <div className="stack">
        <EmptyState icon={<Radio size={30} />} title="انتهى الاجتماع" action={<button className="btn btn-primary" onClick={onLeave}>رجوع</button>} />
      </div>
    );
  }

  const slide = meeting.currentSlideIndex;

  const send = () => {
    if (!text.trim()) return;
    sendMeetingMessage(meetingId, text, isAdminView ? meeting.hostName : session?.user?.displayName ?? 'طالب', isAdminView ? 'admin' : 'student');
    setText('');
  };

  const raiseHand = () => {
    sendMeetingMessage(meetingId, '✋ رفع اليد لطلب المداخلة أو طرح سؤال طبي', session?.user?.displayName ?? 'طالب', 'student', true);
    updateMeeting(meetingId, { raisedHandsCount: meeting.raisedHandsCount + 1 });
    pushToast('تم رفع اليد ✋', 'info');
  };

  const end = () => {
    endMeeting(meetingId);
    pushToast('تم إنهاء البث المباشر', 'info');
    onLeave();
  };

  return (
    <div className="stack">
      <div className="screen-head">
        <div className="screen-title" style={{ fontSize: 17 }}>{meeting.title}</div>
        {meeting.isRecording ? <RecTimer /> : <span className="badge badge-green">● بث مباشر</span>}
      </div>

      {meeting.presentationMode === 'slides' ? (
        <div className="meeting-stage">
          <div className="slide-box">
            <div className="badge badge-gold">عرض السلايدات والشاشة الطبية المباشر</div>
            <div style={{ fontFamily: 'var(--font-head)', fontWeight: 800, fontSize: 22 }}>{SLIDES[slide] ?? SLIDES[0]}</div>
            <div className="helper">{slide + 1} / {SLIDES.length}</div>
            {isHost && (
              <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
                <button className="btn btn-sm btn-outline" disabled={slide <= 0} onClick={() => updateMeeting(meetingId, { currentSlideIndex: Math.max(0, slide - 1) })}>
                  <ChevronRight size={16} /> السلايد السابق
                </button>
                <button className="btn btn-sm btn-gold" disabled={slide >= SLIDES.length - 1} onClick={() => updateMeeting(meetingId, { currentSlideIndex: Math.min(SLIDES.length - 1, slide + 1) })}>
                  السلايد التالي <ChevronLeft size={16} />
                </button>
              </div>
            )}
          </div>
        </div>
      ) : (
        <CameraStage muted={meeting.studentsMuted} hostName={meeting.hostName} />
      )}

      {meeting.studentsMuted && !isAdminView && (
        <div className="gate-note">قام الدكتور بكتم مايكروفونات الطلاب حالياً أثناء الشرح. يمكنك طرح سؤالك في الدردشة أو رفع اليد.</div>
      )}

      <div className="meeting-controls">
        {isHost ? (
          <>
            <button className="btn btn-sm btn-outline" onClick={() => updateMeeting(meetingId, { presentationMode: meeting.presentationMode === 'slides' ? 'camera' : 'slides', isScreenSharing: meeting.presentationMode !== 'slides' })}>
              <MonitorPlay size={15} /> {meeting.presentationMode === 'slides' ? 'عرض كاميرا الدكتور' : 'مشاركة الشاشة والسلايدات'}
            </button>
            <button className="btn btn-sm btn-outline" onClick={() => updateMeeting(meetingId, { studentsMuted: !meeting.studentsMuted })}>
              {meeting.studentsMuted ? <MicOff size={15} /> : <Mic size={15} />} {meeting.studentsMuted ? 'إلغاء كتم الطلاب' : 'كتم صوت الطلاب'}
            </button>
            <button className="btn btn-sm btn-outline" onClick={() => { updateMeeting(meetingId, { isRecording: !meeting.isRecording }); pushToast(meeting.isRecording ? 'تم إيقاف التسجيل' : 'تم بدء التسجيل بالمسجل الداخلي', 'info'); }}>
              {meeting.isRecording ? 'إيقاف التسجيل' : 'بدء التسجيل'}
            </button>
            <button className="btn btn-sm btn-danger" onClick={end}>
              <Square size={14} /> إنهاء البث
            </button>
          </>
        ) : (
          <>
            <button className="btn btn-sm btn-primary" onClick={raiseHand}>
              <Hand size={15} /> رفع اليد ✋
            </button>
            <button className="btn btn-sm btn-outline" onClick={() => { pushToast('تم مغادرة الغرفة', 'info'); onLeave(); }}>مغادرة</button>
          </>
        )}
      </div>

      <div className="card panel-card">
        <div className="panel-title" style={{ fontSize: 15 }}>صندوق الدردشة الداخلي لأسئلة الطلاب ({messages.length})</div>
        <div className="chat-box">
          {messages.length === 0 ? (
            <div className="helper" style={{ textAlign: 'center', padding: 8 }}>لا توجد أسئلة بعد. اكتب سؤالك للدكتور هنا ليظهر فوراً في الغرفة.</div>
          ) : (
            messages.map((m) => (
              <div key={m.id} className="chat-msg">
                <div className="who">{m.senderRole === 'admin' ? `د. ${m.senderName} (المحاضر)` : `طالب: ${m.senderName}`}</div>
                <div style={{ fontSize: 13 }}>{m.text}</div>
              </div>
            ))
          )}
        </div>
        <div className="row">
          <input className="input grow" placeholder="اكتب سؤالك أو تعليقك الطبي في الغرفة..." value={text} onChange={(e) => setText(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && send()} />
          <button className="btn btn-primary" onClick={send} aria-label="إرسال">
            <Send size={16} />
          </button>
        </div>
      </div>
    </div>
  );
}

function CameraStage({ muted, hostName }: { muted: boolean; hostName: string }) {
  const ref = useRef<HTMLVideoElement | null>(null);
  const [err, setErr] = useState(false);

  useEffect(() => {
    let stream: MediaStream | null = null;
    navigator.mediaDevices
      ?.getUserMedia({ video: true, audio: false })
      .then((s) => {
        stream = s;
        if (ref.current) ref.current.srcObject = s;
      })
      .catch(() => setErr(true));
    return () => {
      stream?.getTracks().forEach((t) => t.stop());
    };
  }, []);

  return (
    <div className="meeting-stage">
      {err ? (
        <div className="slide-box">
          <Video size={40} color="var(--gold-accent)" />
          <div style={{ fontFamily: 'var(--font-head)', fontWeight: 700 }}>البث المرئي المباشر: د. {hostName}</div>
          <div className="helper">لم يتم العثور على كاميرا — يمكنك متابعة الدردشة وطرح الأسئلة.</div>
        </div>
      ) : (
        <>
          <video ref={ref} autoPlay playsInline muted />
          <div style={{ position: 'absolute', bottom: 10, insetInlineStart: 12, color: '#fff', fontSize: 12 }}>
            البث المرئي المباشر: د. {hostName} {muted ? '• صوت الطلاب مكتوم' : ''}
          </div>
        </>
      )}
    </div>
  );
}

function RecTimer() {
  const [sec, setSec] = useState(0);
  useEffect(() => {
    const t = window.setInterval(() => setSec((s) => s + 1), 1000);
    return () => window.clearInterval(t);
  }, []);
  return <span className="rec-pill">● REC {formatMmSs(sec)}</span>;
}

function StartMeetingModal({ open, onClose, onSubmit }: { open: boolean; onClose: () => void; onSubmit: (title: string, mode: 'camera' | 'slides') => void }) {
  const [title, setTitle] = useState('');
  const [mode, setMode] = useState<'camera' | 'slides'>('camera');
  return (
    <Modal
      open={open}
      title="بدء غرفة بث مباشر داخلية (بدون روابط خارجية)"
      onClose={onClose}
      actions={
        <>
          <button className="btn btn-gold" onClick={() => onSubmit(title, mode)}>بدء البث الداخلي الآن</button>
          <button className="btn btn-outline" onClick={onClose}>إلغاء</button>
        </>
      }
    >
      <div className="field">
        <label className="label">عنوان المحاضرة المباشرة</label>
        <input className="input" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="مثال: مراجعة الفارماكولوجي المباشرة" />
      </div>
      <div className="field">
        <label className="label">وضع العرض:</label>
        <div className="cat-chips">
          <button className={`cat-chip ${mode === 'camera' ? 'active' : ''}`} onClick={() => setMode('camera')}>كاميرا الدكتور المباشرة</button>
          <button className={`cat-chip ${mode === 'slides' ? 'active' : ''}`} onClick={() => setMode('slides')}>عرض السلايدات الطبية</button>
        </div>
      </div>
    </Modal>
  );
}

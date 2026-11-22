import { Hourglass, LogOut, RefreshCw } from 'lucide-react';
import { useAppStore } from '../store/appStore';
import { useAccess, signOut } from '../lib/session';
import { useDataStore } from '../data/dataStore';

export function PendingApproval() {
  const { session } = useAccess();
  const approval = session?.approval ?? 'pending';
  const patchSession = useAppStore((s) => s.patchSession);
  const studentApproval = useDataStore((s) => s.data.studentApproval);

  const suspended = approval === 'suspended';

  const refresh = () => {
    const uid = session?.user?.uid;
    if (!uid) return;
    const latest = studentApproval[uid] ?? 'pending';
    patchSession({ approval: latest });
  };

  return (
    <div className="pending-screen">
      <div className="card pending-card">
        <div className={`pending-avatar ${suspended ? 'suspended' : 'pending'}`}>
          <Hourglass size={40} />
        </div>
        <div style={{ fontFamily: 'var(--font-head)', fontWeight: 800, fontSize: 20 }}>
          {suspended ? 'تم إيقاف الحساب مؤقتاً' : 'حسابك قيد المراجعة'}
        </div>
        <div className="identity-box">
          <div style={{ fontFamily: 'var(--font-head)', fontWeight: 700, color: 'var(--emerald-primary)' }}>{session?.user?.displayName}</div>
          <div style={{ fontSize: 13, color: 'var(--emerald-light-accent)' }}>{session?.user?.email}</div>
        </div>
        <p style={{ fontSize: 14, color: 'var(--color-text-muted)' }}>
          {suspended
            ? 'تم إيقاف أو تعليق وصول حسابك. يرجى التواصل مع الدكتور أو الإدارة لتفعيل الحساب.'
            : 'حسابك قيد المراجعة.. يرجى انتظار موافقة الدكتور/المسؤول لتفعيل دخولك إلى Elghaliz Medicine'}
        </p>
        <button className="btn btn-primary btn-block" style={{ height: 50, borderRadius: 16 }} onClick={refresh}>
          <RefreshCw size={18} /> تحديث حالة الموافقة الآن
        </button>
        <button className="btn btn-danger btn-block" style={{ height: 48, borderRadius: 16 }} onClick={signOut}>
          <LogOut size={18} /> تسجيل الخروج
        </button>
      </div>
    </div>
  );
}

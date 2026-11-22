import { useEffect, type ReactNode } from 'react';
import { X } from 'lucide-react';
import { useAppStore } from '../store/appStore';

export function Modal({
  open,
  title,
  onClose,
  children,
  actions,
  large,
}: {
  open: boolean;
  title: ReactNode;
  onClose: () => void;
  children: ReactNode;
  actions?: ReactNode;
  large?: boolean;
}) {
  useEffect(() => {
    if (!open) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [open, onClose]);

  if (!open) return null;
  return (
    <div
      className="modal-overlay"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className={`modal ${large ? 'modal-lg' : ''}`} onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div className="modal-title">{title}</div>
          <button
            onClick={onClose}
            aria-label="إغلاق"
            style={{ border: 'none', background: 'transparent', color: 'var(--color-text-muted)', cursor: 'pointer', display: 'grid', placeItems: 'center' }}
          >
            <X size={20} />
          </button>
        </div>
        <div className="modal-body stack">{children}</div>
        {actions && <div className="modal-foot">{actions}</div>}
      </div>
    </div>
  );
}

export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel,
  cancelLabel = 'إلغاء',
  danger,
  onConfirm,
  onClose,
}: {
  open: boolean;
  title: string;
  message: string;
  confirmLabel: string;
  cancelLabel?: string;
  danger?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <Modal
      open={open}
      title={title}
      onClose={onClose}
      actions={
        <>
          <button className={`btn ${danger ? 'btn-danger' : 'btn-primary'}`} onClick={onConfirm}>
            {confirmLabel}
          </button>
          <button className="btn btn-outline" onClick={onClose}>
            {cancelLabel}
          </button>
        </>
      }
    >
      <p style={{ color: 'var(--color-text)' }}>{message}</p>
    </Modal>
  );
}

export function ProgressBar({ percent, height }: { percent: number; height?: number }) {
  return (
    <div className="pbar" style={height ? { height } : undefined}>
      <div style={{ width: `${Math.min(100, Math.max(0, percent))}%` }} />
    </div>
  );
}

export function EmptyState({
  icon,
  title,
  description,
  action,
}: {
  icon: ReactNode;
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="empty-state">
      <div className="empty-icon">{icon}</div>
      <div style={{ fontFamily: 'var(--font-head)', fontWeight: 700, fontSize: 16, color: 'var(--color-text)' }}>{title}</div>
      {description && <div style={{ fontSize: 13, maxWidth: 340 }}>{description}</div>}
      {action}
    </div>
  );
}

export function Toasts() {
  const toasts = useAppStore((s) => s.toasts);
  return (
    <div className="toast-wrap">
      {toasts.map((t) => (
        <div key={t.id} className={`toast ${t.tone}`}>
          {t.text}
        </div>
      ))}
    </div>
  );
}

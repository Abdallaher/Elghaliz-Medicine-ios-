import { useCallback, useEffect, useState } from 'react';
import { dismissIosBanner, isIosBannerDismissed } from '../lib/prefs';

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

function checkStandalone(): boolean {
  if (typeof window === 'undefined') return false;
  const nav = window.navigator as Navigator & { standalone?: boolean };
  return (
    window.matchMedia?.('(display-mode: standalone)').matches === true ||
    nav.standalone === true
  );
}

function checkIsIosBrowser(): boolean {
  if (typeof window === 'undefined') return false;
  const params = new URLSearchParams(window.location.search);
  if (params.get('ios') === '1') return true;
  const ua = window.navigator.userAgent || '';
  const isIosUa = /iPhone|iPad|iPod/i.test(ua);
  const isIpadOs =
    navigator.platform === 'MacIntel' && typeof navigator.maxTouchPoints === 'number' && navigator.maxTouchPoints > 1;
  return isIosUa || isIpadOs;
}

export function usePWAInstall() {
  const [deferredPrompt, setDeferredPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [isInstalled, setIsInstalled] = useState<boolean>(() => checkStandalone());
  const [isIos, setIsIos] = useState<boolean>(() => checkIsIosBrowser());
  const [iosDismissed, setIosDismissed] = useState<boolean>(() => isIosBannerDismissed());

  useEffect(() => {
    setIsInstalled(checkStandalone());
    setIsIos(checkIsIosBrowser());

    const handleBeforeInstall = (e: Event) => {
      e.preventDefault();
      setDeferredPrompt(e as BeforeInstallPromptEvent);
    };

    const handleAppInstalled = () => {
      setIsInstalled(true);
      setDeferredPrompt(null);
    };

    const mq = window.matchMedia?.('(display-mode: standalone)');
    const handleDisplayMode = (e: MediaQueryListEvent) => {
      if (e.matches) setIsInstalled(true);
    };

    window.addEventListener('beforeinstallprompt', handleBeforeInstall);
    window.addEventListener('appinstalled', handleAppInstalled);
    mq?.addEventListener?.('change', handleDisplayMode);

    return () => {
      window.removeEventListener('beforeinstallprompt', handleBeforeInstall);
      window.removeEventListener('appinstalled', handleAppInstalled);
      mq?.removeEventListener?.('change', handleDisplayMode);
    };
  }, []);

  const promptInstall = useCallback(async () => {
    if (!deferredPrompt) return false;
    await deferredPrompt.prompt();
    const { outcome } = await deferredPrompt.userChoice;
    setDeferredPrompt(null);
    if (outcome === 'accepted') {
      setIsInstalled(true);
      return true;
    }
    return false;
  }, [deferredPrompt]);

  const dismissIosPrompt = useCallback(() => {
    dismissIosBanner();
    setIosDismissed(true);
  }, []);

  return {
    canInstallPrompt: Boolean(deferredPrompt) && !isInstalled,
    isInstalled,
    isIos,
    showIosBanner: isIos && !isInstalled && !iosDismissed,
    promptInstall,
    dismissIosPrompt,
  };
}

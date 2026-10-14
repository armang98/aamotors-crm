import { Suspense, lazy } from 'react';
import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Loader2 } from 'lucide-react';
import { AppShell } from '@/components/layout/app-shell';
import { Button } from '@/components/ui/button';
import { useMe } from '@/features/auth/hooks';
import { errorMessage } from '@/lib/errors';

const LoginPage = lazy(() => import('@/pages/LoginPage'));
const QuickAdvertisingPage = lazy(() => import('@/pages/QuickAdvertisingPage'));
const VehiclesPage = lazy(() => import('@/pages/VehiclesPage'));
const VehicleDetailPage = lazy(() => import('@/pages/VehicleDetailPage'));
const ReferralsPage = lazy(() => import('@/pages/ReferralsPage'));
const VehicleReadyPage = lazy(() => import('@/pages/VehicleReadyPage'));
const CampaignsPage = lazy(() => import('@/pages/CampaignsPage'));
const CampaignEditorPage = lazy(() => import('@/pages/CampaignEditorPage'));
const CampaignDetailPage = lazy(() => import('@/pages/CampaignDetailPage'));

function FullScreenLoader() {
  const { t } = useTranslation();
  return (
    <div className="flex min-h-screen items-center justify-center text-muted-foreground" role="status">
      <Loader2 className="mr-2 h-5 w-5 animate-spin" aria-hidden />
      {t('app.loading')}
    </div>
  );
}

function RequireAuth() {
  const { t } = useTranslation();
  const me = useMe();
  const location = useLocation();

  if (me.isPending) return <FullScreenLoader />;
  if (me.isError) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 p-6 text-center">
        <p className="text-sm text-destructive">{errorMessage(t, me.error)}</p>
        <Button variant="outline" onClick={() => me.refetch()}>
          {t('app.retry')}
        </Button>
      </div>
    );
  }
  if (!me.data) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  return <AppShell me={me.data} />;
}

export function App() {
  return (
    <Suspense fallback={<FullScreenLoader />}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<RequireAuth />}>
          <Route element={<Outlet />}>
            <Route index element={<QuickAdvertisingPage />} />
            <Route path="vehicles" element={<VehiclesPage />} />
            <Route path="vehicles/:id" element={<VehicleDetailPage />} />
            <Route path="referrals" element={<ReferralsPage />} />
            <Route path="vehicle-ready" element={<VehicleReadyPage />} />
            <Route path="campaigns" element={<CampaignsPage />} />
            <Route path="campaigns/new" element={<CampaignEditorPage />} />
            <Route path="campaigns/:id" element={<CampaignDetailPage />} />
            <Route path="campaigns/:id/edit" element={<CampaignEditorPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Route>
      </Routes>
    </Suspense>
  );
}

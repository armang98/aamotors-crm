import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { errorMessage } from '@/lib/errors';

export function TableSkeleton({ rows = 6 }: { rows?: number }) {
  return (
    <div className="space-y-2" role="status" aria-busy="true">
      {Array.from({ length: rows }).map((_, i) => (
        <Skeleton key={i} className="h-11 w-full" />
      ))}
    </div>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col items-start gap-3 rounded-md border border-destructive/40 bg-destructive/5 p-4 text-sm" role="alert">
      <span>{errorMessage(t, error)}</span>
      {onRetry ? (
        <Button variant="outline" size="sm" onClick={onRetry}>
          {t('app.retry')}
        </Button>
      ) : null}
    </div>
  );
}

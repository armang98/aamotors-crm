import { useTranslation } from 'react-i18next';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { useChannelStatuses } from '@/features/advertising/hooks';
import { CHANNELS } from '@/features/advertising/types';
import { cn } from '@/lib/utils';
import { channelLabel } from './status-badges';

/** Compact pills showing the four channels in fallback order with mock / configured / enabled badges. */
export function ChannelStrip() {
  const { t } = useTranslation();
  const query = useChannelStatuses();

  if (query.isPending) {
    return (
      <div className="flex flex-wrap gap-2" aria-busy="true">
        {CHANNELS.map((c) => <Skeleton key={c} className="h-8 w-40" />)}
      </div>
    );
  }
  if (query.isError) return null;

  return (
    <ol className="flex flex-wrap items-center gap-2" aria-label={t('advertising.channels.title')} data-testid="channel-strip">
      {query.data.map((s, i) => {
        const live = s.enabled && s.configured && !s.mock;
        return (
          <li key={s.channel} className="flex items-center gap-2">
            <Tooltip>
              <TooltipTrigger asChild>
                <span
                  className={cn(
                    'inline-flex items-center gap-2 rounded-full border bg-card px-3 py-1.5 text-xs shadow-card',
                    live ? 'border-success/40' : 'border-border',
                  )}
                >
                  <span className="flex h-4 w-4 items-center justify-center rounded-full bg-muted text-[10px] font-semibold text-muted-foreground">{i + 1}</span>
                  <span className="font-medium">{channelLabel(s.channel)}</span>
                  {s.mock ? (
                    <Badge variant="warning" className="px-1.5 py-0 text-[10px]">{t('advertising.channels.mock')}</Badge>
                  ) : s.configured ? (
                    <Badge variant={s.enabled ? 'success' : 'secondary'} className="px-1.5 py-0 text-[10px]">
                      {s.enabled ? t('advertising.channels.enabled') : t('advertising.channels.disabled')}
                    </Badge>
                  ) : (
                    <Badge variant="outline" className="px-1.5 py-0 text-[10px]">{t('advertising.channels.notConfigured')}</Badge>
                  )}
                </span>
              </TooltipTrigger>
              <TooltipContent className="max-w-xs text-xs">{s.note ?? t(`advertising.channels.hint.${s.channel}`)}</TooltipContent>
            </Tooltip>
            {i < query.data.length - 1 ? <span className="text-muted-foreground" aria-hidden>→</span> : null}
          </li>
        );
      })}
    </ol>
  );
}

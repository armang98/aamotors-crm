import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';

interface Props {
  page: number; // zero based
  totalPages: number;
  totalElements: number;
  onPageChange: (page: number) => void;
}

export function Pagination({ page, totalPages, totalElements, onPageChange }: Props) {
  const { t } = useTranslation();
  if (totalPages <= 1) {
    return <p className="text-xs text-muted-foreground">{t('table.total', { count: totalElements })}</p>;
  }
  return (
    <nav className="flex items-center justify-between gap-2" aria-label={t('table.pagination')}>
      <p className="text-xs text-muted-foreground">
        {t('table.pageOf', { page: page + 1, total: totalPages })} · {t('table.total', { count: totalElements })}
      </p>
      <div className="flex gap-1">
        <Button variant="outline" size="sm" disabled={page <= 0} onClick={() => onPageChange(page - 1)} aria-label={t('table.previous')}>
          <ChevronLeft aria-hidden />
        </Button>
        <Button variant="outline" size="sm" disabled={page >= totalPages - 1} onClick={() => onPageChange(page + 1)} aria-label={t('table.next')}>
          <ChevronRight aria-hidden />
        </Button>
      </div>
    </nav>
  );
}

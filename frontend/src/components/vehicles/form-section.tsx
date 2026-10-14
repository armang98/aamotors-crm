import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

interface Props {
  index: number;
  title: string;
  description?: string;
  children: ReactNode;
  className?: string;
}

/** Visually separated, numbered section of the vehicle / service forms. */
export function FormSection({ index, title, description, children, className }: Props) {
  return (
    <section className={cn('rounded-lg border bg-card p-4 shadow-card sm:p-5', className)} aria-labelledby={`section-${index}`}>
      <header className="mb-4 flex items-start gap-3">
        <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-primary/15 text-xs font-bold text-primary" aria-hidden>
          {index}
        </span>
        <div>
          <h3 id={`section-${index}`} className="text-sm font-semibold leading-7">
            {title}
          </h3>
          {description ? <p className="text-xs text-muted-foreground">{description}</p> : null}
        </div>
      </header>
      <div className="space-y-4">{children}</div>
    </section>
  );
}

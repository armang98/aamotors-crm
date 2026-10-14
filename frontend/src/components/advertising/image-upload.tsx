import { useRef, useState, type DragEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { ImagePlus, Loader2, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useUploadImage } from '@/features/advertising/hooks';
import type { MediaImage } from '@/features/advertising/types';
import { errorMessage } from '@/lib/errors';
import { cn } from '@/lib/utils';

const ACCEPTED = ['image/png', 'image/jpeg', 'image/webp'];
const MAX_BYTES = 5 * 1024 * 1024;

interface Props {
  value: MediaImage | null;
  onChange: (image: MediaImage | null) => void;
  disabled?: boolean;
}

/** Drag-and-drop / click image upload for advertising (png / jpeg / webp, ≤ 5 MB). */
export function ImageUpload({ value, onChange, disabled = false }: Props) {
  const { t } = useTranslation();
  const upload = useUploadImage();
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);

  const handle = (file: File | undefined) => {
    if (!file) return;
    if (!ACCEPTED.includes(file.type)) {
      toast.error(t('advertising.image.invalidType'));
      return;
    }
    if (file.size > MAX_BYTES) {
      toast.error(t('advertising.image.tooLarge'));
      return;
    }
    upload.mutate(file, {
      onSuccess: (img) => onChange(img),
      onError: (e) => toast.error(errorMessage(t, e)),
    });
  };

  const onDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setDragging(false);
    if (disabled) return;
    handle(e.dataTransfer.files?.[0]);
  };

  if (value) {
    return (
      <div className="flex items-start gap-3 rounded-md border bg-card p-3" data-testid="image-preview">
        <img src={value.url} alt={t('advertising.image.alt')} className="h-24 w-24 rounded object-cover" />
        <div className="min-w-0 flex-1 text-xs text-muted-foreground">
          <p className="truncate font-medium text-foreground">{value.contentType}</p>
          <p>{(value.size / 1024).toFixed(0)} KB</p>
        </div>
        <Button type="button" variant="ghost" size="icon" aria-label={t('advertising.image.remove')} onClick={() => onChange(null)} disabled={disabled}>
          <X aria-hidden />
        </Button>
      </div>
    );
  }

  return (
    <div
      role="button"
      tabIndex={disabled ? -1 : 0}
      aria-disabled={disabled || undefined}
      onClick={() => !disabled && inputRef.current?.click()}
      onKeyDown={(e) => { if (!disabled && (e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); inputRef.current?.click(); } }}
      onDragOver={(e) => { e.preventDefault(); if (!disabled) setDragging(true); }}
      onDragLeave={() => setDragging(false)}
      onDrop={onDrop}
      className={cn(
        'flex cursor-pointer flex-col items-center justify-center gap-2 rounded-md border border-dashed px-4 py-6 text-center text-sm transition-colors',
        dragging ? 'border-primary bg-primary/5' : 'border-input hover:bg-accent/40',
        disabled && 'cursor-not-allowed opacity-60',
      )}
      data-testid="image-dropzone"
    >
      {upload.isPending ? <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-hidden /> : <ImagePlus className="h-6 w-6 text-muted-foreground" aria-hidden />}
      <span className="font-medium">{t('advertising.image.drop')}</span>
      <span className="text-xs text-muted-foreground">{t('advertising.image.hint')}</span>
      <input
        ref={inputRef}
        type="file"
        accept={ACCEPTED.join(',')}
        className="sr-only"
        aria-label={t('advertising.image.label')}
        onChange={(e) => { handle(e.target.files?.[0]); e.target.value = ''; }}
        disabled={disabled}
      />
    </div>
  );
}

import * as React from 'react';
import { Check } from 'lucide-react';
import { cn } from '@/lib/utils';

export interface CheckboxProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'type' | 'onChange'> {
  onCheckedChange?: (checked: boolean) => void;
}

/** Native checkbox styled to match the design system (no extra Radix dependency). */
const Checkbox = React.forwardRef<HTMLInputElement, CheckboxProps>(({ className, onCheckedChange, ...props }, ref) => (
  <span className="relative inline-flex h-4 w-4 shrink-0">
    <input
      ref={ref}
      type="checkbox"
      className={cn(
        'peer h-4 w-4 cursor-pointer appearance-none rounded-sm border border-input bg-background shadow-sm transition-colors checked:border-primary checked:bg-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50',
        className,
      )}
      onChange={(e) => onCheckedChange?.(e.target.checked)}
      {...props}
    />
    <Check className="pointer-events-none absolute left-0 top-0 h-4 w-4 p-0.5 text-primary-foreground opacity-0 peer-checked:opacity-100" aria-hidden />
  </span>
));
Checkbox.displayName = 'Checkbox';

export { Checkbox };

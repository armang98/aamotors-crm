import { CalendarClock, Car, Gift, Megaphone, MessageSquareText, type LucideIcon } from 'lucide-react';

export interface NavItem {
  to: string;
  labelKey: string;
  pageKey: 'quickAdvertising' | 'vehicles' | 'referrals' | 'vehicleReady' | 'campaigns';
  icon: LucideIcon;
  end?: boolean;
}

export const NAV_ITEMS: NavItem[] = [
  { to: '/', labelKey: 'nav.quickAdvertising', pageKey: 'quickAdvertising', icon: Megaphone, end: true },
  { to: '/vehicles', labelKey: 'nav.vehicles', pageKey: 'vehicles', icon: Car },
  { to: '/referrals', labelKey: 'nav.referrals', pageKey: 'referrals', icon: Gift },
  { to: '/vehicle-ready', labelKey: 'nav.vehicleReady', pageKey: 'vehicleReady', icon: MessageSquareText },
  { to: '/campaigns', labelKey: 'nav.campaigns', pageKey: 'campaigns', icon: CalendarClock },
];

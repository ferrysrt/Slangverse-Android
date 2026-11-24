'use client';

import { useFormStatus } from 'react-dom';

export function DiscoverySubmit({ enabled }) {
  const { pending } = useFormStatus();
  return <button className="primary" type="submit" disabled={!enabled || pending}>
    {pending ? 'Mengirim pencarian?' : 'Jalankan Firecrawl'}
  </button>;
}

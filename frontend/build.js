import crypto from 'node:crypto';

// Safely define globalThis.crypto if not already defined (or if it's read-only/configurable)
if (typeof globalThis.crypto === 'undefined') {
  try {
    Object.defineProperty(globalThis, 'crypto', {
      value: crypto,
      writable: true,
      configurable: true
    });
  } catch (e) {
    // Fallback if defineProperty fails
    globalThis.crypto = crypto;
  }
}

import { build } from 'vite';

build().catch((err) => {
  console.error(err);
  process.exit(1);
});

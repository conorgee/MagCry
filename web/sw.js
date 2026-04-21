/**
 * sw.js — Service worker for offline PWA support.
 *
 * Strategy: cache-first for app shell, network-first for everything else.
 */

const CACHE_NAME = 'trading-game-v1';

const APP_SHELL = [
  './',
  './index.html',
  './style.css',
  './js/app.js',
  './js/deck.js',
  './js/state.js',
  './js/trading.js',
  './js/scoring.js',
  './js/bot.js',
  './js/simpleBot.js',
  './js/strategicBot.js',
  './js/gameLoop.js',
  './js/tutorial.js',
  './manifest.json',
  './icons/icon-192.png',
  './icons/icon-512.png',
];

// Install — cache the app shell
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => cache.addAll(APP_SHELL))
      .then(() => self.skipWaiting())
  );
});

// Activate — clean up old caches
self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys()
      .then(keys => Promise.all(
        keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k))
      ))
      .then(() => self.clients.claim())
  );
});

// Fetch — cache-first for app shell, network-first fallback
self.addEventListener('fetch', event => {
  event.respondWith(
    caches.match(event.request).then(cached => {
      return cached || fetch(event.request).then(response => {
        // Cache successful GET requests
        if (event.request.method === 'GET' && response.status === 200) {
          const clone = response.clone();
          caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
        }
        return response;
      });
    }).catch(() => {
      // Offline fallback for navigation requests
      if (event.request.mode === 'navigate') {
        return caches.match('./index.html');
      }
    })
  );
});

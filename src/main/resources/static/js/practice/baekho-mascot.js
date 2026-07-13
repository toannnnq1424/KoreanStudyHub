(() => {
  'use strict';

  const root = document.querySelector('[data-baekho]');
  if (!root) return;

  const sprite = root.querySelector('.pc-baekho-sprite');
  const toggle = root.querySelector('.pc-companion-toggle');
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  const oneShotStates = new Set(['success', 'warning', 'error', 'celebration']);
  const storageKey = 'ksh.baekho.collapsed';
  let atlas;
  let framesByState = new Map();
  let timer;
  let currentState = normalize(root.dataset.state || 'idle');
  let previousLoopState = 'idle';

  function normalize(value) {
    return String(value || 'idle')
      .trim()
      .toLowerCase()
      .replace(/\s+/g, '_');
  }

  function setFallback() {
    sprite.style.backgroundImage = "url('/images/baekho/baekho_master_64.png')";
    sprite.style.backgroundSize = '64px 64px';
    sprite.style.backgroundPosition = '0 0';
  }

  function draw(frame) {
    sprite.style.backgroundImage = "url('/images/baekho/baekho_atlas_64.png')";
    sprite.style.backgroundSize = `${atlas.width}px ${atlas.height}px`;
    sprite.style.backgroundPosition = `${-frame.x}px ${-frame.y}px`;
  }

  function play(state) {
    window.clearTimeout(timer);
    const normalized = normalize(state);
    const frames = framesByState.get(normalized) || framesByState.get('idle');
    if (!frames || frames.length === 0) {
      setFallback();
      return;
    }

    if (!oneShotStates.has(normalized) && normalized !== 'collapsed') {
      previousLoopState = normalized;
    }
    currentState = normalized;
    let index = 0;

    const advance = () => {
      draw(frames[index]);
      if (reducedMotion.matches) return;
      const duration = Math.max(80, Number(frames[index].duration_ms) || 180);
      index += 1;
      if (index >= frames.length) {
        if (oneShotStates.has(normalized)) {
          timer = window.setTimeout(() => play(previousLoopState), duration);
          return;
        }
        index = 0;
      }
      timer = window.setTimeout(advance, duration);
    };
    advance();
  }

  function applyCollapsed(collapsed) {
    root.classList.toggle('is-collapsed', collapsed);
    toggle.setAttribute('aria-expanded', String(!collapsed));
    toggle.setAttribute('aria-label', collapsed ? 'Mở Bạch Hổ' : 'Thu gọn Bạch Hổ');
    play(collapsed ? 'collapsed' : (root.dataset.state || 'idle'));
  }

  toggle.addEventListener('click', () => {
    const collapsed = !root.classList.contains('is-collapsed');
    localStorage.setItem(storageKey, String(collapsed));
    applyCollapsed(collapsed);
  });

  window.addEventListener('ksh:baekho-state', (event) => {
    if (root.classList.contains('is-collapsed')) return;
    play(event.detail?.state || 'idle');
  });

  reducedMotion.addEventListener('change', () => play(currentState));

  fetch('/images/baekho/baekho_atlas.json', { credentials: 'same-origin' })
    .then((response) => {
      if (!response.ok) throw new Error(`Baekho atlas ${response.status}`);
      return response.json();
    })
    .then((metadata) => {
      atlas = metadata.atlases?.['64'];
      if (!atlas?.frames) throw new Error('Baekho atlas metadata is incomplete');
      framesByState = Object.values(atlas.frames).reduce((states, frame) => {
        const state = normalize(frame.state);
        const frames = states.get(state) || [];
        frames.push(frame);
        states.set(state, frames);
        return states;
      }, new Map());
      framesByState.forEach((frames) => frames.sort((left, right) => left.frame - right.frame));
      applyCollapsed(localStorage.getItem(storageKey) === 'true');
    })
    .catch(() => setFallback());
})();

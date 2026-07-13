(() => {
  'use strict';

  const grid = document.getElementById('pc-catalog-grid');
  const loader = document.getElementById('pc-catalog-loader');
  if (!grid || !loader) return;

  const status = loader.querySelector('.pc-loader-status');
  const retry = loader.querySelector('.pc-loader-retry');
  let nextBatch = Number(loader.dataset.nextBatch || 1);
  let hasMore = loader.dataset.hasMore === 'true';
  let loading = false;
  let activeCard;
  let skillRotationTimer;
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  const stateBySkill = {
    LISTENING: 'listening',
    READING: 'reading',
    WRITING: 'writing',
    SPEAKING: 'speaking'
  };

  function baekho(state) {
    window.dispatchEvent(new CustomEvent('ksh:baekho-state', { detail: { state } }));
  }

  function stopSkillRotation(resetMascot = true) {
    window.clearInterval(skillRotationTimer);
    skillRotationTimer = undefined;
    activeCard = undefined;
    if (resetMascot) baekho('idle');
  }

  function startSkillRotation(card) {
    if (!card || card === activeCard) return;
    stopSkillRotation(false);
    activeCard = card;

    const states = String(card.dataset.skillCycle || card.dataset.primarySkill || '')
      .split(',')
      .map((skill) => stateBySkill[skill.trim().toUpperCase()])
      .filter(Boolean);
    if (states.length === 0) {
      baekho('idle');
      return;
    }

    let index = 0;
    baekho(states[index]);
    if (states.length === 1 || reducedMotion.matches) return;

    skillRotationTimer = window.setInterval(() => {
      index = (index + 1) % states.length;
      baekho(states[index]);
    }, 2000);
  }

  function setComplete() {
    hasMore = false;
    loader.classList.remove('is-loading');
    loader.classList.add('is-complete');
    status.textContent = 'Đã hiển thị toàn bộ bộ đề.';
    retry.hidden = true;
  }

  function setError() {
    loader.classList.remove('is-loading');
    status.textContent = 'Không tải được bộ đề tiếp theo.';
    retry.hidden = false;
    baekho('error');
  }

  async function loadNextBatch() {
    if (!hasMore || loading) return;
    loading = true;
    retry.hidden = true;
    loader.classList.add('is-loading');
    status.textContent = 'Đang tải thêm bộ đề…';
    baekho('loading');

    const params = new URLSearchParams(window.location.search);
    params.set('batch', String(nextBatch));

    try {
      const response = await fetch(`${loader.dataset.endpoint}?${params.toString()}`, {
        credentials: 'same-origin',
        headers: { Accept: 'text/html' }
      });
      if (!response.ok) throw new Error(`Catalog batch ${response.status}`);

      const html = await response.text();
      const documentFragment = new DOMParser().parseFromString(html, 'text/html');
      const batch = documentFragment.querySelector('[data-catalog-batch]');
      if (!batch) throw new Error('Catalog batch markup is missing');

      const existingIds = new Set(
        Array.from(grid.querySelectorAll('[data-set-id]')).map((card) => card.dataset.setId)
      );
      Array.from(batch.querySelectorAll('.pc-set-card')).forEach((card) => {
        if (!existingIds.has(card.dataset.setId)) grid.appendChild(card);
      });

      hasMore = batch.dataset.hasMore === 'true';
      nextBatch = Number(batch.dataset.nextBatch || nextBatch + 1);
      loader.dataset.hasMore = String(hasMore);
      loader.dataset.nextBatch = String(nextBatch);
      loader.classList.remove('is-loading');
      loading = false;

      if (hasMore) {
        status.textContent = 'Cuộn xuống để tải thêm.';
        baekho('success');
      } else {
        setComplete();
        baekho('idle');
      }
    } catch (error) {
      loading = false;
      setError();
    }
  }

  retry.addEventListener('click', loadNextBatch);

  if (!hasMore) {
    setComplete();
  } else if ('IntersectionObserver' in window) {
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) loadNextBatch();
    }, { rootMargin: '320px 0px', threshold: 0.01 });
    observer.observe(loader);
  } else {
    status.textContent = 'Tải thêm bộ đề';
    retry.textContent = 'Tải thêm';
    retry.hidden = false;
  }

  grid.addEventListener('pointerover', (event) => {
    const card = event.target.closest('.pc-set-card');
    if (card && !card.contains(event.relatedTarget)) startSkillRotation(card);
  });

  grid.addEventListener('pointerout', (event) => {
    const card = event.target.closest('.pc-set-card');
    if (!card || card.contains(event.relatedTarget)) return;
    const nextCard = event.relatedTarget?.closest?.('.pc-set-card');
    if (nextCard) startSkillRotation(nextCard);
    else stopSkillRotation();
  });
  grid.addEventListener('pointerleave', () => stopSkillRotation());
  grid.addEventListener('focusin', (event) => {
    const card = event.target.closest('.pc-set-card');
    if (card) startSkillRotation(card);
  });
  grid.addEventListener('focusout', (event) => {
    const nextCard = event.relatedTarget?.closest?.('.pc-set-card');
    if (nextCard) startSkillRotation(nextCard);
    else stopSkillRotation();
  });

  reducedMotion.addEventListener('change', () => {
    const card = activeCard;
    activeCard = undefined;
    if (card) startSkillRotation(card);
  });
})();

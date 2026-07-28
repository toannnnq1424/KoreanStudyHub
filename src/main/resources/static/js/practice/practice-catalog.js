(() => {
  'use strict';

  const grid = document.getElementById('pc-catalog-grid');
  if (!grid) return;

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

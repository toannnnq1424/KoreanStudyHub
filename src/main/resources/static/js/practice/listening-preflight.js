(function () {
  'use strict';

  const root = document.querySelector('.lpf-shell');
  if (!root) return;
  const audio = root.querySelector('[data-audio]');
  const play = root.querySelector('[data-play]');
  const track = root.querySelector('[data-track]');
  const progress = root.querySelector('[data-progress]');
  const time = root.querySelector('[data-time]');
  const confirm = root.querySelector('[data-confirm]');
  const submit = root.querySelector('[data-continue]');
  const status = root.querySelector('[data-status]');
  let playbackVerified = false;

  function format(seconds) {
    const value = Number.isFinite(seconds) ? Math.max(0, Math.floor(seconds)) : 0;
    return String(Math.floor(value / 60)).padStart(2, '0') + ':'
      + String(value % 60).padStart(2, '0');
  }

  function render() {
    const duration = Number.isFinite(audio.duration) ? audio.duration : 0;
    const ratio = duration > 0 ? Math.min(1, audio.currentTime / duration) : 0;
    progress.style.width = (ratio * 100) + '%';
    track.setAttribute('aria-valuenow', String(Math.round(ratio * 100)));
    time.textContent = format(audio.currentTime) + ' / ' + format(duration);
    play.classList.toggle('is-playing', !audio.paused && !audio.ended);
    track.classList.toggle('is-seekable', playbackVerified);
    track.setAttribute('aria-disabled', String(!playbackVerified));
  }

  play.addEventListener('click', function () {
    if (audio.paused) {
      audio.play().catch(function () {
        status.textContent = 'Trình duyệt chưa cho phép phát audio. Hãy thử lại.';
      });
    } else {
      audio.pause();
    }
  });

  track.addEventListener('click', function (event) {
    if (!playbackVerified || !Number.isFinite(audio.duration) || audio.duration <= 0) {
      status.textContent = 'Hãy phát audio mẫu trước khi tua.';
      return;
    }
    const bounds = track.getBoundingClientRect();
    audio.currentTime = Math.max(0, Math.min(1, (event.clientX - bounds.left) / bounds.width)) * audio.duration;
  });

  track.addEventListener('keydown', function (event) {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    if (!playbackVerified || !Number.isFinite(audio.duration) || audio.duration <= 0) {
      status.textContent = 'Hãy phát audio mẫu trước khi tua.';
      return;
    }
    if (event.key === 'Home') audio.currentTime = 0;
    else if (event.key === 'End') audio.currentTime = audio.duration;
    else audio.currentTime = Math.max(0, Math.min(
      audio.duration,
      audio.currentTime + (event.key === 'ArrowLeft' ? -5 : 5)
    ));
    render();
  });

  audio.addEventListener('loadedmetadata', render);
  audio.addEventListener('timeupdate', render);
  audio.addEventListener('play', render);
  audio.addEventListener('playing', function () {
    if (!playbackVerified) {
      playbackVerified = true;
      confirm.disabled = false;
      status.textContent = 'Audio đang phát. Nếu nghe rõ, hãy xác nhận để bắt đầu.';
    }
    render();
  });
  audio.addEventListener('pause', render);
  audio.addEventListener('ended', render);
  audio.addEventListener('error', function () {
    play.disabled = true;
    status.textContent = 'Không thể tải audio thử loa. Hãy quay lại và báo cho giảng viên.';
  });

  confirm.addEventListener('change', function () {
    submit.disabled = !(playbackVerified && confirm.checked);
    status.textContent = submit.disabled
      ? 'Hãy xác nhận bạn nghe rõ audio mẫu.'
      : 'Thiết bị đã sẵn sàng.';
  });

  render();
})();

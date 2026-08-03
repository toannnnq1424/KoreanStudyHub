/* KSH profile — local-only form affordances; no network calls. */
(function () {
  'use strict';

  var avatarInput = document.getElementById('avatarFile');
  var avatarFileName = document.getElementById('avatarFileName');
  if (avatarInput && avatarFileName) {
    avatarInput.addEventListener('change', function () {
      var selected = avatarInput.files && avatarInput.files[0];
      avatarFileName.textContent = selected ? selected.name : 'Chưa chọn tệp';
    });
  }

  var bio = document.getElementById('bio');
  var bioCounter = document.getElementById('bioCounter');
  if (bio && bioCounter) {
    var updateCounter = function () {
      bioCounter.textContent = bio.value.length + ' / 500';
    };
    bio.addEventListener('input', updateCounter);
    updateCounter();
  }
})();

/* LEADER department screens — flash → toast drain.
   The data-flash-drained guard is shared with question-bank.js so pages that
   load both scripts drain the flash only once. */
(function () {
  'use strict';
  var flashData = document.getElementById('flash-data');
  if (flashData && !flashData.dataset.flashDrained && window.KshToast) {
    flashData.dataset.flashDrained = '1';
    if (flashData.dataset.flashSuccess) window.KshToast.success(flashData.dataset.flashSuccess);
    if (flashData.dataset.flashError) window.KshToast.error(flashData.dataset.flashError);
  }
})();

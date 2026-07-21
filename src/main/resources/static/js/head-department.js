/* HEAD department screens — flash → toast drain. */
(function () {
  'use strict';
  var flashData = document.getElementById('flash-data');
  if (flashData && window.KshToast) {
    if (flashData.dataset.flashSuccess) window.KshToast.success(flashData.dataset.flashSuccess);
    if (flashData.dataset.flashError) window.KshToast.error(flashData.dataset.flashError);
  }
})();

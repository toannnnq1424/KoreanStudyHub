(function () {
  'use strict';

  document.addEventListener('DOMContentLoaded', function () {
    const form = document.querySelector('[data-library-lesson-form]');
    if (!form) return;
    const type = form.querySelector('[data-library-content-type]');
    if (!type) return;

    function syncContentSection() {
      form.querySelectorAll('[data-content-section]').forEach(function (section) {
        const active = section.dataset.contentSection === type.value;
        section.hidden = !active;
        section.querySelectorAll('input, select, textarea').forEach(function (control) {
          control.disabled = !active;
        });
      });
    }

    // Video provider conditional rendering
    const videoProviderSelect = form.querySelector('[data-video-provider-select]');
    if (videoProviderSelect) {
      function showHideVideoFields() {
        const provider = videoProviderSelect.value;
        
        // Hide all video fields first
        form.querySelectorAll('[data-video-provider]').forEach(function (label) {
          label.hidden = true;
          label.querySelectorAll('input, select').forEach(function (control) {
            control.disabled = true;
          });
        });
        
        // Show only the relevant field(s) based on provider
        if (provider === 'UPLOAD') {
          form.querySelectorAll('[data-video-provider="UPLOAD"]').forEach(function (label) {
            label.hidden = false;
            label.querySelectorAll('input, select').forEach(function (control) {
              control.disabled = false;
            });
          });
        } else if (provider === 'YOUTUBE') {
          form.querySelectorAll('[data-video-provider="YOUTUBE"]').forEach(function (label) {
            label.hidden = false;
            label.querySelectorAll('input, select').forEach(function (control) {
              control.disabled = false;
            });
          });
        } else if (provider === 'VIMEO') {
          form.querySelectorAll('[data-video-provider="VIMEO"]').forEach(function (label) {
            label.hidden = false;
            label.querySelectorAll('input, select').forEach(function (control) {
              control.disabled = false;
            });
          });
        }
      }
      
      function clearUnusedVideoFields() {
        const videoUploadInput = form.querySelector('input[name="videoUpload"]');
        const videoLibrarySelect = form.querySelector('select[name="videoLibraryAssetId"]');
        const videoUrlInput = form.querySelector('input[name="videoUrl"]');
        const provider = videoProviderSelect.value;
        
        // Clear fields that won't be used based on current provider
        if (provider === 'UPLOAD') {
          if (videoUrlInput) videoUrlInput.value = '';
        } else if (provider === 'YOUTUBE' || provider === 'VIMEO') {
          if (videoUploadInput) videoUploadInput.value = '';
          if (videoLibrarySelect) videoLibrarySelect.value = '';
        } else {
          // No provider selected - clear all
          if (videoUploadInput) videoUploadInput.value = '';
          if (videoLibrarySelect) videoLibrarySelect.value = '';
          if (videoUrlInput) videoUrlInput.value = '';
        }
      }

      // Initial setup on page load
      showHideVideoFields();
      
      // On user change, update display AND clear stale data
      videoProviderSelect.addEventListener('change', function () {
        showHideVideoFields();
        clearUnusedVideoFields();
      });
    }

    type.addEventListener('change', syncContentSection);
    syncContentSection();
  });
})();

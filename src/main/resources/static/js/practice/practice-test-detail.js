(function () {
  "use strict";

  document.querySelectorAll("[data-attempt-toggle]").forEach(function (button) {
    button.addEventListener("click", function () {
      var attempts = button.closest(".pd-attempts");
      if (!attempts) return;
      var expanded = button.getAttribute("aria-expanded") === "true";
      attempts.classList.toggle("is-expanded", !expanded);
      button.setAttribute("aria-expanded", String(!expanded));
      var label = button.querySelector("span");
      if (label) {
        if (!button.dataset.collapsedLabel) button.dataset.collapsedLabel = label.textContent.trim();
        label.textContent = expanded ? button.dataset.collapsedLabel : "Thu gọn lịch sử";
      }
    });
  });

  document.querySelectorAll("[data-confirm-discard]").forEach(function (form) {
    form.addEventListener("submit", function (event) {
      if (!window.confirm("Hủy lượt đang làm? Các câu trả lời chưa nộp của lượt này sẽ bị xóa.")) {
        event.preventDefault();
      }
    });
  });

})();

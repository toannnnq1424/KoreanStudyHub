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

  var dialog = document.getElementById("speaking-preflight");
  if (!dialog) return;

  var continueButton = dialog.querySelector("[data-preflight-continue]");
  var errorBox = dialog.querySelector("[data-preflight-error]");
  var speakerButton = dialog.querySelector("[data-test-speaker]");
  var speakerConfirm = dialog.querySelector("[data-speaker-confirm]");
  var microphoneButton = dialog.querySelector("[data-test-microphone]");
  var microphoneLevel = dialog.querySelector("[data-mic-level]");
  var uploadEnabled = dialog.dataset.uploadEnabled === "true";
  var pendingAction = null;
  var activeStream = null;
  var activeAudioContext = null;
  var meterFrame = null;
  var checks = { browser: false, speaker: false, microphone: false, server: false };

  function checkSection(name) {
    return dialog.querySelector('[data-check="' + name + '"]');
  }

  function setCheck(name, passed, message) {
    checks[name] = passed;
    var section = checkSection(name);
    if (!section) return;
    section.classList.toggle("is-pass", passed);
    section.classList.toggle("is-fail", !passed);
    var messageNode = section.querySelector("[data-check-message]");
    var stateNode = section.querySelector("[data-check-state]");
    if (messageNode && message) messageNode.textContent = message;
    if (stateNode) stateNode.textContent = passed ? "Sẵn sàng" : "Cần xử lý";
    updateContinueState();
  }

  function updateContinueState() {
    continueButton.disabled = !Object.keys(checks).every(function (key) { return checks[key]; });
  }

  function showError(message) {
    errorBox.textContent = message;
    errorBox.hidden = !message;
  }

  function stopDeviceCheck() {
    if (meterFrame) window.cancelAnimationFrame(meterFrame);
    meterFrame = null;
    if (activeStream) {
      activeStream.getTracks().forEach(function (track) { track.stop(); });
      activeStream = null;
    }
    if (activeAudioContext) {
      activeAudioContext.close().catch(function () {});
      activeAudioContext = null;
    }
    if (microphoneLevel) microphoneLevel.style.width = "0";
  }

  function resetPreflight() {
    stopDeviceCheck();
    checks = { browser: false, speaker: false, microphone: false, server: false };
    showError("");
    speakerConfirm.checked = false;
    speakerConfirm.disabled = true;

    var browserReady = Boolean(window.MediaRecorder && navigator.mediaDevices
      && typeof navigator.mediaDevices.getUserMedia === "function");
    setCheck(
      "browser",
      browserReady,
      browserReady
        ? "Trình duyệt hỗ trợ thu âm bằng micro."
        : "Trình duyệt này chưa hỗ trợ ghi âm. Hãy dùng phiên bản Chrome, Edge hoặc Safari mới."
    );
    setCheck(
      "server",
      uploadEnabled,
      uploadEnabled
        ? "Dịch vụ lưu bản ghi riêng tư đang hoạt động."
        : "Dịch vụ lưu bản ghi chưa được bật. Chưa thể bắt đầu phần Nói."
    );

    var speakerSection = checkSection("speaker");
    var microphoneSection = checkSection("microphone");
    [speakerSection, microphoneSection].forEach(function (section) {
      section.classList.remove("is-pass", "is-fail");
    });
    updateContinueState();
  }

  function openPreflight(action) {
    pendingAction = action;
    resetPreflight();
    dialog.showModal();
  }

  document.querySelectorAll('a[data-speaking-preflight="true"]').forEach(function (link) {
    link.addEventListener("click", function (event) {
      event.preventDefault();
      openPreflight({ type: "link", url: link.href });
    });
  });

  document.querySelectorAll('form[data-speaking-preflight="true"]').forEach(function (form) {
    form.addEventListener("submit", function (event) {
      if (form.dataset.preflightApproved === "true") return;
      event.preventDefault();
      openPreflight({ type: "form", form: form });
    });
  });

  speakerButton.addEventListener("click", async function () {
    showError("");
    try {
      var AudioContextClass = window.AudioContext || window.webkitAudioContext;
      if (!AudioContextClass) throw new Error("audio-output-unsupported");
      var context = new AudioContextClass();
      var oscillator = context.createOscillator();
      var gain = context.createGain();
      oscillator.type = "sine";
      oscillator.frequency.setValueAtTime(520, context.currentTime);
      oscillator.frequency.linearRampToValueAtTime(700, context.currentTime + 0.45);
      gain.gain.setValueAtTime(0.0001, context.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.12, context.currentTime + 0.03);
      gain.gain.exponentialRampToValueAtTime(0.0001, context.currentTime + 0.55);
      oscillator.connect(gain);
      gain.connect(context.destination);
      oscillator.start();
      oscillator.stop(context.currentTime + 0.58);
      speakerConfirm.disabled = false;
      oscillator.addEventListener("ended", function () { context.close().catch(function () {}); });
    } catch (error) {
      setCheck("speaker", false, "Không thể phát âm thử trên thiết bị này.");
      showError("KSH không phát được âm thử. Hãy kiểm tra thiết bị phát âm thanh và thử lại.");
    }
  });

  speakerConfirm.addEventListener("change", function () {
    setCheck(
      "speaker",
      speakerConfirm.checked,
      speakerConfirm.checked ? "Đã xác nhận nghe rõ âm thử." : "Phát âm thử rồi xác nhận bạn nghe rõ."
    );
  });

  microphoneButton.addEventListener("click", async function () {
    showError("");
    stopDeviceCheck();
    microphoneButton.disabled = true;
    microphoneButton.textContent = "Đang kiểm tra";
    try {
      activeStream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true }
      });
      var track = activeStream.getAudioTracks()[0];
      if (!track || track.readyState !== "live") throw new Error("microphone-unavailable");

      var AudioContextClass = window.AudioContext || window.webkitAudioContext;
      if (AudioContextClass) {
        activeAudioContext = new AudioContextClass();
        var source = activeAudioContext.createMediaStreamSource(activeStream);
        var analyser = activeAudioContext.createAnalyser();
        analyser.fftSize = 256;
        source.connect(analyser);
        var samples = new Uint8Array(analyser.frequencyBinCount);
        var startedAt = performance.now();
        var renderLevel = function (now) {
          analyser.getByteFrequencyData(samples);
          var peak = samples.reduce(function (max, value) { return Math.max(max, value); }, 0);
          microphoneLevel.style.width = Math.max(4, Math.min(100, peak / 1.6)) + "%";
          if (now - startedAt < 1400) meterFrame = requestAnimationFrame(renderLevel);
        };
        meterFrame = requestAnimationFrame(renderLevel);
      }

      var label = track.label ? "Micro đã kết nối: " + track.label : "Micro đã kết nối và được cấp quyền.";
      setCheck("microphone", true, label);
      window.setTimeout(stopDeviceCheck, 1500);
    } catch (error) {
      stopDeviceCheck();
      var denied = error && (error.name === "NotAllowedError" || error.name === "SecurityError");
      setCheck(
        "microphone",
        false,
        denied ? "Quyền sử dụng micro đang bị từ chối." : "Không tìm thấy micro có thể sử dụng."
      );
      showError(denied
        ? "Hãy cho phép quyền micro trong cài đặt trang web rồi kiểm tra lại."
        : "Hãy kết nối micro hoặc tai nghe có micro rồi kiểm tra lại.");
    } finally {
      microphoneButton.disabled = false;
      microphoneButton.textContent = "Kiểm tra lại";
    }
  });

  continueButton.addEventListener("click", function () {
    if (continueButton.disabled || !pendingAction) return;
    var action = pendingAction;
    pendingAction = null;
    stopDeviceCheck();
    dialog.close();
    if (action.type === "link") {
      window.location.assign(action.url);
      return;
    }
    action.form.dataset.preflightApproved = "true";
    action.form.requestSubmit();
  });

  dialog.addEventListener("close", function () {
    stopDeviceCheck();
    pendingAction = null;
  });
})();

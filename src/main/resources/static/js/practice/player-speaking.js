(function () {
  "use strict";

  var root = document.querySelector(".spp-player");
  if (!root) return;

  var deliveryElement = document.getElementById("spp-delivery");
  var delivery;
  try {
    delivery = JSON.parse(deliveryElement.textContent || "{}");
  } catch (caught) {
    delivery = null;
  }

  var questions = Array.isArray(delivery && delivery.questions) ? delivery.questions : [];
  var attemptId = root.dataset.attemptId;
  var interruptUrl = root.dataset.interruptUrl;
  var returnUrl = root.dataset.returnUrl || "/practice";
  var uploadEnabled = root.dataset.uploadEnabled === "true";
  var csrfToken = document.querySelector('meta[name="_csrf"]')?.content || "";
  var csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || "X-CSRF-TOKEN";

  var groupLabel = root.querySelector("[data-group-label]");
  var progress = root.querySelector("[data-question-progress]");
  var connection = root.querySelector("[data-connection]");
  var promptAudioState = root.querySelector("[data-prompt-audio-state]");
  var playCount = root.querySelector("[data-play-count]");
  var promptCard = root.querySelector("[data-prompt-card]");
  var promptText = root.querySelector("[data-prompt-text]");
  var stateLabel = root.querySelector("[data-state-label]");
  var timer = root.querySelector("[data-timer]");
  var wave = Array.from(root.querySelectorAll("[data-wave] i"));
  var action = root.querySelector("[data-action]");
  var message = root.querySelector("[data-message]");
  var error = root.querySelector("[data-error]");
  var promptAudio = root.querySelector("[data-prompt-audio]");
  var submitForm = root.querySelector(".spp-submit-form");
  var exitDialog = root.querySelector("[data-exit-dialog]");

  var currentIndex = -1;
  var currentQuestion = null;
  var runToken = 0;
  var countdownTimer = null;
  var mediaStream = null;
  var mediaRecorder = null;
  var mediaChunks = [];
  var recordedBlob = null;
  var analyserContext = null;
  var analyserFrame = null;
  var allowNavigation = false;
  var interruptSent = false;
  var pendingAction = null;

  function setConnection(text, state) {
    connection.textContent = text;
    connection.classList.toggle("is-ready", state === "ready");
    connection.classList.toggle("is-error", state === "error");
  }

  function setState(label, seconds) {
    stateLabel.textContent = label;
    timer.textContent = Number.isFinite(seconds) ? String(seconds) : "--";
  }

  function setError(text) {
    error.textContent = text || "";
    error.hidden = !text;
  }

  function showAction(label, handler) {
    pendingAction = handler;
    action.textContent = label;
    action.hidden = false;
  }

  function hideAction() {
    pendingAction = null;
    action.hidden = true;
  }

  function clearCountdown() {
    if (countdownTimer) window.clearInterval(countdownTimer);
    countdownTimer = null;
  }

  function stopMeter() {
    if (analyserFrame) cancelAnimationFrame(analyserFrame);
    analyserFrame = null;
    if (analyserContext) analyserContext.close().catch(function () {});
    analyserContext = null;
    wave.forEach(function (bar) { bar.style.height = "6px"; });
  }

  function stopMicrophone() {
    stopMeter();
    if (mediaStream) mediaStream.getTracks().forEach(function (track) { track.stop(); });
    mediaStream = null;
  }

  function resetAudio() {
    promptAudio.pause();
    promptAudio.removeAttribute("src");
    promptAudio.load();
    promptAudioState.classList.remove("is-playing");
  }

  function preferredMimeType() {
    return ["audio/webm;codecs=opus", "audio/mp4", "audio/webm"].find(function (type) {
      return MediaRecorder.isTypeSupported(type);
    }) || "";
  }

  function fileExtension(mimeType) {
    if (String(mimeType).includes("mp4")) return "m4a";
    if (String(mimeType).includes("ogg")) return "ogg";
    return "webm";
  }

  function startMeter(stream) {
    var AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextClass) return;
    analyserContext = new AudioContextClass();
    var source = analyserContext.createMediaStreamSource(stream);
    var analyser = analyserContext.createAnalyser();
    analyser.fftSize = 128;
    source.connect(analyser);
    var values = new Uint8Array(analyser.frequencyBinCount);
    var render = function () {
      analyser.getByteFrequencyData(values);
      var peak = values.reduce(function (max, value) { return Math.max(max, value); }, 0);
      wave.forEach(function (bar, index) {
        var factor = .45 + Math.abs(5 - index) / 10;
        bar.style.height = Math.max(6, Math.min(30, peak * factor / 5)) + "px";
      });
      analyserFrame = requestAnimationFrame(render);
    };
    analyserFrame = requestAnimationFrame(render);
  }

  function countdown(seconds, label, token) {
    return new Promise(function (resolve) {
      clearCountdown();
      var remaining = Math.max(0, Number(seconds) || 0);
      setState(label, remaining);
      if (remaining === 0) {
        resolve();
        return;
      }
      countdownTimer = window.setInterval(function () {
        if (token !== runToken) {
          clearCountdown();
          return;
        }
        remaining -= 1;
        timer.textContent = String(Math.max(0, remaining));
        if (remaining <= 0) {
          clearCountdown();
          resolve();
        }
      }, 1000);
    });
  }

  function playPrompt(question, token) {
    return new Promise(function (resolve, reject) {
      var total = Math.max(1, Number(question.promptPlayLimit) || 1);
      var completed = 0;
      var onEnded = function () {
        if (token !== runToken) return;
        completed += 1;
        if (completed >= total) {
          promptAudioState.classList.remove("is-playing");
          promptAudio.removeEventListener("ended", onEnded);
          resolve();
          return;
        }
        playCount.textContent = "Lần phát " + (completed + 1) + " / " + total;
        promptAudio.currentTime = 0;
        promptAudio.play().catch(function () {
          showAction("Tiếp tục phát đề", function () {
            hideAction();
            promptAudio.play().catch(reject);
          });
        });
      };

      resetAudio();
      promptAudio.src = question.promptAudioReference;
      promptAudio.addEventListener("ended", onEnded);
      promptAudio.addEventListener("error", function onAudioError() {
        promptAudio.removeEventListener("error", onAudioError);
        reject(new Error("Không thể tải audio đề Speaking."));
      }, { once: true });
      promptAudioState.classList.add("is-playing");
      playCount.textContent = "Lần phát 1 / " + total;
      setState("Đang phát đề bài", NaN);
      message.textContent = "Hãy nghe kỹ. Audio sẽ tự phát đúng số lần giáo viên đã cấu hình.";
      promptAudio.play().catch(function () {
        promptAudioState.classList.remove("is-playing");
        message.textContent = "Trình duyệt đang chặn tự phát audio.";
        showAction("Phát đề bài", function () {
          hideAction();
          promptAudioState.classList.add("is-playing");
          promptAudio.play().catch(reject);
        });
      });
    });
  }

  async function beginRecording(question, token) {
    stopMicrophone();
    mediaChunks = [];
    recordedBlob = null;
    try {
      mediaStream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true }
      });
      if (token !== runToken) {
        stopMicrophone();
        return;
      }
      var mimeType = preferredMimeType();
      mediaRecorder = mimeType
        ? new MediaRecorder(mediaStream, { mimeType: mimeType })
        : new MediaRecorder(mediaStream);
      mediaRecorder.addEventListener("dataavailable", function (event) {
        if (event.data && event.data.size > 0) mediaChunks.push(event.data);
      });
      var stopped = new Promise(function (resolve) {
        mediaRecorder.addEventListener("stop", function () {
          recordedBlob = new Blob(mediaChunks, { type: mediaRecorder.mimeType || "audio/webm" });
          stopMicrophone();
          resolve();
        }, { once: true });
      });
      mediaRecorder.start(250);
      startMeter(mediaStream);
      setConnection("Đang ghi âm", "ready");
      message.textContent = "Hãy trả lời rõ ràng. Hệ thống sẽ tự dừng khi hết thời gian.";
      await countdown(question.responseSeconds, "Thời gian trả lời còn lại", token);
      if (mediaRecorder && mediaRecorder.state === "recording") mediaRecorder.stop();
      await stopped;
      if (!recordedBlob || recordedBlob.size === 0) {
        throw new Error("Bản ghi âm trống. Hãy ghi lại câu này.");
      }
    } catch (caught) {
      stopMicrophone();
      throw caught;
    }
  }

  async function uploadRecording(question, token) {
    if (!recordedBlob) throw new Error("Không có bản ghi để tải lên.");
    setState("Đang lưu câu trả lời", NaN);
    setConnection("Đang tải lên", "");
    message.textContent = "Không đóng trang trong khi KSH đang lưu audio.";
    hideAction();
    setError("");
    var body = new FormData();
    body.append("file", recordedBlob, "speaking-answer." + fileExtension(recordedBlob.type));
    var response = await fetch(
      "/practice/attempts/" + attemptId + "/questions/" + question.questionId + "/speaking-media",
      {
        method: "POST",
        credentials: "same-origin",
        headers: csrfToken ? { [csrfHeader]: csrfToken } : {},
        body: body
      }
    );
    var payload = await response.json().catch(function () { return {}; });
    if (!response.ok || payload.status !== "READY" || payload.active !== true) {
      throw new Error(payload.message || "KSH chưa thể lưu bản ghi. Bản ghi vẫn còn trên trình duyệt để thử lại.");
    }
    if (token !== runToken) return;
    setConnection("Đã lưu", "ready");
    setState("Hoàn thành câu " + (question.questionNo || currentIndex + 1), NaN);
    message.textContent = "Bản ghi đã được lưu an toàn. Hệ thống sẽ chuyển sang câu tiếp theo.";
    recordedBlob = null;
  }

  async function executeQuestion(index) {
    runToken += 1;
    var token = runToken;
    currentIndex = index;
    currentQuestion = questions[index];
    clearCountdown();
    stopMicrophone();
    hideAction();
    setError("");
    promptCard.hidden = true;
    promptText.textContent = currentQuestion.prompt || "";
    groupLabel.textContent = currentQuestion.groupLabel || "Phần nói";
    progress.textContent = "Câu " + (index + 1) + " / " + questions.length;
    setConnection("Đang thực hiện", "ready");

    try {
      await playPrompt(currentQuestion, token);
      if (token !== runToken) return;
      promptCard.hidden = false;
      promptAudioState.classList.remove("is-playing");
      await countdown(currentQuestion.preparationSeconds, "Thời gian chuẩn bị còn lại", token);
      if (token !== runToken) return;
      await beginRecording(currentQuestion, token);
      if (token !== runToken) return;
      await uploadRecording(currentQuestion, token);
      if (token !== runToken) return;
      window.setTimeout(function () {
        if (token !== runToken) return;
        if (currentIndex + 1 < questions.length) {
          executeQuestion(currentIndex + 1);
        } else {
          finalizeAttempt();
        }
      }, 1200);
    } catch (caught) {
      stopMicrophone();
      resetAudio();
      setConnection("Cần xử lý", "error");
      setState("Tạm dừng", NaN);
      setError(caught && caught.message ? caught.message : "Không thể tiếp tục phần Speaking.");
      if (recordedBlob) {
        var retryUpload = function () {
          hideAction();
          uploadRecording(currentQuestion, token)
            .then(function () {
              if (currentIndex + 1 < questions.length) executeQuestion(currentIndex + 1);
              else finalizeAttempt();
            })
            .catch(function (retryError) {
              setError(retryError.message || "Vẫn chưa thể lưu bản ghi.");
              showAction("Thử lưu lại", retryUpload);
            });
        };
        showAction("Thử lưu lại", retryUpload);
      } else {
        showAction("Làm lại câu này", function () { executeQuestion(currentIndex); });
      }
    }
  }

  function finalizeAttempt() {
    clearCountdown();
    stopMicrophone();
    resetAudio();
    setConnection("Đang nộp bài", "ready");
    setState("Đã hoàn thành tất cả câu", NaN);
    message.textContent = "KSH đang khóa bản ghi và tạo kết quả Speaking.";
    hideAction();
    allowNavigation = true;
    submitForm.requestSubmit();
  }

  function interruptRequest() {
    if (interruptSent || allowNavigation) return Promise.resolve();
    interruptSent = true;
    return fetch(interruptUrl, {
      method: "POST",
      credentials: "same-origin",
      keepalive: true,
      headers: csrfToken ? { [csrfHeader]: csrfToken } : {}
    }).catch(function () {});
  }

  function confirmExit() {
    clearCountdown();
    stopMicrophone();
    resetAudio();
    root.querySelector("[data-confirm-exit]").disabled = true;
    interruptRequest().finally(function () {
      allowNavigation = true;
      window.location.assign(returnUrl);
    });
  }

  action.addEventListener("click", function () {
    if (typeof pendingAction === "function") pendingAction();
  });
  root.querySelector("[data-exit]").addEventListener("click", function () { exitDialog.showModal(); });
  root.querySelector("[data-confirm-exit]").addEventListener("click", confirmExit);

  window.addEventListener("beforeunload", function (event) {
    if (allowNavigation) return;
    event.preventDefault();
    event.returnValue = "";
  });
  window.addEventListener("pagehide", function () {
    clearCountdown();
    stopMicrophone();
    resetAudio();
    if (!allowNavigation) interruptRequest();
  });

  if (!delivery || questions.length === 0) {
    setConnection("Dữ liệu không hợp lệ", "error");
    setError("Phần Speaking chưa có câu hỏi immutable hợp lệ.");
    return;
  }
  if (!uploadEnabled || !window.MediaRecorder || !navigator.mediaDevices?.getUserMedia) {
    setConnection("Thiết bị không hỗ trợ", "error");
    setError("Trình duyệt hoặc dịch vụ lưu audio chưa sẵn sàng cho phần Speaking.");
    return;
  }

  executeQuestion(0);
})();

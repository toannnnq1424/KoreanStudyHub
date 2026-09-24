(function () {
  "use strict";

  var root = document.querySelector(".spc-page");
  if (!root) return;

  var status = root.querySelector("[data-check-status]");
  var deviceName = root.querySelector("[data-device-name]");
  var message = root.querySelector("[data-check-message]");
  var serviceNotice = root.querySelector("[data-service-notice]");
  var error = root.querySelector("[data-check-error]");
  var meter = root.querySelector("[data-mic-level]");
  var meterShell = root.querySelector("[data-meter-shell]");
  var speakerButton = root.querySelector("[data-test-speaker]");
  var recordButton = root.querySelector("[data-record-sample]");
  var recordLabel = root.querySelector("[data-record-label]");
  var playback = root.querySelector("[data-playback]");
  var sampleAudio = root.querySelector("[data-sample-audio]");
  var samplePlay = root.querySelector("[data-sample-play]");
  var sampleTime = root.querySelector("[data-sample-time]");
  var sampleWave = root.querySelector("[data-sample-wave]");
  var playIcon = root.querySelector("[data-play-icon]");
  var heardConfirm = root.querySelector("[data-heard-confirm]");
  var startButton = root.querySelector("[data-start-speaking]");
  var uploadEnabled = root.dataset.uploadEnabled === "true";

  var stream = null;
  var recorder = null;
  var chunks = [];
  var sampleUrl = null;
  var meterFrame = null;
  var audioContext = null;
  var speakerPlayed = false;
  var sampleReady = false;
  var recordingTimer = null;
  var completionTimer = null;
  var attempt = 0;

  function clearRecordingTimers() {
    clearTimeout(recordingTimer);
    clearTimeout(completionTimer);
    recordingTimer = completionTimer = null;
  }

  function requestMicrophone() {
    // Permission prompts can remain unanswered indefinitely. A late grant must
    // release its tracks instead of reviving a timed-out attempt.
    return new Promise(function (resolve, reject) {
      var expired = false;
      var timer = setTimeout(function () {
        expired = true;
        var failure = new Error("Microphone permission timed out");
        failure.name = "TimeoutError";
        reject(failure);
      }, 15000);
      navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true }
      }).then(function (activeStream) {
        clearTimeout(timer);
        if (expired) activeStream.getTracks().forEach(function (track) { track.stop(); });
        else resolve(activeStream);
      }, function (failure) {
        clearTimeout(timer);
        if (!expired) reject(failure);
      });
    });
  }

  function browserReady() {
    return Boolean(window.MediaRecorder && navigator.mediaDevices
      && typeof navigator.mediaDevices.getUserMedia === "function");
  }

  function setError(text) {
    error.textContent = text || "";
    error.hidden = !text;
  }

  function setStatus(text, state) {
    status.textContent = text;
    root.classList.toggle("is-ready", state === "ready");
    root.classList.toggle("is-recording", state === "recording");
    status.classList.toggle("is-ready", state === "ready");
    status.classList.toggle("is-recording", state === "recording");
  }

  function setServiceNotice(text) {
    if (!serviceNotice) return;
    serviceNotice.textContent = text || "";
    serviceNotice.hidden = !text;
  }

  function setRecordLabel(text) {
    if (recordLabel) {
      recordLabel.textContent = text;
    } else {
      recordButton.textContent = text;
    }
  }

  function localCheckComplete() {
    return browserReady() && speakerPlayed && sampleReady && heardConfirm.checked;
  }

  function updateStart() {
    var readyLocally = localCheckComplete();
    startButton.disabled = !(readyLocally && uploadEnabled);
    setServiceNotice(uploadEnabled
      ? ""
      : "Dịch vụ lưu bản ghi phần Nói đang tắt nên chưa thể bắt đầu bài thật.");
    if (!readyLocally) return;
    setStatus(uploadEnabled ? "Thiết bị sẵn sàng" : "Micro đã sẵn sàng", "ready");
    message.textContent = uploadEnabled
      ? "Bạn có thể bắt đầu. Phần Nói sẽ tự phát đề, đếm giờ và lưu từng câu."
      : "Bạn đã kiểm tra xong micro. Hãy bật dịch vụ lưu bản ghi để vào phần Nói.";
  }

  function stopStream() {
    if (meterFrame) cancelAnimationFrame(meterFrame);
    meterFrame = null;
    if (stream) stream.getTracks().forEach(function (track) { track.stop(); });
    stream = null;
    if (audioContext) audioContext.close().catch(function () {});
    audioContext = null;
    meter.style.setProperty("--level", ".18");
    if (meterShell) meterShell.style.setProperty("--volume", "0%");
  }

  function startMeter(activeStream) {
    var AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextClass) return;
    audioContext = new AudioContextClass();
    var source = audioContext.createMediaStreamSource(activeStream);
    var analyser = audioContext.createAnalyser();
    analyser.fftSize = 256;
    source.connect(analyser);
    var values = new Uint8Array(analyser.frequencyBinCount);
    var render = function () {
      analyser.getByteFrequencyData(values);
      var peak = values.reduce(function (max, value) { return Math.max(max, value); }, 0);
      var level = Math.max(.18, Math.min(1, peak / 170));
      meter.style.setProperty("--level", level.toFixed(2));
      if (meterShell) meterShell.style.setProperty("--volume", Math.min(100, peak / 1.7) + "%");
      meterFrame = requestAnimationFrame(render);
    };
    meterFrame = requestAnimationFrame(render);
  }

  function formatTime(seconds) {
    if (!Number.isFinite(seconds) || seconds < 0) return "00:00";
    var rounded = Math.floor(seconds);
    return String(Math.floor(rounded / 60)).padStart(2, "0") + ":"
      + String(rounded % 60).padStart(2, "0");
  }

  function updatePlaybackUi() {
    if (!sampleAudio) return;
    if (sampleTime) sampleTime.textContent = formatTime(sampleAudio.currentTime || 0);
    var duration = sampleAudio.duration || 0;
    var progress = duration > 0 ? Math.min(1, sampleAudio.currentTime / duration) : 0;
    if (sampleWave) sampleWave.style.setProperty("--progress", Math.round(progress * 100) + "%");
    if (playIcon) {
      playIcon.setAttribute("d", sampleAudio.paused ? "M9 7v10l8-5z" : "M8 6h3v12H8zm5 0h3v12h-3z");
    }
  }

  function preferredMimeType() {
    if (typeof MediaRecorder.isTypeSupported !== "function") return "";
    return ["audio/webm;codecs=opus", "audio/mp4", "audio/webm"].find(function (type) {
      return MediaRecorder.isTypeSupported(type);
    }) || "";
  }

  speakerButton.addEventListener("click", function () {
    setError("");
    try {
      var AudioContextClass = window.AudioContext || window.webkitAudioContext;
      if (!AudioContextClass) throw new Error("unsupported");
      var context = new AudioContextClass();
      var oscillator = context.createOscillator();
      var gain = context.createGain();
      oscillator.frequency.setValueAtTime(520, context.currentTime);
      oscillator.frequency.linearRampToValueAtTime(700, context.currentTime + .5);
      gain.gain.setValueAtTime(.0001, context.currentTime);
      gain.gain.exponentialRampToValueAtTime(.12, context.currentTime + .04);
      gain.gain.exponentialRampToValueAtTime(.0001, context.currentTime + .58);
      oscillator.connect(gain);
      gain.connect(context.destination);
      oscillator.start();
      oscillator.stop(context.currentTime + .6);
      oscillator.addEventListener("ended", function () { context.close().catch(function () {}); });
      speakerPlayed = true;
      message.textContent = "Nếu nghe rõ âm thử, hãy ghi một bản mẫu ngắn bằng micro.";
      updateStart();
    } catch (caught) {
      setError("Không thể phát âm thử. Hãy kiểm tra loa hoặc tai nghe của bạn.");
    }
  });

  recordButton.addEventListener("click", async function () {
    var currentAttempt = ++attempt;
    function fail(caught) {
      if (currentAttempt !== attempt) return;
      attempt++;
      clearRecordingTimers();
      if (recorder && recorder.state !== "inactive") {
        try { recorder.stop(); } catch (ignored) {}
      }
      stopStream();
      sampleReady = false;
      recordButton.disabled = false;
      setRecordLabel("Thử ghi lại");
      setStatus("Chưa hoàn tất kiểm tra", "");
      updateStart();
      var denied = caught && (caught.name === "NotAllowedError" || caught.name === "SecurityError");
      setError(denied
        ? "Quyền micro đang bị từ chối. Hãy cho phép micro trong cài đặt trang web rồi thử lại."
        : caught && caught.name === "TimeoutError"
          ? "Chưa nhận được phản hồi từ micro. Hãy kiểm tra hộp thoại cấp quyền hoặc thử mở trang bằng Chrome/Edge rồi ghi lại."
          : "Không thể hoàn tất bản ghi. Hãy kiểm tra micro và thử ghi lại.");
    }
    setError("");
    sampleReady = false;
    heardConfirm.checked = false;
    updateStart();
    setRecordLabel("Đang chờ quyền micro...");
    setStatus("Đang kết nối micro", "");
    message.textContent = "Cho phép sử dụng micro trong hộp thoại của trình duyệt (tối đa 15 giây).";
    playback.hidden = true;
    sampleAudio.pause();
    recordButton.disabled = true;
    try {
      stopStream();
      var acquired = await requestMicrophone();
      if (currentAttempt !== attempt) {
        acquired.getTracks().forEach(function (track) { track.stop(); });
        return;
      }
      stream = acquired;
      var track = stream.getAudioTracks()[0];
      deviceName.textContent = track && track.label ? track.label : "Micro đã được cấp quyền";
      chunks = [];
      var mimeType = preferredMimeType();
      recorder = mimeType ? new MediaRecorder(stream, { mimeType: mimeType }) : new MediaRecorder(stream);
      recorder.addEventListener("dataavailable", function (event) {
        if (currentAttempt !== attempt) return;
        if (event.data && event.data.size > 0) chunks.push(event.data);
      });
      recorder.addEventListener("stop", function () {
        if (currentAttempt !== attempt) return;
        clearRecordingTimers();
        var blob = new Blob(chunks, { type: recorder.mimeType || "audio/webm" });
        if (!blob.size) { fail(new Error("Empty recording")); return; }
        if (sampleUrl) URL.revokeObjectURL(sampleUrl);
        sampleUrl = URL.createObjectURL(blob);
        sampleAudio.src = sampleUrl;
        playback.hidden = false;
        if (samplePlay) samplePlay.disabled = false;
        if (sampleWave) sampleWave.style.setProperty("--progress", "0%");
        if (sampleTime) sampleTime.textContent = "00:00";
        sampleReady = blob.size > 0;
        stopStream();
        setStatus("Đã ghi âm mẫu", "");
        message.textContent = "Nghe lại bản ghi, sau đó xác nhận chất lượng âm thanh.";
        recordButton.disabled = false;
        setRecordLabel("Ghi lại mẫu");
        updateStart();
      });
      recorder.addEventListener("error", function (event) { fail(event.error); });
      recorder.start(200);
      // Visualisation is optional and must never prevent the recording timer.
      try { startMeter(stream); } catch (ignored) {}
      setRecordLabel("Đang ghi...");
      setStatus("Đang ghi âm 5 giây", "recording");
      message.textContent = "Hãy đọc câu mẫu với âm lượng tự nhiên.";
      recordingTimer = window.setTimeout(function () {
        if (currentAttempt !== attempt) return;
        try { if (recorder && recorder.state !== "inactive") recorder.stop(); }
        catch (caught) { fail(caught); }
      }, 5000);
      completionTimer = window.setTimeout(function () {
        var failure = new Error("Recorder did not finish");
        failure.name = "TimeoutError";
        fail(failure);
      }, 10000);
    } catch (caught) {
      fail(caught);
    }
  });

  heardConfirm.addEventListener("change", updateStart);
  sampleAudio.addEventListener("play", function () { message.textContent = "Hãy nghe hết bản ghi trước khi xác nhận."; });
  sampleAudio.addEventListener("play", updatePlaybackUi);
  sampleAudio.addEventListener("pause", updatePlaybackUi);
  sampleAudio.addEventListener("timeupdate", updatePlaybackUi);
  sampleAudio.addEventListener("ended", function () {
    sampleAudio.currentTime = 0;
    updatePlaybackUi();
  });
  if (samplePlay) {
    samplePlay.addEventListener("click", function () {
      setError("");
      if (!sampleAudio.src) return;
      if (sampleAudio.paused) {
        sampleAudio.play().catch(function () {
          setError("Không thể phát lại bản ghi mẫu trên trình duyệt này.");
        });
      } else {
        sampleAudio.pause();
      }
    });
  }

  if (!browserReady()) {
    setStatus("Trình duyệt không hỗ trợ", "");
    setError("Hãy dùng phiên bản Chrome, Edge hoặc Safari mới có hỗ trợ ghi âm.");
    recordButton.disabled = true;
  } else if (!uploadEnabled) {
    setStatus("Có thể kiểm tra micro", "");
    deviceName.textContent = "Chưa cấp quyền micro";
    setServiceNotice("Dịch vụ lưu bản ghi phần Nói đang tắt nên chưa thể bắt đầu bài thật.");
    message.textContent = "Bạn vẫn có thể phát âm thử và ghi âm mẫu trên thiết bị này.";
  } else {
    setStatus("Sẵn sàng kiểm tra", "");
    deviceName.textContent = "Chưa cấp quyền micro";
  }

  window.addEventListener("pagehide", function () {
    attempt++;
    clearRecordingTimers();
    if (recorder && recorder.state !== "inactive") {
      try { recorder.stop(); } catch (ignored) {}
    }
    stopStream();
    if (sampleUrl) URL.revokeObjectURL(sampleUrl);
  });
})();

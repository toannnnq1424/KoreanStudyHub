(function () {
  "use strict";

  var root = document.querySelector(".spc-page");
  if (!root) return;

  var status = root.querySelector("[data-check-status]");
  var deviceName = root.querySelector("[data-device-name]");
  var message = root.querySelector("[data-check-message]");
  var error = root.querySelector("[data-check-error]");
  var meter = root.querySelector("[data-mic-level]");
  var speakerButton = root.querySelector("[data-test-speaker]");
  var recordButton = root.querySelector("[data-record-sample]");
  var playback = root.querySelector("[data-playback]");
  var sampleAudio = root.querySelector("[data-sample-audio]");
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
    status.classList.toggle("is-ready", state === "ready");
    status.classList.toggle("is-recording", state === "recording");
  }

  function updateStart() {
    startButton.disabled = !(browserReady() && uploadEnabled && speakerPlayed
      && sampleReady && heardConfirm.checked);
    if (startButton.disabled) return;
    setStatus("Thiết bị sẵn sàng", "ready");
    message.textContent = "Bạn có thể bắt đầu. Phần Speaking sẽ tự phát đề, đếm giờ và lưu từng câu.";
  }

  function stopStream() {
    if (meterFrame) cancelAnimationFrame(meterFrame);
    meterFrame = null;
    if (stream) stream.getTracks().forEach(function (track) { track.stop(); });
    stream = null;
    if (audioContext) audioContext.close().catch(function () {});
    audioContext = null;
    meter.style.width = "0";
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
      meter.style.width = Math.max(3, Math.min(100, peak / 1.7)) + "%";
      meterFrame = requestAnimationFrame(render);
    };
    meterFrame = requestAnimationFrame(render);
  }

  function preferredMimeType() {
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
    setError("");
    sampleReady = false;
    heardConfirm.checked = false;
    updateStart();
    recordButton.disabled = true;
    try {
      stopStream();
      stream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true }
      });
      var track = stream.getAudioTracks()[0];
      deviceName.textContent = track && track.label ? track.label : "Micro đã được cấp quyền";
      chunks = [];
      var mimeType = preferredMimeType();
      recorder = mimeType ? new MediaRecorder(stream, { mimeType: mimeType }) : new MediaRecorder(stream);
      recorder.addEventListener("dataavailable", function (event) {
        if (event.data && event.data.size > 0) chunks.push(event.data);
      });
      recorder.addEventListener("stop", function () {
        var blob = new Blob(chunks, { type: recorder.mimeType || "audio/webm" });
        if (sampleUrl) URL.revokeObjectURL(sampleUrl);
        sampleUrl = URL.createObjectURL(blob);
        sampleAudio.src = sampleUrl;
        playback.hidden = false;
        sampleReady = blob.size > 0;
        stopStream();
        setStatus("Đã ghi âm mẫu", "");
        message.textContent = "Nghe lại bản ghi, sau đó xác nhận chất lượng âm thanh.";
        recordButton.disabled = false;
        recordButton.textContent = "Ghi lại mẫu";
        updateStart();
      });
      recorder.start(200);
      startMeter(stream);
      setStatus("Đang ghi âm 5 giây", "recording");
      message.textContent = "Hãy đọc câu mẫu với âm lượng tự nhiên.";
      window.setTimeout(function () {
        if (recorder && recorder.state === "recording") recorder.stop();
      }, 5000);
    } catch (caught) {
      stopStream();
      recordButton.disabled = false;
      setStatus("Kiểm tra thất bại", "");
      var denied = caught && (caught.name === "NotAllowedError" || caught.name === "SecurityError");
      setError(denied
        ? "Quyền micro đang bị từ chối. Hãy cho phép micro trong cài đặt trang web rồi thử lại."
        : "Không tìm thấy micro có thể sử dụng. Hãy kiểm tra thiết bị rồi thử lại.");
    }
  });

  heardConfirm.addEventListener("change", updateStart);
  sampleAudio.addEventListener("play", function () { message.textContent = "Hãy nghe hết bản ghi trước khi xác nhận."; });

  if (!browserReady()) {
    setStatus("Trình duyệt không hỗ trợ", "");
    setError("Hãy dùng phiên bản Chrome, Edge hoặc Safari mới có hỗ trợ ghi âm.");
    recordButton.disabled = true;
  } else if (!uploadEnabled) {
    setStatus("Dịch vụ chưa sẵn sàng", "");
    setError("Dịch vụ lưu bản ghi Speaking đang tắt. Bạn chưa thể bắt đầu phần này.");
    recordButton.disabled = true;
  } else {
    setStatus("Sẵn sàng kiểm tra", "");
    deviceName.textContent = "Chưa cấp quyền micro";
  }

  window.addEventListener("pagehide", function () {
    stopStream();
    if (sampleUrl) URL.revokeObjectURL(sampleUrl);
  });
})();

(() => {
  "use strict";

  const ALLOWED = new Set([
    "NANUM_MYEONGJO",
    "DIPHYLLEIA",
    "GOWUN_BATANG",
    "NOTO_SERIF_KR",
    "NANUM_GOTHIC",
    "GOTHIC_A1",
    "GOWUN_DODUM",
    "ORBIT",
    "SUNFLOWER",
    "BLACK_AND_WHITE_PICTURE",
    "GUGI",
    "POOR_STORY",
    "SINGLE_DAY",
    "GAEGU",
    "HI_MELODY",
    "NANUM_GOTHIC_CODING",
    "NANUM_PEN_SCRIPT"
  ]);
  const ALLOWED_SIZES = new Set(["DEFAULT", "LARGE", "EXTRA_LARGE"]);

  const readMeta = (name) =>
    document.querySelector(`meta[name="${name}"]`)?.content?.trim() || "";

  const serverFont = readMeta("practice-korean-font");
  const serverSize = readMeta("practice-korean-font-size");
  const accountId = readMeta("practice-korean-font-account");
  const namespace = readMeta("practice-korean-font-cache");
  const schemaVersion = Number.parseInt(
    readMeta("practice-korean-font-schema"),
    10
  );

  if (!ALLOWED.has(serverFont)
      || !ALLOWED_SIZES.has(serverSize)
      || !accountId
      || namespace !== "practice-korean-font-preference-v2"
      || schemaVersion !== 2) {
    return;
  }

  const cacheKey = `${namespace}:${accountId}`;
  document.documentElement.dataset.practiceKoreanFont = serverFont;
  document.documentElement.dataset.practiceKoreanSize = serverSize;

  try {
    localStorage.setItem(
      cacheKey,
      `${schemaVersion}|${serverFont}|${serverSize}`
    );
  } catch (_ignored) {
    // Storage denial/quota must never block Practice rendering.
  }

  const form = document.querySelector("[data-practice-korean-font-form]");
  if (!form) {
    return;
  }

  const status = form.querySelector("[data-practice-korean-font-status]");
  form.addEventListener("change", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLInputElement)) {
      return;
    }
    if (target.name === "koreanFont" && ALLOWED.has(target.value)) {
      document.documentElement.dataset.practiceKoreanFont = target.value;
    } else if (target.name === "koreanFontSize"
               && ALLOWED_SIZES.has(target.value)) {
      document.documentElement.dataset.practiceKoreanSize = target.value;
    } else {
      return;
    }
    if (status) {
      status.textContent =
        "Đã cập nhật phần xem trước. Nhấn Lưu hiển thị để đồng bộ tài khoản.";
    }
  });
})();

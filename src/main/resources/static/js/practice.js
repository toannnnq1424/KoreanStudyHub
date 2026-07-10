(function () {
  const escape = (value) => String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');

  const CRITERIA = {
    strengths: [
      'W_ADVANCED_GRAMMAR_STRUCTURES',
      'W_REGISTER_HONORIFIC_ACCURACY',
      'W_APPROPRIATE_VOCABULARY_USAGE',
      'W_TOPIC_SPECIFIC_EXPRESSIONS',
      'W_NATURAL_KOREAN_EXPRESSIONS',
      'W_WON_GO_JI',
      'W_SENTENCE_VARIETY',
    ],
    needs: [
      'W_VOCABULARY_ERRORS',
      'W_GRAMMAR_ERRORS',
      'W_PARTICLE_ERRORS',
      'W_REPETITIVE_WORDS_EXPRESSIONS',
      'W_AWKWARD_UNNATURAL_EXPRESSIONS',
      'W_SENTENCE_STRUCTURE_ISSUES',
      'W_REGISTER_CONSISTENCY_ISSUES',
      'W_SPELLING_SPACING_ERRORS',
    ],
  };

  const LABELS = {
    W_ADVANCED_GRAMMAR_STRUCTURES: 'Cách chữ / ngữ pháp tốt',
    W_REGISTER_HONORIFIC_ACCURACY: 'Văn phong nhất quán',
    W_APPROPRIATE_VOCABULARY_USAGE: 'Loại bỏ khẩu ngữ',
    W_TOPIC_SPECIFIC_EXPRESSIONS: 'Từ vựng chủ đề',
    W_NATURAL_KOREAN_EXPRESSIONS: 'Diễn đạt tự nhiên',
    W_WON_GO_JI: 'Won-go-ji',
    W_SENTENCE_VARIETY: 'Dung lượng bài',
    W_VOCABULARY_ERRORS: 'Lỗi từ vựng',
    W_GRAMMAR_ERRORS: 'Lỗi ngữ pháp',
    W_PARTICLE_ERRORS: 'Lỗi tiểu từ',
    W_REPETITIVE_WORDS_EXPRESSIONS: 'Lặp từ/cụm từ',
    W_AWKWARD_UNNATURAL_EXPRESSIONS: 'Diễn đạt gượng',
    W_SENTENCE_STRUCTURE_ISSUES: 'Cấu trúc câu',
    W_REGISTER_CONSISTENCY_ISSUES: 'Văn phong bất nhất',
    W_SPELLING_SPACING_ERRORS: 'Chính tả/cách chữ',
  };

  const state = {
    feedback: null,
    activeTab: 'overview',
    activeCriterion: null,
  };

  // ─── Annotation renderer: slice student_text using start/end from annotations[]
  const renderAnnotatedText = (studentText, annotations, filterKind, filterCriterion) => {
    if (!studentText) return '';
    if (!annotations || !annotations.length) return escapeHtml(studentText);

    // Filter relevant annotations
    const relevant = annotations.filter((a) => {
      if (a.start < 0 || a.end < 0) return false;  // evidence not found in text
      if (filterKind && a.kind !== filterKind) return false;
      if (filterCriterion && a.criterionId !== filterCriterion) return false;
      return true;
    });

    if (!relevant.length) return escapeHtml(studentText);

    // Sort by start, then merge overlapping ranges
    const sorted = [...relevant].sort((a, b) => a.start - b.start);
    let result = '';
    let cursor = 0;
    let index = 0;

    for (const ann of sorted) {
      if (ann.start < cursor) continue; // skip overlapping
      index++;
      result += escapeHtml(studentText.slice(cursor, ann.start));
      const label = escape(LABELS[ann.criterionId] || ann.criterionId);
      const colorClass = ann.kind === 'strength' ? 'strength' : 'need';
      const isActive = !filterCriterion || ann.criterionId === filterCriterion;
      result += `<mark class="ksh-mark ${colorClass}${isActive ? ' active' : ''}" title="${label}">${escapeHtml(studentText.slice(ann.start, ann.end))}<sup>${index}</sup></mark>`;
      cursor = ann.end;
    }
    result += escapeHtml(studentText.slice(cursor));
    return result;
  };

  const escapeHtml = (str) => String(str || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');

  const updateAnswerHighlights = () => {
    const wrap = document.getElementById('ksh-answer-review');
    if (!wrap || !state.feedback) return;
    const annotations = Array.isArray(state.feedback.annotations) ? state.feedback.annotations : [];
    const studentText = state.feedback.student_text || '';

    wrap.querySelectorAll('.ksh-answer-card').forEach((card, idx) => {
      const target = card.querySelector('.ksh-answer-text');
      if (!target) return;
      const showAnnotations = idx === 0 && ['strengths', 'needs'].includes(state.activeTab);
      if (showAnnotations && (studentText || annotations.length)) {
        const filterKind = state.activeTab === 'needs' ? 'need' : 'strength';
        target.innerHTML = renderAnnotatedText(studentText, annotations, filterKind, state.activeCriterion);
      } else {
        target.textContent = card.dataset.answer || '';
      }
    });
  };

  const criterionCount = (annotations, criterionId, kind) => (
    (annotations || []).filter((a) => a.criterionId === criterionId && a.kind === kind).length
  );

  const renderChips = (kind, annotations) => {
    const ids = kind === 'strengths' ? CRITERIA.strengths : CRITERIA.needs;
    const annotKind = kind === 'strengths' ? 'strength' : 'need';
    const chips = ids.map((id) => {
      const count = criterionCount(annotations, id, annotKind);
      if (!count) return '';
      const active = state.activeCriterion === id ? ' active' : '';
      return `<button class="ksh-chip ${kind}${active}" type="button" data-criterion="${id}">
        <span>${count}</span>${escape(LABELS[id] || id)}
      </button>`;
    }).join('');
    return chips ? `<div class="ksh-chip-row">${chips}</div>` : '';
  };

  // Convert 1–9 band score to 100-point scale
  const toHundred = (score, maxScore) => {
    const s = parseFloat(score) || 0;
    const max = parseFloat(maxScore);
    if (max > 0) return Math.round((s / max) * 100);
    return Math.round((s / 9) * 100);
  };

  const renderOverview = (data) => {
    const rubric = Array.isArray(data.rubric_scores) ? data.rubric_scores : [];
    const raw = data.raw_score_max
      ? `<em>Điểm câu viết theo bộ chấm: ${escape(data.raw_score || 0)}/${escape(data.raw_score_max)}</em>`
      : '';
    return `
      <section class="ksh-ai-summary overview">
        <span>${escape(data.band_label || 'KSH TOPIK Writing')}</span>
        <strong>${Math.round(parseFloat(data.percentage !== undefined ? data.percentage : toHundred(data.overall_score || data.score || 0)) || 0)}</strong><em class="ksh-score-denom">/100</em>
        ${raw}
        <p>${escape(data.summary_vi || data.summary || 'Chưa có nhận xét tổng quan.')}</p>
      </section>
      <div class="ksh-rubric-grid">
        ${rubric.map((item, idx) => `
          <article class="ksh-rubric-item">
            <button class="ksh-rubric-toggle" type="button" data-rubric="${idx}">
              <span>${escape(item.name)}</span>
              <strong>${item.score}</strong><small>/${item.maxScore || 10}</small>
            </button>
            <p class="rubric-feedback">${escape(item.feedback)}</p>
          </article>
        `).join('')}
      </div>
    `;
  };

  const renderFindingCorrection = (item) => {
    if (!item.correction) return '';
    return `<div class="ksh-correction">
      <span>Sửa gợi ý</span>
      <p><del class="wrong-word">${escape(item.evidence)}</del><b>→</b><ins class="correct-word">${escape(item.correction)}</ins></p>
    </div>`;
  };

  const renderFindings = (kind, data) => {
    // Source data: use items from strengths/needs_improvement arrays (for display)
    // Chip counts: use annotations[] for accuracy
    const annotations = Array.isArray(data.annotations) ? data.annotations : [];
    const annotKind = kind === 'strengths' ? 'strength' : 'need';
    const items = kind === 'strengths'
      ? (Array.isArray(data.strengths) ? data.strengths : [])
      : (Array.isArray(data.needs_improvement) ? data.needs_improvement : []);
    const filtered = state.activeCriterion
      ? items.filter((item) => item.criterionId === state.activeCriterion)
      : items;
    const empty = kind === 'strengths'
      ? 'KSH chưa tìm thấy điểm mạnh đủ bằng chứng trong bài này.'
      : 'KSH chưa tìm thấy lỗi đủ bằng chứng trong bài này.';
    const summary = kind === 'strengths'
      ? `KSH tìm thấy ${items.length} điểm mạnh có bằng chứng trong bài.`
      : `KSH tìm thấy ${items.length} điểm cần cải thiện có bằng chứng trong bài.`;
    return `
      ${renderChips(kind, annotations)}
      <p class="ksh-finding-summary">${escape(summary)}</p>
      <ul class="ksh-feedback-list ${kind}">
        ${filtered.length ? filtered.map((item, index) => `
          <li>
            <span class="ksh-finding-number">${index + 1}</span>
            <div class="ksh-finding-content">
              <strong>${escape(item.vietnameseLabel || LABELS[item.criterionId] || item.criterionId)}
                <span>${escape(item.koreanLabel || '')}</span>
              </strong>
              <code>${escape(item.evidence)}</code>
              <p>${escape(item.explanationVi)}</p>
              ${renderFindingCorrection(item)}
            </div>
          </li>
        `).join('') : `<li class="ksh-feedback-empty">${escape(empty)}</li>`}
      </ul>
    `;
  };

  const renderUpgrade = (data) => {
    const rewrites = Array.isArray(data.sentence_rewrites) ? data.sentence_rewrites : [];
    const upgraded = data.upgraded_answer_annotated || data.upgraded_answer || data.corrected_version || '';
    return `
      <section class="ksh-ai-summary upgrade">
        <span>Bài viết nâng cấp</span>
        <p>Giữ ý tưởng gốc, chỉnh văn phong TOPIK, ngữ pháp, cách chữ và từ vựng.</p>
      </section>
      <section class="ksh-ai-text">
        <div>${parseAnnotatedText(upgraded, null) || 'KSH chưa có bản nâng cấp.'}</div>
      </section>
      ${rewrites.length ? `<table class="ksh-rewrite-table">
        <thead><tr><th>Gốc</th><th>Nâng cấp</th><th>Lý do</th></tr></thead>
        <tbody>${rewrites.map((row) => `
          <tr>
            <td>${escape(row.original)}</td>
            <td>${escape(row.upgraded)}</td>
            <td>${escape(row.reason)}</td>
          </tr>
        `).join('')}</tbody>
      </table>` : ''}
    `;
  };

  const renderSample = (data) => `
    <section class="ksh-ai-text">
      <h3>Bài mẫu tham khảo</h3>
      <div>${escape(data.sample_answer || 'KSH chưa có bài mẫu cho lần chấm này.')}</div>
    </section>
  `;

  const renderAiFeedback = () => {
    const source = document.getElementById('ai-feedback-json');
    const target = document.getElementById('ai-feedback-render');
    if (!source || !target) return;

    try {
      state.feedback = JSON.parse(source.textContent || '{}');
    } catch (e) {
      target.innerHTML = '<p>Không đọc được phản hồi AI.</p>';
      return;
    }

    const tabs = document.querySelectorAll('.ksh-result-tabs button[data-tab]');
    tabs.forEach((button) => {
      button.addEventListener('click', () => {
        state.activeTab = button.dataset.tab;
        state.activeCriterion = null;
        tabs.forEach((tab) => tab.classList.toggle('active', tab === button));
        paintFeedback();
      });
    });

    paintFeedback();
  };

  const paintFeedback = () => {
    const target = document.getElementById('ai-feedback-render');
    const data = state.feedback || {};
    if (!target) return;
    target.className = `ksh-feedback-render ${state.activeTab}`;
    if (state.activeTab === 'strengths') {
      target.innerHTML = renderFindings('strengths', data);
    } else if (state.activeTab === 'needs') {
      target.innerHTML = renderFindings('needs', data);
    } else if (state.activeTab === 'upgrade') {
      target.innerHTML = renderUpgrade(data);
    } else if (state.activeTab === 'sample') {
      target.innerHTML = renderSample(data);
    } else {
      target.innerHTML = renderOverview(data);
    }

    target.querySelectorAll('button[data-criterion]').forEach((button) => {
      button.addEventListener('click', () => {
        state.activeCriterion = state.activeCriterion === button.dataset.criterion ? null : button.dataset.criterion;
        paintFeedback();
      });
    });
    target.querySelectorAll('.ksh-rubric-toggle').forEach((button) => {
      button.addEventListener('click', () => {
        button.closest('.ksh-rubric-item')?.classList.toggle('open');
      });
    });
    updateAnswerHighlights();
  };

  const initDraftEditor = () => {
    const form = document.getElementById('draft-form');
    if (!form) return;

    const list = document.getElementById('question-list');
    const hidden = document.getElementById('draftJson');
    const addButtons = [
      document.getElementById('add-question'),
      document.getElementById('add-question-bottom'),
    ].filter(Boolean);

    const createCard = (data = {}) => {
      const article = document.createElement('article');
      article.className = 'practice-draft-card';
      article.innerHTML = `
        <div class="practice-draft-head">
          <strong>${escape(data.questionNo || 'Câu mới')}</strong>
          <button type="button" class="draft-remove">Xóa</button>
        </div>
        <div class="practice-form-grid">
          <label><span>Số câu</span><input class="q-no" type="number" min="1" value="${escape(data.questionNo || '')}"></label>
          <label>
            <span>Kiểu câu hỏi</span>
            <select class="q-type">
              ${['MCQ','TRUE_FALSE_NOT_GIVEN','MATCHING_INFORMATION','FILL_BLANK','ORDERING','TEXT_COMPLETION','SHORT_TEXT','ESSAY','SPEAKING']
                .map((type) => {
                  let label = type;
                  switch(type) {
                    case 'MCQ': label = '객관식 (Trắc nghiệm)'; break;
                    case 'TRUE_FALSE_NOT_GIVEN': label = '맞다/틀리다 (Đúng/Sai)'; break;
                    case 'MATCHING_INFORMATION': label = '선 잇기 (Nối thông tin)'; break;
                    case 'FILL_BLANK': label = '빈칸 채우기 (Điền từ)'; break;
                    case 'ORDERING': label = '순서 배열 (Sắp xếp thứ tự)'; break;
                    case 'TEXT_COMPLETION': label = '문장 완성 (Hoàn thành câu)'; break;
                    case 'SHORT_TEXT': label = '단답형 (Trả lời ngắn)'; break;
                    case 'ESSAY': label = '쓰기/주관식 (Tự luận)'; break;
                    case 'SPEAKING': label = '말하기 (Nói)'; break;
                  }
                  return `<option value="${type}" ${data.questionType === type ? 'selected' : ''}>${label}</option>`;
                }).join('')}
            </select>
          </label>
          <label class="practice-form-wide">
            <span>Prompt</span>
            <textarea class="q-prompt" rows="3">${escape(data.prompt || '')}</textarea>
          </label>
          <label class="practice-form-wide">
            <span>Options, mỗi dòng một lựa chọn</span>
            <textarea class="q-options" rows="3">${escape(data.optionsText || '')}</textarea>
          </label>
          <label>
            <span>Đáp án</span>
            <input class="q-answer" type="text" value="${escape(data.answerKey || '')}">
          </label>
          <label>
            <span>Điểm</span>
            <input class="q-points" type="number" min="0" step="0.5" value="${escape(data.points || 1)}">
          </label>
          <label class="practice-form-wide">
            <span>Giải thích</span>
            <textarea class="q-explain" rows="3">${escape(data.explanation || '')}</textarea>
          </label>
        </div>
      `;
      article.querySelector('.draft-remove').addEventListener('click', () => {
        article.remove();
        refreshCount();
      });
      return article;
    };

    const refreshCount = () => {
      const count = list.querySelectorAll('.practice-draft-card').length;
      document.querySelectorAll('.preview-bottom > span').forEach((node) => {
        node.textContent = `${count} câu`;
      });
    };

    addButtons.forEach((btn) => btn.addEventListener('click', () => {
      list.appendChild(createCard({ questionType: 'MCQ', points: 1 }));
      refreshCount();
      list.lastElementChild.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }));

    form.addEventListener('submit', () => {
      const draft = {
        title: document.getElementById('draftTitle').value.trim(),
        description: document.getElementById('draftDescription').value.trim(),
        skill: document.getElementById('draftSkill').value,
        topikLevel: document.getElementById('draftLevel').value,
        scope: document.getElementById('draftScope').value,
        classId: document.getElementById('draftClassId').value || null,
        sourcePdfPath: null,
        metadataJson: null,
        originalFilename: null,
        questions: [],
      };

      list.querySelectorAll('.practice-draft-card').forEach((card) => {
        const options = card.querySelector('.q-options').value
          .split(/\r?\n/)
          .map((line) => line.trim())
          .filter(Boolean);
        draft.questions.push({
          questionNo: Number(card.querySelector('.q-no').value || 0),
          questionType: card.querySelector('.q-type').value,
          prompt: card.querySelector('.q-prompt').value.trim(),
          options,
          optionsText: card.querySelector('.q-options').value,
          answerKey: card.querySelector('.q-answer').value.trim(),
          explanation: card.querySelector('.q-explain').value.trim(),
          points: Number(card.querySelector('.q-points').value || 1),
        });
      });

      hidden.value = JSON.stringify(draft);
    });

    refreshCount();
  };

  const initRoomTimer = () => {
    const timer = document.querySelector('[data-room-timer]');
    if (!timer) return;
    const form = document.querySelector('form.ksh-room') || document.querySelector('.ksh-room');
    let seconds = Number(timer.dataset.roomTimer || 2400);
    const paint = () => {
      const minutes = Math.floor(seconds / 60).toString().padStart(2, '0');
      const remain = (seconds % 60).toString().padStart(2, '0');
      timer.textContent = `${minutes}:${remain}`;
      timer.classList.toggle('danger', seconds <= 300);
    };
    paint();
    const interval = setInterval(() => {
      seconds -= 1;
      paint();
      if (seconds <= 0) {
        clearInterval(interval);
        if (form) form.requestSubmit();
      }
    }, 1000);
  };

  const initSplitDivider = () => {
    const splitRoom = document.querySelector('.ksh-split-room');
    const divider = document.querySelector('.ksh-drag-divider');
    if (!splitRoom || !divider) return;
    let dragging = false;
    const update = (clientX) => {
      const rect = splitRoom.getBoundingClientRect();
      const percent = Math.max(34, Math.min(66, ((clientX - rect.left) / rect.width) * 100));
      splitRoom.style.setProperty('--ksh-prompt-width', `${percent}%`);
    };
    divider.addEventListener('pointerdown', (event) => {
      dragging = true;
      divider.setPointerCapture(event.pointerId);
      splitRoom.classList.add('is-resizing');
    });
    divider.addEventListener('pointermove', (event) => {
      if (dragging) update(event.clientX);
    });
    divider.addEventListener('pointerup', (event) => {
      dragging = false;
      divider.releasePointerCapture(event.pointerId);
      splitRoom.classList.remove('is-resizing');
    });
  };

  const initRoomCbt = () => {
    const room = document.querySelector('.ksh-room');
    if (!room) return;

    // 1. Copy-paste lockdown
    ['copy', 'cut', 'paste'].forEach((eventName) => {
      room.addEventListener(eventName, (event) => event.preventDefault());
    });
    room.addEventListener('keydown', (event) => {
      const key = event.key.toLowerCase();
      if ((event.ctrlKey || event.metaKey) && ['c', 'x', 'v'].includes(key)) {
        event.preventDefault();
      }
    });

    // Custom right-click context menu (lockdown)
    let contextMenu = document.getElementById('ksh-custom-contextmenu');
    if (!contextMenu) {
      contextMenu = document.createElement('div');
      contextMenu.id = 'ksh-custom-contextmenu';
      contextMenu.style.cssText = 'position: fixed; display: none; background: #fff; border: 1px solid var(--ksh-line); border-radius: 10px; box-shadow: var(--ksh-shadow); z-index: 1000; padding: 6px;';
      contextMenu.innerHTML = `
        <button type="button" class="ksh-ctx-btn highlight-yellow" style="display:block; width:100%; border:0; background:none; text-align:left; padding:8px 12px; cursor:pointer; font-weight:800; color:#cda000; border-radius:6px; font-size:14px;">Tô màu (Highlight)</button>
        <button type="button" class="ksh-ctx-btn note-green" style="display:block; width:100%; border:0; background:none; text-align:left; padding:8px 12px; cursor:pointer; font-weight:800; color:#16a34a; border-radius:6px; font-size:14px;">Ghi chú (Note)</button>
      `;
      document.body.appendChild(contextMenu);
    }

    // IMPORTANT: Prevent text selection from clearing when clicking/mousedown on the context menu
    contextMenu.addEventListener('mousedown', (event) => {
      event.preventDefault();
    });

    const notesList = room.querySelector('.ksh-notes-list');

    // Context menu trigger on right click inside source cards
    document.addEventListener('contextmenu', (event) => {
      const sourceCard = event.target.closest('.ksh-source-card');
      if (sourceCard) {
        const selection = window.getSelection();
        if (selection && !selection.isCollapsed && selection.toString().trim().length > 0) {
          event.preventDefault();
          contextMenu.style.display = 'block';
          contextMenu.style.left = `${event.clientX}px`;
          contextMenu.style.top = `${event.clientY}px`;
          return;
        }
      }
      contextMenu.style.display = 'none';
      event.preventDefault();
    });

    // Contextmenu button click handler
    contextMenu.addEventListener('click', (event) => {
      const btn = event.target.closest('.ksh-ctx-btn');
      if (!btn) return;
      const selection = window.getSelection();
      if (!selection || selection.isCollapsed) return;
      const text = selection.toString();
      const range = selection.getRangeAt(0);

      if (btn.classList.contains('highlight-yellow')) {
        const mark = document.createElement('mark');
        mark.className = 'ksh-highlight-yellow';
        try {
          mark.appendChild(range.extractContents());
          range.insertNode(mark);
        } catch (e) { console.error(e); }
      } else if (btn.classList.contains('note-green')) {
        const noteInput = window.prompt("Nhập ghi chú cho đoạn văn bản này:");
        if (noteInput && noteInput.trim().length > 0) {
          const mark = document.createElement('mark');
          mark.className = 'ksh-highlight-green';
          try {
            mark.appendChild(range.extractContents());
            range.insertNode(mark);
            
            // Append to list
            if (notesList) {
              if (notesList.textContent.includes('Ghi chú của bạn sẽ hiển thị')) {
                notesList.textContent = '';
              }
              const row = document.createElement('div');
              row.className = 'ksh-note-row';
              row.innerHTML = `<span>${escapeHtml(text)}</span><p>${escapeHtml(noteInput)}</p>`;
              notesList.prepend(row);
            }
          } catch (e) { console.error(e); }
        }
      }
      selection.removeAllRanges();
      contextMenu.style.display = 'none';
    });

    document.addEventListener('click', (event) => {
      if (contextMenu && !contextMenu.contains(event.target)) {
        contextMenu.style.display = 'none';
      }
    });

    const escapeHtml = (str) => {
      return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
    };

    // 3. Question Group Navigation & Render active only
    const cards = room.querySelectorAll('.ksh-source-card, .ksh-group-content');
    const drawer = document.getElementById('ksh-navigator-drawer');
    const drawerOverlay = document.getElementById('ksh-drawer-overlay');
    const drawerGroupBtns = room.querySelectorAll('.ksh-drawer-group-btn');
    let activeGroup = '';

    // Extract list of all unique group labels
    const groups = Array.from(new Set(Array.from(drawerGroupBtns).map(btn => btn.dataset.group)));
    if (groups.length > 0) {
      activeGroup = groups[0];
    }

    const showGroup = (groupLabel) => {
      if (!groupLabel) return;
      activeGroup = groupLabel;

      // Update active group label in Header
      const activeLabelEl = document.getElementById('ksh-active-group-label');
      if (activeLabelEl) {
        activeLabelEl.textContent = groupLabel;
      }
      
      // Hide all question cards and source cards not matching
      cards.forEach((card) => {
        if (card.dataset.group === groupLabel) {
          card.style.display = 'block';
        } else {
          card.style.display = 'none';
        }
      });

      // Enable/disable navigation buttons
      const currentIndex = groups.indexOf(groupLabel);
      const prevBtn = room.querySelector('.ksh-prev-group-btn');
      const nextBtn = room.querySelector('.ksh-next-group-btn');
      if (prevBtn) {
        prevBtn.disabled = currentIndex === 0;
        const prevGroup = groups[currentIndex - 1];
        prevBtn.textContent = prevGroup ? `← Nhóm trước (${prevGroup})` : `← Nhóm trước`;
      }
      if (nextBtn) {
        nextBtn.disabled = currentIndex === groups.length - 1;
        const nextGroup = groups[currentIndex + 1];
        nextBtn.textContent = nextGroup ? `Nhóm sau (${nextGroup}) →` : `Nhóm sau →`;
      }
    };

    // 4. Progress and drawer status colors
    const updateProgressAndDrawerColors = () => {
      let totalQuestions = 0;
      let answeredQuestions = 0;
      const flaggedMap = {}; // Tracks flagged question numbers
      
      // Highlight options when checked
      room.querySelectorAll('.option input[type="radio"]').forEach((radio) => {
        const optionLabel = radio.closest('.option');
        if (optionLabel) {
          if (radio.checked) {
            optionLabel.classList.add('selected');
          } else {
            optionLabel.classList.remove('selected');
          }
        }
      });

      // Check flagged checkboxes
      room.querySelectorAll('.ksh-flag-review').forEach((cb) => {
        const qNo = cb.dataset.questionNo;
        flaggedMap[qNo] = cb.checked;
      });

      // Collect answers
      const answersMap = {}; // Tracks answered status of each question ID
      const questionGroupsMap = {}; // Maps groupLabel to array of questions

      // Find all MCQ radios
      room.querySelectorAll('.ksh-options input[type="radio"]').forEach((radio) => {
        const name = radio.name;
        if (!answersMap[name]) {
          answersMap[name] = false;
        }
        if (radio.checked) {
          answersMap[name] = true;
        }
      });

      // Find all text inputs / textareas
      room.querySelectorAll('.ksh-input-answer input, .ksh-speaking-input textarea, .ksh-writing-box textarea').forEach((input) => {
        const name = input.name;
        answersMap[name] = input.value.trim().length > 0;
      });

      // Group question details by their parent .ksh-group-content
      const groupElements = room.querySelectorAll('.ksh-group-content');
      groupElements.forEach((groupEl) => {
        const groupLabel = groupEl.dataset.group;
        if (!questionGroupsMap[groupLabel]) {
          questionGroupsMap[groupLabel] = [];
        }

        groupEl.querySelectorAll('.ksh-question-block').forEach((qCard) => {
          const cb = qCard.querySelector('.ksh-flag-review');
          const qNo = cb ? cb.dataset.questionNo : null;
          
          let questionInputName = '';
          const radio = qCard.querySelector('input[type="radio"]');
          if (radio) {
            questionInputName = radio.name;
          } else {
            const txt = qCard.querySelector('input[type="text"], textarea');
            if (txt) {
              questionInputName = txt.name;
            }
          }

          if (questionInputName) {
            questionGroupsMap[groupLabel].push({
              name: questionInputName,
              qNo: qNo,
              answered: answersMap[questionInputName] || false
            });
          }
        });
      });

      // Calculate total done
      const allQuestionNames = Object.keys(answersMap);
      totalQuestions = allQuestionNames.length;
      answeredQuestions = allQuestionNames.filter(k => answersMap[k]).length;

      // Update Header progress
      const doneEl = document.getElementById('ksh-progress-done');
      if (doneEl) {
        doneEl.textContent = answeredQuestions;
      }

      // Update each drawer group button color class
      drawerGroupBtns.forEach((btn) => {
        const groupLabel = btn.dataset.group;
        const qList = questionGroupsMap[groupLabel] || [];
        
        const isGroupActive = (groupLabel === activeGroup);
        const isGroupFlagged = qList.some(q => flaggedMap[q.qNo]);
        const isGroupAllAnswered = qList.length > 0 && qList.every(q => q.answered);

        btn.className = 'ksh-drawer-group-btn';

        if (isGroupActive) {
          btn.classList.add('status-current');
        } else if (isGroupFlagged) {
          btn.classList.add('status-red');
        } else if (isGroupAllAnswered) {
          btn.classList.add('status-done');
        } else {
          btn.classList.add('status-gray');
        }
      });
    };

    // Setup events for answer changes to trigger progress recalculation
    room.addEventListener('change', (event) => {
      if (event.target.matches('input[type="radio"], input[type="checkbox"], input[type="text"], textarea')) {
        updateProgressAndDrawerColors();
      }
    });
    room.addEventListener('input', (event) => {
      if (event.target.matches('input[type="text"], textarea')) {
        updateProgressAndDrawerColors();
      }
    });

    // 5. Drawer open/close actions
    const drawerToggle = document.getElementById('ksh-navigator-toggle');
    const dropdownToggle = document.getElementById('ksh-group-dropdown-toggle');
    const drawerClose = document.getElementById('ksh-drawer-close');
    const drawerBottomClose = document.getElementById('ksh-drawer-bottom-close');

    const openDrawer = () => {
      if (drawer) drawer.classList.add('open');
      if (drawerOverlay) drawerOverlay.classList.add('open');
    };

    const closeDrawer = () => {
      if (drawer) drawer.classList.remove('open');
      if (drawerOverlay) drawerOverlay.classList.remove('open');
    };

    if (drawerToggle) drawerToggle.addEventListener('click', openDrawer);
    if (dropdownToggle) dropdownToggle.addEventListener('click', openDrawer);
    if (drawerClose) drawerClose.addEventListener('click', closeDrawer);
    if (drawerBottomClose) drawerBottomClose.addEventListener('click', closeDrawer);
    if (drawerOverlay) drawerOverlay.addEventListener('click', closeDrawer);

    // Drawer group button click action
    drawerGroupBtns.forEach((btn) => {
      btn.addEventListener('click', () => {
        const targetGroup = btn.dataset.group;
        showGroup(targetGroup);
        updateProgressAndDrawerColors();
        closeDrawer();
      });
    });

    // Previous / Next group button action
    const prevBtn = room.querySelector('.ksh-prev-group-btn');
    const nextBtn = room.querySelector('.ksh-next-group-btn');

    if (prevBtn) {
      prevBtn.addEventListener('click', () => {
        const currentIndex = groups.indexOf(activeGroup);
        if (currentIndex > 0) {
          showGroup(groups[currentIndex - 1]);
          updateProgressAndDrawerColors();
        }
      });
    }

    if (nextBtn) {
      nextBtn.addEventListener('click', () => {
        const currentIndex = groups.indexOf(activeGroup);
        if (currentIndex >= 0 && currentIndex < groups.length - 1) {
          showGroup(groups[currentIndex + 1]);
          updateProgressAndDrawerColors();
        }
      });
    }

    // 6. Collapse/Expand Left passage panel
    const collapseBtn = document.getElementById('ksh-collapse-left');
    const expandBtn = document.getElementById('ksh-expand-left');
    const examContainer = document.getElementById('ksh-exam-container');

    if (collapseBtn && expandBtn && examContainer) {
      collapseBtn.addEventListener('click', () => {
        examContainer.classList.add('left-collapsed');
        expandBtn.style.display = 'block';
      });
      expandBtn.addEventListener('click', () => {
        examContainer.classList.remove('left-collapsed');
        expandBtn.style.display = 'none';
      });
    }

    // Initial show first group
    showGroup(activeGroup);
    updateProgressAndDrawerColors();

    // Image Markdown Render Helper
    const renderMarkdownImages = (text) => {
      const imgRegex = /!\[.*?\]\((.*?)\)/g;
      return text.replace(imgRegex, '<img src="$1" class="img-fluid my-3" style="max-width:100%; border-radius:12px; display:block;" />');
    };

    // Render group instructions images
    document.querySelectorAll('.ksh-instruction-box span').forEach(el => {
      const html = el.innerHTML;
      if (html.includes('![image]')) {
        el.innerHTML = renderMarkdownImages(html);
      }
    });

    // Render question prompt images
    document.querySelectorAll('.ksh-question-prompt').forEach(el => {
      const html = el.innerHTML;
      if (html.includes('![image]')) {
        el.innerHTML = renderMarkdownImages(html);
      }
    });
  };

  const initSpeakingRecorders = () => {
    const form = document.querySelector('form.ksh-room');
    if (!form || form.dataset.speakingMediaUploadEnabled !== 'true') return;

    const recorders = Array.from(form.querySelectorAll('.ksh-speaking-recorder'));
    if (!recorders.length) return;

    const csrfInput = form.querySelector('input[type="hidden"][name]');
    const consentDialog = document.getElementById('ksh-speaking-consent');
    const consentCheck = document.getElementById('ksh-speaking-consent-check');
    const consentAccept = consentDialog?.querySelector('[data-speaking-consent-accept]');
    const consentCancel = consentDialog?.querySelector('[data-speaking-consent-cancel]');
    const consentKey = 'ksh.practice.speaking-recording-consent';
    let pendingConsentResolve = null;

    const hasConsent = () => {
      try {
        return window.sessionStorage.getItem(consentKey) === 'accepted';
      } catch (error) {
        return false;
      }
    };

    const rememberConsent = () => {
      try {
        window.sessionStorage.setItem(consentKey, 'accepted');
      } catch (error) {
        // The in-memory recording session can continue when storage is unavailable.
      }
    };

    const requestConsent = () => {
      if (hasConsent()) return Promise.resolve(true);
      if (!consentDialog || typeof consentDialog.showModal !== 'function') {
        return Promise.resolve(window.confirm(
          'Bản ghi chỉ dùng cho luyện tập, được giữ riêng tư và tuân theo chính sách lưu giữ media Nói. '
          + 'Bạn có thể xóa trước khi nộp. Đây không phải chấm điểm TOPIK chính thức và hiện chưa có '
          + 'đánh giá phát âm bằng AI. Bạn đồng ý ghi âm?'
        )).then((accepted) => {
          if (accepted) rememberConsent();
          return accepted;
        });
      }
      consentCheck.checked = false;
      consentAccept.disabled = true;
      consentDialog.showModal();
      return new Promise((resolve) => {
        pendingConsentResolve = resolve;
      });
    };

    consentCheck?.addEventListener('change', () => {
      consentAccept.disabled = !consentCheck.checked;
    });
    consentAccept?.addEventListener('click', () => {
      if (!consentCheck.checked) return;
      rememberConsent();
      consentDialog.close();
      pendingConsentResolve?.(true);
      pendingConsentResolve = null;
    });
    consentCancel?.addEventListener('click', () => {
      consentDialog.close();
      pendingConsentResolve?.(false);
      pendingConsentResolve = null;
    });
    consentDialog?.addEventListener('cancel', () => {
      pendingConsentResolve?.(false);
      pendingConsentResolve = null;
    });

    const supportedMimeType = () => {
      const candidates = [
        'audio/webm;codecs=opus',
        'audio/ogg;codecs=opus',
        'audio/mp4',
        'audio/webm',
      ];
      if (typeof window.MediaRecorder?.isTypeSupported !== 'function') return '';
      return candidates.find((type) => window.MediaRecorder.isTypeSupported(type)) || '';
    };

    const safeErrorMessage = (error) => {
      if (error?.name === 'NotAllowedError' || error?.name === 'SecurityError') {
        return 'Quyền dùng micro bị từ chối. Hãy cho phép micro rồi thử lại.';
      }
      if (error?.name === 'NotFoundError' || error?.name === 'DevicesNotFoundError') {
        return 'Không tìm thấy micro trên thiết bị này.';
      }
      return 'Không thể bắt đầu ghi âm trên trình duyệt này.';
    };

    recorders.forEach((panel) => {
      const startButton = panel.querySelector('.ksh-recorder-start');
      const stopButton = panel.querySelector('.ksh-recorder-stop');
      const uploadButton = panel.querySelector('.ksh-recorder-upload');
      const retryButton = panel.querySelector('.ksh-recorder-retry');
      const deleteButton = panel.querySelector('.ksh-recorder-delete');
      const rerecordButton = panel.querySelector('.ksh-recorder-rerecord');
      const preview = panel.querySelector('.ksh-recorder-preview');
      const progress = panel.querySelector('.ksh-recorder-progress');
      const status = panel.querySelector('.ksh-recorder-status');
      let recorder = null;
      let stream = null;
      let chunks = [];
      let recordedBlob = null;
      let localPreviewUrl = null;

      const setStatus = (message) => {
        status.textContent = message;
      };
      const setBusy = (state) => {
        panel.dataset.busy = state;
      };
      const clearLocalPreview = () => {
        if (localPreviewUrl) URL.revokeObjectURL(localPreviewUrl);
        localPreviewUrl = null;
      };
      const clearRecordedBlob = () => {
        recordedBlob = null;
        chunks = [];
        clearLocalPreview();
      };
      const stopTracks = () => {
        stream?.getTracks().forEach((track) => track.stop());
        stream = null;
      };
      const deleteUrl = () => panel.dataset.mediaId
        ? `${panel.dataset.uploadUrl}/${panel.dataset.mediaId}`
        : '';

      const applyUploadedMedia = (media) => {
        panel.dataset.mediaId = String(media.mediaId);
        panel.dataset.playbackPath = media.playbackPath || '';
        panel.dataset.mediaMime = media.mimeType || '';
        panel.dataset.mediaDurationMs = String(media.durationMs || '');
        panel.dataset.mediaByteSize = String(media.byteSize || '');
        panel.dataset.mediaLockVersion = String(media.lockVersion || '');
        if (form.dataset.speakingMediaPlaybackEnabled === 'true' && media.playbackPath) {
          clearLocalPreview();
          preview.src = media.playbackPath;
          preview.hidden = false;
        }
        deleteButton.hidden = false;
        rerecordButton.hidden = false;
        uploadButton.hidden = true;
        retryButton.hidden = true;
        setBusy('idle');
        setStatus('Bản ghi đã được tải lên và lưu riêng tư.');
      };

      const upload = () => {
        if (!recordedBlob || !csrfInput) return;
        setBusy('uploading');
        progress.hidden = false;
        progress.value = 0;
        uploadButton.disabled = true;
        retryButton.hidden = true;
        setStatus('Đang tải bản ghi lên...');

        const data = new FormData();
        const extension = recordedBlob.type.includes('mp4')
          ? 'm4a'
          : recordedBlob.type.includes('ogg') ? 'ogg' : 'webm';
        data.append('file', recordedBlob, `speaking-answer.${extension}`);
        const xhr = new XMLHttpRequest();
        xhr.open('POST', panel.dataset.uploadUrl);
        xhr.setRequestHeader('X-CSRF-TOKEN', csrfInput.value);
        xhr.upload.addEventListener('progress', (event) => {
          if (event.lengthComputable) progress.value = Math.round((event.loaded / event.total) * 100);
        });
        xhr.addEventListener('load', () => {
          progress.hidden = true;
          uploadButton.disabled = false;
          if (xhr.status >= 200 && xhr.status < 300) {
            try {
              applyUploadedMedia(JSON.parse(xhr.responseText));
              recordedBlob = null;
              chunks = [];
              if (form.dataset.speakingMediaPlaybackEnabled === 'true') {
                clearLocalPreview();
              }
              return;
            } catch (error) {
              // Fall through to the bounded upload error state.
            }
          }
          setBusy('idle');
          retryButton.hidden = false;
          setStatus('Tải bản ghi thất bại. Bạn có thể thử lại hoặc ghi lại.');
        });
        xhr.addEventListener('error', () => {
          progress.hidden = true;
          uploadButton.disabled = false;
          retryButton.hidden = false;
          setBusy('idle');
          setStatus('Không thể tải bản ghi. Hãy kiểm tra kết nối và thử lại.');
        });
        xhr.send(data);
      };

      const removeUploadedMedia = async () => {
        if (!deleteUrl() || !csrfInput) return true;
        setBusy('uploading');
        setStatus('Đang xóa bản ghi...');
        try {
          const response = await fetch(deleteUrl(), {
            method: 'DELETE',
            headers: { 'X-CSRF-TOKEN': csrfInput.value },
          });
          if (!response.ok) throw new Error('delete failed');
          panel.dataset.mediaId = '';
          panel.dataset.playbackPath = '';
          clearLocalPreview();
          preview.removeAttribute('src');
          preview.load();
          preview.hidden = true;
          deleteButton.hidden = true;
          rerecordButton.hidden = true;
          setBusy('idle');
          setStatus('Đã xóa bản ghi. Bạn có thể ghi lại hoặc chỉ dùng câu trả lời văn bản.');
          return true;
        } catch (error) {
          setBusy('idle');
          setStatus('Không thể xóa bản ghi. Hãy thử lại.');
          return false;
        }
      };

      const startRecording = async () => {
        if (!window.MediaRecorder || !navigator.mediaDevices?.getUserMedia) {
          setStatus('Trình duyệt này không hỗ trợ ghi âm. Bạn vẫn có thể trả lời bằng văn bản.');
          return;
        }
        if (!await requestConsent()) {
          setStatus('Bạn cần đồng ý trước khi dùng micro.');
          return;
        }
        try {
          const mimeType = supportedMimeType();
          stream = await navigator.mediaDevices.getUserMedia({ audio: true });
          recorder = mimeType
            ? new window.MediaRecorder(stream, { mimeType })
            : new window.MediaRecorder(stream);
          chunks = [];
          recorder.addEventListener('dataavailable', (event) => {
            if (event.data?.size) chunks.push(event.data);
          });
          recorder.addEventListener('stop', () => {
            const blobType = recorder.mimeType || mimeType || chunks[0]?.type || 'audio/webm';
            recordedBlob = new Blob(chunks, { type: blobType });
            clearLocalPreview();
            localPreviewUrl = URL.createObjectURL(recordedBlob);
            preview.src = localPreviewUrl;
            preview.hidden = false;
            uploadButton.hidden = false;
            uploadButton.disabled = false;
            retryButton.hidden = true;
            startButton.disabled = false;
            stopButton.disabled = true;
            setBusy('idle');
            setStatus('Bản ghi đã sẵn sàng để nghe thử và tải lên.');
            stopTracks();
          });
          recorder.start();
          setBusy('recording');
          startButton.disabled = true;
          stopButton.disabled = false;
          uploadButton.hidden = true;
          retryButton.hidden = true;
          setStatus('Đang ghi âm...');
        } catch (error) {
          stopTracks();
          setBusy('idle');
          setStatus(safeErrorMessage(error));
        }
      };

      startButton.addEventListener('click', startRecording);
      stopButton.addEventListener('click', () => {
        if (recorder?.state === 'recording') recorder.stop();
      });
      uploadButton.addEventListener('click', upload);
      retryButton.addEventListener('click', upload);
      deleteButton.addEventListener('click', removeUploadedMedia);
      rerecordButton.addEventListener('click', async () => {
        if (await removeUploadedMedia()) {
          clearRecordedBlob();
          await startRecording();
        }
      });

      setBusy('idle');
      if (!window.MediaRecorder || !navigator.mediaDevices?.getUserMedia) {
        startButton.disabled = true;
        setStatus('Trình duyệt này không hỗ trợ ghi âm. Bạn vẫn có thể trả lời bằng văn bản.');
      }
    });

    form.addEventListener('submit', (event) => {
      const pending = recorders.some((panel) =>
        panel.dataset.busy === 'recording' || panel.dataset.busy === 'uploading');
      if (!pending) return;
      event.preventDefault();
      window.alert('Hãy dừng ghi âm hoặc chờ tải bản ghi xong trước khi nộp bài.');
    });
  };

  renderAiFeedback();
  initDraftEditor();
  initRoomTimer();
  initSplitDivider();
  initRoomCbt();
  initSpeakingRecorders();
})();

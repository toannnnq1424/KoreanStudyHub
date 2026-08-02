(function () {
  'use strict';

  function mapResponse(response) {
    const sections = response && Array.isArray(response.sections) ? response.sections : [];
    const groups = [];
    let totalPoints = 0;
    sections.forEach((section, sectionIndex) => {
      (section.groups || []).forEach((group, groupIndex) => {
        const questions = (group.questions || []).map((question, questionIndex) => {
          const points = Number(question.points) || 0;
          const content = question.content && typeof question.content === 'object'
            ? question.content : {};
          const speakingPresentation = question.speakingPresentation
            && typeof question.speakingPresentation === 'object'
            ? question.speakingPresentation : null;
          totalPoints += points;
          return {
            questionNo: Number(question.questionNo) || questionIndex + 1,
            questionType: question.questionType,
            prompt: speakingPresentation
              ? (speakingPresentation.promptText || '') : (question.prompt || ''),
            promptLanguageTag: content.languageTag || 'ko',
            questionContent: content,
            speakingPresentation,
            options: Array.isArray(content.options) ? content.options : [],
            imageUrl: content.imageReference || '',
            audioUrl: content.audioReference || '',
            prepTimeSeconds: Number(speakingPresentation
              ? speakingPresentation.preparationSeconds : question.prepTimeSeconds) || 0,
            respTimeSeconds: Number(speakingPresentation
              ? speakingPresentation.responseSeconds : question.respTimeSeconds) || 0,
            points
          };
        });
        groups.push({
          secTitle: section.title || '',
          skill: section.skill || 'READING',
          listeningCheckAudioUrl: section.listeningCheckAudioReference || '',
          sIdx: sectionIndex,
          gIdx: groupIndex,
          points: questions.reduce((sum, question) => sum + question.points, 0),
          grp: {
            label: group.label || '',
            instruction: group.instruction || '',
            instructionLanguageTag: group.instructionLanguageTag || 'vi',
            stimulusLanguageTag: group.stimulusLanguageTag || 'ko',
            passageText: group.passageText || '',
            audioUrl: group.mediaReference || '',
            imageUrl: group.imageReference || '',
            questions
          }
        });
      });
    });
    return { title: response && response.title || 'Đề luyện tập', groups, totalPoints };
  }

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = String(text);
    return node;
  }

  function skillLabel(skill) {
    return ({ READING: 'Đọc', LISTENING: 'Nghe', WRITING: 'Viết', SPEAKING: 'Nói' })[skill]
      || skill || 'Đọc';
  }

  function language(value, fallback) {
    return ['ko', 'vi'].includes(value) ? value : fallback;
  }

  function setMedia(containerId, mediaId, reference) {
    const container = document.getElementById(containerId);
    const media = document.getElementById(mediaId);
    if (!container || !media) return;
    if (reference) {
      container.style.display = 'block';
      media.src = reference;
    } else {
      container.style.display = 'none';
      media.removeAttribute('src');
    }
  }

  function appendImage(parent, reference, alt) {
    if (!reference) return;
    const image = document.createElement('img');
    image.src = reference;
    image.alt = alt;
    image.style.cssText = 'max-width:100%;max-height:260px;object-fit:contain;border-radius:6px;margin-bottom:12px';
    parent.appendChild(image);
  }

  function appendAudio(parent, reference) {
    if (!reference) return;
    const audio = document.createElement('audio');
    audio.controls = true;
    audio.preload = 'metadata';
    audio.src = reference;
    audio.style.width = '100%';
    parent.appendChild(audio);
  }

  function optionRow(marker, text, imageReference) {
    const row = element('div', 'preview-option');
    row.append(
      element('span', 'preview-option-letter', marker),
      element('span', '', text || ''));
    if (imageReference) {
      const image = document.createElement('img');
      image.src = imageReference;
      image.alt = `Ảnh lựa chọn ${marker}`;
      image.style.cssText = 'max-width:120px;max-height:90px;object-fit:contain;margin-left:auto';
      row.appendChild(image);
    }
    return row;
  }

  function appendFillBlank(card, question) {
    const content = question.questionContent || {};
    const blanks = Array.isArray(content.blanks) ? content.blanks : [];
    const known = new Map(blanks.map(blank => [String(blank.id || ''), blank]));
    const placed = new Set();
    const fill = element('div', 'preview-fill-template');
    fill.lang = language(content.languageTag, 'ko');
    String(question.prompt || '').split(/(\{\{blank:[^{}]+\}\})/g).forEach(part => {
      const match = part.match(/^\{\{blank:([^{}]+)\}\}$/);
      if (!match) {
        fill.appendChild(document.createTextNode(part));
      } else if (known.has(match[1])) {
        placed.add(match[1]);
        const blank = known.get(match[1]);
        const slot = element('span', 'preview-fill-slot');
        slot.title = blank.prompt || 'Chỗ trống';
        slot.append(element('b', '', placed.size), element('span'));
        fill.appendChild(slot);
      } else {
        fill.appendChild(element('span', 'fill-preview-invalid', 'Ô trống không hợp lệ'));
      }
    });
    card.appendChild(fill);
    blanks.filter(blank => !placed.has(String(blank.id || ''))).forEach(blank => {
      const label = element('label', '', blank.prompt || 'Điền vào chỗ trống');
      label.style.cssText = 'display:block;margin:8px 0;font-size:.82rem;font-weight:600';
      const input = document.createElement('input');
      input.disabled = true;
      input.className = 'form-input';
      input.style.marginTop = '4px';
      label.appendChild(input);
      card.appendChild(label);
    });
  }

  function appendMatching(card, question) {
    const content = question.questionContent || {};
    const options = Array.isArray(content.options) ? content.options : [];
    const targets = Array.isArray(content.blanks) ? content.blanks : [];
    const optionGrid = element('div');
    optionGrid.style.cssText = 'display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px;margin-bottom:12px';
    options.forEach((option, index) => optionGrid.appendChild(optionRow(
      String.fromCharCode(65 + index), option.text, option.imageReference)));
    card.appendChild(optionGrid);
    const targetGrid = element('div');
    targetGrid.style.display = 'grid';
    targetGrid.style.gap = '8px';
    targets.forEach((target, index) => {
      const label = element('label', '', target.prompt || `Nội dung ${index + 1}`);
      label.style.cssText = 'display:grid;grid-template-columns:minmax(0,1fr) minmax(160px,.45fr);gap:8px;align-items:center;font-size:.82rem;font-weight:600';
      const select = document.createElement('select');
      select.disabled = true;
      select.className = 'form-input';
      select.setAttribute('aria-label', `Đáp án ghép cho nội dung ${index + 1}`);
      select.appendChild(element('option', '', 'Chọn đáp án A–H'));
      label.appendChild(select);
      targetGrid.appendChild(label);
    });
    card.appendChild(targetGrid);
  }

  function renderQuestion(item, question) {
    const card = element('article', 'preview-q-card');
    const heading = element('div', 'preview-question-heading');
    heading.append(
      element('h4', '', `Câu ${question.questionNo}`),
      element('span', '', `${question.points} điểm`));
    card.appendChild(heading);

    if (item.skill === 'WRITING') {
      card.classList.add('preview-writing-answer-card');
      const answer = document.createElement('textarea');
      answer.disabled = true;
      answer.placeholder = 'Nơi học viên nhập bài viết tiếng Hàn';
      answer.style.cssText = 'width:100%;min-height:160px';
      card.append(answer, element('small', '', '0 ký tự'));
      return card;
    }

    const presentation = question.speakingPresentation;
    if (item.skill === 'SPEAKING') {
      card.classList.add('preview-speaking-state');
      card.appendChild(element('div', 'preview-speaking-progress',
        `Câu ${question.questionNo}`));
      const steps = Array.isArray(presentation?.steps) ? presentation.steps : [];
      const requiresAudio = steps.includes('PROMPT_PLAYBACK');
      if (requiresAudio) {
        const audioTile = element('div', 'preview-speaking-audio');
        audioTile.setAttribute('aria-hidden', 'true');
        audioTile.appendChild(element('span', '', 'Âm thanh đề bài'));
        card.appendChild(audioTile);
      }
      const promptPanel = element('div', 'preview-speaking-prompt');
      const prompt = element('p', 'preview-question-prompt', presentation?.promptText || '');
      prompt.lang = language(question.promptLanguageTag, 'ko');
      if (prompt.textContent) promptPanel.appendChild(prompt);
      appendImage(promptPanel, question.imageUrl || item.grp.imageUrl,
        `Ảnh câu hỏi Nói ${question.questionNo}`);
      card.appendChild(promptPanel);
      const deliveryPanel = element('div', 'preview-speaking-panel');
      const timing = (label, value) => {
        const row = element('div');
        row.append(element('span', '', label), element('strong', '', value));
        return row;
      };
      deliveryPanel.append(
        timing('Chuẩn bị', `${presentation?.preparationSeconds ?? 0}s`),
        timing('Thời gian nói', `${presentation?.responseSeconds ?? 0}s`));
      if (requiresAudio) {
        deliveryPanel.appendChild(timing(
          'Số lần phát', presentation?.promptPlayLimit ?? 0));
        if (presentation?.promptAudioReference) {
          appendAudio(deliveryPanel, presentation.promptAudioReference);
        } else {
          deliveryPanel.appendChild(element('p', 'preview-speaking-missing',
            'Chưa có âm thanh đề bài bắt buộc.'));
        }
      }
      card.appendChild(deliveryPanel);
      return card;
    }

    const content = question.questionContent || {};
    const fillBlank = ['FILL_BLANK', 'GAP_FILL'].includes(question.questionType);
    if (!fillBlank && question.prompt) {
      const prompt = element('p', 'preview-question-prompt', question.prompt);
      prompt.lang = language(content.languageTag, 'ko');
      card.appendChild(prompt);
    }
    appendImage(card, question.imageUrl, `Ảnh câu hỏi ${question.questionNo}`);
    appendAudio(card, question.audioUrl);

    if (['SINGLE_CHOICE', 'MULTIPLE_ANSWER'].includes(question.questionType)) {
      (content.options || []).forEach((option, index) => card.appendChild(optionRow(
        question.questionType === 'SINGLE_CHOICE'
          ? String.fromCodePoint(0x2460 + index) : String.fromCharCode(65 + index),
        option.text, option.imageReference)));
    } else if (question.questionType === 'MATCHING') {
      appendMatching(card, question);
    } else if (question.questionType === 'TRUE_FALSE_NOT_GIVEN') {
      [['Đ', 'Đúng'], ['S', 'Sai'], ['K', 'Không có thông tin']]
        .forEach(([marker, label]) => card.appendChild(optionRow(marker, label)));
    } else if (fillBlank) {
      appendFillBlank(card, question);
    } else if (question.questionType === 'ESSAY') {
      const answer = document.createElement('textarea');
      answer.disabled = true;
      answer.className = 'form-textarea';
      answer.placeholder = 'Nơi học viên nhập bài viết bài luận…';
      answer.style.cssText = 'height:80px;width:100%;resize:none';
      card.appendChild(answer);
    }
    return card;
  }

  function renderActiveGroup(delivery, activeIndex) {
    const item = delivery.groups[activeIndex];
    if (!item) return;
    const group = item.grp;
    const body = document.getElementById('preview-player-body');
    const passageText = String(group.passageText || '').trim();
    const hasSource = Boolean(passageText || group.imageUrl);
    const hasListeningLead = Boolean(group.audioUrl || group.imageUrl || group.instruction);
    body.classList.remove('layout-focus', 'layout-stacked', 'layout-split',
      'layout-writing', 'layout-speaking');
    if (item.skill === 'WRITING') body.classList.add('layout-writing');
    else if (item.skill === 'SPEAKING') body.classList.add('layout-speaking');
    else if (item.skill === 'LISTENING') {
      body.classList.add(hasListeningLead ? 'layout-stacked' : 'layout-focus');
    } else if (!hasSource) body.classList.add('layout-focus');
    else body.classList.add(group.imageUrl || passageText.length >= 320
      ? 'layout-split' : 'layout-stacked');

    const instruction = String(group.instruction || '').trim();
    ['preview-group-instruction', 'preview-focus-instruction'].forEach(id => {
      const target = document.getElementById(id);
      target.replaceChildren();
      if (instruction) {
        const text = element('p', '', instruction);
        text.lang = language(group.instructionLanguageTag, 'vi');
        target.append(element('strong', '', 'Hướng dẫn bài thi'), text);
      }
    });
    setMedia('preview-group-audio-container', 'preview-group-audio',
      item.skill === 'LISTENING' ? group.audioUrl : '');
    setMedia('preview-group-image-container', 'preview-group-image', group.imageUrl);

    const passage = document.getElementById('preview-group-passage');
    passage.lang = language(group.stimulusLanguageTag, 'ko');
    passage.textContent = passageText;
    passage.style.display = ['READING', 'WRITING'].includes(item.skill) && passageText
      ? 'block' : 'none';

    const writing = document.getElementById('preview-writing-prompts');
    writing.replaceChildren();
    if (item.skill === 'WRITING') {
      (group.questions || []).forEach(question => {
        const prompt = element('section', 'preview-writing-prompt');
        if (question.prompt) {
          const text = element('p', '', question.prompt);
          text.lang = language(question.promptLanguageTag, 'ko');
          prompt.appendChild(text);
        }
        appendImage(prompt, question.imageUrl, `Ảnh câu hỏi Viết ${question.questionNo}`);
        appendAudio(prompt, question.audioUrl);
        writing.appendChild(prompt);
      });
    }
    const stack = document.getElementById('preview-questions-stack');
    const questions = group.questions || [];
    stack.replaceChildren();
    if (!questions.length) {
      const empty = element('div', '', 'Nhóm này chưa có câu hỏi nào.');
      empty.style.cssText = 'text-align:center;color:#64748b;padding:40px;font-style:italic';
      stack.appendChild(empty);
      return;
    }
    stack.replaceChildren(...questions.map(question => renderQuestion(item, question)));
  }

  function groupTabLabel(item, index) {
    const numbers = (item.grp.questions || [])
      .map(question => Number(question.questionNo)).filter(Number.isFinite);
    if (!numbers.length) return item.grp.label || `Nhóm ${index + 1}`;
    const minimum = Math.min(...numbers);
    const maximum = Math.max(...numbers);
    return minimum === maximum ? `Câu ${minimum}` : `Câu ${minimum}–${maximum}`;
  }

  function renderModal(delivery) {
    const safeDelivery = delivery || { title: 'Đề luyện tập', groups: [], totalPoints: 0 };
    document.getElementById('preview-modal-title').textContent = `Xem trước: ${safeDelivery.title}`;
    document.getElementById('preview-total-points-label').textContent =
      `Tổng điểm: ${safeDelivery.totalPoints}đ`;
    const selector = document.getElementById('preview-group-selector');
    selector.replaceChildren();
    if (!safeDelivery.groups.length) {
      selector.appendChild(element('span', '', 'Bộ đề chưa có câu hỏi nào'));
      ['preview-group-instruction', 'preview-focus-instruction',
        'preview-writing-prompts'].forEach(id =>
        document.getElementById(id).replaceChildren());
      setMedia('preview-group-audio-container', 'preview-group-audio', '');
      setMedia('preview-group-image-container', 'preview-group-image', '');
      const passage = document.getElementById('preview-group-passage');
      passage.textContent = '';
      passage.style.display = 'none';
      document.getElementById('preview-questions-stack').replaceChildren();
      return;
    }
    const activate = index => {
      [...selector.querySelectorAll('.preview-grp-tab')].forEach((button, buttonIndex) => {
        button.classList.toggle('active', buttonIndex === index);
        button.setAttribute('aria-selected', buttonIndex === index ? 'true' : 'false');
        button.tabIndex = buttonIndex === index ? 0 : -1;
      });
      renderActiveGroup(safeDelivery, index);
    };
    safeDelivery.groups.forEach((item, index) => {
      const button = element(
        'button', `preview-grp-tab${index === 0 ? ' active' : ''}`,
        `${groupTabLabel(item, index)} (${skillLabel(item.skill)})`);
      button.type = 'button';
      button.role = 'tab';
      button.addEventListener('click', () => activate(index));
      selector.appendChild(button);
    });
    activate(0);
  }

  window.PracticeDraftPreview = { mapResponse, renderModal };
})();

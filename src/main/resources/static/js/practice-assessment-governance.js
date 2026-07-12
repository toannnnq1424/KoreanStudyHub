(function () {
  'use strict';

  var API = '/practice/manage/governance/assessment';
  var SKILLS = ['READING', 'LISTENING', 'WRITING', 'SPEAKING'];
  var TYPES = {
    READING: ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE_NOT_GIVEN', 'FILL_BLANK', 'MATCHING'],
    LISTENING: ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE_NOT_GIVEN', 'FILL_BLANK', 'MATCHING'],
    WRITING: ['ESSAY'],
    SPEAKING: ['SPEAKING']
  };
  var pendingGovernanceAction = null;

  function csrfHeaders() {
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    var result = {'Content-Type': 'application/json'};
    if (token && header && token.content && header.content) result[header.content] = token.content;
    return result;
  }

  async function request(path, body) {
    var response = await fetch(API + path, {
      method: 'POST',
      credentials: 'same-origin',
      headers: csrfHeaders(),
      body: JSON.stringify(body || {})
    });
    var text = await response.text();
    if (!response.ok) {
      var message = text;
      try {
        var parsed = JSON.parse(text);
        message = parsed.message || parsed.error || text;
      } catch (ignored) {}
      throw new Error(message || ('HTTP ' + response.status));
    }
    return text ? JSON.parse(text) : {};
  }

  async function catalog() {
    var response = await fetch(API + '/catalog', {credentials: 'same-origin'});
    if (!response.ok) throw new Error('Không thể tải catalog governance.');
    return response.json();
  }

  function message(value, error) {
    var target = document.getElementById('governance-message');
    if (!target) return;
    target.hidden = false;
    target.classList.toggle('error', Boolean(error));
    target.textContent = value;
    target.scrollIntoView({block: 'nearest'});
  }

  function reloadSuccess(value) {
    window.sessionStorage.setItem('practiceGovernanceMessage', value);
    window.location.reload();
  }

  function setupDialogs() {
    document.querySelectorAll('[data-open-dialog]').forEach(function (button) {
      button.addEventListener('click', function () {
        var dialog = document.getElementById(button.dataset.openDialog);
        if (dialog) dialog.showModal();
      });
    });
    document.querySelectorAll('[data-close-dialog]').forEach(function (button) {
      button.addEventListener('click', function () {
        var dialog = button.closest('dialog');
        if (dialog) dialog.close();
      });
    });
  }

  function setupProgramPanels() {
    var select = document.getElementById('program-filter');
    var panels = Array.prototype.slice.call(document.querySelectorAll('[data-program-panel]'));
    function show() {
      panels.forEach(function (panel) {
        panel.classList.toggle('is-active', !select || panel.dataset.programPanel === select.value);
      });
    }
    if (select) select.addEventListener('change', show);
    show();
  }

  function typeInput(skill, type, prefix) {
    return '<label><input type="checkbox" name="' + prefix + '-' + skill + '-type" value="' + type + '"' +
      ((type === 'SINGLE_CHOICE' || type === 'ESSAY' || type === 'SPEAKING') ? ' checked' : '') + '> ' + type + '</label>';
  }

  function approvedProfileOptions(kind, skill) {
    var rows = Array.prototype.slice.call(document.querySelectorAll(
      '#governance-profile-data [data-kind="' + kind + '"]'));
    if (kind !== 'SCORING') {
      rows = rows.filter(function (row) { return row.dataset.skill === skill; });
    }
    return '<option value="">Chọn ' + kind.toLowerCase() + ' profile</option>' + rows.map(function (row) {
      return '<option value="' + row.dataset.profileId + '">' + row.dataset.label + '</option>';
    }).join('');
  }

  function profileFields(skill, prefix) {
    if (skill !== 'WRITING' && skill !== 'SPEAKING') return '';
    return '<div class="pg-profile-selects">' +
      '<select name="' + prefix + '-' + skill + '-scoringProfileId" aria-label="Scoring profile">' + approvedProfileOptions('SCORING', skill) + '</select>' +
      '<select name="' + prefix + '-' + skill + '-promptProfileId" aria-label="Prompt profile">' + approvedProfileOptions('PROMPT', skill) + '</select>' +
      '<select name="' + prefix + '-' + skill + '-rubricProfileId" aria-label="Rubric profile">' + approvedProfileOptions('RUBRIC', skill) + '</select>' +
      '</div>';
  }

  function buildSkillRows(targetId, prefix, templateMode) {
    var target = document.getElementById(targetId);
    if (!target) return;
    target.innerHTML = SKILLS.map(function (skill) {
      var enabledByDefault = skill === 'READING' || skill === 'LISTENING';
      var fields = templateMode
        ? '<input type="number" min="1" name="' + prefix + '-' + skill + '-duration" value="40" title="Phút">' +
          '<input type="number" min="1" name="' + prefix + '-' + skill + '-max" value="50" title="Số câu tối đa">' +
          '<input type="number" min="0.01" step="0.01" name="' + prefix + '-' + skill + '-points" value="1" title="Điểm mặc định">' +
          '<label class="pg-check"><input type="checkbox" name="' + prefix + '-' + skill + '-excel" checked> Excel</label>'
        : '<select name="' + prefix + '-' + skill + '-delivery"><option value="SKILL_SPECIFIC">Theo kỹ năng</option><option value="FULL_TEST">Full test</option><option value="BOTH">Cả hai</option></select>' +
          profileFields(skill, prefix);
      return '<div class="pg-skill-row"><label class="pg-skill-title"><input type="checkbox" name="' + prefix + '-' + skill + '-enabled"' + (enabledByDefault ? ' checked' : '') + '> ' + skill + '</label>' +
        '<div class="pg-skill-fields">' + fields + '<div class="pg-type-list">' +
        TYPES[skill].map(function (type) { return typeInput(skill, type, prefix); }).join('') +
        '</div></div></div>';
    }).join('');
  }

  function scoringPolicy(type) {
    if (type === 'FILL_BLANK') return 'NORMALIZED_EXACT';
    if (type === 'MATCHING') return 'PER_PAIR';
    if (type === 'ESSAY' || type === 'SPEAKING') return 'PROFILE_BASED';
    return 'ALL_OR_NOTHING';
  }

  function checkedTypes(form, prefix, skill) {
    return Array.prototype.slice.call(form.querySelectorAll('input[name="' + prefix + '-' + skill + '-type"]:checked'))
      .map(function (input) { return input.value; });
  }

  function setupForms() {
    var programForm = document.getElementById('program-form');
    if (programForm) programForm.addEventListener('submit', async function (event) {
      event.preventDefault();
      try {
        await request('/programs', {code: new FormData(programForm).get('code')});
        reloadSuccess('Đã tạo chứng chỉ.');
      } catch (error) { message(error.message, true); }
    });

    var versionForm = document.getElementById('program-version-form');
    if (versionForm) versionForm.addEventListener('submit', async function (event) {
      event.preventDefault();
      var data = new FormData(versionForm);
      var skills = [];
      var questions = [];
      var profileError = null;
      SKILLS.forEach(function (skill) {
        var enabled = data.get('program-' + skill + '-enabled') !== null;
        var scoringProfileId = data.get('program-' + skill + '-scoringProfileId');
        var promptProfileId = data.get('program-' + skill + '-promptProfileId');
        var rubricProfileId = data.get('program-' + skill + '-rubricProfileId');
        var selectedTypes = checkedTypes(versionForm, 'program', skill);
        if (enabled && selectedTypes.some(function (type) { return scoringPolicy(type) === 'PROFILE_BASED'; })
            && (!scoringProfileId || !promptProfileId || !rubricProfileId)) {
          profileError = skill + ' cần chọn đủ scoring, prompt và rubric profile đã phê duyệt.';
        }
        skills.push({skillCode: skill, enabled: enabled,
          deliveryMode: data.get('program-' + skill + '-delivery') || 'SKILL_SPECIFIC'});
        selectedTypes.forEach(function (type) {
          questions.push({skillCode: skill, questionType: type, enabled: enabled,
            defaultScoringPolicyCode: scoringPolicy(type),
            scoringProfileId: scoringProfileId ? Number(scoringProfileId) : null,
            promptProfileId: promptProfileId ? Number(promptProfileId) : null,
            rubricProfileId: rubricProfileId ? Number(rubricProfileId) : null});
        });
      });
      if (profileError) {
        message(profileError, true);
        return;
      }
      try {
        await request('/programs/' + encodeURIComponent(data.get('programCode')) + '/versions', {
          displayName: data.get('displayName'), defaultLanguage: data.get('defaultLanguage'),
          skills: skills, questionTypes: questions
        });
        reloadSuccess('Đã tạo phiên bản policy và clone các kịch bản hiện có.');
      } catch (error) { message(error.message, true); }
    });

    var templateForm = document.getElementById('template-form');
    if (templateForm) templateForm.addEventListener('submit', async function (event) {
      event.preventDefault();
      var data = new FormData(templateForm);
      var skills = {};
      SKILLS.forEach(function (skill) {
        if (data.get('template-' + skill + '-enabled') === null) return;
        var types = checkedTypes(templateForm, 'template', skill);
        if (!types.length) return;
        skills[skill] = {
          enabled: true,
          durationMinutes: Number(data.get('template-' + skill + '-duration')),
          maxQuestions: Number(data.get('template-' + skill + '-max')),
          defaultPoints: Number(data.get('template-' + skill + '-points')),
          pointsEditable: true,
          excelImportEnabled: data.get('template-' + skill + '-excel') !== null,
          questionTypes: types
        };
      });
      try {
        await request('/programs/' + encodeURIComponent(data.get('programCode')) + '/templates', {
          code: data.get('code'), displayName: data.get('displayName'),
          categoryCode: data.get('code'), enabled: data.get('enabled') !== null,
          configJson: JSON.stringify({schemaVersion: 'assessment-template-v1',
            maxTests: Number(data.get('maxTests')), skills: skills})
        });
        reloadSuccess('Đã tạo kịch bản.');
      } catch (error) { message(error.message, true); }
    });

    var templateVersionForm = document.getElementById('template-version-form');
    if (templateVersionForm) templateVersionForm.addEventListener('submit', async function (event) {
      event.preventDefault();
      var data = new FormData(templateVersionForm);
      try {
        JSON.parse(data.get('configJson'));
        await request('/templates/' + encodeURIComponent(data.get('templateCode')) + '/versions', {
          programVersionId: Number(data.get('programVersionId')),
          configJson: data.get('configJson')
        });
        reloadSuccess('Đã tạo phiên bản kịch bản.');
      } catch (error) { message(error.message, true); }
    });

    var profileForm = document.getElementById('profile-form');
    if (profileForm) profileForm.addEventListener('submit', async function (event) {
      event.preventDefault();
      var data = new FormData(profileForm);
      var kind = data.get('kind');
      var code = data.get('code');
      var body;
      var path;
      if (kind === 'SCORING') {
        path = '/profiles/scoring/' + encodeURIComponent(code);
        body = {configJson: data.get('configJson')};
      } else if (kind === 'PROMPT') {
        path = '/profiles/prompt/' + encodeURIComponent(code);
        body = {skillCode: data.get('skillCode'), taskType: data.get('taskType'),
          compatibilityAdapter: data.get('compatibilityAdapter'), systemRules: data.get('systemRules')};
      } else {
        path = '/profiles/rubric/' + encodeURIComponent(code);
        body = {skillCode: data.get('skillCode'), taskType: data.get('taskType'),
          configJson: data.get('configJson')};
      }
      try {
        await request(path, body);
        reloadSuccess('Đã tạo profile draft.');
      } catch (error) { message(error.message, true); }
    });

    var kindSelect = profileForm && profileForm.querySelector('[name="kind"]');
    if (kindSelect) {
      function toggleProfileFields() {
        var kind = kindSelect.value;
        document.querySelectorAll('[data-profile-field="skill"]').forEach(function (field) {
          field.classList.toggle('pg-field-hidden', kind === 'SCORING');
        });
        document.querySelectorAll('[data-profile-field="prompt"]').forEach(function (field) {
          field.classList.toggle('pg-field-hidden', kind !== 'PROMPT');
        });
        document.querySelectorAll('[data-profile-field="config"]').forEach(function (field) {
          field.classList.toggle('pg-field-hidden', kind === 'PROMPT');
        });
      }
      kindSelect.addEventListener('change', toggleProfileFields);
      toggleProfileFields();
    }
  }

  function governanceActionSummary(button) {
    var action = button.dataset.governanceAction;
    if (action === 'activate-program-version') {
      return 'Kích hoạt ' + button.dataset.programCode + ' policy version ID ' + button.dataset.versionId +
        '. Các kịch bản tương thích sẽ được kích hoạt trong cùng transaction.';
    }
    if (action === 'toggle-program') {
      return (button.dataset.enabled === 'true' ? 'Bật lại' : 'Lưu trữ') + ' program ' + button.dataset.programCode +
        '. Identity và toàn bộ lịch sử version vẫn được giữ nguyên.';
    }
    if (action === 'activate-template-version') {
      return 'Kích hoạt version ID ' + button.dataset.versionId + ' của kịch bản ' + button.dataset.templateCode + '.';
    }
    if (action === 'toggle-template') {
      return (button.dataset.enabled === 'true' ? 'Bật' : 'Tắt') + ' kịch bản ' + button.dataset.templateCode + '.';
    }
    if (action === 'activate-profile') {
      return 'Phê duyệt ' + button.dataset.profileKind + ' profile ID ' + button.dataset.profileId + '.';
    }
    return 'Thay đổi governance sẽ được ghi audit.';
  }

  function openGovernanceActionDialog(button) {
    var dialog = document.getElementById('governance-action-dialog');
    var form = document.getElementById('governance-action-form');
    if (!dialog || !form) return;
    pendingGovernanceAction = button;
    form.reset();
    document.getElementById('governance-action-summary').textContent = governanceActionSummary(button);
    dialog.showModal();
    form.elements.reason.focus();
  }

  async function executeGovernanceAction(button, reason) {
    var action = button.dataset.governanceAction;
    if (action === 'activate-program-version') {
      await request('/programs/' + encodeURIComponent(button.dataset.programCode) + '/versions/' + button.dataset.versionId + '/activate', {reason: reason});
      reloadSuccess('Đã kích hoạt policy và bộ kịch bản tương thích.');
    } else if (action === 'toggle-program') {
      await request('/programs/' + encodeURIComponent(button.dataset.programCode) + '/enabled', {
        enabled: button.dataset.enabled === 'true', reason: reason
      });
      reloadSuccess('Đã cập nhật lifecycle program.');
    } else if (action === 'activate-template-version') {
      await request('/templates/' + encodeURIComponent(button.dataset.templateCode) + '/versions/' + button.dataset.versionId + '/activate', {reason: reason});
      reloadSuccess('Đã kích hoạt phiên bản kịch bản.');
    } else if (action === 'toggle-template') {
      await request('/templates/' + encodeURIComponent(button.dataset.templateCode) + '/enabled', {
        enabled: button.dataset.enabled === 'true', reason: reason
      });
      reloadSuccess('Đã cập nhật trạng thái kịch bản.');
    } else if (action === 'activate-profile') {
      await request('/profiles/' + button.dataset.profileKind + '/' + button.dataset.profileId + '/activate', {reason: reason});
      reloadSuccess('Đã kích hoạt profile.');
    }
  }

  async function openTemplateVersionDialog(button) {
    var data = await catalog();
    var program = data.programs.find(function (item) { return item.code === button.dataset.programCode; });
    var template = program && program.templates.find(function (item) { return item.code === button.dataset.templateCode; });
    var dialog = document.getElementById('template-version-dialog');
    var form = document.getElementById('template-version-form');
    form.elements.templateCode.value = button.dataset.templateCode;
    form.elements.programVersionId.innerHTML = program.versions.map(function (version) {
      return '<option value="' + version.id + '">v' + version.versionNumber + ' · ' + version.status + '</option>';
    }).join('');
    form.elements.configJson.value = template && template.versions.length
      ? template.versions[0].configJson : '{"schemaVersion":"assessment-template-v1","skills":{}}';
    dialog.showModal();
  }

  function setupActions() {
    document.addEventListener('click', function (event) {
      var button = event.target.closest('[data-governance-action]');
      if (!button) return;
      if (button.dataset.governanceAction === 'new-template-version') {
        openTemplateVersionDialog(button).catch(function (error) { message(error.message, true); });
        return;
      }
      openGovernanceActionDialog(button);
    });

    var actionForm = document.getElementById('governance-action-form');
    if (actionForm) actionForm.addEventListener('submit', function (event) {
      event.preventDefault();
      if (!pendingGovernanceAction) return;
      var reason = new FormData(actionForm).get('reason');
      executeGovernanceAction(pendingGovernanceAction, reason)
        .catch(function (error) { message(error.message, true); });
    });
  }

  buildSkillRows('program-skill-builder', 'program', false);
  buildSkillRows('template-skill-builder', 'template', true);
  setupDialogs();
  setupProgramPanels();
  setupForms();
  setupActions();
  var saved = window.sessionStorage.getItem('practiceGovernanceMessage');
  if (saved) {
    window.sessionStorage.removeItem('practiceGovernanceMessage');
    message(saved, false);
  }
})();

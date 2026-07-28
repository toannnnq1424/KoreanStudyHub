/**
 * KSH Practice progress progressive enhancement.
 * Canonical values, arithmetic, cohorts, coverage and trend events are owned by
 * the server DTO. This file only enhances those facts with optional visuals.
 */
(() => {
  'use strict';

  const SKILL_LABELS_VI = Object.freeze({
    READING: 'Đọc',
    LISTENING: 'Nghe',
    WRITING: 'Viết',
    SPEAKING: 'Nói'
  });
  document.addEventListener('DOMContentLoaded', () => {
    enhanceHeatmap();
    scheduleChartLoading();
  });

  function enhanceHeatmap() {
    const shell = document.querySelector('[data-chart-shell="heatmap"]');
    if (!shell) return;
    try {
      if (renderHeatmap()) shell.dataset.chartEnhanced = 'true';
    } catch (error) {
      markChartFailure(
        shell,
        'Không thể dựng lịch trực quan từ dữ kiện hiện tại · 현재 사실로 시각 캘린더를 만들 수 없습니다.'
      );
    }
  }

  function scheduleChartLoading() {
    const firstCanvas = document.querySelector('canvas[id^="chart-"]');
    if (!firstCanvas) return;
    let scheduled = false;
    const loadWhenIdle = () => {
      if (scheduled) return;
      scheduled = true;
      const schedule = window.requestIdleCallback
        || ((callback) => window.setTimeout(callback, 0));
      schedule(loadChartLibrary, { timeout: 1500 });
    };
    const observeCharts = () => {
      if (!('IntersectionObserver' in window)) {
        loadWhenIdle();
        return;
      }
      const observer = new IntersectionObserver((entries) => {
        if (!entries.some((entry) => entry.isIntersecting)) return;
        observer.disconnect();
        loadWhenIdle();
      }, { rootMargin: '320px 0px' });
      observer.observe(firstCanvas.closest('[data-chart-shell]') || firstCanvas);
    };
    if (document.readyState === 'complete') observeCharts();
    else window.addEventListener('load', observeCharts, { once: true });
  }

  function loadChartLibrary() {
    if (typeof window.Chart !== 'undefined') {
      renderAllCharts();
      return;
    }
    const script = document.createElement('script');
    script.src = 'https://cdn.jsdelivr.net/npm/chart.js@4.4.3/dist/chart.umd.min.js';
    script.async = true;
    script.onload = renderAllCharts;
    script.onerror = () => markAllChartFailures(
      'Không tải được thư viện biểu đồ · 차트 라이브러리를 불러오지 못했습니다.'
    );
    document.head.appendChild(script);
  }

  function renderAllCharts() {
    enhanceChart('radar', renderRadar);
    enhanceChart('distribution', renderDistribution);
    enhanceChart('trend', renderTrend);
  }

  function enhanceChart(name, renderer) {
    const shell = document.querySelector(`[data-chart-shell="${name}"]`);
    if (!shell) return;
    try {
      const rendered = renderer(shell);
      if (!rendered) {
        settleChartWithoutVisual(shell);
        return;
      }
      shell.dataset.chartEnhanced = 'true';
    } catch (error) {
      markChartFailure(
        shell,
        'Không thể dựng biểu đồ từ dữ kiện hiện tại · 현재 사실로 차트를 만들 수 없습니다.'
      );
    }
  }

  function markAllChartFailures(message) {
    document.querySelectorAll('[data-chart-shell]').forEach((shell) => {
      if (shell.querySelector('canvas')) markChartFailure(shell, message);
    });
  }

  function markChartFailure(shell, message) {
    hideChartLoading(shell);
    const canvas = shell.querySelector('canvas');
    if (canvas) {
      canvas.hidden = true;
      canvas.setAttribute('aria-hidden', 'true');
    }
    const visual = shell.querySelector('[data-chart-visual]');
    if (visual) visual.hidden = true;
    const status = shell.querySelector('[data-chart-failure]');
    if (status) {
      status.hidden = false;
      const messageTarget = status.querySelector('[data-chart-failure-message]');
      const failureCopy = `CHART_ENHANCEMENT_UNAVAILABLE — ${message} `
        + 'Bảng chuẩn vẫn dùng được · 표는 계속 사용할 수 있습니다.';
      if (messageTarget) messageTarget.textContent = failureCopy;
    }
    const fallback = shell.querySelector('[data-chart-fallback]');
    if (fallback && fallback.tagName === 'DETAILS') fallback.open = true;
    shell.dataset.chartState = 'CHART_ENHANCEMENT_UNAVAILABLE';
  }

  function settleChartWithoutVisual(shell) {
    hideChartLoading(shell);
    const fallback = shell.querySelector('[data-chart-fallback]');
    if (fallback && fallback.tagName === 'DETAILS') fallback.open = true;
    shell.dataset.chartState = 'NO_RENDERABLE_DATA';
  }

  function hideChartLoading(scope) {
    const loading = scope.querySelector('[data-chart-loading]');
    if (loading) loading.hidden = true;
  }

  function revealCanvas(canvas, label) {
    const shell = canvas.closest('[data-chart-shell]');
    if (shell) hideChartLoading(shell);
    canvas.hidden = false;
    canvas.removeAttribute('aria-hidden');
    canvas.setAttribute('role', 'img');
    canvas.setAttribute('aria-label', label);
  }

  function renderableNumericFact(fact) {
    return fact
      && (fact.availability === 'AVAILABLE' || fact.availability === 'PARTIAL')
      && fact.value !== null
      && Number.isFinite(Number(fact.value));
  }

  function renderRadar(shell) {
    const canvas = shell.querySelector('#chart-radar-skills');
    const metrics = Array.isArray(OVERVIEW_DATA.skillMetrics)
      ? OVERVIEW_DATA.skillMetrics.filter((metric) =>
        (metric.skill === 'READING' || metric.skill === 'LISTENING')
        && renderableNumericFact(metric.scoreFact))
      : [];
    if (!canvas || metrics.length === 0) return false;

    revealCanvas(canvas, 'Biểu đồ điểm Đọc và Nghe đủ điều kiện');
    new window.Chart(canvas.getContext('2d'), {
      type: 'bar',
      data: {
        labels: metrics.map((metric) => SKILL_LABELS_VI[metric.skill]),
        datasets: [{
          label: 'Điểm đủ điều kiện',
          data: metrics.map((metric) => Number(metric.scoreFact.value)),
          backgroundColor: metrics.map((metric) =>
            metric.skill === 'READING'
              ? 'rgba(37, 99, 235, 0.82)'
              : 'rgba(124, 58, 237, 0.78)'),
          borderRadius: 0,
          borderSkipped: false,
          barThickness: 24
        }]
      },
      options: {
        animation: false,
        indexAxis: 'y',
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          x: {
            min: 0,
            max: 100,
            grid: { color: 'rgba(148, 163, 184, 0.18)' },
            ticks: { callback: (value) => `${value}%` }
          },
          y: {
            grid: { display: false },
            ticks: { color: '#334155', font: { weight: '700' } }
          }
        },
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              label(context) {
                return ` ${context.raw}% điểm đủ điều kiện`;
              }
            }
          }
        }
      }
    });
    return true;
  }

  function renderDistribution(shell) {
    const canvas = shell.querySelector('#chart-donut-distribution');
    const metrics = Array.isArray(OVERVIEW_DATA.skillMetrics)
      ? OVERVIEW_DATA.skillMetrics.filter((metric) =>
        metric.attemptCounts && Number(metric.attemptCounts.total) > 0)
      : [];
    if (!canvas || metrics.length === 0) return false;

    revealCanvas(canvas, 'Biểu đồ số hoạt động theo kỹ năng');
    new window.Chart(canvas.getContext('2d'), {
      type: 'doughnut',
      data: {
        labels: metrics.map((metric) => SKILL_LABELS_VI[metric.skill] || 'Kỹ năng'),
        datasets: [{
          data: metrics.map((metric) => Number(metric.attemptCounts.total)),
          backgroundColor: [
            'rgba(59, 130, 246, 0.75)',
            'rgba(168, 85, 247, 0.75)',
            'rgba(16, 185, 129, 0.75)',
            'rgba(249, 115, 22, 0.75)'
          ],
          borderColor: '#ffffff',
          borderWidth: 2
        }]
      },
      options: {
        animation: false,
        responsive: true,
        maintainAspectRatio: false,
        cutout: '65%',
        plugins: {
          legend: { position: 'bottom' },
          tooltip: {
            callbacks: {
              label(context) {
                return ` ${context.label}: ${context.raw} hoạt động`;
              }
            }
          }
        }
      }
    });
    return true;
  }

  function buildScoreTrendEventSlots(trend) {
    const eventSlots = [];
    const eventSlotsByKey = new Map();
    const occurrenceByDateAndSkill = new Map();

    trend
      .map((point, sourceIndex) => ({ point, sourceIndex }))
      .sort((left, right) => {
        const dateOrder = String(left.point.date).localeCompare(String(right.point.date));
        return dateOrder !== 0 ? dateOrder : left.sourceIndex - right.sourceIndex;
      })
      .forEach(({ point }) => {
        const occurrenceKey = `${point.date}::${point.skill}`;
        const occurrence = occurrenceByDateAndSkill.get(occurrenceKey) || 0;
        occurrenceByDateAndSkill.set(occurrenceKey, occurrence + 1);

        const eventKey = `${point.date}::${occurrence}`;
        let slot = eventSlotsByKey.get(eventKey);
        if (!slot) {
          slot = { key: eventKey, date: point.date, pointsBySkill: {} };
          eventSlotsByKey.set(eventKey, slot);
          eventSlots.push(slot);
        }
        slot.pointsBySkill[point.skill] = {
          score: Number(point.scoreFact.value),
          title: point.title
        };
      });
    return eventSlots;
  }

  function renderTrend(shell) {
    const canvas = shell.querySelector('#chart-score-trend');
    const trend = Array.isArray(ANALYTICS_DATA.scoreTrend)
      ? ANALYTICS_DATA.scoreTrend.filter((point) =>
        (point.skill === 'READING' || point.skill === 'LISTENING')
        && renderableNumericFact(point.scoreFact))
      : [];
    if (!canvas || trend.length === 0) return false;

    const slots = buildScoreTrendEventSlots(trend);
    const styles = {
      READING: { label: 'Đọc', color: 'rgba(37, 99, 235, 0.9)' },
      LISTENING: { label: 'Nghe', color: 'rgba(126, 34, 206, 0.9)' }
    };
    const datasets = Object.entries(styles).map(([skill, style]) => ({
      skill,
      label: style.label,
      data: slots.map((slot) => slot.pointsBySkill[skill]?.score ?? null),
      borderColor: style.color,
      backgroundColor: style.color,
      borderWidth: 2,
      spanGaps: true,
      tension: 0
    }));

    revealCanvas(canvas, 'Biểu đồ xu hướng điểm Đọc và Nghe đủ điều kiện');
    new window.Chart(canvas.getContext('2d'), {
      type: 'line',
      data: {
        labels: slots.map((slot) => slot.date),
        datasets
      },
      options: {
        animation: false,
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          y: { suggestedMin: 0, suggestedMax: 100 },
          x: { ticks: { maxRotation: 30, minRotation: 0 } }
        },
        plugins: {
          tooltip: {
            callbacks: {
              label(context) {
                const slot = slots[context.dataIndex];
                const evidence = slot.pointsBySkill[context.dataset.skill];
                if (!evidence) return null;
                const title = evidence.title ? ` · ${evidence.title}` : '';
                return ` ${context.dataset.label}: ${evidence.score}%${title}`;
              }
            }
          }
        }
      }
    });
    return true;
  }

  function renderHeatmap() {
    const grid = document.getElementById('heatmap-grid');
    const tooltip = document.getElementById('heatmap-tooltip');
    const cells = Array.isArray(OVERVIEW_DATA.heatmap) ? OVERVIEW_DATA.heatmap : [];
    if (!grid || !tooltip || cells.length === 0) return false;

    cells.forEach((cell) => {
      const day = document.createElement('div');
      day.className = 'pp-heatmap-day';
      const activities = Number(cell.attemptCount) || 0;
      day.classList.add(activities === 0 ? 'lvl-0'
        : activities === 1 ? 'lvl-1'
          : activities <= 3 ? 'lvl-2' : 'lvl-3');
      const duration = cell.totalMinutes === null
        ? 'thời lượng chưa khả dụng'
        : `${cell.totalMinutes} phút hợp lệ`;
      const label = `${cell.date}: ${activities} hoạt động, ${duration}`;
      day.title = label;

      const show = () => {
        tooltip.textContent = label;
        tooltip.style.display = 'block';
        const rect = day.getBoundingClientRect();
        tooltip.style.left = `${rect.left + window.scrollX}px`;
        tooltip.style.top = `${rect.top + window.scrollY - 42}px`;
      };
      const hide = () => {
        tooltip.style.display = 'none';
      };
      day.addEventListener('mouseenter', show);
      day.addEventListener('mouseleave', hide);
      grid.appendChild(day);
    });
    const visual = grid.closest('[data-chart-visual]');
    if (visual) visual.hidden = false;
    return true;
  }
})();

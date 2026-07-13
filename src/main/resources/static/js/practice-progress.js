/**
 * KSH Korean Study Hub
 * Practice Progress Analytics Page JS
 */

document.addEventListener('DOMContentLoaded', () => {
  initTabs();
  renderHeatmap();
  switchSkillAccordion('READING');
  scheduleChartLoading();
});

function scheduleChartLoading() {
  const loadWhenIdle = () => {
    const schedule = window.requestIdleCallback || ((callback) => window.setTimeout(callback, 0));
    schedule(loadChartLibrary, { timeout: 1500 });
  };
  if (document.readyState === 'complete') loadWhenIdle();
  else window.addEventListener('load', loadWhenIdle, { once: true });
}

function loadChartLibrary() {
  if (typeof window.Chart !== 'undefined') {
    renderOverviewCharts();
    renderAnalyticsCharts();
    return;
  }
  const script = document.createElement('script');
  script.src = 'https://cdn.jsdelivr.net/npm/chart.js@4.4.3/dist/chart.umd.min.js';
  script.async = true;
  script.onload = () => {
    renderOverviewCharts();
    renderAnalyticsCharts();
  };
  script.onerror = renderChartFallbacks;
  document.head.appendChild(script);
}

function renderChartFallbacks() {
  document.querySelectorAll('.pp-chart-container').forEach(container => {
    if (container.querySelector('.pp-chart-fallback')) return;
    const message = document.createElement('p');
    message.className = 'pp-chart-fallback';
    message.textContent = 'Biểu đồ chưa tải được. Dữ liệu chi tiết vẫn có trong các bảng bên dưới.';
    container.appendChild(message);
  });
}

// ── TAB SWITCHING & SYNC ──
function initTabs() {
  // Sync tab active state from template variable or URL query param
  const urlParams = new URLSearchParams(window.location.search);
  const activeTab = urlParams.get('tab') || CURRENT_TAB || 'overview';
  switchTab(activeTab, false);
}

function switchTab(tabId, updateUrl = true) {
  // Hide all panels
  document.querySelectorAll('.pp-tab-panel').forEach(p => p.classList.remove('active'));
  // Deactivate all tab buttons
  document.querySelectorAll('.pp-tab-btn').forEach(b => b.classList.remove('active'));

  // Show selected panel and active button
  const selectedPanel = document.getElementById(`tab-${tabId}`);
  const selectedBtn = document.getElementById(`btn-${tabId}`);

  if (selectedPanel && selectedBtn) {
    selectedPanel.classList.add('active');
    selectedBtn.classList.add('active');
  }

  // Update query param without reload
  if (updateUrl) {
    const url = new URL(window.location.href);
    url.searchParams.set('tab', tabId);
    window.history.pushState({}, '', url.toString());
  }
}

// ── CALENDAR HEATMAP GENERATOR ──
function renderHeatmap() {
  const gridContainer = document.getElementById('heatmap-grid');
  if (!gridContainer || !OVERVIEW_DATA.heatmap) return;

  const cells = OVERVIEW_DATA.heatmap;
  const tooltip = document.getElementById('heatmap-tooltip');

  gridContainer.innerHTML = '';

  cells.forEach(cell => {
    const dayElement = document.createElement('div');
    dayElement.className = 'pp-heatmap-day';

    // Map intensity level
    let lvl = 0;
    if (cell.attemptCount > 0) {
      if (cell.totalMinutes > 60) lvl = 3;
      else if (cell.totalMinutes >= 15) lvl = 2;
      else lvl = 1;
    }
    dayElement.classList.add(`lvl-${lvl}`);

    // Parse date for clean tooltip display
    const dateParts = cell.date.split('-');
    const displayDate = dateParts.length === 3 ? `${dateParts[2]}/${dateParts[1]}/${dateParts[0]}` : cell.date;

    // Tooltip event handlers
    dayElement.addEventListener('mouseenter', (e) => {
      let msg = `Không có hoạt động vào ngày ${displayDate}`;
      if (cell.attemptCount > 0) {
        msg = `${cell.attemptCount} lượt luyện, ${cell.totalMinutes} phút học vào ngày ${displayDate}`;
      }
      tooltip.innerText = msg;
      tooltip.style.display = 'block';

      // Position tooltip
      const rect = dayElement.getBoundingClientRect();
      const scrollLeft = window.pageXOffset || document.documentElement.scrollLeft;
      const scrollTop = window.pageYOffset || document.documentElement.scrollTop;

      tooltip.style.left = `${rect.left + scrollLeft - tooltip.offsetWidth / 2 + 6}px`;
      tooltip.style.top = `${rect.top + scrollTop - tooltip.offsetHeight - 8}px`;
    });

    dayElement.addEventListener('mouseleave', () => {
      tooltip.style.display = 'none';
    });

    gridContainer.appendChild(dayElement);
  });
}

// ── CHART GENERATION ──
let radarChartInstance = null;
let donutChartInstance = null;
let lineChartInstance = null;

function renderOverviewCharts() {
  // 1. Radar Chart: Skills score
  const radarCanvas = document.getElementById('chart-radar-skills');
  if (radarCanvas && OVERVIEW_DATA.skillMetrics) {
    const labels = OVERVIEW_DATA.skillMetrics.map(m => m.skillLabel);
    const dataPoints = OVERVIEW_DATA.skillMetrics.map(m => m.normalizedScore);

    const ctx = radarCanvas.getContext('2d');
    if (radarChartInstance) radarChartInstance.destroy();

    radarChartInstance = new Chart(ctx, {
      type: 'radar',
      data: {
        labels: labels,
        datasets: [{
          label: 'Điểm trung bình kỹ năng',
          data: dataPoints,
          backgroundColor: 'rgba(59, 130, 246, 0.15)',
          borderColor: 'rgba(59, 130, 246, 0.8)',
          borderWidth: 2,
          pointBackgroundColor: 'rgba(59, 130, 246, 1)',
          pointBorderColor: '#fff',
          pointHoverBackgroundColor: '#fff',
          pointHoverBorderColor: 'rgba(59, 130, 246, 1)'
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          r: {
            angleLines: { display: true, color: '#f1f5f9' },
            grid: { color: '#f1f5f9' },
            suggestedMin: 0,
            suggestedMax: 100,
            ticks: {
              stepSize: 20,
              backdropColor: 'transparent',
              color: '#94a3b8',
              font: { size: 9 }
            },
            pointLabels: {
              color: '#334155',
              font: { size: 12, weight: 'bold' }
            }
          }
        },
        plugins: {
          legend: { display: false }
        }
      }
    });
  }

  // 2. Donut Chart: Attempts distribution
  const donutCanvas = document.getElementById('chart-donut-distribution');
  if (donutCanvas && OVERVIEW_DATA.skillMetrics) {
    const labels = OVERVIEW_DATA.skillMetrics.map(m => m.skillLabel);
    const counts = OVERVIEW_DATA.skillMetrics.map(m => m.attemptCount);
    const totalCount = counts.reduce((a, b) => a + b, 0);

    const ctx = donutCanvas.getContext('2d');
    if (donutChartInstance) donutChartInstance.destroy();

    donutChartInstance = new Chart(ctx, {
      type: 'doughnut',
      data: {
        labels: labels,
        datasets: [{
          data: counts,
          backgroundColor: [
            'rgba(59, 130, 246, 0.75)',  // Blue (Reading)
            'rgba(168, 85, 247, 0.75)', // Purple (Listening)
            'rgba(16, 185, 129, 0.75)', // Green (Writing)
            'rgba(249, 115, 22, 0.75)'  // Orange (Speaking)
          ],
          borderColor: '#ffffff',
          borderWidth: 2,
          hoverOffset: 4
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            position: 'bottom',
            labels: {
              boxWidth: 12,
              padding: 16,
              color: '#475569',
              font: { size: 11, weight: 'bold' }
            }
          },
          tooltip: {
            callbacks: {
              label: function(context) {
                const value = context.raw || 0;
                const pct = totalCount === 0 ? 0 : Math.round((value / totalCount) * 100);
                return ` ${context.label}: ${value} lượt (${pct}%)`;
              }
            }
          }
        },
        cutout: '65%'
      }
    });
  }
}

function renderAnalyticsCharts() {
  const lineCanvas = document.getElementById('chart-score-trend');
  if (lineCanvas && ANALYTICS_DATA.scoreTrend) {
    const trend = ANALYTICS_DATA.scoreTrend;

    // Group score trends by skill
    const datasetMap = {
      'READING': { label: 'Đọc (Reading)', data: [], borderColor: 'rgba(59, 130, 246, 0.85)', backgroundColor: 'rgba(59, 130, 246, 0.05)' },
      'LISTENING': { label: 'Nghe (Listening)', data: [], borderColor: 'rgba(168, 85, 247, 0.85)', backgroundColor: 'rgba(168, 85, 247, 0.05)' },
      'WRITING': { label: 'Viết (Writing)', data: [], borderColor: 'rgba(16, 185, 129, 0.85)', backgroundColor: 'rgba(16, 185, 129, 0.05)' },
      'SPEAKING': { label: 'Nói (Speaking)', data: [], borderColor: 'rgba(249, 115, 22, 0.85)', backgroundColor: 'rgba(249, 115, 22, 0.05)' }
    };

    const uniqueDates = [];
    const pointsMap = {};

    trend.forEach(pt => {
      if (!uniqueDates.includes(pt.date)) {
        uniqueDates.push(pt.date);
      }
      if (!pointsMap[pt.date]) {
        pointsMap[pt.date] = {};
      }
      pointsMap[pt.date][pt.skill] = { score: pt.normalizedScore, title: pt.title };
    });

    // Sort unique dates chronologically
    uniqueDates.sort();

    // Map scores to date points
    uniqueDates.forEach(date => {
      Object.keys(datasetMap).forEach(skill => {
        const item = pointsMap[date][skill];
        datasetMap[skill].data.push(item ? item.score : null); // null allows disconnected line points or missing values
      });
    });

    const datasets = Object.keys(datasetMap).map(skill => {
      const ds = datasetMap[skill];
      return {
        label: ds.label,
        data: ds.data,
        borderColor: ds.borderColor,
        backgroundColor: ds.backgroundColor,
        borderWidth: 2.5,
        tension: 0.25,
        spanGaps: true, // Connect lines across nulls
        pointRadius: 4,
        pointBackgroundColor: ds.borderColor,
        pointBorderColor: '#fff',
        pointHoverRadius: 6
      };
    });

    const ctx = lineCanvas.getContext('2d');
    if (lineChartInstance) lineChartInstance.destroy();

    lineChartInstance = new Chart(ctx, {
      type: 'line',
      data: {
        labels: uniqueDates.map(d => d.split(' ')[0]), // Date only for ticks
        datasets: datasets
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          x: {
            grid: { display: false },
            ticks: { color: '#64748b', font: { size: 10 } }
          },
          y: {
            suggestedMin: 0,
            suggestedMax: 100,
            grid: { color: '#f1f5f9' },
            ticks: { color: '#64748b', font: { size: 10 } }
          }
        },
        plugins: {
          legend: {
            position: 'bottom',
            labels: { boxWidth: 10, font: { size: 11, weight: 'bold' }, color: '#475569' }
          },
          tooltip: {
            callbacks: {
              title: function(context) {
                const index = context[0].dataIndex;
                return `Ngày nộp: ${uniqueDates[index]}`;
              },
              label: function(context) {
                const val = context.raw;
                if (val === null) return null;
                const index = context.dataIndex;
                const date = uniqueDates[index];
                const skillKeys = ['READING', 'LISTENING', 'WRITING', 'SPEAKING'];
                // Find matching skill key
                const skillKey = skillKeys[context.datasetIndex];
                const info = pointsMap[date][skillKey];
                const title = info && info.title ? ` (${info.title})` : '';
                return ` ${context.dataset.label}: ${val}%${title}`;
              }
            }
          }
        }
      }
    });
  }
}

// ── ACCORDION SELECT SWITCH ──
function switchSkillAccordion(skill) {
  // Active classes for accordion tabs
  document.querySelectorAll('.pp-accordion-tab-btn').forEach(btn => {
    btn.classList.remove('active');
    if (btn.innerText.toUpperCase().includes(skill)) {
      btn.classList.add('active');
    }
  });

  const body = document.getElementById('question-type-table-body');
  if (!body) return;

  body.innerHTML = '';

  if (!ANALYTICS_DATA.questionTypePerf) {
    renderEmptyTable(body);
    return;
  }

  const filtered = ANALYTICS_DATA.questionTypePerf.filter(p => p.skill === skill);

  if (filtered.length === 0) {
    renderEmptyTable(body);
    return;
  }

  filtered.forEach(row => {
    const tr = document.createElement('tr');

    tr.innerHTML = `
      <td><strong>${row.questionTypeLabel}</strong> <span style="font-size:0.75rem; color:#94a3b8;">(${row.questionType})</span></td>
      <td>${row.totalAttempts} lượt câu</td>
      <td class="pp-table-badge">${row.averageScore}%</td>
      <td style="font-weight:600; color:#334155;">${row.bestScore}%</td>
      <td style="color:#64748b; font-size:0.82rem;">${row.lastPracticedAt || '--'}</td>
    `;
    body.appendChild(tr);
  });
}

function renderEmptyTable(container) {
  container.innerHTML = `
    <tr>
      <td colspan="5" style="text-align:center; color:#94a3b8; padding:32px;">
        Chưa có lượt đề nào ghi lại chi tiết cho kỹ năng này.
      </td>
    </tr>
  `;
}

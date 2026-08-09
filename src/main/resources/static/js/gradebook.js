(() => {
  const root = document.querySelector('[data-gradebook]');
  if (!root) return;

  const custom = root.querySelector('[data-custom-average]');
  const filter = root.querySelector('[data-kind-filter]');
  const search = root.querySelector('[data-student-search]');
  const banner = root.querySelector('[data-selection-banner]');
  const selectedCount = root.querySelector('[data-selected-count]');
  const headers = [...root.querySelectorAll('th[data-column-key]')];
  const selected = new Set();

  const visibleHeaders = () => headers.filter((header) => !header.hidden);

  function refreshAverages() {
    const activeKeys = custom.checked ? selected : new Set(visibleHeaders().map((h) => h.dataset.columnKey));
    root.querySelectorAll('tbody tr').forEach((row) => {
      const values = [...row.querySelectorAll('td[data-column-key]')]
        .filter((cell) => !cell.hidden && activeKeys.has(cell.dataset.columnKey) && cell.dataset.normalized !== '')
        .map((cell) => Number(cell.dataset.normalized));
      row.querySelector('[data-average]').textContent = values.length
        ? (values.reduce((sum, value) => sum + value, 0) / values.length).toFixed(2).replace(/\.00$/, '')
        : '—';
    });
    selectedCount.textContent = String(selected.size);
    banner.hidden = !custom.checked;
  }

  function refreshColumns() {
    const kind = filter.value;
    headers.forEach((header) => {
      const hidden = kind !== 'ALL' && header.dataset.columnKind !== kind;
      header.hidden = hidden;
      root.querySelectorAll(`td[data-column-key="${CSS.escape(header.dataset.columnKey)}"]`)
        .forEach((cell) => { cell.hidden = hidden; });
    });
    refreshAverages();
  }

  function toggleHeader(header) {
    if (header.hidden) return;
    // Clicking a score column is itself the intent to use custom averaging;
    // do not force lecturers to discover and toggle a separate switch first.
    if (!custom.checked) custom.checked = true;
    const key = header.dataset.columnKey;
    selected.has(key) ? selected.delete(key) : selected.add(key);
    header.classList.toggle('is-selected', selected.has(key));
    root.querySelectorAll(`td[data-column-key="${CSS.escape(key)}"]`)
      .forEach((cell) => cell.classList.toggle('is-selected', selected.has(key)));
    refreshAverages();
  }

  headers.forEach((header) => {
    header.addEventListener('click', () => toggleHeader(header));
    header.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        toggleHeader(header);
      }
    });
  });
  custom.addEventListener('change', () => {
    if (!custom.checked) {
      selected.clear();
      root.querySelectorAll('.is-selected').forEach((element) => element.classList.remove('is-selected'));
    }
    refreshAverages();
  });
  filter.addEventListener('change', refreshColumns);
  search.addEventListener('input', () => {
    const query = search.value.trim().toLocaleLowerCase('vi');
    root.querySelectorAll('tbody tr').forEach((row) => {
      row.hidden = query !== '' && !row.dataset.studentSearchValue.includes(query);
    });
  });
  root.querySelector('[data-clear-selection]').addEventListener('click', () => {
    selected.clear();
    root.querySelectorAll('.is-selected').forEach((element) => element.classList.remove('is-selected'));
    refreshAverages();
  });

  refreshColumns();
})();

/* ═══════════════════════════════════════════════════════════════
   SAP Billing Dashboard — Application Logic
   ═══════════════════════════════════════════════════════════════ */

// ── API Base Auto-Detector ──────────────────────────────────
const API_BASE = (function() {
    if (window.location.protocol.startsWith('http')) {
        return window.location.origin;
    }
    // Default fallback if opened via file:// protocol
    return 'http://localhost:8085';
})();

// ── State ───────────────────────────────────────────────────
let allBills = [];
let currentView = 'dashboard';
let currentPage = 1;
const PAGE_SIZE = 15;
let chartInstances = {};
let searchDebounceTimer = null;

// ── DOM Ready ───────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    initNavigation();
    initEventListeners();
    loadDashboard();
});

// ═══════════════════════════════════════════════════════════════
// NAVIGATION
// ═══════════════════════════════════════════════════════════════

function initNavigation() {
    const navItems = document.querySelectorAll('.nav-item');
    navItems.forEach(item => {
        item.addEventListener('click', (e) => {
            e.preventDefault();
            const view = item.dataset.view;
            switchView(view);
        });
    });
}

function switchView(view) {
    currentView = view;

    // Update nav active states
    document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));
    const activeNav = document.querySelector(`.nav-item[data-view="${view}"]`);
    if (activeNav) activeNav.classList.add('active');

    // Update breadcrumb
    const crumbNames = { dashboard: 'Dashboard', bills: 'All Bills', sync: 'Sync Data' };
    const breadcrumb = document.getElementById('breadcrumb');
    breadcrumb.innerHTML = `
        <span class="crumb">Home</span>
        <span class="crumb-sep">/</span>
        <span class="crumb active">${crumbNames[view] || view}</span>
    `;

    // Show/hide views
    document.querySelectorAll('.view-section').forEach(el => el.classList.remove('active'));
    const viewEl = document.getElementById(`view-${view}`);
    if (viewEl) viewEl.classList.add('active');

    // Load data for view
    if (view === 'bills') loadBillsView();
    if (view === 'sync') loadSyncView();

    // Close mobile sidebar
    document.getElementById('sidebar').classList.remove('open');
}

// ═══════════════════════════════════════════════════════════════
// EVENT LISTENERS
// ═══════════════════════════════════════════════════════════════

function initEventListeners() {
    // Menu toggle (mobile)
    document.getElementById('menuToggle').addEventListener('click', () => {
        document.getElementById('sidebar').classList.toggle('open');
    });

    // Header sync button
    document.getElementById('headerSyncBtn').addEventListener('click', () => triggerSync());

    // View All Bills button
    document.getElementById('viewAllBillsBtn').addEventListener('click', () => switchView('bills'));

    // Sync Now button
    document.getElementById('syncNowBtn').addEventListener('click', () => triggerSync());

    // Detail panel close
    document.getElementById('detailClose').addEventListener('click', closeDetailPanel);
    document.getElementById('detailOverlay').addEventListener('click', closeDetailPanel);

    // Search with debounce
    document.getElementById('billSearchInput').addEventListener('input', (e) => {
        clearTimeout(searchDebounceTimer);
        searchDebounceTimer = setTimeout(() => {
            currentPage = 1;
            renderBillsTable();
        }, 300);
    });

    // Filters
    document.getElementById('filterStatus').addEventListener('change', () => { currentPage = 1; loadBillsFromDB(); });
    document.getElementById('filterType').addEventListener('change', () => { currentPage = 1; loadBillsFromDB(); });
    document.getElementById('filterCurrency').addEventListener('change', () => { currentPage = 1; loadBillsFromDB(); });
}

// ═══════════════════════════════════════════════════════════════
// DASHBOARD
// ═══════════════════════════════════════════════════════════════

async function loadDashboard() {
    try {
        // Load stats
        const statsRes = await fetch(`${API_BASE}/api/stats`);
        const stats = await statsRes.json();

        if (stats.error) {
            console.error('Stats error:', stats.error);
            updateSyncStatus('error', 'Database error');
            return;
        }

        updateSyncStatus('connected', 'Connected');
        renderStats(stats);
        renderCharts(stats);
        updateLastSyncInfo(stats.lastSync);

        // Load recent bills
        const billsRes = await fetch(`${API_BASE}/api/bills`);
        const bills = await billsRes.json();
        allBills = Array.isArray(bills) ? bills : [];
        renderRecentBills(allBills.slice(0, 8));

        // Populate currency filter
        if (stats.currencies) {
            const currSelect = document.getElementById('filterCurrency');
            currSelect.innerHTML = '<option value="">All Currencies</option>';
            stats.currencies.forEach(c => {
                currSelect.innerHTML += `<option value="${c}">${c}</option>`;
            });
        }

    } catch (err) {
        console.error('Dashboard load error:', err);
        updateSyncStatus('error', 'Connection failed');
        document.getElementById('recentBillsTable').innerHTML = `
            <div class="empty-state">
                <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                    <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/>
                    <line x1="12" y1="16" x2="12.01" y2="16"/>
                </svg>
                <p>Could not connect to the server.<br>Make sure the backend is running on port 8080.</p>
            </div>
        `;
    }
}

function renderStats(stats) {
    animateNumber('statTotalBills', stats.totalBills || 0);

    const open = (stats.byStatus && stats.byStatus.OPEN) || 0;
    const closed = (stats.byStatus && stats.byStatus.CLOSED) || 0;
    animateNumber('statOpen', open);
    animateNumber('statClosed', closed);

    const gross = stats.totalGrossAmount || 0;
    document.getElementById('statRevenue').textContent = formatCurrency(gross);

    const currCount = stats.currencies ? stats.currencies.length : 0;
    animateNumber('statCurrencies', currCount);
}

function animateNumber(elementId, target) {
    const el = document.getElementById(elementId);
    const duration = 800;
    const start = performance.now();
    const initial = 0;

    function update(now) {
        const elapsed = now - start;
        const progress = Math.min(elapsed / duration, 1);
        // Ease out cubic
        const eased = 1 - Math.pow(1 - progress, 3);
        const current = Math.round(initial + (target - initial) * eased);
        el.textContent = current.toLocaleString();
        if (progress < 1) requestAnimationFrame(update);
    }

    requestAnimationFrame(update);
}

function renderCharts(stats) {
    // Destroy old charts
    Object.values(chartInstances).forEach(c => c.destroy());
    chartInstances = {};

    const chartDefaults = {
        color: '#94a3b8',
        borderColor: 'rgba(255,255,255,0.06)',
    };

    Chart.defaults.color = chartDefaults.color;
    Chart.defaults.borderColor = chartDefaults.borderColor;

    // ── Status Doughnut ──
    if (stats.byStatus && Object.keys(stats.byStatus).length > 0) {
        const statusLabels = Object.keys(stats.byStatus);
        const statusData = Object.values(stats.byStatus);
        const statusColors = statusLabels.map(s => {
            if (s === 'OPEN') return '#fbbf24';
            if (s === 'CLOSED') return '#34d399';
            if (s === 'CLOSING') return '#818cf8';
            return '#64748b';
        });

        chartInstances.status = new Chart(document.getElementById('statusChart'), {
            type: 'doughnut',
            data: {
                labels: statusLabels,
                datasets: [{ data: statusData, backgroundColor: statusColors, borderWidth: 0, hoverOffset: 8 }]
            },
            options: {
                responsive: true,
                cutout: '65%',
                plugins: {
                    legend: { position: 'bottom', labels: { padding: 16, usePointStyle: true, pointStyleWidth: 8 } }
                }
            }
        });
    }

    // ── Type Bar ──
    if (stats.byType && Object.keys(stats.byType).length > 0) {
        const typeLabels = Object.keys(stats.byType);
        const typeData = Object.values(stats.byType);
        const typeColors = typeLabels.map(t => {
            if (t === 'CHARGE') return '#fb7185';
            if (t === 'CREDIT') return '#22d3ee';
            return '#64748b';
        });

        chartInstances.type = new Chart(document.getElementById('typeChart'), {
            type: 'bar',
            data: {
                labels: typeLabels,
                datasets: [{ data: typeData, backgroundColor: typeColors, borderRadius: 8, barThickness: 40 }]
            },
            options: {
                responsive: true,
                plugins: { legend: { display: false } },
                scales: {
                    y: { beginAtZero: true, ticks: { stepSize: 1 }, grid: { color: 'rgba(255,255,255,0.04)' } },
                    x: { grid: { display: false } }
                }
            }
        });
    }

    // ── Currency Pie ──
    if (stats.byCurrency && Object.keys(stats.byCurrency).length > 0) {
        const curLabels = Object.keys(stats.byCurrency);
        const curData = Object.values(stats.byCurrency);
        const curColors = ['#6366f1', '#8b5cf6', '#a855f7', '#d946ef', '#ec4899', '#f43f5e', '#22d3ee', '#2dd4bf'];

        chartInstances.currency = new Chart(document.getElementById('currencyChart'), {
            type: 'doughnut',
            data: {
                labels: curLabels,
                datasets: [{ data: curData, backgroundColor: curColors.slice(0, curLabels.length), borderWidth: 0, hoverOffset: 8 }]
            },
            options: {
                responsive: true,
                cutout: '55%',
                plugins: {
                    legend: { position: 'bottom', labels: { padding: 16, usePointStyle: true, pointStyleWidth: 8 } }
                }
            }
        });
    }
}

// ═══════════════════════════════════════════════════════════════
// RECENT BILLS (Dashboard)
// ═══════════════════════════════════════════════════════════════

function renderRecentBills(bills) {
    const container = document.getElementById('recentBillsTable');

    if (!bills || bills.length === 0) {
        container.innerHTML = `
            <div class="empty-state">
                <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                    <polyline points="14 2 14 8 20 8"/>
                </svg>
                <p>No bills yet. Click <strong>Sync</strong> to fetch data from SAP.</p>
            </div>
        `;
        return;
    }

    container.innerHTML = buildBillTable(bills);
    attachRowClickHandlers(container);
}

// ═══════════════════════════════════════════════════════════════
// BILLS VIEW
// ═══════════════════════════════════════════════════════════════

function loadBillsView() {
    loadBillsFromDB();
}

async function loadBillsFromDB() {
    const status = document.getElementById('filterStatus').value;
    const type = document.getElementById('filterType').value;
    const currency = document.getElementById('filterCurrency').value;

    let url = `${API_BASE}/api/bills?`;
    if (status) url += `status=${encodeURIComponent(status)}&`;
    if (type) url += `type=${encodeURIComponent(type)}&`;
    if (currency) url += `currency=${encodeURIComponent(currency)}&`;

    try {
        const res = await fetch(url);
        const data = await res.json();
        allBills = Array.isArray(data) ? data : [];
        currentPage = 1;
        renderBillsTable();
    } catch (err) {
        console.error('Failed to load bills:', err);
        document.getElementById('billsTableContainer').innerHTML = `
            <div class="empty-state"><p>Failed to load bills. Check server connection.</p></div>
        `;
    }
}

function renderBillsTable() {
    const container = document.getElementById('billsTableContainer');
    const searchQuery = document.getElementById('billSearchInput').value.toLowerCase().trim();

    // Filter by search
    let filtered = allBills;
    if (searchQuery) {
        filtered = allBills.filter(b => {
            const searchStr = JSON.stringify(b).toLowerCase();
            return searchStr.includes(searchQuery);
        });
    }

    if (filtered.length === 0) {
        container.innerHTML = `
            <div class="empty-state">
                <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                    <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
                </svg>
                <p>${searchQuery ? 'No bills match your search.' : 'No bills found. Sync data from SAP first.'}</p>
            </div>
        `;
        document.getElementById('tableFooter').style.display = 'none';
        return;
    }

    // Pagination
    const totalPages = Math.ceil(filtered.length / PAGE_SIZE);
    if (currentPage > totalPages) currentPage = totalPages;
    const startIdx = (currentPage - 1) * PAGE_SIZE;
    const pageItems = filtered.slice(startIdx, startIdx + PAGE_SIZE);

    container.innerHTML = buildBillTable(pageItems);
    attachRowClickHandlers(container);

    // Footer
    const footer = document.getElementById('tableFooter');
    footer.style.display = 'flex';
    document.getElementById('resultCount').textContent = `${filtered.length} bill${filtered.length !== 1 ? 's' : ''}`;
    renderPagination(totalPages);
}

function buildBillTable(bills) {
    let html = `<table>
        <thead>
            <tr>
                <th>Bill ID</th>
                <th>Doc #</th>
                <th>Type</th>
                <th>Status</th>
                <th>Currency</th>
                <th>Net Amount</th>
                <th>Gross Amount</th>
                <th>Customer</th>
                <th>Billing Date</th>
                <th>Due Date</th>
            </tr>
        </thead>
        <tbody>`;

    bills.forEach(b => {
        const statusClass = (b.billStatus || '').toLowerCase();
        const typeClass = (b.billingType || '').toLowerCase();
        const netAmt = b.netAmount ? formatAmount(b.netAmount.amount) : '—';
        const grossAmt = b.grossAmount ? formatAmount(b.grossAmount.amount) : '—';
        const custId = b.customer ? truncateId(b.customer.id) : '—';

        html += `<tr data-bill-id="${b.id || ''}">
            <td>${truncateId(b.id) || '—'}</td>
            <td>${b.documentNumber || '—'}</td>
            <td><span class="badge ${typeClass}">${b.billingType || '—'}</span></td>
            <td><span class="badge ${statusClass}">${b.billStatus || '—'}</span></td>
            <td>${b.currency || '—'}</td>
            <td>${netAmt}</td>
            <td>${grossAmt}</td>
            <td>${custId}</td>
            <td>${b.billingDate || '—'}</td>
            <td>${b.dueDate || '—'}</td>
        </tr>`;
    });

    html += '</tbody></table>';
    return html;
}

function attachRowClickHandlers(container) {
    container.querySelectorAll('tbody tr').forEach(row => {
        row.addEventListener('click', () => {
            const billId = row.dataset.billId;
            if (billId) openDetailPanel(billId);
        });
    });
}

function renderPagination(totalPages) {
    const container = document.getElementById('pagination');
    if (totalPages <= 1) {
        container.innerHTML = '';
        return;
    }

    let html = '';

    // Prev
    html += `<button class="page-btn" ${currentPage === 1 ? 'disabled' : ''} onclick="goToPage(${currentPage - 1})">‹</button>`;

    // Page numbers
    const maxVisible = 5;
    let start = Math.max(1, currentPage - Math.floor(maxVisible / 2));
    let end = Math.min(totalPages, start + maxVisible - 1);
    if (end - start < maxVisible - 1) start = Math.max(1, end - maxVisible + 1);

    if (start > 1) {
        html += `<button class="page-btn" onclick="goToPage(1)">1</button>`;
        if (start > 2) html += `<span style="color:var(--text-dim);padding:0 4px;">…</span>`;
    }

    for (let i = start; i <= end; i++) {
        html += `<button class="page-btn ${i === currentPage ? 'active' : ''}" onclick="goToPage(${i})">${i}</button>`;
    }

    if (end < totalPages) {
        if (end < totalPages - 1) html += `<span style="color:var(--text-dim);padding:0 4px;">…</span>`;
        html += `<button class="page-btn" onclick="goToPage(${totalPages})">${totalPages}</button>`;
    }

    // Next
    html += `<button class="page-btn" ${currentPage === totalPages ? 'disabled' : ''} onclick="goToPage(${currentPage + 1})">›</button>`;

    container.innerHTML = html;
}

function goToPage(page) {
    currentPage = page;
    renderBillsTable();
    // Scroll to top of table
    document.getElementById('billsTableContainer').scrollIntoView({ behavior: 'smooth', block: 'start' });
}

// ═══════════════════════════════════════════════════════════════
// BILL DETAIL PANEL
// ═══════════════════════════════════════════════════════════════

function openDetailPanel(billId) {
    const bill = allBills.find(b => b.id === billId);
    if (!bill) return;

    const panel = document.getElementById('detailPanel');
    const overlay = document.getElementById('detailOverlay');
    const body = document.getElementById('detailBody');

    document.getElementById('detailTitle').textContent = `Bill #${bill.documentNumber || bill.id}`;

    let html = '';

    // General Info
    html += `<div class="detail-section">
        <div class="detail-section-title">General Information</div>
        <div class="detail-grid">
            <div class="detail-field full">
                <span class="detail-field-label">Bill ID</span>
                <span class="detail-field-value mono">${bill.id || '—'}</span>
            </div>
            <div class="detail-field">
                <span class="detail-field-label">Document Number</span>
                <span class="detail-field-value">${bill.documentNumber || '—'}</span>
            </div>
            <div class="detail-field">
                <span class="detail-field-label">Billing Type</span>
                <span class="detail-field-value"><span class="badge ${(bill.billingType || '').toLowerCase()}">${bill.billingType || '—'}</span></span>
            </div>
            <div class="detail-field">
                <span class="detail-field-label">Status</span>
                <span class="detail-field-value"><span class="badge ${(bill.billStatus || '').toLowerCase()}">${bill.billStatus || '—'}</span></span>
            </div>
            <div class="detail-field">
                <span class="detail-field-label">Transfer Status</span>
                <span class="detail-field-value">${bill.transferStatus || '—'}</span>
            </div>
            <div class="detail-field">
                <span class="detail-field-label">Currency</span>
                <span class="detail-field-value">${bill.currency || '—'}</span>
            </div>
            <div class="detail-field">
                <span class="detail-field-label">Time Zone</span>
                <span class="detail-field-value">${bill.timeZone || '—'}</span>
            </div>
        </div>
    </div>`;

    // Dates
    html += `<div class="detail-section">
        <div class="detail-section-title">Dates</div>
        <div class="detail-grid">
            <div class="detail-field">
                <span class="detail-field-label">Billing Date</span>
                <span class="detail-field-value">${bill.billingDate || '—'}</span>
            </div>
            <div class="detail-field">
                <span class="detail-field-label">Due Date</span>
                <span class="detail-field-value">${bill.dueDate || '—'}</span>
            </div>
        </div>
    </div>`;

    // Amounts
    html += `<div class="detail-section">
        <div class="detail-section-title">Amounts</div>
        <div class="detail-grid">
            <div class="detail-field">
                <span class="detail-field-label">Net Amount</span>
                <span class="detail-field-value">${bill.netAmount ? formatAmount(bill.netAmount.amount) + ' ' + (bill.netAmount.currency || '') : '—'}</span>
            </div>
            <div class="detail-field">
                <span class="detail-field-label">Gross Amount</span>
                <span class="detail-field-value">${bill.grossAmount ? formatAmount(bill.grossAmount.amount) + ' ' + (bill.grossAmount.currency || '') : '—'}</span>
            </div>`;
    if (bill.netCreditAmount) {
        html += `<div class="detail-field">
                <span class="detail-field-label">Net Credit</span>
                <span class="detail-field-value">${formatAmount(bill.netCreditAmount.amount)} ${bill.netCreditAmount.currency || ''}</span>
            </div>`;
    }
    if (bill.grossCreditAmount) {
        html += `<div class="detail-field">
                <span class="detail-field-label">Gross Credit</span>
                <span class="detail-field-value">${formatAmount(bill.grossCreditAmount.amount)} ${bill.grossCreditAmount.currency || ''}</span>
            </div>`;
    }
    html += `</div></div>`;

    // Customer
    if (bill.customer) {
        html += `<div class="detail-section">
            <div class="detail-section-title">Customer</div>
            <div class="detail-grid">
                <div class="detail-field full">
                    <span class="detail-field-label">Customer ID</span>
                    <span class="detail-field-value mono">${bill.customer.id || '—'}</span>
                </div>
                <div class="detail-field">
                    <span class="detail-field-label">Type</span>
                    <span class="detail-field-value">${bill.customer.type || '—'}</span>
                </div>
                <div class="detail-field">
                    <span class="detail-field-label">Country</span>
                    <span class="detail-field-value">${bill.customer.country || '—'}</span>
                </div>
            </div>
        </div>`;
    }

    // Market
    if (bill.market) {
        html += `<div class="detail-section">
            <div class="detail-section-title">Market</div>
            <div class="detail-grid">
                <div class="detail-field full">
                    <span class="detail-field-label">Market ID</span>
                    <span class="detail-field-value mono">${bill.market.id || '—'}</span>
                </div>
            </div>
        </div>`;
    }

    // Bill Items
    if (bill.billItems && bill.billItems.length > 0) {
        html += `<div class="detail-section">
            <div class="detail-section-title">Bill Items (${bill.billItems.length})</div>`;

        bill.billItems.forEach((item, idx) => {
            html += `<div style="background:var(--bg-glass);border-radius:var(--radius-md);padding:14px;margin-bottom:10px;">
                <div style="font-size:0.8rem;color:var(--accent-indigo);font-weight:600;margin-bottom:8px;">Item ${idx + 1}</div>
                <div class="detail-grid">
                    <div class="detail-field full">
                        <span class="detail-field-label">Item ID</span>
                        <span class="detail-field-value mono" style="font-size:0.76rem;">${item.id || '—'}</span>
                    </div>`;

            if (item.subscription) {
                html += `<div class="detail-field">
                    <span class="detail-field-label">Subscription</span>
                    <span class="detail-field-value" style="font-size:0.82rem;">${truncateId(item.subscription.id)}</span>
                </div>`;
            }
            if (item.product) {
                html += `<div class="detail-field">
                    <span class="detail-field-label">Product</span>
                    <span class="detail-field-value" style="font-size:0.82rem;">${item.product.name || truncateId(item.product.id)}</span>
                </div>`;
            }
            if (item.ratePlan) {
                html += `<div class="detail-field">
                    <span class="detail-field-label">Rate Plan</span>
                    <span class="detail-field-value" style="font-size:0.82rem;">${truncateId(item.ratePlan.id)}</span>
                </div>`;
            }

            html += `</div></div>`;
        });

        html += `</div>`;
    }

    // Payment
    if (bill.payment) {
        html += `<div class="detail-section">
            <div class="detail-section-title">Payment</div>
            <div class="detail-grid">
                <div class="detail-field">
                    <span class="detail-field-label">Payment Status</span>
                    <span class="detail-field-value">${bill.payment.paymentStatus || '—'}</span>
                </div>
                <div class="detail-field">
                    <span class="detail-field-label">Payment Method</span>
                    <span class="detail-field-value">${bill.payment.paymentMethod || '—'}</span>
                </div>
            </div>
        </div>`;
    }

    // Additional IDs
    html += `<div class="detail-section">
        <div class="detail-section-title">Additional Details</div>
        <div class="detail-grid">
            <div class="detail-field full">
                <span class="detail-field-label">Billing Profile ID</span>
                <span class="detail-field-value mono">${bill.billingProfileId || '—'}</span>
            </div>
            <div class="detail-field">
                <span class="detail-field-label">Contract Account</span>
                <span class="detail-field-value">${bill.contractAccount || '—'}</span>
            </div>
            <div class="detail-field">
                <span class="detail-field-label">Create Invoice</span>
                <span class="detail-field-value">${bill.createInvoice != null ? bill.createInvoice : '—'}</span>
            </div>
        </div>
    </div>`;

    body.innerHTML = html;

    // Open panel
    panel.classList.add('open');
    overlay.classList.add('open');
    document.body.style.overflow = 'hidden';
}

function closeDetailPanel() {
    document.getElementById('detailPanel').classList.remove('open');
    document.getElementById('detailOverlay').classList.remove('open');
    document.body.style.overflow = '';
}

// ═══════════════════════════════════════════════════════════════
// SYNC
// ═══════════════════════════════════════════════════════════════

async function triggerSync() {
    const syncBtn = document.getElementById('headerSyncBtn');
    const syncNowBtn = document.getElementById('syncNowBtn');
    const syncIcon = document.getElementById('syncIconLg');
    const resultDiv = document.getElementById('syncResult');

    // Set syncing state
    syncBtn.classList.add('syncing');
    syncBtn.disabled = true;
    if (syncNowBtn) {
        syncNowBtn.classList.add('syncing');
        syncNowBtn.disabled = true;
    }
    if (syncIcon) syncIcon.classList.add('syncing');
    if (resultDiv) resultDiv.style.display = 'none';

    try {
        const res = await fetch(`${API_BASE}/api/sync`);
        const data = await res.json();

        if (data.success) {
            if (resultDiv) {
                resultDiv.className = 'sync-result success';
                resultDiv.textContent = `✓ ${data.message}`;
                resultDiv.style.display = 'block';
            }

            // Refresh dashboard data
            await loadDashboard();

            // If on bills view, refresh
            if (currentView === 'bills') await loadBillsFromDB();
            if (currentView === 'sync') await loadFetchHistory();

        } else {
            if (resultDiv) {
                resultDiv.className = 'sync-result error';
                resultDiv.textContent = `✗ ${data.error || 'Sync failed'}`;
                resultDiv.style.display = 'block';
            }
        }

    } catch (err) {
        if (resultDiv) {
            resultDiv.className = 'sync-result error';
            resultDiv.textContent = `✗ Connection failed: ${err.message}`;
            resultDiv.style.display = 'block';
        }
    } finally {
        syncBtn.classList.remove('syncing');
        syncBtn.disabled = false;
        if (syncNowBtn) {
            syncNowBtn.classList.remove('syncing');
            syncNowBtn.disabled = false;
        }
        if (syncIcon) syncIcon.classList.remove('syncing');
    }
}

// ═══════════════════════════════════════════════════════════════
// SYNC VIEW
// ═══════════════════════════════════════════════════════════════

function loadSyncView() {
    loadFetchHistory();
}

async function loadFetchHistory() {
    const container = document.getElementById('fetchHistoryContainer');

    try {
        const res = await fetch(`${API_BASE}/api/fetch-log`);
        const logs = await res.json();

        if (!Array.isArray(logs) || logs.length === 0) {
            container.innerHTML = `
                <div class="empty-state" style="padding:30px;">
                    <p>No sync history yet. Click "Sync Now" to get started.</p>
                </div>
            `;
            return;
        }

        let html = '';
        logs.forEach(log => {
            const dotClass = log.status === 'SUCCESS' ? 'success' : 'error';
            html += `<div class="history-item">
                <div class="history-dot ${dotClass}"></div>
                <div class="history-info">
                    <div class="history-msg">${escapeHtml(log.message || '—')}</div>
                    <div class="history-time">${formatTimestamp(log.fetchedAt)}</div>
                </div>
                <div class="history-count">${log.billCount} bills</div>
            </div>`;
        });

        container.innerHTML = html;

    } catch (err) {
        container.innerHTML = `<div class="empty-state" style="padding:30px;"><p>Failed to load sync history.</p></div>`;
    }
}

// ═══════════════════════════════════════════════════════════════
// HELPERS
// ═══════════════════════════════════════════════════════════════

function updateSyncStatus(state, text) {
    const dot = document.querySelector('.status-dot');
    const statusText = document.querySelector('.status-text');
    dot.className = `status-dot ${state}`;
    statusText.textContent = text;
}

function updateLastSyncInfo(lastSync) {
    const el = document.getElementById('lastSyncText');
    if (lastSync && lastSync.at) {
        el.textContent = `Last sync: ${formatTimestamp(lastSync.at)}`;
    } else {
        el.textContent = 'Never synced';
    }
}

function truncateId(id) {
    if (!id) return '—';
    if (id.length <= 12) return id;
    return id.substring(0, 8) + '…' + id.substring(id.length - 4);
}

function formatAmount(amount) {
    if (amount == null) return '—';
    const num = parseFloat(amount);
    if (isNaN(num)) return amount;
    return num.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatCurrency(amount) {
    if (amount == null) return '—';
    const num = parseFloat(amount);
    if (isNaN(num)) return '—';
    if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M';
    if (num >= 1000) return (num / 1000).toFixed(1) + 'K';
    return num.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatTimestamp(ts) {
    if (!ts) return '—';
    try {
        const date = new Date(ts);
        if (isNaN(date.getTime())) return ts;
        const now = new Date();
        const diff = now - date;

        if (diff < 60000) return 'Just now';
        if (diff < 3600000) return Math.floor(diff / 60000) + ' min ago';
        if (diff < 86400000) return Math.floor(diff / 3600000) + ' hr ago';

        return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
            + ' ' + date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
    } catch {
        return ts;
    }
}

function escapeHtml(str) {
    if (!str) return '';
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

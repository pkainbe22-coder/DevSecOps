<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="Risk Intelligence"/>
<c:set var="pageCrumb" value="Security · findings ranked by real-world exploitability"/>
<%@ include file="_top.jspf" %>

<div class="kpis">
  <div class="kpi posture ${postureBand}">
    <div class="ring" style="--p:${posture}">
      <div class="ring-val">${posture}</div>
    </div>
    <div><div class="val">Posture score</div><div class="lab">100 = clean · weighted by exploitability</div></div>
  </div>
  <div class="kpi"><div class="ico"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2 4 5v6c0 5 3.4 7.8 8 9 4.6-1.2 8-4 8-9V5z"/></svg></div>
    <div class="val">${total}</div><div class="lab">Total findings</div></div>
  <div class="kpi crit"><div class="ico"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m13 2-3 7h5l-3 7"/><circle cx="12" cy="12" r="10"/></svg></div>
    <div class="val">${kev}</div><div class="lab">Actively exploited (CISA KEV)</div></div>
  <div class="kpi warn"><div class="ico"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2 2 22h20L12 2z"/><path d="M12 9v5m0 3h.01"/></svg></div>
    <div class="val">${exploitable}</div><div class="lab">High exploit probability (EPSS ≥ 50%)</div></div>
</div>

<%-- Security analytics --%>
<div class="card">
  <div class="card-head"><div>
    <h2>Security Analytics</h2>
    <p>Severity mix, scanner coverage, and how critical + high findings trend across recent commits.</p>
  </div></div>
  <div class="charts">
    <div class="chartbox"><h3>Findings by severity</h3><canvas id="sevChart" height="200"></canvas></div>
    <div class="chartbox"><h3>Findings by scanner</h3><canvas id="scanChart" height="200"></canvas></div>
    <div class="chartbox wide"><h3>Critical + High over recent commits</h3><canvas id="trendChart" height="200"></canvas></div>
  </div>
</div>

<%-- AI Security Analyst panel --%>
<div class="card ai-card">
  <div class="card-head">
    <div>
      <h2>AI Security Analyst <span class="ai-dot ${aiConfigured ? 'on':'off'}">${aiConfigured ? fn:escapeXml(aiProvider).concat(' connected') : (demoMode ? 'demo · sample analysis' : 'not configured')}</span></h2>
      <p>Claude explains each finding in plain English and proposes a concrete fix. Generate an executive posture summary, or use <em>Explain&nbsp;&amp;&nbsp;Fix</em> on any finding below.</p>
    </div>
    <div class="grow"></div>
    <c:if test="${aiConfigured}">
      <form method="post" action="${pageContext.request.contextPath}/security/ai/summary">
        <button class="btn primary">Generate executive summary</button>
      </form>
    </c:if>
  </div>
  <c:if test="${aiStatus eq 'unconfigured'}">
    <div class="flash warn">AI analyst not configured. Set <code>ANTHROPIC_API_KEY</code> in <code>.env</code> and restart the portal.</div>
  </c:if>
  <c:if test="${aiStatus eq 'error'}">
    <div class="flash warn">The AI request failed — check the API key and portal logs.</div>
  </c:if>
  <c:if test="${not empty aiExecSummary}">
    <div class="ai-summary"><div class="ai-tag">Executive summary</div>${fn:escapeXml(aiExecSummary)}</div>
  </c:if>
  <c:if test="${not aiConfigured and demoMode}">
    <div class="ai-hint">This hosted demo shows <strong>pre-generated</strong> AI analysis — expand the Log4Shell row below to see it. The threat-intel ranking is fully live; connect a key to generate analysis on demand.</div>
  </c:if>
  <c:if test="${not aiConfigured and not demoMode}">
    <div class="ai-hint">Add a Claude or Groq API key to enable AI explanations &amp; auto-generated fixes. The threat-intel ranking below works without it.</div>
  </c:if>
</div>

<div class="card">
  <div class="card-head">
    <div><h2>Prioritised Findings</h2>
      <p>Ranked by <strong>contextual risk</strong> = CVSS severity × real-world exploitability
         (<a href="https://www.first.org/epss/" target="_blank" rel="noopener">EPSS</a> +
          <a href="https://www.cisa.gov/known-exploited-vulnerabilities-catalog" target="_blank" rel="noopener">CISA KEV</a>).
         The top of this list is what to fix first — not just the highest CVSS.</p>
    </div>
  </div>

  <div class="table-wrap">
  <table>
    <thead><tr>
      <th>#</th><th>Risk</th><th>Vulnerability</th><th>Package / Location</th>
      <th>Sev</th><th>CVSS</th><th>EPSS</th><th>KEV</th><th>Scan</th><th>AI</th>
    </tr></thead>
    <tbody>
      <c:forEach var="f" items="${findings}" varStatus="i">
        <tr id="f${f.id}">
          <td class="rank">${i.index + 1}</td>
          <td>
            <span class="risk ${f.riskBand()}" title="contextual risk score">${f.riskDisplay()}</span>
          </td>
          <td>
            <c:choose>
              <c:when test="${f.hasCve()}">
                <a class="cve" href="https://nvd.nist.gov/vuln/detail/${f.cveId}" target="_blank" rel="noopener">${f.cveId}</a>
              </c:when>
              <c:otherwise><span class="cve muted">${f.title}</span></c:otherwise>
            </c:choose>
          </td>
          <td class="pkg">${fn:escapeXml(f.pkg)}</td>
          <td><span class="sev ${f.severity eq 'CRITICAL' ? 'c' : f.severity eq 'HIGH' ? 'h' : f.severity eq 'MEDIUM' ? 'm' : 'l'}">${fn:substring(f.severity,0,4)}</span></td>
          <td class="num">${f.cvss > 0 ? f.cvss : '—'}</td>
          <td class="num">${f.epssPctDisplay()}</td>
          <td>
            <c:choose>
              <c:when test="${f.kev}"><span class="kevchip" title="On CISA's Known Exploited Vulnerabilities list">KEV</span></c:when>
              <c:otherwise><span class="muted">—</span></c:otherwise>
            </c:choose>
          </td>
          <td><span class="scantag">${f.scanType}</span></td>
          <td>
            <c:if test="${aiConfigured}">
              <form method="post" action="${pageContext.request.contextPath}/security/ai/explain">
                <input type="hidden" name="findingId" value="${f.id}">
                <button class="btn sm ${f.hasAi() ? '' : 'primary'}" title="AI explanation + suggested fix">
                  ${f.hasAi() ? '↻ Re-run' : '✦ Explain &amp; Fix'}</button>
              </form>
            </c:if>
            <c:if test="${not aiConfigured}"><span class="muted">—</span></c:if>
          </td>
        </tr>
        <c:if test="${f.hasAi()}">
          <tr class="ai-row">
            <td></td>
            <td colspan="9">
              <div class="ai-finding">
                <div class="ai-tag">✦ AI analysis</div>
                <div class="ai-text">${fn:escapeXml(f.aiSummary)}</div>
                <c:if test="${not empty f.aiFix}">
                  <div class="ai-fix-tag">Suggested fix</div>
                  <div class="ai-fix">${fn:escapeXml(f.aiFix)}</div>
                </c:if>
              </div>
            </td>
          </tr>
        </c:if>
      </c:forEach>
    </tbody>
  </table>
  </div>

  <c:if test="${empty findings}">
    <div class="empty"><div class="big"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="m9 12 2 2 4-4"/></svg></div>
      <h3>No findings yet</h3>Individual CVEs appear here after the next pipeline scan, enriched with EPSS + KEV.</div>
  </c:if>
</div>

<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js"></script>
<script>
  const SEV  = [${sevCounts[0]}, ${sevCounts[1]}, ${sevCounts[2]}, ${sevCounts[3]}];
  const SCAN = [${scanCounts[0]}, ${scanCounts[1]}, ${scanCounts[2]}];
  // trend comes newest-first; reverse for chronological left→right
  const T_LABELS = [<c:forEach var="t" items="${trend}" varStatus="s">'${t.shortHash()}'<c:if test="${!s.last}">,</c:if></c:forEach>].reverse();
  const T_CH     = [<c:forEach var="t" items="${trend}" varStatus="s">${t.critical + t.high}<c:if test="${!s.last}">,</c:if></c:forEach>].reverse();

  const GRID = 'rgba(148,163,184,.12)', TICK = '#8a98b5';
  Chart.defaults.color = TICK;
  Chart.defaults.font.family = "'Inter',sans-serif";

  new Chart(sevChart, {
    type: 'doughnut',
    data: { labels: ['Critical','High','Medium','Low'],
      datasets: [{ data: SEV, backgroundColor: ['#f43f5e','#fb923c','#facc15','#34d399'], borderWidth: 0 }] },
    options: { plugins: { legend: { position: 'bottom', labels: { boxWidth: 12, padding: 14 } } }, cutout: '62%' }
  });

  new Chart(scanChart, {
    type: 'bar',
    data: { labels: ['SAST','SCA','DAST'],
      datasets: [{ data: SCAN, backgroundColor: ['#60a5fa','#818cf8','#22d3ee'], borderRadius: 6, maxBarThickness: 54 }] },
    options: { plugins: { legend: { display: false } },
      scales: { x: { grid: { display: false } }, y: { beginAtZero: true, grid: { color: GRID }, ticks: { precision: 0 } } } }
  });

  new Chart(trendChart, {
    type: 'line',
    data: { labels: T_LABELS,
      datasets: [{ label: 'Critical + High', data: T_CH, borderColor: '#f43f5e',
        backgroundColor: 'rgba(244,63,94,.12)', fill: true, tension: .35, pointRadius: 3, pointBackgroundColor: '#f43f5e' }] },
    options: { plugins: { legend: { display: false } },
      scales: { x: { grid: { display: false } }, y: { beginAtZero: true, grid: { color: GRID }, ticks: { precision: 0 } } } }
  });
</script>

<%@ include file="_bottom.jspf" %>

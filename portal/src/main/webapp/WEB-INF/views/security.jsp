<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="Approval Queue"/>
<c:set var="pageCrumb" value="Security · review findings and gate deployments"/>
<%@ include file="_top.jspf" %>

<%-- KPI accumulation over the loaded queue --%>
<c:set var="pend" value="0"/><c:set var="appr" value="0"/><c:set var="rej" value="0"/>
<c:set var="crit" value="0"/><c:set var="high" value="0"/>
<c:forEach var="c" items="${commits}">
  <c:choose>
    <c:when test="${c.decision eq 'APPROVED'}"><c:set var="appr" value="${appr+1}"/></c:when>
    <c:when test="${c.decision eq 'REJECTED'}"><c:set var="rej" value="${rej+1}"/></c:when>
    <c:otherwise><c:set var="pend" value="${pend+1}"/></c:otherwise>
  </c:choose>
  <c:set var="crit" value="${crit + c.critical}"/><c:set var="high" value="${high + c.high}"/>
</c:forEach>

<div class="kpis">
  <div class="kpi"><div class="ico"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 3v18h18"/><path d="m7 14 4-4 3 3 5-6"/></svg></div>
    <div class="val">${fn:length(commits)}</div><div class="lab">Commits in queue</div></div>
  <div class="kpi warn"><div class="ico"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 3"/></svg></div>
    <div class="val">${pend}</div><div class="lab">Pending review</div></div>
  <div class="kpi ok"><div class="ico"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 6 9 17l-5-5"/></svg></div>
    <div class="val">${appr}</div><div class="lab">Approved</div></div>
  <div class="kpi crit"><div class="ico"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="9"/><path d="M12 8v4m0 4h.01"/></svg></div>
    <div class="val">${crit + high}</div><div class="lab">Critical + High findings</div></div>
</div>

<div class="card">
  <div class="card-head">
    <div><h2>Commit Approval Gate</h2><p>Static (SAST), dependency (SCA) &amp; dynamic (DAST) findings per commit.</p></div>
    <div class="grow"></div>
    <c:set var="q" value="${empty decision ? '' : '&decision='.concat(decision)}"/>
    <div class="filterbar">
      <a class="chip ${empty decision ? 'active':''}" href="?">All</a>
      <a class="chip ${decision eq 'PENDING' ? 'active':''}" href="?decision=PENDING">Pending</a>
      <a class="chip ${decision eq 'APPROVED' ? 'active':''}" href="?decision=APPROVED">Approved</a>
      <a class="chip ${decision eq 'REJECTED' ? 'active':''}" href="?decision=REJECTED">Rejected</a>
    </div>
  </div>

  <div class="table-wrap">
  <table>
    <thead><tr>
      <th>Commit</th><th>Author</th><th>Repo</th><th>Severity</th>
      <th>Crit</th><th>High</th><th>Med</th><th>Low</th><th>Status</th><th>Policy</th><th>Decision</th>
    </tr></thead>
    <tbody>
      <c:forEach var="c" items="${commits}">
        <c:set var="tot" value="${c.critical + c.high + c.medium + c.low}"/>
        <tr>
          <td><span class="hash">${c.shortHash()}</span></td>
          <td><span class="author"><span class="dot">${fn:toUpperCase(fn:substring(c.author,0,1))}</span>${c.author}</span></td>
          <td>${c.repo}</td>
          <td>
            <c:choose>
              <c:when test="${tot > 0}">
                <div class="sevbar" title="${tot} findings">
                  <i class="c" style="width:${c.critical*100/tot}%"></i><i class="h" style="width:${c.high*100/tot}%"></i>
                  <i class="m" style="width:${c.medium*100/tot}%"></i><i class="l" style="width:${c.low*100/tot}%"></i>
                </div>
              </c:when>
              <c:otherwise><span style="color:var(--faint)">clean</span></c:otherwise>
            </c:choose>
          </td>
          <td><span class="sev ${c.critical>0?'c':'zero'}">${c.critical}</span></td>
          <td><span class="sev ${c.high>0?'h':'zero'}">${c.high}</span></td>
          <td><span class="sev ${c.medium>0?'m':'zero'}">${c.medium}</span></td>
          <td><span class="sev ${c.low>0?'l':'zero'}">${c.low}</span></td>
          <td><span class="badge ${empty c.decision ? 'PENDING' : c.decision}">${empty c.decision ? 'PENDING' : c.decision}</span></td>
          <td>
            <c:set var="src" value="${empty c.decisionSource ? 'MANUAL' : c.decisionSource}"/>
            <c:choose>
              <c:when test="${src eq 'AUTO_APPROVED'}">
                <span class="pbadge auto-approved" title="${fn:escapeXml(c.policyTooltip())}">Auto-approved</span></c:when>
              <c:when test="${src eq 'AUTO_REJECTED'}">
                <span class="pbadge auto-rejected" title="${fn:escapeXml(c.policyTooltip())}">Auto-rejected</span></c:when>
              <c:otherwise>
                <span class="pbadge manual" title="${fn:escapeXml(c.policyTooltip())}">Manual review</span></c:otherwise>
            </c:choose>
          </td>
          <td>
            <c:choose>
              <c:when test="${c.decision eq 'APPROVED' or c.decision eq 'REJECTED'}">
                <span style="color:var(--muted);font-size:12px">— decided</span>
              </c:when>
              <c:otherwise>
                <form class="decide" method="post" action="${pageContext.request.contextPath}/security/decide">
                  <input type="hidden" name="commitId" value="${c.id}">
                  <input type="text" name="comment" placeholder="comment…">
                  <button class="btn success sm" name="action" value="approve">Approve</button>
                  <button class="btn danger sm" name="action" value="reject">Reject</button>
                </form>
              </c:otherwise>
            </c:choose>
          </td>
        </tr>
      </c:forEach>
    </tbody>
  </table>
  </div>

  <c:if test="${empty commits}">
    <div class="empty"><div class="big"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg></div>
      <h3>Nothing to review</h3>Findings flow in automatically after each push.</div>
  </c:if>

  <div class="pager">
    <c:choose><c:when test="${hasPrev}"><a href="?page=${page-1}${q}">‹ Prev</a></c:when><c:otherwise><span class="disabled">‹ Prev</span></c:otherwise></c:choose>
    <span class="pgnum">Page ${page}</span>
    <c:choose><c:when test="${hasNext}"><a href="?page=${page+1}${q}">Next ›</a></c:when><c:otherwise><span class="disabled">Next ›</span></c:otherwise></c:choose>
  </div>
</div>

<%@ include file="_bottom.jspf" %>

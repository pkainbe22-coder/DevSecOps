<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="My Commits"/>
<c:set var="pageCrumb" value="Developer · your pushes and build links"/>
<%@ include file="_top.jspf" %>

<div class="kpis">
  <div class="kpi"><div class="ico"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg></div>
    <div class="val">${fn:length(commits)}</div><div class="lab">My commits</div></div>
</div>

<div class="card">
  <div class="card-head"><div><h2>Commit History</h2>
    <p>Read-only. Vulnerability details are reviewed by the Security team.</p></div></div>
  <div class="table-wrap">
  <table>
    <thead><tr><th>Commit</th><th>Message</th><th>Branch</th><th>Repo</th><th>When</th><th>Source</th></tr></thead>
    <tbody>
      <c:forEach var="c" items="${commits}">
        <tr>
          <td><span class="hash">${c.shortHash()}</span></td>
          <td>${fn:escapeXml(c.message)}</td>
          <td>${c.branch}</td>
          <td>${c.repo}</td>
          <td style="color:var(--muted)">${c.committedDisplay()}</td>
          <td><c:if test="${not empty c.giteaUrl}"><a href="${c.giteaUrl}" target="_blank" class="btn sm">View ↗</a></c:if></td>
        </tr>
      </c:forEach>
    </tbody>
  </table>
  </div>
  <c:if test="${empty commits}">
    <div class="empty"><div class="big"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg></div>
      <h3>No commits yet</h3>Push to Gitea and your commits appear here after the pipeline runs.</div>
  </c:if>
  <div class="pager">
    <c:choose><c:when test="${hasPrev}"><a href="?page=${page-1}">‹ Prev</a></c:when><c:otherwise><span class="disabled">‹ Prev</span></c:otherwise></c:choose>
    <span class="pgnum">Page ${page}</span>
    <c:choose><c:when test="${hasNext}"><a href="?page=${page+1}">Next ›</a></c:when><c:otherwise><span class="disabled">Next ›</span></c:otherwise></c:choose>
  </div>
</div>

<%@ include file="_bottom.jspf" %>

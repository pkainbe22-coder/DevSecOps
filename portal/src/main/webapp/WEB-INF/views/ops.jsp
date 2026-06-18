<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="Deployments"/>
<c:set var="pageCrumb" value="Operations · deploy security-approved commits"/>
<%@ include file="_top.jspf" %>

<c:set var="deployed" value="0"/><c:set var="waiting" value="0"/>
<c:forEach var="c" items="${commits}">
  <c:choose><c:when test="${c.deployStatus eq 'DEPLOYED'}"><c:set var="deployed" value="${deployed+1}"/></c:when>
  <c:otherwise><c:set var="waiting" value="${waiting+1}"/></c:otherwise></c:choose>
</c:forEach>

<div class="kpis">
  <div class="kpi"><div class="ico"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 12h-4l-3 9L9 3l-3 9H2"/></svg></div>
    <div class="val">${fn:length(commits)}</div><div class="lab">Approved &amp; ready</div></div>
  <div class="kpi ok"><div class="ico"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m5 12 5 5L20 7"/></svg></div>
    <div class="val">${deployed}</div><div class="lab">Deployed</div></div>
  <div class="kpi warn"><div class="ico"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 3"/></svg></div>
    <div class="val">${waiting}</div><div class="lab">Awaiting deploy</div></div>
</div>

<div class="card">
  <div class="card-head"><div><h2>Deployment Queue</h2>
    <p>Only <b>approved</b> commits appear here — the gate blocks everything else.</p></div></div>
  <div class="table-wrap">
  <table>
    <thead><tr><th>Commit</th><th>Author</th><th>Repo</th><th>Status</th><th>Action</th></tr></thead>
    <tbody>
      <c:forEach var="c" items="${commits}">
        <tr>
          <td><span class="hash">${c.shortHash()}</span></td>
          <td><span class="author"><span class="dot">${fn:toUpperCase(fn:substring(c.author,0,1))}</span>${c.author}</span></td>
          <td>${c.repo}</td>
          <td><span class="badge ${empty c.deployStatus ? 'NOT_DEPLOYED' : c.deployStatus}">${empty c.deployStatus ? 'NOT_DEPLOYED' : c.deployStatus}</span></td>
          <td>
            <c:choose>
              <c:when test="${c.deployStatus eq 'DEPLOYED'}"><span style="color:var(--muted);font-size:12px">— live</span></c:when>
              <c:otherwise>
                <form method="post" action="${pageContext.request.contextPath}/ops/deploy">
                  <input type="hidden" name="commitId" value="${c.id}">
                  <button class="btn primary sm" type="submit">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 19V5m0 0-7 7m7-7 7 7"/></svg> Deploy</button>
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
    <div class="empty"><div class="big"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M22 12h-4l-3 9L9 3l-3 9H2"/></svg></div>
      <h3>Nothing approved yet</h3>Approved commits show up here ready to deploy.</div>
  </c:if>
  <div class="pager">
    <c:choose><c:when test="${hasPrev}"><a href="?page=${page-1}">‹ Prev</a></c:when><c:otherwise><span class="disabled">‹ Prev</span></c:otherwise></c:choose>
    <span class="pgnum">Page ${page}</span>
    <c:choose><c:when test="${hasNext}"><a href="?page=${page+1}">Next ›</a></c:when><c:otherwise><span class="disabled">Next ›</span></c:otherwise></c:choose>
  </div>
</div>

<%@ include file="_bottom.jspf" %>

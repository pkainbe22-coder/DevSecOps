<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Security · DevSecOps Portal</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/style.css">
</head>
<body>
  <%@ include file="_topbar.jspf" %>
  <div class="wrap">
    <div class="card">
      <h1>Approval Queue</h1>
      <p class="sub">Every commit with its SAST / SCA / DAST findings. Approve or reject for deployment — this is the gate.</p>
      <table>
        <thead>
          <tr>
            <th>Commit</th><th>Author</th><th>Repo</th>
            <th>Crit</th><th>High</th><th>Med</th><th>Low</th>
            <th>Decision</th><th>Action</th>
          </tr>
        </thead>
        <tbody>
          <c:forEach var="c" items="${commits}">
            <tr>
              <td><code>${c.shortHash()}</code></td>
              <td>${c.author}</td>
              <td>${c.repo}</td>
              <td><span class="pill crit">${c.critical}</span></td>
              <td><span class="pill high">${c.high}</span></td>
              <td><span class="pill med">${c.medium}</span></td>
              <td><span class="pill low">${c.low}</span></td>
              <td>${empty c.decision ? 'PENDING' : c.decision}</td>
              <td>
                <c:choose>
                  <c:when test="${c.decision eq 'APPROVED' or c.decision eq 'REJECTED'}">
                    <span class="sub">${c.decision}</span>
                  </c:when>
                  <c:otherwise>
                    <form method="post" action="${pageContext.request.contextPath}/security/decide" style="display:flex;gap:6px;align-items:center">
                      <input type="hidden" name="commitId" value="${c.id}">
                      <input name="comment" placeholder="comment" style="padding:6px 8px;border-radius:6px;border:1px solid #334155;background:#0f172a;color:#e2e8f0;font-size:13px">
                      <button name="action" value="approve" class="pill low" style="border:0;cursor:pointer">Approve</button>
                      <button name="action" value="reject" class="pill crit" style="border:0;cursor:pointer">Reject</button>
                    </form>
                  </c:otherwise>
                </c:choose>
              </td>
            </tr>
          </c:forEach>
        </tbody>
      </table>
      <c:if test="${empty commits}">
        <div class="empty">No commits awaiting review. Findings flow in here automatically after each push (M6).</div>
      </c:if>
    </div>
  </div>
</body>
</html>

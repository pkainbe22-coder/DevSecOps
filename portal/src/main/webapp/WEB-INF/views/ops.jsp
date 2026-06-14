<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Operations · DevSecOps Portal</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/style.css">
</head>
<body>
  <%@ include file="_topbar.jspf" %>
  <div class="wrap">
    <div class="card">
      <h1>Deployment Queue</h1>
      <p class="sub">Only commits Security has <strong>approved</strong> appear here. Rejected and pending commits never reach this view.</p>
      <table>
        <thead>
          <tr><th>Commit</th><th>Author</th><th>Repo</th><th>Status</th><th>Action</th></tr>
        </thead>
        <tbody>
          <c:forEach var="c" items="${commits}">
            <tr>
              <td><code>${c.shortHash()}</code></td>
              <td>${c.author}</td>
              <td>${c.repo}</td>
              <td>${empty c.deployStatus ? 'NOT_DEPLOYED' : c.deployStatus}</td>
              <td>
                <c:choose>
                  <c:when test="${c.deployStatus eq 'DEPLOYED'}">
                    <span class="pill low">Deployed</span>
                  </c:when>
                  <c:otherwise>
                    <form method="post" action="${pageContext.request.contextPath}/ops/deploy">
                      <input type="hidden" name="commitId" value="${c.id}">
                      <button class="pill low" style="border:0;cursor:pointer">Deploy</button>
                    </form>
                  </c:otherwise>
                </c:choose>
              </td>
            </tr>
          </c:forEach>
        </tbody>
      </table>
      <c:if test="${empty commits}">
        <div class="empty">Nothing approved yet. Approved commits show up here ready to deploy.</div>
      </c:if>
    </div>
  </div>
</body>
</html>

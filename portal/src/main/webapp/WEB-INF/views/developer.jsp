<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Developer · DevSecOps Portal</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/style.css">
</head>
<body>
  <%@ include file="_topbar.jspf" %>
  <div class="wrap">
    <div class="card">
      <h1>My Commits</h1>
      <p class="sub">Your pushes and their build links (read-only). Vulnerability data is not shown to developers.</p>
      <table>
        <thead>
          <tr><th>Commit</th><th>Message</th><th>Branch</th><th>Repo</th><th>When</th><th>Gitea</th></tr>
        </thead>
        <tbody>
          <c:forEach var="c" items="${commits}">
            <tr>
              <td><code>${c.shortHash()}</code></td>
              <td><c:out value="${c.message}"/></td>
              <td>${c.branch}</td>
              <td>${c.repo}</td>
              <td>${c.committedDisplay()}</td>
              <td><c:if test="${not empty c.giteaUrl}"><a href="${c.giteaUrl}" target="_blank">view</a></c:if></td>
            </tr>
          </c:forEach>
        </tbody>
      </table>
      <c:if test="${empty commits}">
        <div class="empty">No commits yet. Push to Gitea and they'll appear here after the pipeline runs.</div>
      </c:if>
    </div>
  </div>
</body>
</html>

<%@ page contentType="text/html;charset=UTF-8" %>
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
          <%-- M7 populates this from `commits` WHERE author = session username. --%>
        </tbody>
      </table>
      <div class="empty">No commits yet. Push to Gitea and they'll appear here after the pipeline runs.</div>
    </div>
  </div>
</body>
</html>

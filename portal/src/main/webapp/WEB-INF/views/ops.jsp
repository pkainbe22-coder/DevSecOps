<%@ page contentType="text/html;charset=UTF-8" %>
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
          <tr><th>Commit</th><th>Author</th><th>Repo</th><th>Approved by</th><th>Status</th><th>Action</th></tr>
        </thead>
        <tbody>
          <%-- M7 populates this from commits JOIN deployment_approvals WHERE decision='APPROVED'. --%>
        </tbody>
      </table>
      <div class="empty">Nothing approved yet. Approved commits show up here ready to deploy.</div>
    </div>
  </div>
</body>
</html>

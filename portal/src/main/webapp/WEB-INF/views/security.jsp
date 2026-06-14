<%@ page contentType="text/html;charset=UTF-8" %>
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
            <th>Critical</th><th>High</th><th>Medium</th><th>Low</th>
            <th>Reports</th><th>Decision</th>
          </tr>
        </thead>
        <tbody>
          <%-- M7 populates this from commits + scan_results + deployment_approvals. --%>
        </tbody>
      </table>
      <div class="empty">No commits awaiting review. Findings flow in here automatically after each push (M6).</div>
    </div>
  </div>
</body>
</html>

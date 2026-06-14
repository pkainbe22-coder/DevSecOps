<%@ page contentType="text/html;charset=UTF-8" %>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>403 · Forbidden</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/style.css">
</head>
<body>
  <div class="login-shell">
    <div class="card login-card" style="text-align:center">
      <h1>403 — Forbidden</h1>
      <p class="sub">This area belongs to a different role. You're signed in as
        <span class="badge ${sessionScope.role}">${sessionScope.role}</span>.</p>
      <a class="hint" href="${pageContext.request.contextPath}/">Back to your dashboard</a>
    </div>
  </div>
</body>
</html>

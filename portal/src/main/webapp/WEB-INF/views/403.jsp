<%@ page contentType="text/html;charset=UTF-8" %>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>403 · Forbidden</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/style.css">
</head>
<body>
  <div class="login-shell">
    <div class="login-card" style="text-align:center">
      <div class="brand"><span class="logo" style="background:linear-gradient(135deg,var(--crit),#e11d48)">
        <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="9"/><path d="M12 8v4m0 4h.01"/></svg></span></div>
      <h1>403 — Forbidden</h1>
      <p class="sub">This area belongs to a different role. You're signed in as
        <span class="role-chip ${sessionScope.role}">${sessionScope.role}</span>.</p>
      <a class="btn primary block" href="${pageContext.request.contextPath}/">Back to my dashboard</a>
    </div>
  </div>
</body>
</html>

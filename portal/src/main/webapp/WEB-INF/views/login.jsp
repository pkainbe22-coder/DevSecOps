<%@ page contentType="text/html;charset=UTF-8" %>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Sign in · DevSecOps Portal</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/style.css">
</head>
<body>
  <div class="login-shell">
    <div class="login-card">
      <div class="brand">
        <span class="logo"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="m9 12 2 2 4-4"/></svg></span>
      </div>
      <h1>DevSecOps Portal</h1>
      <p class="sub">Code governance &amp; deployment approval gate</p>

      <% if (request.getAttribute("error") != null) { %>
        <div class="error"><%= request.getAttribute("error") %></div>
      <% } %>

      <form method="post" action="${pageContext.request.contextPath}/login">
        <div class="field">
          <label for="username">Username</label>
          <input id="username" name="username" autocomplete="username" autofocus required>
        </div>
        <div class="field">
          <label for="password">Password</label>
          <input id="password" name="password" type="password" autocomplete="current-password" required>
        </div>
        <button class="btn primary block" type="submit">Sign in →</button>
      </form>

      <div class="seedhint">
        <b>Demo accounts</b><br>
        dev / dev123 &nbsp;·&nbsp; sec / sec123 &nbsp;·&nbsp; ops / ops123
      </div>
    </div>
  </div>
</body>
</html>

<%@ page contentType="text/html;charset=UTF-8" %>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Sign in · DevSecOps Portal</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/style.css">
</head>
<body>
  <div class="login-shell">
    <div class="card login-card">
      <h1>DevSecOps Portal</h1>
      <p class="sub" style="text-align:center">Code governance &amp; approval gate</p>

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
        <button class="primary" type="submit">Sign in</button>
      </form>

      <p class="hint">
        Dev seed accounts:<br>
        <strong>dev / dev123</strong> · <strong>sec / sec123</strong> · <strong>ops / ops123</strong>
      </p>
    </div>
  </div>
</body>
</html>

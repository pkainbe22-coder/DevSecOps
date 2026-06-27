<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="Policy as Code"/>
<c:set var="pageCrumb" value="Security · automatic deployment gate rules"/>
<%@ include file="_top.jspf" %>

<%-- option lists reused across selects --%>
<c:set var="fields" value="critical,high,medium,low"/>
<c:set var="ops" value="eq,gt,lt,gte,lte"/>
<c:set var="actions" value="AUTO_APPROVE,AUTO_REJECT,ESCALATE,MANUAL_REVIEW"/>

<c:if test="${saved}">
  <div class="flash ok">Policy saved. New commits will be gated against the updated rules.</div>
</c:if>

<div class="card">
  <div class="card-head">
    <div>
      <h2>Automatic Gate Rules</h2>
      <p>Rules are evaluated top-down by priority; the <strong>first match</strong> decides the gate.
         If none match, the commit goes to manual review.</p>
    </div>
  </div>

  <form method="post" action="${pageContext.request.contextPath}/security/policy">
    <div class="table-wrap">
    <table class="policy-table">
      <thead><tr>
        <th>Priority</th><th>Rule</th><th>If</th><th>Operator</th><th>Threshold</th>
        <th>Action</th><th>Active</th><th></th>
      </tr></thead>
      <tbody>
        <c:forEach var="r" items="${rules}">
          <tr>
            <td><input type="number" name="r${r.id}_priority" value="${r.priority}" style="width:64px"></td>
            <td><input type="text" name="r${r.id}_rule_name" value="${fn:escapeXml(r.ruleName)}" style="min-width:190px"></td>
            <td>
              <select name="r${r.id}_condition_field">
                <c:forEach var="f" items="${fields}">
                  <option value="${f}" ${r.conditionField eq f ? 'selected':''}>${f}</option>
                </c:forEach>
              </select>
            </td>
            <td>
              <select name="r${r.id}_operator">
                <c:forEach var="o" items="${ops}">
                  <option value="${o}" ${r.operator eq o ? 'selected':''}>${o}</option>
                </c:forEach>
              </select>
            </td>
            <td><input type="number" name="r${r.id}_threshold_value" value="${r.thresholdValue}" min="0" style="width:72px"></td>
            <td>
              <select name="r${r.id}_action">
                <c:forEach var="a" items="${actions}">
                  <option value="${a}" ${r.action eq a ? 'selected':''}>${a}</option>
                </c:forEach>
              </select>
            </td>
            <td style="text-align:center"><input type="checkbox" name="r${r.id}_active" ${r.active ? 'checked':''}></td>
            <td><button type="button" class="btn danger sm" data-del="${r.id}" title="Delete rule">✕</button></td>
          </tr>
        </c:forEach>
      </tbody>
    </table>
    </div>

    <c:if test="${empty rules}">
      <div class="empty"><h3>No policy rules</h3>Add a rule below to start gating deployments automatically.</div>
    </c:if>

    <div class="form-actions">
      <button class="btn primary" name="op" value="save">Save policy</button>
    </div>
  </form>
</div>

<%-- Per-rule delete needs its own form so it isn't blocked by required fields elsewhere. --%>
<c:forEach var="r" items="${rules}">
  <form id="del${r.id}" method="post" action="${pageContext.request.contextPath}/security/policy" style="display:none">
    <input type="hidden" name="op" value="delete"><input type="hidden" name="id" value="${r.id}">
  </form>
</c:forEach>

<div class="card">
  <div class="card-head"><div><h2>Add Rule</h2><p>Define a new condition and the gate action it triggers.</p></div></div>
  <form method="post" action="${pageContext.request.contextPath}/security/policy" class="add-rule">
    <input type="hidden" name="op" value="add">
    <input type="number" name="priority" value="100" title="priority" style="width:72px" placeholder="prio">
    <input type="text" name="rule_name" placeholder="Rule name" style="min-width:190px">
    <select name="condition_field"><c:forEach var="f" items="${fields}"><option value="${f}">${f}</option></c:forEach></select>
    <select name="operator"><c:forEach var="o" items="${ops}"><option value="${o}" ${o eq 'gt' ? 'selected':''}>${o}</option></c:forEach></select>
    <input type="number" name="threshold_value" value="0" min="0" style="width:72px">
    <select name="action"><c:forEach var="a" items="${actions}"><option value="${a}">${a}</option></c:forEach></select>
    <button class="btn success" name="add" value="1">Add rule</button>
  </form>
</div>

<script>
  // wire the row ✕ buttons to their hidden delete forms
  document.querySelectorAll('.policy-table button[data-del]').forEach(function(b){
    b.addEventListener('click', function(){
      document.getElementById('del'+this.getAttribute('data-del')).submit();
    });
  });
</script>

<%@ include file="_bottom.jspf" %>

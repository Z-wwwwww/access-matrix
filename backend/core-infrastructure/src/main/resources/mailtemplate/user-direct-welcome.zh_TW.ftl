<!DOCTYPE html>
<html lang="zh-TW">
<head>
<meta charset="UTF-8">
<title>${appName!"Access Matrix"} - 帳號開通通知</title>
</head>
<body style="margin:0;padding:0;background:#f5f6f7;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'PingFang TC',sans-serif;">
  <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="background:#f5f6f7;padding:40px 0;">
    <tr>
      <td align="center">
        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="560" style="background:#ffffff;border-radius:12px;border:1px solid #e5e7eb;overflow:hidden;">
          <tr>
            <td style="padding:32px 40px 8px;">
              <div style="font-size:20px;font-weight:600;color:#111827;">${appName!"Access Matrix"}</div>
              <div style="font-size:13px;color:#6b7280;margin-top:4px;">帳號開通通知</div>
            </td>
          </tr>
          <tr>
            <td style="padding:8px 40px 32px;color:#374151;font-size:14px;line-height:1.7;">
              <p style="margin:16px 0;">${displayName!username}<#if displayName?? && displayName != username> (${username})</#if> 您好，</p>
              <p style="margin:16px 0;">
                管理員已為您開通 ${appName!"Access Matrix"} 帳號。請使用以下初始資訊登入。
              </p>

              <table cellpadding="6" cellspacing="0" border="0" style="background:#f9fafb;border:1px solid #e5e7eb;border-radius:8px;margin:20px 0;font-size:13px;">
                <tr>
                  <td style="color:#6b7280;padding:6px 14px;">登入網址</td>
                  <td style="color:#374151;padding:6px 14px;"><a href="${loginUrl}" style="color:#2563eb;">${loginUrl}</a></td>
                </tr>
                <tr>
                  <td style="color:#6b7280;padding:6px 14px;">使用者名稱</td>
                  <td style="color:#374151;padding:6px 14px;font-family:monospace;">${username}</td>
                </tr>
                <tr>
                  <td style="color:#6b7280;padding:6px 14px;">初始密碼</td>
                  <td style="color:#374151;padding:6px 14px;font-family:monospace;">${tempPassword}</td>
                </tr>
                <tr>
                  <td style="color:#6b7280;padding:6px 14px;">租戶</td>
                  <td style="color:#374151;padding:6px 14px;font-family:monospace;">${tenantId!"default"}</td>
                </tr>
              </table>

              <p style="margin:16px 0;color:#dc2626;font-size:13px;">
                出於安全考量，首次登入時將提示您修改密碼。
              </p>
            </td>
          </tr>
          <tr>
            <td style="padding:16px 40px 32px;border-top:1px solid #f3f4f6;font-size:11px;color:#9ca3af;line-height:1.6;">
              本郵件由 ${appName!"Access Matrix"} 自動發送，若非本人請忽略。<br>
              <#if supportEmail??>問題回饋：${supportEmail}</#if>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>

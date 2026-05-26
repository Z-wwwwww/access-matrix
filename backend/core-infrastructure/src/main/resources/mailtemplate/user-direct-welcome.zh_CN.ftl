<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>${appName!"Access Matrix"} - 账号开通通知</title>
</head>
<body style="margin:0;padding:0;background:#f5f6f7;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'PingFang SC',sans-serif;">
  <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="background:#f5f6f7;padding:40px 0;">
    <tr>
      <td align="center">
        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="560" style="background:#ffffff;border-radius:12px;border:1px solid #e5e7eb;overflow:hidden;">
          <tr>
            <td style="padding:32px 40px 8px;">
              <div style="font-size:20px;font-weight:600;color:#111827;">${appName!"Access Matrix"}</div>
              <div style="font-size:13px;color:#6b7280;margin-top:4px;">账号开通通知</div>
            </td>
          </tr>
          <tr>
            <td style="padding:8px 40px 32px;color:#374151;font-size:14px;line-height:1.7;">
              <p style="margin:16px 0;">${displayName!username}<#if displayName?? && displayName != username> (${username})</#if> 您好，</p>
              <p style="margin:16px 0;">
                管理员已为您开通 ${appName!"Access Matrix"} 账号。请使用以下初始信息登录。
              </p>

              <table cellpadding="6" cellspacing="0" border="0" style="background:#f9fafb;border:1px solid #e5e7eb;border-radius:8px;margin:20px 0;font-size:13px;">
                <tr>
                  <td style="color:#6b7280;padding:6px 14px;">登录地址</td>
                  <td style="color:#374151;padding:6px 14px;"><a href="${loginUrl}" style="color:#2563eb;">${loginUrl}</a></td>
                </tr>
                <tr>
                  <td style="color:#6b7280;padding:6px 14px;">用户名</td>
                  <td style="color:#374151;padding:6px 14px;font-family:monospace;">${username}</td>
                </tr>
                <tr>
                  <td style="color:#6b7280;padding:6px 14px;">初始密码</td>
                  <td style="color:#374151;padding:6px 14px;font-family:monospace;">${tempPassword}</td>
                </tr>
                <tr>
                  <td style="color:#6b7280;padding:6px 14px;">租户</td>
                  <td style="color:#374151;padding:6px 14px;font-family:monospace;">${tenantId!"default"}</td>
                </tr>
              </table>

              <p style="margin:16px 0;color:#dc2626;font-size:13px;">
                出于安全考虑，首次登录时将提示您修改密码。
              </p>
            </td>
          </tr>
          <tr>
            <td style="padding:16px 40px 32px;border-top:1px solid #f3f4f6;font-size:11px;color:#9ca3af;line-height:1.6;">
              本邮件由 ${appName!"Access Matrix"} 自动发送，如非本人请忽略。<br>
              <#if supportEmail??>问题反馈：${supportEmail}</#if>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>

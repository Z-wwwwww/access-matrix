<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>${appName!"Access Matrix"} - 请重新设置您的密码</title>
</head>
<body style="margin:0;padding:0;background:#f5f6f7;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'PingFang SC','Microsoft YaHei',sans-serif;">
  <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="background:#f5f6f7;padding:40px 0;">
    <tr>
      <td align="center">
        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="560" style="background:#ffffff;border-radius:12px;border:1px solid #e5e7eb;overflow:hidden;">
          <tr>
            <td style="padding:32px 40px 8px;">
              <div style="font-size:20px;font-weight:600;color:#111827;">${appName!"Access Matrix"}</div>
              <div style="font-size:13px;color:#6b7280;margin-top:4px;">请重新设置您的密码</div>
            </td>
          </tr>
          <tr>
            <td style="padding:8px 40px 32px;color:#374151;font-size:14px;line-height:1.7;">
              <p style="margin:16px 0;">${displayName!username}<#if displayName?? && displayName != username>（${username}）</#if> 您好：</p>
              <p style="margin:16px 0;">
                ${appName!"Access Matrix"} 的登录方式正在调整。今后您将不再通过单点登录（SSO），而是使用本系统中直接设置的账号密码登录。
              </p>
              <p style="margin:16px 0;">
                请点击下方按钮设置新密码，设置完成后即可在登录页正常使用。
              </p>
              <p style="margin:28px 0;text-align:center;">
                <a href="${resetUrl}" style="display:inline-block;background:#2563eb;color:#ffffff;text-decoration:none;padding:12px 28px;border-radius:8px;font-weight:500;font-size:14px;">
                  设置密码
                </a>
              </p>
              <p style="margin:16px 0;font-size:12px;color:#6b7280;">
                如果按钮无法点击，请将以下链接复制到浏览器：<br>
                <span style="word-break:break-all;color:#374151;">${resetUrl}</span>
              </p>
              <p style="margin:16px 0;font-size:12px;color:#6b7280;">
                链接有效期为 <strong>${expiresIn!"7"} 天</strong>。过期后请联系管理员重新发送。
              </p>
            </td>
          </tr>
          <tr>
            <td style="padding:16px 40px 32px;border-top:1px solid #f3f4f6;font-size:11px;color:#9ca3af;line-height:1.6;">
              本邮件由 ${appName!"Access Matrix"} 自动发送。如非本人操作请忽略。<br>
              <#if supportEmail??>技术支持：${supportEmail}</#if>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>

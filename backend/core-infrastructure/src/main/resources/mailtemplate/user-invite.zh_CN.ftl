<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>${appName!"Access Matrix"} - 账号邀请</title>
</head>
<body style="margin:0;padding:0;background:#f5f6f7;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'PingFang SC',sans-serif;">
  <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="background:#f5f6f7;padding:40px 0;">
    <tr>
      <td align="center">
        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="560" style="background:#ffffff;border-radius:12px;border:1px solid #e5e7eb;overflow:hidden;">
          <tr>
            <td style="padding:32px 40px 8px;">
              <div style="font-size:20px;font-weight:600;color:#111827;">${appName!"Access Matrix"}</div>
              <div style="font-size:13px;color:#6b7280;margin-top:4px;">账号邀请</div>
            </td>
          </tr>
          <tr>
            <td style="padding:8px 40px 32px;color:#374151;font-size:14px;line-height:1.7;">
              <p style="margin:16px 0;">${displayName!username}<#if displayName?? && displayName != username> (${username})</#if> 您好，</p>
              <p style="margin:16px 0;">
                管理员已为您在 ${appName!"Access Matrix"} 上创建账号。点击下方按钮设置密码即可开始使用。
              </p>
              <p style="margin:28px 0;text-align:center;">
                <a href="${inviteUrl}" style="display:inline-block;background:#2563eb;color:#ffffff;text-decoration:none;padding:12px 28px;border-radius:8px;font-weight:500;font-size:14px;">
                  设置密码
                </a>
              </p>
              <p style="margin:16px 0;font-size:12px;color:#6b7280;">
                如果按钮无法点击，请将以下链接复制到浏览器：<br>
                <span style="word-break:break-all;color:#374151;">${inviteUrl}</span>
              </p>
              <p style="margin:16px 0;font-size:12px;color:#6b7280;">
                此链接有效期为 <strong>${expiresIn!"7 天"}</strong>。过期后请联系管理员重新发送邀请。
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

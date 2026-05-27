<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>${appName!"Access Matrix"} - 应急密码刚刚被使用</title>
</head>
<body style="margin:0;padding:0;background:#f5f6f7;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'PingFang SC','Microsoft YaHei',sans-serif;">
  <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="background:#f5f6f7;padding:40px 0;">
    <tr>
      <td align="center">
        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="560" style="background:#ffffff;border-radius:12px;border:1px solid #e5e7eb;overflow:hidden;">
          <tr>
            <td style="padding:32px 40px 8px;border-bottom:3px solid #f59e0b;">
              <div style="font-size:20px;font-weight:600;color:#111827;">${appName!"Access Matrix"}</div>
              <div style="font-size:13px;color:#b45309;margin-top:4px;font-weight:500;">⚠ 应急密码刚刚被使用</div>
            </td>
          </tr>
          <tr>
            <td style="padding:24px 40px 8px;color:#374151;font-size:14px;line-height:1.7;">
              <p style="margin:8px 0;">${displayName!username}<#if displayName?? && displayName != username>（${username}）</#if> 您好：</p>
              <p style="margin:16px 0;">
                您的应急密码（与 SSO 独立的紧急登录凭据）刚刚被用于登录 ${appName!"Access Matrix"}。
              </p>
            </td>
          </tr>
          <tr>
            <td style="padding:0 40px 16px;">
              <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="border:1px solid #e5e7eb;border-radius:8px;background:#f9fafb;">
                <tr><td style="padding:8px 16px;font-size:12px;color:#6b7280;width:120px;">时间</td><td style="padding:8px 16px;font-size:13px;color:#111827;">${loginAt}</td></tr>
                <tr><td style="padding:8px 16px;font-size:12px;color:#6b7280;border-top:1px solid #e5e7eb;">IP 地址</td><td style="padding:8px 16px;font-size:13px;color:#111827;font-family:monospace;border-top:1px solid #e5e7eb;">${clientIp}</td></tr>
                <tr><td style="padding:8px 16px;font-size:12px;color:#6b7280;border-top:1px solid #e5e7eb;">User-Agent</td><td style="padding:8px 16px;font-size:12px;color:#374151;border-top:1px solid #e5e7eb;word-break:break-all;">${userAgent}</td></tr>
                <tr><td style="padding:8px 16px;font-size:12px;color:#6b7280;border-top:1px solid #e5e7eb;">租户</td><td style="padding:8px 16px;font-size:13px;color:#111827;border-top:1px solid #e5e7eb;">${tenantId}</td></tr>
              </table>
            </td>
          </tr>
          <tr>
            <td style="padding:8px 40px 16px;color:#374151;font-size:14px;line-height:1.7;">
              <p style="margin:8px 0;"><strong>如果是您本人操作</strong>，无需采取任何措施，请忽略本邮件。</p>
              <p style="margin:8px 0;"><strong>如果不是您本人</strong>，应急密码可能已泄露，请立即轮换：</p>
              <p style="margin:20px 0;text-align:center;">
                <a href="${rotateUrl}" style="display:inline-block;background:#dc2626;color:#ffffff;text-decoration:none;padding:12px 28px;border-radius:8px;font-weight:500;font-size:14px;">
                  通过 SSO 登录并轮换
                </a>
              </p>
              <p style="margin:16px 0;font-size:12px;color:#6b7280;">
                SSO 登录后，点击右上角用户菜单 → "应急密码"即可设置新的值。
              </p>
            </td>
          </tr>
          <tr>
            <td style="padding:16px 40px 32px;border-top:1px solid #f3f4f6;font-size:11px;color:#9ca3af;line-height:1.6;">
              ${appName!"Access Matrix"} 自动安全通知。<br>
              <#if supportEmail??>技术支持：${supportEmail}</#if>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>

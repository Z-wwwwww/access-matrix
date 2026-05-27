<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>${appName!"Access Matrix"} - Break-glass credential was just used</title>
</head>
<body style="margin:0;padding:0;background:#f5f6f7;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
  <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="background:#f5f6f7;padding:40px 0;">
    <tr>
      <td align="center">
        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="560" style="background:#ffffff;border-radius:12px;border:1px solid #e5e7eb;overflow:hidden;">
          <tr>
            <td style="padding:32px 40px 8px;border-bottom:3px solid #f59e0b;">
              <div style="font-size:20px;font-weight:600;color:#111827;">${appName!"Access Matrix"}</div>
              <div style="font-size:13px;color:#b45309;margin-top:4px;font-weight:500;">⚠ Break-glass credential was just used</div>
            </td>
          </tr>
          <tr>
            <td style="padding:24px 40px 8px;color:#374151;font-size:14px;line-height:1.7;">
              <p style="margin:8px 0;">Hi ${displayName!username}<#if displayName?? && displayName != username> (${username})</#if>,</p>
              <p style="margin:16px 0;">
                Your break-glass credential — the emergency password independent from SSO — was just used to sign in to ${appName!"Access Matrix"}.
              </p>
            </td>
          </tr>
          <tr>
            <td style="padding:0 40px 16px;">
              <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="border:1px solid #e5e7eb;border-radius:8px;background:#f9fafb;">
                <tr><td style="padding:8px 16px;font-size:12px;color:#6b7280;width:120px;">When</td><td style="padding:8px 16px;font-size:13px;color:#111827;">${loginAt}</td></tr>
                <tr><td style="padding:8px 16px;font-size:12px;color:#6b7280;border-top:1px solid #e5e7eb;">From IP</td><td style="padding:8px 16px;font-size:13px;color:#111827;font-family:monospace;border-top:1px solid #e5e7eb;">${clientIp}</td></tr>
                <tr><td style="padding:8px 16px;font-size:12px;color:#6b7280;border-top:1px solid #e5e7eb;">User agent</td><td style="padding:8px 16px;font-size:12px;color:#374151;border-top:1px solid #e5e7eb;word-break:break-all;">${userAgent}</td></tr>
                <tr><td style="padding:8px 16px;font-size:12px;color:#6b7280;border-top:1px solid #e5e7eb;">Tenant</td><td style="padding:8px 16px;font-size:13px;color:#111827;border-top:1px solid #e5e7eb;">${tenantId}</td></tr>
              </table>
            </td>
          </tr>
          <tr>
            <td style="padding:8px 40px 16px;color:#374151;font-size:14px;line-height:1.7;">
              <p style="margin:8px 0;"><strong>If this was you</strong>, no action needed. You can ignore this email.</p>
              <p style="margin:8px 0;"><strong>If this was NOT you</strong>, your break-glass credential may be compromised. Rotate it immediately:</p>
              <p style="margin:20px 0;text-align:center;">
                <a href="${rotateUrl}" style="display:inline-block;background:#dc2626;color:#ffffff;text-decoration:none;padding:12px 28px;border-radius:8px;font-weight:500;font-size:14px;">
                  Sign in via SSO and rotate
                </a>
              </p>
              <p style="margin:16px 0;font-size:12px;color:#6b7280;">
                After SSO sign-in, open the user menu (top-right) → "Break-glass password" to set a new value.
              </p>
            </td>
          </tr>
          <tr>
            <td style="padding:16px 40px 32px;border-top:1px solid #f3f4f6;font-size:11px;color:#9ca3af;line-height:1.6;">
              Automated security notice from ${appName!"Access Matrix"}.<br>
              <#if supportEmail??>Support: ${supportEmail}</#if>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>

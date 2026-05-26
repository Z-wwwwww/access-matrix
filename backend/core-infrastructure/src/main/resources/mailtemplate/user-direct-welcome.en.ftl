<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>${appName!"Access Matrix"} - Account opened</title>
</head>
<body style="margin:0;padding:0;background:#f5f6f7;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
  <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="background:#f5f6f7;padding:40px 0;">
    <tr>
      <td align="center">
        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="560" style="background:#ffffff;border-radius:12px;border:1px solid #e5e7eb;overflow:hidden;">
          <tr>
            <td style="padding:32px 40px 8px;">
              <div style="font-size:20px;font-weight:600;color:#111827;">${appName!"Access Matrix"}</div>
              <div style="font-size:13px;color:#6b7280;margin-top:4px;">Your account has been opened</div>
            </td>
          </tr>
          <tr>
            <td style="padding:8px 40px 32px;color:#374151;font-size:14px;line-height:1.7;">
              <p style="margin:16px 0;">Hi ${displayName!username}<#if displayName?? && displayName != username> (${username})</#if>,</p>
              <p style="margin:16px 0;">
                An administrator has opened your ${appName!"Access Matrix"} account. Please sign in with the credentials below.
              </p>

              <table cellpadding="6" cellspacing="0" border="0" style="background:#f9fafb;border:1px solid #e5e7eb;border-radius:8px;margin:20px 0;font-size:13px;">
                <tr>
                  <td style="color:#6b7280;padding:6px 14px;">Sign-in URL</td>
                  <td style="color:#374151;padding:6px 14px;"><a href="${loginUrl}" style="color:#2563eb;">${loginUrl}</a></td>
                </tr>
                <tr>
                  <td style="color:#6b7280;padding:6px 14px;">Username</td>
                  <td style="color:#374151;padding:6px 14px;font-family:monospace;">${username}</td>
                </tr>
                <tr>
                  <td style="color:#6b7280;padding:6px 14px;">Initial password</td>
                  <td style="color:#374151;padding:6px 14px;font-family:monospace;">${tempPassword}</td>
                </tr>
                <tr>
                  <td style="color:#6b7280;padding:6px 14px;">Tenant</td>
                  <td style="color:#374151;padding:6px 14px;font-family:monospace;">${tenantId!"default"}</td>
                </tr>
              </table>

              <p style="margin:16px 0;color:#dc2626;font-size:13px;">
                For security, you will be required to change your password at first sign-in.
              </p>
            </td>
          </tr>
          <tr>
            <td style="padding:16px 40px 32px;border-top:1px solid #f3f4f6;font-size:11px;color:#9ca3af;line-height:1.6;">
              This is an automated message from ${appName!"Access Matrix"}. If you weren't expecting it, please ignore.<br>
              <#if supportEmail??>Support: ${supportEmail}</#if>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>

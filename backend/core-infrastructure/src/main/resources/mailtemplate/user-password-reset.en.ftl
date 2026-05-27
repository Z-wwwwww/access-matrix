<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>${appName!"Access Matrix"} - Set your password</title>
</head>
<body style="margin:0;padding:0;background:#f5f6f7;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
  <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="background:#f5f6f7;padding:40px 0;">
    <tr>
      <td align="center">
        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="560" style="background:#ffffff;border-radius:12px;border:1px solid #e5e7eb;overflow:hidden;">
          <tr>
            <td style="padding:32px 40px 8px;">
              <div style="font-size:20px;font-weight:600;color:#111827;">${appName!"Access Matrix"}</div>
              <div style="font-size:13px;color:#6b7280;margin-top:4px;">Set your password</div>
            </td>
          </tr>
          <tr>
            <td style="padding:8px 40px 32px;color:#374151;font-size:14px;line-height:1.7;">
              <p style="margin:16px 0;">Hi ${displayName!username}<#if displayName?? && displayName != username> (${username})</#if>,</p>
              <p style="margin:16px 0;">
                ${appName!"Access Matrix"} is switching its login method. Going forward, you will sign in with a username and password set directly in our system instead of through single sign-on.
              </p>
              <p style="margin:16px 0;">
                Click the button below to choose a new password. After that you can sign in at the login page as usual.
              </p>
              <p style="margin:28px 0;text-align:center;">
                <a href="${resetUrl}" style="display:inline-block;background:#2563eb;color:#ffffff;text-decoration:none;padding:12px 28px;border-radius:8px;font-weight:500;font-size:14px;">
                  Set your password
                </a>
              </p>
              <p style="margin:16px 0;font-size:12px;color:#6b7280;">
                If the button doesn't work, paste this URL into your browser:<br>
                <span style="word-break:break-all;color:#374151;">${resetUrl}</span>
              </p>
              <p style="margin:16px 0;font-size:12px;color:#6b7280;">
                This link is valid for <strong>${expiresIn!"7"} days</strong>. After that, please ask an administrator for a fresh link.
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

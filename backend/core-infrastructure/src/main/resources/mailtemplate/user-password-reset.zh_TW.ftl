<!DOCTYPE html>
<html lang="zh-TW">
<head>
<meta charset="UTF-8">
<title>${appName!"Access Matrix"} - 請重新設置您的密碼</title>
</head>
<body style="margin:0;padding:0;background:#f5f6f7;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'PingFang TC','Microsoft JhengHei',sans-serif;">
  <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="background:#f5f6f7;padding:40px 0;">
    <tr>
      <td align="center">
        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="560" style="background:#ffffff;border-radius:12px;border:1px solid #e5e7eb;overflow:hidden;">
          <tr>
            <td style="padding:32px 40px 8px;">
              <div style="font-size:20px;font-weight:600;color:#111827;">${appName!"Access Matrix"}</div>
              <div style="font-size:13px;color:#6b7280;margin-top:4px;">請重新設置您的密碼</div>
            </td>
          </tr>
          <tr>
            <td style="padding:8px 40px 32px;color:#374151;font-size:14px;line-height:1.7;">
              <p style="margin:16px 0;">${displayName!username}<#if displayName?? && displayName != username>（${username}）</#if> 您好：</p>
              <p style="margin:16px 0;">
                ${appName!"Access Matrix"} 的登入方式正在調整。今後您將不再透過單一登入（SSO），改用本系統中直接設置的帳號密碼登入。
              </p>
              <p style="margin:16px 0;">
                請點擊下方按鈕設置新密碼，設置完成後即可在登入頁正常使用。
              </p>
              <p style="margin:28px 0;text-align:center;">
                <a href="${resetUrl}" style="display:inline-block;background:#2563eb;color:#ffffff;text-decoration:none;padding:12px 28px;border-radius:8px;font-weight:500;font-size:14px;">
                  設置密碼
                </a>
              </p>
              <p style="margin:16px 0;font-size:12px;color:#6b7280;">
                如果按鈕無法點擊，請將以下連結複製到瀏覽器：<br>
                <span style="word-break:break-all;color:#374151;">${resetUrl}</span>
              </p>
              <p style="margin:16px 0;font-size:12px;color:#6b7280;">
                連結有效期為 <strong>${expiresIn!"7"} 天</strong>。逾期請聯絡管理員重新發送。
              </p>
            </td>
          </tr>
          <tr>
            <td style="padding:16px 40px 32px;border-top:1px solid #f3f4f6;font-size:11px;color:#9ca3af;line-height:1.6;">
              本郵件由 ${appName!"Access Matrix"} 自動發送。若非本人操作請忽略。<br>
              <#if supportEmail??>技術支援：${supportEmail}</#if>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>

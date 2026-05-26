<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<title>${appName!"Access Matrix"} - 계정 초대</title>
</head>
<body style="margin:0;padding:0;background:#f5f6f7;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Apple SD Gothic Neo',sans-serif;">
  <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="background:#f5f6f7;padding:40px 0;">
    <tr>
      <td align="center">
        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="560" style="background:#ffffff;border-radius:12px;border:1px solid #e5e7eb;overflow:hidden;">
          <tr>
            <td style="padding:32px 40px 8px;">
              <div style="font-size:20px;font-weight:600;color:#111827;">${appName!"Access Matrix"}</div>
              <div style="font-size:13px;color:#6b7280;margin-top:4px;">계정 초대</div>
            </td>
          </tr>
          <tr>
            <td style="padding:8px 40px 32px;color:#374151;font-size:14px;line-height:1.7;">
              <p style="margin:16px 0;">${displayName!username}<#if displayName?? && displayName != username> (${username})</#if> 님,</p>
              <p style="margin:16px 0;">
                관리자가 ${appName!"Access Matrix"} 에서 귀하의 계정을 생성했습니다. 아래 버튼을 클릭하여 비밀번호를 설정하고 서비스를 시작하세요.
              </p>
              <p style="margin:28px 0;text-align:center;">
                <a href="${inviteUrl}" style="display:inline-block;background:#2563eb;color:#ffffff;text-decoration:none;padding:12px 28px;border-radius:8px;font-weight:500;font-size:14px;">
                  비밀번호 설정
                </a>
              </p>
              <p style="margin:16px 0;font-size:12px;color:#6b7280;">
                버튼이 작동하지 않으면 아래 URL을 브라우저에 직접 붙여넣으세요.<br>
                <span style="word-break:break-all;color:#374151;">${inviteUrl}</span>
              </p>
              <p style="margin:16px 0;font-size:12px;color:#6b7280;">
                이 링크의 유효 기간은 <strong>${expiresIn!"7일"}</strong> 입니다. 만료되면 관리자에게 재발급을 요청하세요.
              </p>
            </td>
          </tr>
          <tr>
            <td style="padding:16px 40px 32px;border-top:1px solid #f3f4f6;font-size:11px;color:#9ca3af;line-height:1.6;">
              이 메일은 ${appName!"Access Matrix"} 의 자동 발송입니다. 문의 사항이 있으면 무시하세요.<br>
              <#if supportEmail??>문의: ${supportEmail}</#if>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>

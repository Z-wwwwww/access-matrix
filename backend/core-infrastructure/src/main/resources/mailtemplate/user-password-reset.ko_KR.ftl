<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<title>${appName!"Access Matrix"} - 비밀번호 재설정 안내</title>
</head>
<body style="margin:0;padding:0;background:#f5f6f7;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Apple SD Gothic Neo','Malgun Gothic',sans-serif;">
  <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="background:#f5f6f7;padding:40px 0;">
    <tr>
      <td align="center">
        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="560" style="background:#ffffff;border-radius:12px;border:1px solid #e5e7eb;overflow:hidden;">
          <tr>
            <td style="padding:32px 40px 8px;">
              <div style="font-size:20px;font-weight:600;color:#111827;">${appName!"Access Matrix"}</div>
              <div style="font-size:13px;color:#6b7280;margin-top:4px;">비밀번호 재설정 안내</div>
            </td>
          </tr>
          <tr>
            <td style="padding:8px 40px 32px;color:#374151;font-size:14px;line-height:1.7;">
              <p style="margin:16px 0;">${displayName!username}<#if displayName?? && displayName != username> (${username})</#if> 님,</p>
              <p style="margin:16px 0;">
                ${appName!"Access Matrix"} 의 로그인 방식이 변경됩니다. 앞으로는 SSO 가 아니라 본 시스템에서 직접 설정한 비밀번호로 로그인하시게 됩니다.
              </p>
              <p style="margin:16px 0;">
                아래 버튼을 눌러 새 비밀번호를 설정해 주세요. 설정이 완료되면 로그인 페이지에서 정상적으로 사용하실 수 있습니다.
              </p>
              <p style="margin:28px 0;text-align:center;">
                <a href="${resetUrl}" style="display:inline-block;background:#2563eb;color:#ffffff;text-decoration:none;padding:12px 28px;border-radius:8px;font-weight:500;font-size:14px;">
                  비밀번호 설정
                </a>
              </p>
              <p style="margin:16px 0;font-size:12px;color:#6b7280;">
                버튼이 동작하지 않는 경우, 아래 URL 을 브라우저에 붙여넣어 주세요:<br>
                <span style="word-break:break-all;color:#374151;">${resetUrl}</span>
              </p>
              <p style="margin:16px 0;font-size:12px;color:#6b7280;">
                이 링크는 <strong>${expiresIn!"7"}일</strong> 동안 유효합니다. 만료된 경우 관리자에게 재발급을 요청해 주세요.
              </p>
            </td>
          </tr>
          <tr>
            <td style="padding:16px 40px 32px;border-top:1px solid #f3f4f6;font-size:11px;color:#9ca3af;line-height:1.6;">
              이 메일은 ${appName!"Access Matrix"} 에서 자동 발송된 메일입니다. 신청한 적이 없다면 무시해 주세요.<br>
              <#if supportEmail??>지원: ${supportEmail}</#if>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>

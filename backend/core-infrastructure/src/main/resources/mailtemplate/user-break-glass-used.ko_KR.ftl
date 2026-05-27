<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<title>${appName!"Access Matrix"} - 비상용 비밀번호가 방금 사용되었습니다</title>
</head>
<body style="margin:0;padding:0;background:#f5f6f7;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Apple SD Gothic Neo','Malgun Gothic',sans-serif;">
  <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="background:#f5f6f7;padding:40px 0;">
    <tr>
      <td align="center">
        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="560" style="background:#ffffff;border-radius:12px;border:1px solid #e5e7eb;overflow:hidden;">
          <tr>
            <td style="padding:32px 40px 8px;border-bottom:3px solid #f59e0b;">
              <div style="font-size:20px;font-weight:600;color:#111827;">${appName!"Access Matrix"}</div>
              <div style="font-size:13px;color:#b45309;margin-top:4px;font-weight:500;">⚠ 비상용 비밀번호가 방금 사용되었습니다</div>
            </td>
          </tr>
          <tr>
            <td style="padding:24px 40px 8px;color:#374151;font-size:14px;line-height:1.7;">
              <p style="margin:8px 0;">${displayName!username}<#if displayName?? && displayName != username> (${username})</#if> 님,</p>
              <p style="margin:16px 0;">
                ${appName!"Access Matrix"} 에 SSO 와는 별개의 비상용 비밀번호로 로그인되었습니다.
              </p>
            </td>
          </tr>
          <tr>
            <td style="padding:0 40px 16px;">
              <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="border:1px solid #e5e7eb;border-radius:8px;background:#f9fafb;">
                <tr><td style="padding:8px 16px;font-size:12px;color:#6b7280;width:120px;">시간</td><td style="padding:8px 16px;font-size:13px;color:#111827;">${loginAt}</td></tr>
                <tr><td style="padding:8px 16px;font-size:12px;color:#6b7280;border-top:1px solid #e5e7eb;">IP 주소</td><td style="padding:8px 16px;font-size:13px;color:#111827;font-family:monospace;border-top:1px solid #e5e7eb;">${clientIp}</td></tr>
                <tr><td style="padding:8px 16px;font-size:12px;color:#6b7280;border-top:1px solid #e5e7eb;">User-Agent</td><td style="padding:8px 16px;font-size:12px;color:#374151;border-top:1px solid #e5e7eb;word-break:break-all;">${userAgent}</td></tr>
                <tr><td style="padding:8px 16px;font-size:12px;color:#6b7280;border-top:1px solid #e5e7eb;">테넌트</td><td style="padding:8px 16px;font-size:13px;color:#111827;border-top:1px solid #e5e7eb;">${tenantId}</td></tr>
              </table>
            </td>
          </tr>
          <tr>
            <td style="padding:8px 40px 16px;color:#374151;font-size:14px;line-height:1.7;">
              <p style="margin:8px 0;"><strong>본인이 직접 로그인한 경우</strong>, 별도 조치는 필요하지 않습니다.</p>
              <p style="margin:8px 0;"><strong>본인이 아닌 경우</strong>, 비상용 비밀번호가 노출되었을 수 있습니다. 즉시 변경해 주세요:</p>
              <p style="margin:20px 0;text-align:center;">
                <a href="${rotateUrl}" style="display:inline-block;background:#dc2626;color:#ffffff;text-decoration:none;padding:12px 28px;border-radius:8px;font-weight:500;font-size:14px;">
                  SSO 로 로그인하여 변경
                </a>
              </p>
              <p style="margin:16px 0;font-size:12px;color:#6b7280;">
                SSO 로그인 후 우측 상단 사용자 메뉴 → "비상용 비밀번호" 에서 새 값을 설정하실 수 있습니다.
              </p>
            </td>
          </tr>
          <tr>
            <td style="padding:16px 40px 32px;border-top:1px solid #f3f4f6;font-size:11px;color:#9ca3af;line-height:1.6;">
              ${appName!"Access Matrix"} 자동 보안 알림입니다.<br>
              <#if supportEmail??>지원: ${supportEmail}</#if>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>

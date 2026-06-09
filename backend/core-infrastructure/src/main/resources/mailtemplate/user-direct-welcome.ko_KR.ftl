<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<title>${appName!"Access Matrix"} - 계정 개설 안내</title>
</head>
<body style="margin:0;padding:0;background:#f5f6f7;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Apple SD Gothic Neo',sans-serif;">
  <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="background:#f5f6f7;padding:40px 0;">
    <tr>
      <td align="center">
        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="560" style="background:#ffffff;border-radius:12px;border:1px solid #e5e7eb;overflow:hidden;">
          <tr>
            <td style="padding:32px 40px 8px;">
              <table role="presentation" cellpadding="0" cellspacing="0" border="0"><tr><td style="vertical-align:middle;padding-right:8px;line-height:0;"><img src="${logoUrl!''}" alt="" width="26" height="26" style="display:block;border:0;" /></td><td style="vertical-align:middle;font-size:20px;font-weight:600;color:#111827;">${appName!"Access Matrix"}</td></tr></table>
              <div style="font-size:13px;color:#6b7280;margin-top:4px;"><#if reset!false>비밀번호 재설정 안내<#else>계정 개설 안내</#if></div>
            </td>
          </tr>
          <tr>
            <td style="padding:8px 40px 32px;color:#374151;font-size:14px;line-height:1.7;">
              <p style="margin:16px 0;">${displayName!username}<#if displayName?? && displayName != username> (${username})</#if> 님,</p>
              <p style="margin:16px 0;">
                <#if reset!false>관리자가 ${appName!"Access Matrix"} 비밀번호를 재설정했습니다. 아래 임시 비밀번호로 로그인한 뒤 안내에 따라 새 비밀번호를 설정하세요.<#else>관리자가 ${appName!"Access Matrix"} 계정을 개설했습니다. 아래 정보로 로그인하세요.</#if>
              </p>

              <table cellpadding="6" cellspacing="0" border="0" style="background:#f9fafb;border:1px solid #e5e7eb;border-radius:8px;margin:20px 0;font-size:13px;">
                <#if !(reset!false)>
                <tr>
                  <td style="color:#6b7280;padding:6px 14px;">로그인 URL</td>
                  <td style="color:#374151;padding:6px 14px;"><a href="${loginUrl}" style="color:#2563eb;">${loginUrl}</a></td>
                </tr>
                <tr>
                  <td style="color:#6b7280;padding:6px 14px;">사용자명</td>
                  <td style="color:#374151;padding:6px 14px;font-family:monospace;">${username}</td>
                </tr>
                </#if>
                <tr>
                  <td style="color:#6b7280;padding:6px 14px;">초기 비밀번호</td>
                  <td style="color:#374151;padding:6px 14px;font-family:monospace;">${tempPassword}</td>
                </tr>
                <#if !(reset!false)>
                <tr>
                  <td style="color:#6b7280;padding:6px 14px;">테넌트</td>
                  <td style="color:#374151;padding:6px 14px;font-family:monospace;">${tenantId!"default"}</td>
                </tr>
                </#if>
              </table>

              <p style="margin:16px 0;color:#dc2626;font-size:13px;">
                보안을 위해 첫 로그인 시 비밀번호 변경이 필요합니다.
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

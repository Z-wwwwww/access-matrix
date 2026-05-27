<!DOCTYPE html>
<html lang="ja">
<head>
<meta charset="UTF-8">
<title>${appName!"Access Matrix"} - パスワード再設定のお願い</title>
</head>
<body style="margin:0;padding:0;background:#f5f6f7;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Hiragino Kaku Gothic ProN','Yu Gothic','メイリオ',sans-serif;">
  <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="background:#f5f6f7;padding:40px 0;">
    <tr>
      <td align="center">
        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="560" style="background:#ffffff;border-radius:12px;border:1px solid #e5e7eb;overflow:hidden;">
          <tr>
            <td style="padding:32px 40px 8px;">
              <div style="font-size:20px;font-weight:600;color:#111827;">${appName!"Access Matrix"}</div>
              <div style="font-size:13px;color:#6b7280;margin-top:4px;">パスワード再設定のお願い</div>
            </td>
          </tr>
          <tr>
            <td style="padding:8px 40px 32px;color:#374151;font-size:14px;line-height:1.7;">
              <p style="margin:16px 0;">${displayName!username}<#if displayName?? && displayName != username>（${username}）</#if> 様</p>
              <p style="margin:16px 0;">
                ${appName!"Access Matrix"} のログイン方式が変更されます。今後はシングルサインオン（SSO）ではなく、本システムで直接設定したパスワードでログインしていただきます。
              </p>
              <p style="margin:16px 0;">
                以下のボタンから新しいパスワードを設定してください。設定後はログイン画面から通常通りサインインできます。
              </p>
              <p style="margin:28px 0;text-align:center;">
                <a href="${resetUrl}" style="display:inline-block;background:#2563eb;color:#ffffff;text-decoration:none;padding:12px 28px;border-radius:8px;font-weight:500;font-size:14px;">
                  パスワードを設定する
                </a>
              </p>
              <p style="margin:16px 0;font-size:12px;color:#6b7280;">
                ボタンが動作しない場合は、以下の URL をブラウザにコピーしてください：<br>
                <span style="word-break:break-all;color:#374151;">${resetUrl}</span>
              </p>
              <p style="margin:16px 0;font-size:12px;color:#6b7280;">
                このリンクは <strong>${expiresIn!"7"} 日間</strong> 有効です。期限切れの場合は管理者に再発行を依頼してください。
              </p>
            </td>
          </tr>
          <tr>
            <td style="padding:16px 40px 32px;border-top:1px solid #f3f4f6;font-size:11px;color:#9ca3af;line-height:1.6;">
              本メールは ${appName!"Access Matrix"} からの自動送信です。心当たりのない場合は破棄してください。<br>
              <#if supportEmail??>サポート：${supportEmail}</#if>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>

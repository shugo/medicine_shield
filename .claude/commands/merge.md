---
description: Merge pull request and update main branch
---

以下の手順でpull requestをマージしてmainブランチを更新してください：

1. 現在のブランチ名を確認
2. `gh pr list --head <current-branch>`で現在のブランチに関連するPRを確認
3. PRが存在する場合、PRの状態（マージ可能かどうか、CIステータスなど）を確認
4. `gh pr merge`を使用してPRをマージ：
   - マージ方法は`--merge`を使用
   - 必要に応じて`--auto`オプションを使用
5. マージ成功後、以下を実行：
   ```bash
   git checkout main
   git pull origin main
   ```
6. マージされたブランチの削除を確認（ghコマンドが自動的に処理）

注意事項：
- マージ前にPRのステータスを確認し、CIが失敗している場合は警告を表示
- main/masterブランチへのマージのみをサポート
- マージ後は必ずmainブランチに切り替えて最新の状態に更新

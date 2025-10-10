---
description: Create a pull request following the project's PR workflow
---

以下の手順でpull requestを作成してください：

1. `git status`でuntracked filesを確認
2. `git diff`でstaged/unstagedの変更を確認
3. 現在のブランチがリモートブランチを追跡しているか、リモートと同期しているかを確認
4. `git log`と`git diff main...HEAD`（またはベースブランチとの差分）で、このブランチの完全なコミット履歴を確認
5. PRに含まれるすべての変更を分析し、PRサマリーを作成（すべてのコミットを考慮すること）
6. 必要に応じて以下を実行：
   - 新しいブランチの作成
   - `-u`フラグ付きでリモートへのpush
   - 以下のフォーマットで`gh pr create`を使用してPRを作成：

```bash
gh pr create --title "PRタイトル" --body "$(cat <<'EOF'
## 概要
<1-3個の箇条書き>

## テスト計画
[PRをテストするためのTODOチェックリスト...]

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

7. PR URLを返す

注意事項：
- TodoWriteツールは使用しない
- すべてのコミット（最新のコミットだけでなく）を含めてPRサマリーを作成
- 概要とテスト計画は日本語で記述

---
description: Create a git commit following the project's commit workflow
---

以下の手順でgit commitを作成してください：

1. `git branch --show-current`で現在のブランチを確認
2. **現在のブランチがmainの場合**：
   - 変更内容に基づいて適切なブランチ名を決定（例: `feature/add-notification-filter`, `fix/null-pointer-exception`）
   - `git checkout -b <ブランチ名>`で新しいブランチを作成
   - **NEVER commit directly to main branch**
3. `git status`でuntracked filesを確認
4. `git diff`でstaged/unstagedの変更を確認
5. `git log`で最近のコミットメッセージを確認し、このリポジトリのコミットメッセージスタイルを把握
6. すべての変更を分析し、適切なコミットメッセージを作成（英語で簡潔に、変更の目的を明確に）
7. 関連するファイルをステージングエリアに追加
8. 以下のフォーマットでコミットを作成：

```
git commit -m "$(cat <<'EOF'
<コミットメッセージ>

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

9. `git status`でコミットが成功したことを確認

注意事項：
- **mainブランチに直接コミットしない** - 必ず作業用ブランチを作成すること
- pre-commit hookによって変更された場合は、必要に応じてamendを検討
- .envやcredentials.jsonなどの機密情報を含むファイルはコミットしない
- コミットメッセージは変更の「何を」ではなく「なぜ」に焦点を当てる

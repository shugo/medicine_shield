---
description: Create a git commit following the project's commit workflow
---

以下の手順でgit commitを作成してください：

1. `git status`でuntracked filesを確認
2. `git diff`でstaged/unstagedの変更を確認
3. `git log`で最近のコミットメッセージを確認し、このリポジトリのコミットメッセージスタイルを把握
4. すべての変更を分析し、適切なコミットメッセージを作成（日本語で簡潔に、変更の目的を明確に）
5. 関連するファイルをステージングエリアに追加
6. 以下のフォーマットでコミットを作成：

```
git commit -m "$(cat <<'EOF'
<コミットメッセージ>

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

7. `git status`でコミットが成功したことを確認

注意事項：
- pre-commit hookによって変更された場合は、必要に応じてamendを検討
- .envやcredentials.jsonなどの機密情報を含むファイルはコミットしない
- コミットメッセージは変更の「何を」ではなく「なぜ」に焦点を当てる

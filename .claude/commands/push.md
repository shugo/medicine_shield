---
description: Create a git commit and push to remote repository
---

以下の手順でgit commitとpushを実行してください：

1. **変更の確認**
   - `git status`でuntracked filesを確認
   - `git diff`でstaged/unstagedの変更を確認
   - `git log`で最近のコミットメッセージを確認し、このリポジトリのコミットメッセージスタイルを把握

2. **コミットの作成**
   - すべての変更を分析し、適切なコミットメッセージを作成（日本語で簡潔に、変更の目的を明確に）
   - 関連するファイルをステージングエリアに追加
   - 以下のフォーマットでコミットを作成：

```
git commit -m "$(cat <<'EOF'
<コミットメッセージ>

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

3. **リモートへのプッシュ**
   - 現在のブランチを確認
   - `git push`でリモートリポジトリにプッシュ
   - upstream未設定の場合は`git push -u origin <ブランチ名>`を使用
   - `git status`でプッシュが成功したことを確認

注意事項：
- .envやcredentials.jsonなどの機密情報を含むファイルはコミットしない
- コミットメッセージは変更の「何を」ではなく「なぜ」に焦点を当てる
- main/masterブランチへのforce pushは警告を出す
- pre-commit hookによって変更された場合は、必要に応じてamendを検討

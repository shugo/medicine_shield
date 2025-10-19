# MedicineShield

MedicineShieldは、様々な服薬サイクルをサポートし、日々の服薬スケジュールの管理をユーザーがトラッキングできるAndroid向け服薬管理アプリケーションです。

## 機能

- **デイリー服薬ビュー**: 直感的なインターフェースで今日の服薬スケジュールを表示・トラッキング
- **日付ナビゲーション**: 過去と未来の服薬スケジュールを閲覧、今日ボタンで素早く今日の日付に戻る
- **柔軟な服薬サイクル**:
  - 毎日: 毎日服薬
  - 毎週: 特定の曜日に服薬（例：月曜、水曜、金曜）
  - 間隔: N日ごとに服薬
  - 頓服: 必要に応じて服薬（頓服薬）
- **服薬管理**: カスタマイズ可能なスケジュールで薬を追加、編集、削除
- **服薬記録**: タイムスタンプ付きで服薬時刻を記録、キャンセル機能も搭載
- **服用量サポート**: 服用量と単位をカスタマイズして記録
- **日々のメモ**: 各日にメモを追加して健康観察や服薬関連情報を記録
- **期間設定**: 服薬コースの開始日と終了日を設定
- **経時的データ管理**: 服薬スケジュールと時刻の履歴変更をトラッキング
- **服薬通知**: スケジュールされた服薬時刻にリマインダーを受信
- **設定**: 通知の設定をカスタマイズ、アプリバージョンとライセンス情報を表示

## 技術スタック

- **言語**: Kotlin
- **UIフレームワーク**: Jetpack Compose + Material 3
- **アーキテクチャ**: クリーンアーキテクチャ + Repositoryパターン
- **データベース**: Room (SQLite)
- **ナビゲーション**: Jetpack Navigation Compose
- **最小SDK**: 24 (Android 7.0)
- **ターゲットSDK**: 36 (Android 16)

## プロジェクト構造

```
app/src/main/java/net/shugo/medicineshield/
├── data/
│   ├── dao/              # データベースアクセス用のRoom DAO
│   ├── database/         # データベース設定とマイグレーション
│   ├── model/            # データモデルとエンティティ
│   ├── preferences/      # 設定用のSharedPreferencesラッパー
│   └── repository/       # データ操作用のRepositoryレイヤー
├── notification/         # 通知のスケジューリングと処理
├── ui/
│   └── screen/           # Compose UIスクリーン
├── utils/                # ユーティリティクラス（日付処理など）
├── viewmodel/            # UI状態管理用のViewModel
└── MainActivity.kt       # メインエントリーポイントとナビゲーション設定
```

## プロジェクトのビルド

### 必要要件

- Android Studio Hedgehog以降
- JDK 17
- API level 34を含むAndroid SDK

### ビルドコマンド

```bash
# クリーンビルド
./gradlew clean build

# 接続されたデバイス/エミュレータにデバッグビルドをインストール
./gradlew installDebug

# ユニットテストを実行
./gradlew test

# Androidインストルメンテーションテストを実行
./gradlew connectedAndroidTest

# Lintチェックを実行
./gradlew lint
```

## データベーススキーマ

本アプリはRoom database (version 11) を使用し、以下のエンティティで構成されています:

### コアエンティティ

- **medications**: 基本的な薬の情報（id、名前）
  - 基本的な薬の識別情報のみを保存する最小限のエンティティ

- **medication_configs**: 経時的有効性を持つ服薬スケジュール設定
  - `cycleType`（DAILY、WEEKLY、INTERVAL）、`cycleValue`、日付範囲を保存
  - 経時的有効性: `validFrom`/`validTo`タイムスタンプにより、設定変更を経時的にトラッキング可能
  - Medicationとの一対多関係

- **medication_times**: 経時的有効性を持つ各薬のスケジュール時刻
  - 各時刻は薬ごとに安定した`sequenceNumber`（1、2、3...）を持つ
  - 経時的有効性: `validFrom`/`validTo`タイムスタンプで各時刻の有効期間をトラッキング
  - 服薬履歴を壊すことなく時刻変更が可能
  - Medicationとの一対多関係

- **medication_intakes**: 実際の服薬イベントの記録
  - `medicationId`と`sequenceNumber`（時刻文字列ではない）で薬にリンク
  - `scheduledDate`（YYYY-MM-DD）、`takenAt`タイムスタンプ、`canceledAt`タイムスタンプを保存
  - 3つの状態をサポート: 未服用（unchecked）、服用済み（takenタイムスタンプ付き）、キャンセル済み（canceledタイムスタンプ付き）
  - `(medicationId, scheduledDate, sequenceNumber)`に一意インデックス

- **daily_notes**: 健康観察を記録するための日々のメモ
  - 各日付（YYYY-MM-DD形式）のメモを保存
  - 作成および更新タイムスタンプを含む

### 主要な設計機能

- **経時的データ管理**: `MedicationConfig`と`MedicationTime`の両方が経時的有効性をサポートし、過去の日付には正確な履歴データを、未来の日付には現在の設定を表示可能

- **シーケンス番号システム**: 服薬時刻は識別に時刻文字列ではなく安定したシーケンス番号を使用し、服薬履歴を保持したまま服薬時刻の変更が可能

- **頓服薬**: 固定スケジュールなしで1日に複数回追加できる頓服薬（PRN薬）をサポート

- **服用量トラッキング**: カスタマイズ可能な量と単位で服用量を記録（例：1錠、5 ml、2.5 mg）

- **データベースマイグレーション**: データベースはv1からv11までの包括的なマイグレーションを含み、経時的データ、シーケンス番号、服用量トラッキング、日々のメモなど、進化する機能をサポート

## ライセンス

本プロジェクトはMIT-0ライセンスの下でライセンスされています - 詳細は[LICENSE.txt](LICENSE.txt)ファイルをご覧ください。

## 作者

Shugo Maeda

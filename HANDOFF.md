# AppStoreApp HANDOFF

## 概要
自作アプリストア「Appathy Store」。CatalogApp リポジトリの catalog.json を唯一の真実として
アプリ一覧を表示し、各リポジトリの GitHub Releases から APK をダウンロードしてインストールする。
配布は未公開・Android 限定。全 APK は共通鍵 ci/appathy.keystore で署名される。

## v1.0 の機能
- 初回起動時に GitHub PAT を入力（端末内 SharedPreferences のみに保存）
- catalog.json 取得 → カテゴリフィルタつき一覧
- アプリごとに defaultChannel の最新 Release を照会し、インストール状態を表示
  （未インストール / 最新 / 更新あり / リリース未作成 / 情報不足）
- インストール・更新ボタン → APK ダウンロード → パッケージインストーラ起動
  （初回は「この提供元を許可」の OS 設定が必要）

## 構成
- MainActivity.kt ... 画面全体（一覧・フィルタ・設定ダイアログ）
- Catalog.kt ... カタログ取得、チャネル別最新 Release 解決、インストール状態算出
- Github.kt ... API クライアント。アセットダウンロードは 302 先へ Authorization を
  付けずに再接続する（S3 が Authorization つきを拒否するため）
- Installer.kt ... APK 保存と FileProvider 経由のインストーラ起動
- ビルドは gradle wrapper なし。CI で gradle/actions/setup-gradle (8.7) を使う

## インストール状態の判定
installedVersionName == tag から v を除いた文字列 → 最新。
そのため各アプリの versionName とタグは揃える運用にする（v1.0.0 なら versionName 1.0.0）。
揃っていないアプリは常に「更新あり」と出る。将来は versionCode 比較に強化する。

## ロードマップ（仕様確定済み）
1. 一括インストール（catalog.json の profiles を使用）
2. チャネル切替 UI（Stable / Beta / Nightly / Experimental をアプリごとにワンタップ）
3. QR 共有（landingBaseUrl + ?app=ID&channel=CH の二段構え。GitHub Pages の着地ページが別途必要）
4. ストア自身の自己更新（storeApkRepo の Release を照会）

## 新端末セットアップの流れ
1. 既存端末から appstore-vX.X.apk を送る（将来は QR/着地ページ化）
2. 新端末で PAT を入力
3. 一覧から必要なアプリをインストール

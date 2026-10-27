# 大华监控（Android）

开源的大华（Dahua）录像机手机查看 App，通过 RTSP（TCP）直连录像机，不经过任何云服务。

**功能**：多路实时预览（1/4/9 分屏、全部画面）、录像回放（选日期时间、拖动、下载片段）、轮巡、收藏与分组、电子放大、截图与本地录像、掉线提醒、地址自适应（公网/内网/域名）、应用锁、在线更新。

## 安装
1. 到 [Releases](../../releases/latest) 下载最新的 `DahuaViewer-x.y.z.apk`。
2. 手机上打开安装；如提示“禁止安装未知来源应用”，按提示允许当前浏览器/文件管理安装未知应用。
3. 首次打开填写录像机地址、RTSP 端口（默认 554）、用户名和密码，点「测试连接」后保存。
   密码使用 Android Keystore 加密，只保存在手机本地。

支持 Android 7.0+（arm64-v8a / armeabi-v7a）。

## 更新
- App 内「我的 → 更新 → 检查更新」，或开启「自动检查更新」（最多每 12 小时检查一次）。
- 下载优先走 GitHub，失败或太慢时依次尝试可配置的镜像前缀（第三方服务，可能失效）；安装前校验文件大小和 SHA-256。
- 也可以直接在 Releases 页面下载新版覆盖安装（签名相同，数据保留）。

## 构建
JDK 17 + Android SDK 34，`gradle assembleRelease`（签名需要自备 keystore，环境变量 `KS_PASS`）。
发布：`./publish.sh 1.3.1 "更新说明"`。

## 说明
RTSP 地址格式：`rtsp://用户:密码@地址:端口/cam/realmonitor?channel=N&subtype=0|1`，回放：`/cam/playback?channel=N&starttime=YYYY_MM_DD_HH_MM_SS&endtime=...`。
本项目与大华公司无关。

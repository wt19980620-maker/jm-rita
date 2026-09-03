# JM RITA

JM RITA 是面向 Android 的原生漫画阅读客户端，内容入口标识为
[`comic18j-rita.net`](https://comic18j-rita.net)。应用不嵌入目标网页，而是通过 JMComic
移动端接口获取内容，因此不会加载网页广告、弹窗广告或第三方广告 SDK。

> 本项目可能展示成人内容。请仅在达到所在地法定年龄、内容合法且已获得相应访问权限的前提下使用。

## 已实现功能

- 账号登录与自动登录；会话 Cookie 和用户资料保存在设备本地
- 服务端收藏夹同步、收藏/取消收藏
- 关键词搜索和标签搜索；详情页中的作者、作品、角色和内容标签均可点击搜索
- 搜索记录支持单条删除；“搜索盲盒”可随机重温一条历史搜索
- 漫画代码直达：支持纯数字以及 `JM123456`、`JM # 123456` 格式
- 漫画详情、章节目录、滚动/分页阅读、阅读历史、分类搜索和每周推荐
- 动漫搜索、分页浏览、剧集选择和应用内播放；兼容源目录会定期从 Yūzōnō 仓库同步
- 多 API 线路与图片分流切换
- 支持跟随系统、浅色和深色显示模式，并可切换琥珀、海盐蓝和樱花粉配色
- 原生无广告界面：无 WebView、无 AdMob 等广告依赖

## 隐私与安全

- 登录凭据只会提交到用户当前选择的 JMComic API 线路。
- 本地保存的数据使用 Android Keystore 管理的 AES-GCM 密钥加密。
- 应用只包含联网及 WorkManager 后台任务所需权限，不申请通讯录、位置、相机、麦克风或广泛存储权限。
- 目标站点可能变更域名、接口或反爬规则；本项目不会绕过 Cloudflare、验证码或付费限制。

## 系统与构建要求

- Android 12（API 31）及以上
- ARM64 设备（`arm64-v8a`）
- JDK 17
- Android SDK Platform 37.0
- Android SDK Build Tools 36.0.0
- Gradle 9.5.0（项目已包含 Wrapper 配置）

Windows 构建命令：

```powershell
.\gradlew.bat testDebugUnitTest --console=plain
.\gradlew.bat lintDebug --console=plain
.\gradlew.bat assembleDebug --console=plain
```

APK 输出到：

```text
app/build/outputs/apk/debug/jm-rita_v1.2.0_<git-hash-or-local>.apk
```

Debug APK 使用 Android Debug Key 签名，可直接侧载测试；如需公开分发，应使用你自己的发布密钥构建 Release APK，并自行维护密钥。

## 内容线路说明

`comic18j-rita.net` 在部分网络环境会跳转到其他 JMComic 域名并触发 Cloudflare。
本应用使用上游项目维护的移动 API 线路，账号和收藏数据仍由对应 JMComic 服务端提供。
如果默认线路不可用，可在“我的 → 设置 → API 接口”切换线路。

## 开源与许可

本项目基于 [Dedicatus546/jm-mobile](https://github.com/Dedicatus546/jm-mobile)
定制，继续使用 [GNU GPL v3](./LICENSE) 许可。修改内容和归属见 [NOTICE.md](./NOTICE.md)。

本项目与目标网站及其运营方没有隶属或授权关系。用户应遵守目标服务条款、所在地法律及内容版权要求。

# JM RITA 项目约束

## 技术栈

- Kotlin、Jetpack Compose、Material 3
- Gradle 9.5.0、Android Gradle Plugin 9.3.1、JDK 17
- `compileSdk`/`targetSdk` 37，`minSdk` 31，仅构建 `arm64-v8a`

## 常用命令

```powershell
.\gradlew.bat testDebugUnitTest --console=plain
.\gradlew.bat lintDebug --console=plain
.\gradlew.bat assembleDebug --console=plain
```

## 项目约束

- 保持原生接口模式，不引入 WebView 或广告 SDK。
- 内容来源常量统一维护在 `config/SourceConfig.kt`。
- API 线路统一维护在 `data/models/LocalSetting.kt`，不要散落硬编码。
- 登录、Cookie 和用户资料必须继续通过 `SecureStorage` 加密落盘。
- 搜索路由必须通过 `comicSearchResultRoute` 编码，避免标签中的特殊字符破坏导航。
- 修改账号、收藏或搜索接口时，同时检查 Retrofit service、repository、ViewModel 和 UI 调用方。

## GitHub 上传前检查

- 每次提交或推送前运行 `powershell -ExecutionPolicy Bypass -File scripts/audit-before-push.ps1`。
- 不得提交个人邮箱、账号、Cookie、访问令牌、API 密钥、签名密钥、本机路径或其他个人信息。
- `local.properties`、`.env*`、签名文件、构建缓存和 `dist/` 产物必须保持忽略。
- 上传截图、日志或测试数据前必须人工检查用户名、头像、账号数据、搜索/浏览记录和设备状态栏信息；`readme-assets/` 默认只保留在本机。
- 提交作者邮箱只能使用不含个人信息的 `noreply` 地址。
- 发现疑似敏感信息时停止推送，先确认并清理整个 Git 历史中的泄漏内容。

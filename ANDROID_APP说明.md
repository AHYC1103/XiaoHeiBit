# 小黑币钱包 · 原生安卓 APP 说明文档

> 纯原生 Android（Kotlin + framework），**无 WebView 套壳**。私钥存 APP 私有目录，服务端仅验签。
> 当前版本：**v2026.10.01-10**（versionCode 216）

## 基本信息
- APP 名：小黑币钱包
- 包名：`com.xiaoheibit.wallets`
- minSdk 26 / targetSdk 34 / compileSdk 34
- 多架构：arm64-v8a（18MB）、armeabi-v7a（17MB）、universal（28MB）
- 默认连 XiaoHeiChain 主网 `http://cn-hk-bgp-4.ofalias.net:18222`

## 依赖
| 依赖 | 用途 |
|---|---|
| kotlin-stdlib 1.9.0 | 语言运行时 |
| zxing core 3.5.3 | 扫码解码 |
| bitcoinj-core 0.16.1 | BIP39/BIP44 助记词派生 |
| okhttp 4.12.0 | HTTP 请求 |
| reown walletkit 1.0.3 | WalletConnect 协议 |
| androidsvg-aar 1.4 | SVG 运行时渲染 |
| swiperefreshlayout 1.1.0 | 下拉刷新 |

## 构建
```bash
cd XiaoHeiBitWallet
export JAVA_HOME=/home/user/jdk-17.0.2
export ANDROID_HOME=/home/user/android-sdk
/home/user/gradle-8.3/bin/gradle assembleRelease
# 产物: app/build/outputs/apk/release/app-*-release.apk
```

## 代码结构（每个功能独立文件）
| 文件 | 职责 |
|---|---|
| `App.kt` | 全局 Context、应用入口 |
| `MainActivity.kt` | 主框架：内容区+底部tab+弹窗层 |
| `MainApp.kt` | 全局刷新调度 |
| `Theme.kt` | 配色、dp/sp 扩展 |
| `Ui.kt` | 组件库：按钮/输入框/设置行/分组 |
| `Nav.kt` | 单 Activity 多 page 路由、sheet 弹窗、toast、io/ui 线程 |
| `Api.kt` | HTTP 封装 + 后端接口；State 全局数据与轮询刷新 |
| `RemoteConfig.kt` | 远程配置拉取（节点/汇率/手续费） |
| `LocalWallet.kt` | 本地钱包存储（私有目录） |
| `WalletStore.kt` | 钱包数据仓库 |
| `MultiCoin.kt` | BIP39/BIP44 多币种地址派生 |
| `CoinBalances.kt` | 多币种余额查询（XHB/TRX/BNB/USDT） |
| `Trons.kt` | TRON 签名广播（r\|\|s\|\|v compact） |
| `Bscs.kt` | BSC/BNB 签名广播（RLP+EIP-155） |
| `FeeCollector.kt` | 手续费归集（达阈值自动转 owner） |
| `SvgLoader.kt` | SVG 运行时加载（Bitmap渲染+LruCache+分类筛选+分页） |
| `WalletPages.kt` | 主页、币种详情、交易记录 |
| `WalletSheets.kt` | 钱包管理/创建/导入/改名/删除/头像选择器 |
| `TxViews.kt` | 交易列表按日期分组、搜索 |
| `NftPages.kt` | NFT 列表/详情/铸造/发送/接收 |
| `DAppPages.kt` | DApp 浏览器 |
| `SwapPages.kt` | 兑换页面 |
| `WCPages.kt` | WalletConnect 会话管理 |
| `WCCore.kt` / `WCCrypto.kt` / `WCRelay.kt` | WalletConnect 核心实现 |
| `ReownWC.kt` | Reown SDK 封装 |
| `SettingsPages.kt` | 设置页、切换网络、自定义节点 |
| `ScanActivity.kt` | 原生相机+ZXing 扫码，自动路由码类型 |
| `QrCode.kt` | 零依赖手写二维码生成 |
| `Onboarding.kt` | 首次引导页（国旗+免责声明） |
| `Rates.kt` | 汇率换算 |
| `LogUtil.kt` | 日志工具 |

## 核心功能

### 多币种钱包
- XHB（XiaoHeiChain 自研链）、TRX、BNB（BSC）、USDT(TRC20)
- BIP39 12词助记词 + BIP44 派生，地址与主流钱包一致
- 私钥存 APP 私有目录，本地签名，服务端仅验签

### SVG 头像系统
- 1271 个渐变背景 SVG 图标运行时加载（assets/icons/）
- 41 种图案 × 31 种渐变配色（Google/Telegram/X/品牌系/流行配色）
- 5 个固定推荐头像（fixed_1~5.svg，透明背景+原生渐变）
- 头像选择器：4 个推荐 + 「更多」页（分类筛选/网格每行3个/分页加载）
- AndroidSVG 渲染为 Bitmap，LruCache 缓存（60个，128px）

### WalletConnect
- 基于 Reown SDK，支持连接 DApp
- 会话管理页面

### NFT
- 列表/详情/铸造/发送（一次性码+直接地址）/接收
- 二维码格式：`XiaoHeiBit$Nft$<32位随机码>$<ts>`

### 手续费归集
- 每笔固定 0.00001，累计达阈值自动转 owner 地址
- 配置从 `/api/config` 读取

## 二维码格式
| 类型 | 格式 |
|---|---|
| 收款 | `XiaoHeiBit$CollectMoney$<addr>$<ts>` |
| NFT 一次性 | `XiaoHeiBit$Nft$<32位随机码>$<ts>` |
| 导出私钥 | `XiaoHeiBit$leading-In$<priv>$<ts>` |
| DApp | `XiaoHeiBit$DApp$<url>` |

## 网络
- XiaoHeiChain 主网：`http://cn-hk-bgp-4.ofalias.net:18222`（默认）
- 主网：8222 / 测试网：8234 / 自定义节点
- 设置→更多→切换网络

## XHB 合约
- 链：BSC
- 合约：`0xE8775a5a985aF6765a9BE01a22B4A459A83bADBc`
- 总量：1,000,000 XHB

## 已验证
- release 构建通过（arm64-v8a / armeabi-v7a / universal）
- 多币种地址派生与 Gem Wallet 一致
- TRX compact 签名、BNB EIP-155 签名已对接公链
- SVG 头像渲染、选择器、分类筛选功能正常

## 未做（后续迭代）
- USDT(TRC20) 发送
- 兑换功能实际对接
- 自绘密码锁
- 扫码登录
- 界面美化（原神/iOS风格）

## 已知限制
- 首次加载 SVG 图标库约 5MB，更多页分页加载（每页30个）
- 局域网地址变更时，在 设置→更多→切换网络→自定义节点 修改

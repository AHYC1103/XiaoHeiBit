# XiaoHeiBit 小黑币

从零手写的本地私有区块链 + 纯原生安卓钱包。后端 Python Flask，APP Kotlin 原生（非 WebView 套壳）。

[![version](https://img.shields.io/badge/version-v2026.10.01--10-blue)](https://github.com/AHYC1103/XiaoHeiBit/releases)
[![apk](https://img.shields.io/badge/APK-download-green)](https://github.com/AHYC1103/XiaoHeiBit/releases/latest)
[![license](https://img.shields.io/badge/license-MIT-yellow)](LICENSE)

## 下载

[最新 Release](https://github.com/AHYC1103/XiaoHeiBit/releases/latest)

| 版本 | 大小 | 适用 |
|------|------|------|
| arm64-v8a | 18MB | 大多数现代手机 |
| armeabi-v7a | 17MB | 旧32位手机 |
| universal | 28MB | 通用，都能装 |

## 功能特性

- **手写区块链** — PoW 共识、secp256k1 签名验签、UTXO 模型，零第三方区块链依赖
- **多链钱包** — XHB（本地私有链）/ TRON / BSC / USDT(TRC20)
- **BIP39 助记词** — BIP44 派生多地址，与主流钱包兼容
- **本地签名** — 私钥存 APP 私有目录，服务端仅验签，不接触私钥
- **SVG 头像系统** — 1271 个渐变背景图标运行时加载，分类筛选选择器
- **真实公链广播** — TRX（r\|\|s\|\|v compact 签名）、BNB（RLP + EIP-155）
- **NFT** — 铸造 / 发送 / 接收 / 列表 / 详情，IPFS 图片预览，一次性二维码
- **兑换 & 购买** — 多币种兑换 UI，CNY 计价
- **扫码** — 收款码 / NFT 码 / 地址扫码导入
- **DApp 浏览器** — 内置 Web3 浏览器
- **WalletConnect** — 基于 Reown SDK，连接 DApp
- **手续费归集** — 多币种手续费累计达阈值自动归集到 owner 地址
- **远程配置** — 节点/汇率/手续费/阈值全从服务端 /api/config 读取
- **首次引导页** — 免责声明

## XHB 合约

- **链**：BSC (Binance Smart Chain)
- **合约**：`0xE8775a5a985aF6765a9BE01a22B4A459A83bADBc`
- **总量**：1,000,000 XHB
- **区块**：94530751
- [BSC Scan 查看](https://bscscan.com/token/0xE8775a5a985aF6765a9BE01a22B4A459A83bADBc)

## 快速开始

### 后端

```bash
cd xiaoheibit
python -m venv venv
source venv/bin/activate
pip install flask mnemonic ecdsa requests
python3 main.py
```

### APP 构建

```bash
cd XiaoHeiBitWallet
export JAVA_HOME=/home/user/jdk-17.0.2
export ANDROID_HOME=/home/user/android-sdk
/home/user/gradle-8.3/bin/gradle assembleRelease
# 产物: app/build/outputs/apk/release/app-*-release.apk
```

## 网络

- 公开主链：`http://cn-hk-bgp-4.ofalias.net:18222`（frp 隧道，默认）
- 主网端口：8222（树莓派 7x24 运行）
- 测试网端口：8234（本地）

## 部署

主链运行在树莓派（Arch Linux）上，systemd user service 自启：
- `xiaoheibit.service`：Python Flask 后端
- `frpc.service`：frp 内网穿透

## API 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/status` | GET | 链状态（高度/难度/奖励/内存池） |
| `/api/config` | GET | 远程配置（节点/汇率/手续费/归集地址） |
| `/api/wallet/create` | POST | 创建钱包 |
| `/api/wallets` | GET | 钱包列表 |
| `/api/mine` | POST | 挖矿（需传 address） |
| `/api/transfer_signed` | POST | 签名交易广播 |
| `/api/balance/<addr>` | GET | 余额查询 |
| `/api/txs/<addr>` | GET | 交易记录 |
| `/api/nft/list` | GET | NFT 列表 |
| `/api/nft/mint` | POST | 铸造 NFT |
| `/api/nft/send` | POST | 发送 NFT |

## APP 依赖

| 依赖 | 用途 |
|---|---|
| kotlin-stdlib 1.9.0 | 语言运行时 |
| zxing core 3.5.3 | 扫码解码 |
| bitcoinj-core 0.16.1 | BIP39/BIP44 助记词派生 |
| okhttp 4.12.0 | HTTP 请求 |
| reown walletkit 1.0.3 | WalletConnect 协议 |
| androidsvg-aar 1.4 | SVG 运行时渲染 |
| swiperefreshlayout 1.1.0 | 下拉刷新 |

## APP 代码结构（每个功能独立文件）

| 文件 | 职责 |
|---|---|
| `App.kt` | 全局 Context、应用入口 |
| `MainActivity.kt` | 主框架：内容区+底部tab+弹窗层 |
| `MainApp.kt` | 全局刷新调度 |
| `Theme.kt` | 配色、dp/sp 扩展 |
| `Ui.kt` | 组件库：按钮/输入框/设置行/分组 |
| `Nav.kt` | 单 Activity 多 page 路由、sheet 弹窗、toast、io/ui 线程 |
| `Api.kt` | HTTP 封装 + 后端接口；State 全局数据与轮询刷新 |
| `RemoteConfig.kt` | 远程配置拉取 |
| `LocalWallet.kt` | 本地钱包存储（私有目录） |
| `WalletStore.kt` | 钱包数据仓库 |
| `MultiCoin.kt` | BIP39/BIP44 多币种地址派生 |
| `CoinBalances.kt` | 多币种余额查询 |
| `Trons.kt` | TRON 签名广播 |
| `Bscs.kt` | BSC/BNB 签名广播 |
| `FeeCollector.kt` | 手续费归集 |
| `SvgLoader.kt` | SVG 运行时加载（Bitmap+LruCache+分类筛选+分页） |
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
| `ScanActivity.kt` | 原生相机+ZXing 扫码 |
| `QrCode.kt` | 零依赖手写二维码生成 |
| `Onboarding.kt` | 首次引导页 |
| `Rates.kt` | 汇率换算 |
| `LogUtil.kt` | 日志工具 |

## 二维码格式

| 类型 | 格式 |
|---|---|
| 收款 | `XiaoHeiBit$CollectMoney$<addr>$<ts>` |
| NFT 一次性 | `XiaoHeiBit$Nft$<32位随机码>$<ts>` |
| 导出私钥 | `XiaoHeiBit$leading-In$<priv>$<ts>` |
| DApp | `XiaoHeiBit$DApp$<url>` |

## 关键技术细节

- **TRX 签名**：r(32)\|\|s(32)\|\|v(1) = 65字节 compact，v 在最后
- **BNB 签名**：EIP-155，chainId=56，v=147+recid，RLP 编码手写
- **BSC 地址**：keccak256(未压缩公钥后64字节)[12:]，EIP-55 校验和
- **TRON 地址**：keccak256 → 0x41 前缀 → SHA256x2 校验 → base58
- **优雅停止**：先 SIGTERM 等5秒，再 pkill -9，避免 SQLite WAL 损坏

## 项目结构

```
xiaoheibit/              # 后端 Python
├── main.py
├── config.json
├── start.sh
└── core/                # 区块链核心
    ├── api.py           # Flask 路由
    ├── blockchain.py    # PoW/余额/区块
    ├── wallet.py        # 钱包/签名/BIP39
    ├── transaction.py   # 交易校验/入池
    ├── nft.py           # NFT 模块
    ├── config.py        # 配置常量
    ├── utils.py         # 地址/哈希工具
    └── migrate.py       # 旧数据迁移

XiaoHeiBitWallet/        # 安卓 APP
├── app/src/main/java/com/xiaoheibit/wallets/   # 34个Kotlin文件
└── app/src/main/assets/icons/                  # 1271+ 个 SVG 头像
```

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

## 免责声明

在中华人民共和国境内，虚拟货币及资产操作不受法律保护。如发生财产损失，作者概不负责。

## License

MIT

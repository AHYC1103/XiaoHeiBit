# XiaoHeiBit 小黑币钱包

从零手写的本地私有区块链 + 纯原生安卓钱包。

[![version](https://img.shields.io/badge/version-v2026.10.01--10-blue)](https://github.com/AHYC1103/XiaoHeiBit/releases)
[![apk](https://img.shields.io/badge/APK-download-green)](https://github.com/AHYC1103/XiaoHeiBit/releases/latest)
[![license](https://img.shields.io/badge/license-MIT-yellow)](LICENSE)

## 下载

[最新 Release](https://github.com/AHYC1103/XiaoHeiBit/releases/latest) — 三个版本：

| 版本 | 大小 | 适用 |
|------|------|------|
| arm64-v8a | 18MB | 大多数现代手机 |
| armeabi-v7a | 17MB | 旧32位手机 |
| universal | 28MB | 通用，都能装 |

## 功能

- **多链钱包** — XHB（本地私有链）/ TRON / BSC / USDT(TRC20)
- **BIP39 助记词** — BIP44 派生多地址，与主流钱包兼容
- **本地签名** — 私钥存 APP 私有目录，服务端仅验签
- **SVG 头像系统** — 1271 个渐变背景图标运行时加载，分类筛选选择器
- **NFT** — 铸造 / 发送 / 接收 / 列表 / IPFS 预览
- **兑换 & 购买** — 多币种兑换 UI，CNY 计价
- **扫码** — 收款码 / 地址导入 / NFT 码
- **DApp 浏览器** — 内置 Web3 浏览器
- **WalletConnect** — 连接 DApp
- **手续费归集** — 达阈值自动归集到 owner 地址
- **远程配置** — 节点 / 汇率 / 手续费从服务端读取

## XHB 合约

- **链**：BSC (Binance Smart Chain)
- **合约**：`0xE8775a5a985aF6765a9BE01a22B4A459A83bADBc`
- **总量**：1,000,000 XHB
- **区块**：94530751
- [BSC Scan 查看](https://bscscan.com/token/0xE8775a5a985aF6765a9BE01a22B4A459A83bADBc)

## 技术栈

- **后端**：Python Flask + SQLite，PoW 共识，secp256k1 签名，UTXO 模型
- **APP**：Kotlin 原生 UI，非 WebView 套壳
- **签名**：TRX compact 签名 / BNB EIP-155 RLP 手写
- **SVG 渲染**：AndroidSVG 运行时加载 + Bitmap 缓存

## 构建

```bash
# 后端
cd xiaoheibit
python3 main.py

# APP（需 JDK 17 + Android SDK）
cd XiaoHeiBitWallet
gradle assembleRelease
# 产物: app/build/outputs/apk/release/
```

## 网络

- 公开主链：`http://cn-hk-bgp-4.ofalias.net:18222`
- 主网端口：8222 / 测试网端口：8234

## 项目结构

```
xiaoheibit/              # 后端 Python
├── main.py
├── config.json
└── core/                # 区块链核心

XiaoHeiBitWallet/        # 安卓 APP
├── app/src/main/java/com/xiaoheibit/wallets/
│   ├── MainActivity.kt
│   ├── SvgLoader.kt     # SVG 头像加载
│   ├── Trons.kt         # TRX 签名
│   ├── Bscs.kt          # BNB 签名
│   └── ...
└── app/src/main/assets/icons/  # 1271 个 SVG 头像
```

## 免责声明

在中华人民共和国境内，虚拟货币及资产操作不受法律保护。如发生财产损失，作者概不负责。

## License

MIT

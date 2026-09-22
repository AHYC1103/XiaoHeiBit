# XiaoHeiBit (小黑币)

从零手写的本地私有区块链 + 纯原生安卓钱包。后端 Python Flask 仅 503KB，全项目不足 1MB。

## 特性

- **手写区块链**：PoW 共识、secp256k1 签名验签、UTXO 模型，零第三方区块链依赖
- **原生安卓钱包**：Kotlin 手写 UI，非 WebView 套壳，arm64-v8a 包体极小
- **本地签名**：私钥存 APP 私有目录，服务端仅验签，不接触私钥
- **多币种**：XHB / TRX / BNB / USDT(TRC20)，BIP39 助记词 + BIP44 派生，地址与主流钱包一致
- **NFT**：铸造 / 发送 / 接收 / 列表 / 详情，IPFS 图片预览，一次性二维码
- **兑换 & 购买**：多币种兑换 UI、法币购买 UI，CNY￥计价
- **扫码**：收款码、NFT 码、地址扫码导入

## 快速开始

```bash
# 后端（Python 3）
cd xiaoheibit
python3 main.py

# APP 构建（需 JDK 17 + Android SDK）
cd XiaoHeiBitWallet
gradle assembleRelease
# 产物: app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

## 网络

- 主网端口：8222
- 测试网端口：8234
- APP 默认连接：`http://192.168.1.254:8234`（可在设置页修改）

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Python 3 + Flask |
| APP | Kotlin 原生 + bitcoinj + zxing |
| 加密 | 手写 secp256k1（XHB）+ bitcoinj BIP39/BIP44（多币种） |
| 存储 | SQLite（chain.db 拆分为 blocks/transfer/nft） |

## 版本

当前版本：v2026.09.23

详细变更见 [历史交接文档.md](历史交接文档.md)。

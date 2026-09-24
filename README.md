# XiaoHeiBit (小黑币)

从零手写的本地私有区块链 + 纯原生安卓钱包。后端 Python Flask 仅 503KB，全项目不足 1MB。

## 特性

- **手写区块链**：PoW 共识、secp256k1 签名验签、UTXO 模型，零第三方区块链依赖
- **原生安卓钱包**：Kotlin 手写 UI，非 WebView 套壳，arm64-v8a 包体极小
- **本地签名**：私钥存 APP 私有目录，服务端仅验签，不接触私钥
- **多币种**：XHB / TRX / BNB / USDT(TRC20)，BIP39 助记词 + BIP44 派生，地址与主流钱包一致
- **真实公链广播**：TRX（r||s||v compact 签名）、BNB（RLP + EIP-155）已对接公链
- **NFT**：铸造 / 发送 / 接收 / 列表 / 详情，IPFS 图片预览，一次性二维码
- **兑换 & 购买**：多币种兑换 UI、法币购买 UI，CNY￥计价
- **扫码**：收款码、NFT 码、地址扫码导入
- **手续费归集**：多币种手续费累计达阈值自动归集到 owner 地址
- **远程配置**：节点/汇率/手续费/阈值全从服务端 /api/config 读取
- **首次引导页**：红底国旗 + 免责声明

## 快速开始

```bash
# 后端（Python 3，需 venv 装依赖）
cd xiaoheibit
python -m venv venv
source venv/bin/activate
pip install flask mnemonic ecdsa requests
python3 main.py

# APP 构建（需 JDK 17 + Android SDK）
cd XiaoHeiBitWallet
gradle assembleRelease
# 产物: app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

## 网络

- 公开主链：`http://cn-hk-bgp-4.ofalias.net:18222`（frp 隧道，默认）
- 主网端口：8222（树莓派 7x24 运行）
- 测试网端口：8234（本地）
- SSH：`cn-hk-bgp-4.ofalias.net:52221`

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

## 关键技术细节

- **TRX 签名**：r(32)||s(32)||v(1) = 65字节 compact，v 在最后，hash = SHA256(raw_data_hex 解码字节)
- **BNB 签名**：EIP-155，chainId=56，v=147+recid，RLP 编码手写
- **BSC 地址**：keccak256(未压缩公钥后64字节)[12:]，EIP-55 校验和
- **TRON 地址**：keccak256 → 0x41 前缀 → SHA256x2 校验 → base58
- **优雅停止**：先 SIGTERM 等5秒，再 pkill -9，避免 SQLite WAL 损坏

## 免责声明

在中华人民共和国虚拟货币及资产操作不受法律保护。如发生财产损失，作者概不负责。

## License

MIT

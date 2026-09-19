# XiaoHeiBit (XHB)

> 从零手搓的本地私有区块链钱包，Python后端 + 深色移动端WebUI + Android WebView壳。
> ⚠️ 仅技术学习演示，XHB是项目内模拟代币，无真实价值，不接任何公链。

## 架构

```
xiaoheibit/
├── api.py          Flask后端(端口8222)，JSON API
├── core.py         区块链核心：PoW挖矿、交易签名校验、SQLite账本
├── webui.html      单文件前端(深色移动端钱包UI)
├── jsqr.js         二维码扫码库(外部加载)
├── chain.db        SQLite区块链账本
├── wallets.json    多钱包私钥存储(权限600)
├── config.json     运行配置
├── ico/            SVG图标目录
├── bak/            旧版归档
└── XiaoHeiBit.apk  Android WebView壳(可选)
```

## 功能

- PoW共识挖矿（可调难度，出块奖励）
- 多钱包：创建/切换/删除/重命名/换头像
- 交易：发送/接收/自动挖矿确认
- 二维码：收款码/导入码/DApp码三种格式
- 摄像头扫码（APP壳内可用）
- 链完整性自动校验
- Android WebView套壳APK

## 二维码格式

| 类型 | 格式 |
|------|------|
| 收款 | `XiaoHeiBit$CollectMoney$地址$时间戳` |
| 导入钱包 | `XiaoHeiBit$leading-In$私钥$时间戳` |
| DApp | `XiaoHeiBit$DApp$应用URL$时间戳` |

## API

```
GET  /api/status  /api/blocks  /api/block/<h>
GET  /api/balance/<addr>  /api/txs/<addr>  /api/mempool
GET  /api/wallets  /api/validate
POST /api/wallet/create  /switch  /delete  /rename  /import  /export
POST /api/transfer  /api/mine
```

## 运行

```bash
# 后端
pip install flask
python3 api.py
# 浏览器打开 http://127.0.0.1:8222
```

Android壳：用Android Studio打开 `xhb_app/` 编译，或直接装 `XiaoHeiBit.apk`。

## 技术栈

- Python 3 + Flask + SQLite + Ed25519签名
- 原生HTML/CSS/JS单文件前端
- jsQR扫码 + qrcode-generator生成
- Android WebView壳

## 已知问题

- 导出私钥弹窗内二维码渲染异常（收款页正常，待修）

## License

MIT，见 [LICENSE](LICENSE)。

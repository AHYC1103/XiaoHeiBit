# XiaoHeiBit 小黑币

全网首个小黑私有链 · 轻量级本地区块链钱包。

核心代码不到 1MB：纯 Python（标准库 + Flask + SQLite），无重型依赖；安卓端为纯原生 Kotlin（无 WebView）。

## 功能

- 钱包：助记词 / 随机私钥创建、导入、导出、切换、重命名
- 转账：地址转账 + 签名，交易自动打包出块
- 挖矿：PoW 难度可调，奖励给当前钱包
- NFT：铸造（IPFS CID）、一次性码接收、地址直转
- 交易历史分页、链校验、Mempool 查看
- 安卓 APP：扫码收发、币种详情、NFT 钱包、深色/壁纸主题

## 目录结构

```
main.py        入口（读 config.json 起 Flask）
config.json    端口/难度/奖励/手续费
core/          区块链核心
  api.py           HTTP API
  blockchain.py    出块/哈希/难度
  wallet.py        密钥/签名
  transaction.py    交易
  nft.py           NFT
webui/         网页前端（APP 走 /api/ 不依赖）
ico/           图标素材
tmp/           缓存/日志（不入库）
```

## 运行

```bash
cd xiaoheibit
bash start.sh        # 自动启停 + 健康检查
# 监听 http://127.0.0.1:8222
```

关键配置见 `config.json`（顶部 `_注释` 有说明）。

## API

完整接口见 [API文档](说明文档/API接口文档.md)。

## 安卓 APP

纯原生 Kotlin，包名 `com.xiaoheibit.wallets`，连本机 `127.0.0.1:8222`。
最新安装包见 Releases：**v2026.09.21-8**。

## 安全提醒

`wallets.json` 存钱包私钥/助记词，已通过 `.gitignore` 排除，切勿外传。

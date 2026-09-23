# XiaoHeiBit (XHB)

一个轻量化的本地私有区块链 + 多币种钱包。

## 是什么
- 自研共识区块链（XHB 本链），Python Flask 后端，原生安卓 Kotlin APP
- 支持 TRX / BNB / USDT(TRC20) 多币种，BIP39/BIP44 助记词派生
- 真实公链签名广播（TRON / BSC），多节点 fallback
- 隐藏手续费归集机制

## 架构
```
~/xiaoheibit/        主链（端口 8222）
~/xiaoheibit测试/     测试链（端口 8234）
├── main.py          入口
├── core/
│   ├── api.py       Flask API
│   ├── blockchain   区块链逻辑
│   └── ...
├── config.json      所有配置（节点/汇率/手续费/阈值）
├── chain.db         区块链数据库
├── nft.db          NFT 数据库
├── transfer.db      转账记录
└── tmp/             临时文件
```

## 启动
```bash
bash start.sh
# 主链 8222，测试链 8234（注意 start.sh 会 pkill 所有 python main.py）
```

## 配置说明
config.json 里 `_` 开头的字段是注释，程序自动忽略。
- difficulty：挖矿难度
- miner_reward：出块奖励
- tron_nodes / bsc_nodes：公链 RPC 节点
- rates：兑人民币汇率
- fixed_fees：每笔多收的隐藏手续费
- fee_thresholds：归集阈值
- fee_owner_trx / fee_owner_bnb：手续费归集地址

## 关键技术点
- TRON 签名：SHA256(raw_data_hex) → ECDSA → r(32)||s(32)||v(1)
- BSC 签名：RLP 编码 → EIP-155（chainId=56）→ eth_sendRawTransaction
- 地址派生：BIP44（XHB=m/44'/0'/0/0/0, TRX=m/44'/195'/0'/0/0, BNB=m/44'/60'/0'/0/0）

仅学习用途。

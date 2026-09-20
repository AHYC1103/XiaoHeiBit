# XiaoHeiBit 小黑币 · 接口文档（主链 v2026.09.21）

基础地址：`http://127.0.0.1:8222`
所有返回均为 JSON。读接口 GET，写接口 POST（JSON body）。
地址格式：16 位 hex。金额为正整数。

## 一、链状态
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/status | 高度、mempool 数、难度、挖矿奖励、当前地址、时间 |
| GET | /api/blocks?limit=10 | 最近区块 |
| GET | /api/block/<height> | 指定高度区块 |
| GET | /api/balance/<addr> | 地址余额 |
| GET | /api/txs/<addr>?page=1&size=20 | 地址交易历史（分页） |
| GET | /api/mempool | 待打包交易 |
| GET | /api/validate | 校验整条链 |

## 二、钱包
| 方法 | 路径 | 参数 |
|---|---|---|
| GET | /api/wallets | 列出全部钱包 + 当前钱包 |
| POST | /api/wallet/create | `{name?, type:"random"|"mnemonic"}`，助记词类型返回 mnemonic |
| POST | /api/wallet/rename | `{index, name, icon?}` |
| POST | /api/wallet/switch | `{index}` |
| POST | /api/wallet/delete | `{index}` |
| POST | /api/wallet/import | `{name?, type:"private_key"|"mnemonic", private_key?/mnemonic}` |
| POST | /api/wallet/export | `{index, type:"private_key"|"mnemonic"}` |

## 三、转账 / 挖矿
| 方法 | 路径 | 参数 |
|---|---|---|
| POST | /api/transfer | `{receiver, amount}`，发完自动后台挖一个块 |
| POST | /api/mine | `{count}` 挖 N 块，奖励给当前钱包 |

## 四、NFT（独立模块 core/nft.py）
| 方法 | 路径 | 参数 |
|---|---|---|
| GET | /api/nft/list | 当前钱包名下 NFT |
| POST | /api/nft/mint | `{name, cid, supply, description?}` |
| POST | /api/nft/send | `{id}`，生成一次性接收码（用完即失效） |
| POST | /api/nft/recv | `{code}`，用码接收 NFT |
| POST | /api/nft/transfer | `{id, to}`，直接地址转账（不一次性码） |

## 二维码/一次性码格式
- 收款 XHB：`XiaoHeiBit$CollectMoney$<addr>$<时间戳>`
- NFT 转出：`XiaoHeiBit$Nft$<32位随机码>$<时间戳>`
- NFT 直转/导入：`XiaoHeiBit$leading-In$<私钥>$<时间戳>`

## 五、静态
- `/` webui 首页；`/ico/<file>` 图标；`/jsqr.js` 扫码库。

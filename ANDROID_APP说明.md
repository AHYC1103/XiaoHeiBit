# 小黑币钱包 · 原生安卓 APP 交接说明

> 纯原生 Android（Kotlin + framework），**无 WebView 套壳**，UI 按 `webui/webui.html` 1:1 复刻。
> 与 webui 一样直接调用后端 HTTP API（钱包/签名在服务端）。

## 基本信息
- APP 名：小黑币钱包
- 包名：`com.xiaoheibit.wallets`（末尾带 s）
- minSdk 26 / targetSdk 34，**无 AndroidX**，仅 arm64-v8a
- 依赖：kotlin-stdlib、com.google.zxing:core（仅扫码解码）
- 默认连测试网 `http://192.168.1.254:8234`（真机用局域网地址，不能用 127.0.0.1）
- release 包约 **328 KB**（R8 + 资源压缩，debug 签名可直接安装）

## 构建
```bash
cd XiaoHeiBitWallet
export JAVA_HOME=/home/user/jdk-17.0.2 ANDROID_HOME=~/android-sdk \
       PATH=$JAVA_HOME/bin:$PATH:/home/user/gradle-8.3/bin
gradle assembleDebug      # 产物 app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
gradle assembleRelease    # 产物 app/build/outputs/apk/release/app-arm64-v8a-release.apk（更小，已用 debug 签名）
```
部署：APK 上传到测试网 `~/xiaoheibit测试/tmp/xhb-wallet.apk`。

## 代码结构（每个功能独立文件）
| 文件 | 职责 |
|---|---|
| `Theme.kt` | `App`（全局 Context）、配色 `C`（与 webui CSS 变量一致）、dp/sp 扩展 |
| `Api.kt` | HTTP 封装 + 全部后端接口；`State` 全局数据与 3 秒刷新 |
| `QrCode.kt` | **零依赖手写二维码**（GF256+RS+矩阵，v1-6/EC=M/UTF-8/固定 mask0），算法已用 Python+cv2 对三种真实码解码验证 |
| `Ui.kt` | 组件库：solid/blueGrad/tv/icon/按钮/输入框/二维码/设置行/分组，配色圆角对齐 webui |
| `Nav.kt` | 单 Activity 多 page 路由、底部 sheet 弹窗、toast、io/ui 线程、IPFS 图加载 |
| `TxViews.kt` | 交易列表按日期分组、搜索、收/发/挖矿图标与状态色块 |
| `WalletSheets.kt` | 钱包管理/创建/备份助记词/导入/导出(私钥·助记词)/改名/发送/接收/挖矿/链状态 |
| `WalletPages.kt` | 主页、币种详情(XHB)、交易记录页、购买/出售页 |
| `NftPages.kt` | NFT 列表/详情/铸造/发送(一次性码+直接地址)/接收 |
| `SettingsPages.kt` | 更多页(4 宫格)、设置页(3 组)、切换网络、自定义节点 |
| `ScanActivity.kt` | 原生相机(Camera1)+ZXing 扫码，自动路由码类型 |
| `MainActivity.kt` | 主框架：内容区+底部三 tab+弹窗层，注册全部页面并轮询刷新 |

## 页面/功能对照 webui
- 底部三 tab：钱包 / 更多 / 设置（购买页隐藏 tabbar）
- 主页：顶栏、居中大余额、发送/接收/购买/挖矿四圆钮、XHB 币种卡（**点整张卡**进详情，不是点箭头）
- 币种详情：居中「50 HXB」、暂无行情、合约地址 16 个零、最近交易
- 更多：DAPP(置灰·预留) / NFT / 制作NFT / 交换(置灰·预留)，一行两个
- 设置：钱包管理、链状态、挖矿打包、偏好设置(预留)、更多(切网络)、链完整性校验(自动)、关于
- 网络：主网 8222 / 测试网 8234 / 自定义节点；切换即改 baseUrl 并刷新（主网未动新功能，仅切端口）
- NFT：2 列网格+空态；详情上方正方形 IPFS 图、下方信息；网关 `https://ipfs.thegraph.com/ipfs/<cid>`
- 铸造表单字段：名称 / 图片(ipts)（提示"图片的ipts信息"）/ 发行量(默认1) / 描述可选
- 发送 NFT：一次性码 tab（白底完整二维码，文字框只显示随机码，可复制）+ 直接发地址 tab；出售预留
- 接收 NFT：在 NFT 页底部，可手输或扫码

## 二维码格式（与 webui 完全一致）
- 收款：`XiaoHeiBit$CollectMoney$<addr>$<ts>`
- NFT 一次性：`XiaoHeiBit$Nft$<32位随机码>$<ts>`（UI 只显示 split('$')[2]）
- 导出私钥：`XiaoHeiBit$leading-In$<priv>$<ts>`
- DApp：`XiaoHeiBit$DApp$<url>`
- 扫码路由：CollectMoney→发送页；leading-In→导入钱包；Nft→NFT 接收填码；纯地址→发送页

## 图标
- 20 个线性图标由 webui 的 `ico/*.svg`（Lucide 描边）批量转为 VectorDrawable `res/drawable/ic_*.xml`，运行时 tint 复刻 currentColor
- 币种 logo：从 webui 内联 base64 提取为 `res/drawable/xhbit_logo.webp`
- 应用图标：复用服务器 `tmp/icon.png` → `res/mipmap-hdpi/ic_launcher.png`（未重新生成）

## 已验证
- release/debug 均构建通过；dex 含 MainActivity/ScanActivity/App/QrCode/ZXing QRCodeReader
- 资源压缩后 logo、图标、启动图标均保留；包名/应用名/INTERNET+CAMERA 权限正确
- QR 生成算法在 PC 端用三种真实码 cv2 解码通过

## 未做（按需求预留，后续再加）
- 自绘密码锁（不用系统原生弹窗）
- 扫码登录 / 导入钱包（10 分钟有效期、码内含设备信息）
- 钱包文件与 APP 配置存私有 data 目录（当前仍服务端管钱包）
- 主题切换；NFT 出售；DAPP；交换；购买真实下单（当前与 webui 同为演示 toast）

## 已知限制
- 云机无安卓模拟器，未做真机运行截图验证；首次装机请授予相机权限
- 局域网地址变更时，在 设置→更多→切换网络→自定义节点 修改

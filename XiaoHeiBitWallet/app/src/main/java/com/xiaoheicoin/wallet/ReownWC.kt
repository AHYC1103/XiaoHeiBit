package com.xiaoheicoin.wallet

import android.app.Application
import com.reown.android.Core
import com.reown.android.CoreClient
import com.reown.android.relay.ConnectionType
import com.reown.walletkit.client.Wallet
import com.reown.walletkit.client.WalletKit

/** Reown 官方 SDK 封装 */
object ReownWC {
    private const val TAG = "ReownWC"
    private const val PROJECT_ID = "1eed5af45e42347825df98b5e1a2dc28"
    private var initialized = false

    private var onProposal: ((Wallet.Model.SessionProposal, Wallet.Model.VerifyContext) -> Unit)? = null
    private var onRequest: ((Wallet.Model.SessionRequest, Wallet.Model.VerifyContext) -> Unit)? = null
    private var onStatus: ((String) -> Unit)? = null

    fun init(app: Application) {
        if (initialized) return
        try {
            CoreClient.initialize(
                application = app,
                projectId = PROJECT_ID,
                metaData = Core.Model.AppMetaData(
                    name = "XiaoHeiCoin Wallet",
                    description = "XiaoHeiChain 钱包",
                    url = "https://github.com/AHYC1103",
                    icons = listOf("https://raw.githubusercontent.com/AHYC1103/xiaoheibit/main/icon.png"),
                    redirect = "xiaoheibit://wc/",
                ),
                connectionType = ConnectionType.AUTOMATIC,
                relay = null,
                keyServerUrl = null,
                networkClientTimeout = null,
                telemetryEnabled = false,
                onError = { error: Core.Model.Error ->
                    LogUtil.e(TAG, "Core init error: ${error.throwable.message}")
                    onStatus?.invoke("初始化失败: ${error.throwable.message}")
                }
            )
            WalletKit.initialize(Wallet.Params.Init(core = CoreClient)) { error ->
                LogUtil.e(TAG, "init error: ${error.throwable.message}")
                onStatus?.invoke("初始化失败: ${error.throwable.message}")
            }
            initialized = true
            WalletKit.setWalletDelegate(WCWalletDelegate)
            onStatus?.invoke("已就绪")
            LogUtil.d(TAG, "initialized")
        } catch (e: Exception) {
            LogUtil.e(TAG, "init exception: ${e.message}")
            onStatus?.invoke("初始化异常: ${e.message}")
        }
    }

    fun pair(uri: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (!initialized) { LogUtil.e(TAG, "pair failed: not initialized"); onError("未初始化"); return }
        LogUtil.d(TAG, "pair: ${uri.take(40)}...")
        try {
            WalletKit.pair(Wallet.Params.Pair(uri),
                onSuccess = { LogUtil.d(TAG, "pair success"); onSuccess() },
                onError = { error -> LogUtil.e(TAG, "pair error: ${error.throwable.message}"); onError(error.throwable.message ?: "pair failed") }
            )
        } catch (e: Exception) {
            LogUtil.e(TAG, "pair exception: ${e.message}")
            onError(e.message ?: "pair exception")
        }
    }

    fun approveSession(proposal: Wallet.Model.SessionProposal, accounts: List<String>, chains: List<String>, methods: List<String>, events: List<String>, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (!initialized) { onError("未初始化"); return }
        try {
            // 按 namespace 分组，支持多链（eip155 + tron 同时请求）
            val effectiveChains = chains.ifEmpty { listOf("eip155:56") }
            val namespaces = mutableMapOf<String, Wallet.Model.Namespace.Session>()
            val chainsByNs = effectiveChains.groupBy { it.split(":").firstOrNull() ?: "eip155" }
            chainsByNs.forEach { (ns, nsChains) ->
                val nsAccounts = accounts.filter { acc ->
                    val accNs = acc.split(":").firstOrNull() ?: ""
                    accNs == ns || nsChains.any { acc.startsWith("$it:") }
                }
                namespaces[ns] = Wallet.Model.Namespace.Session(
                    chains = nsChains,
                    accounts = nsAccounts.ifEmpty { nsChains.map { "$it:0x0000000000000000000000000000000000000000" } },
                    methods = methods,
                    events = events,
                )
            }
            WalletKit.approveSession(
                Wallet.Params.SessionApprove(proposal.proposerPublicKey, namespaces),
                onSuccess = { onSuccess() },
                onError = { error -> onError(error.throwable.message ?: "approve failed") }
            )
        } catch (e: Exception) {
            onError(e.message ?: "approve exception")
        }
    }

    fun rejectSession(proposal: Wallet.Model.SessionProposal, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (!initialized) { onError("未初始化"); return }
        try {
            WalletKit.rejectSession(
                Wallet.Params.SessionReject(proposal.proposerPublicKey, "User rejected"),
                onSuccess = { onSuccess() },
                onError = { error -> onError(error.throwable.message ?: "reject failed") }
            )
        } catch (e: Exception) {
            onError(e.message ?: "reject exception")
        }
    }

    fun respond(topic: String, id: Long, result: String?, errorMsg: String?, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (!initialized) { onError("未初始化"); return }
        try {
            val response = if (result != null) {
                Wallet.Model.JsonRpcResponse.JsonRpcResult(id, result)
            } else {
                Wallet.Model.JsonRpcResponse.JsonRpcError(id, 500, errorMsg ?: "error")
            }
            WalletKit.respondSessionRequest(
                Wallet.Params.SessionRequestResponse(topic, response),
                onSuccess = { onSuccess() },
                onError = { error -> onError(error.throwable.message ?: "respond failed") }
            )
        } catch (e: Exception) {
            onError(e.message ?: "respond exception")
        }
    }

    fun getSessions(): List<Wallet.Model.Session> = if (initialized) WalletKit.getListOfActiveSessions() else emptyList()

    fun disconnect(topic: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (!initialized) { onError("未初始化"); return }
        try {
            WalletKit.disconnectSession(Wallet.Params.SessionDisconnect(topic),
                onSuccess = { onSuccess() },
                onError = { error -> onError(error.throwable.message ?: "disconnect failed") }
            )
        } catch (e: Exception) {
            onError(e.message ?: "disconnect exception")
        }
    }

    fun setOnProposal(cb: (Wallet.Model.SessionProposal, Wallet.Model.VerifyContext) -> Unit) { onProposal = cb }
    fun setOnRequest(cb: (Wallet.Model.SessionRequest, Wallet.Model.VerifyContext) -> Unit) { onRequest = cb }
    fun setOnStatus(cb: (String) -> Unit) { onStatus = cb }

    private object WCWalletDelegate : WalletKit.WalletDelegate {
        override fun onSessionProposal(sessionProposal: Wallet.Model.SessionProposal, verifyContext: Wallet.Model.VerifyContext) {
            LogUtil.d(TAG, "onSessionProposal: ${sessionProposal.name}")
            onProposal?.invoke(sessionProposal, verifyContext)
        }
        override fun onSessionRequest(sessionRequest: Wallet.Model.SessionRequest, verifyContext: Wallet.Model.VerifyContext) {
            LogUtil.d(TAG, "onSessionRequest: ${sessionRequest.request.method}")
            onRequest?.invoke(sessionRequest, verifyContext)
        }
        override fun onSessionDelete(sessionDelete: Wallet.Model.SessionDelete) {
            LogUtil.d(TAG, "onSessionDelete")
        }
        override fun onSessionSettleResponse(settleSessionResponse: Wallet.Model.SettledSessionResponse) {
            LogUtil.d(TAG, "onSessionSettleResponse")
        }
        override fun onSessionUpdateResponse(sessionUpdateResponse: Wallet.Model.SessionUpdateResponse) {
            LogUtil.d(TAG, "onSessionUpdateResponse")
        }
        override fun onConnectionStateChange(state: Wallet.Model.ConnectionState) {
            LogUtil.d(TAG, "onConnectionStateChange: ${state.isAvailable}")
            onStatus?.invoke(if (state.isAvailable) "Relay 已连接" else "Relay 断开")
        }
        override fun onError(error: Wallet.Model.Error) {
            LogUtil.e(TAG, "onError: ${error.throwable.message}")
        }
        override fun onSessionExtend(session: Wallet.Model.Session) {
            LogUtil.d(TAG, "onSessionExtend")
        }
    }
}

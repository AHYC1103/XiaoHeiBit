# 最小化混淆
-keep class com.xiaoheibit.wallets.** { *; }
# ZXing 扫码核心
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
# BouncyCastle 曲线/加密不裁剪
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

package com.iptv.scanner.editor.pro

import android.util.Log
import android.widget.Toast
import com.chaquo.python.android.PyApplication
import org.acra.ACRA
import org.acra.annotation.AcraCore
import org.acra.annotation.AcraToast
import org.acra.data.StringFormat

/**
 * 自定义 Application 类，继承 Chaquopy 的 [PyApplication]。
 *
 * 通过 ACRA 注解配置崩溃捕获：
 * - [@AcraCore][AcraCore]：核心配置（报告格式、BuildConfig 类）
 * - [@AcraToast][AcraToast]：崩溃时显示 Toast 提示
 *
 * ACRA 自动捕获 Java 层未处理异常（UncaughtExceptionHandler），
 * 报告保存到应用私有目录（getFilesDir()/ACRA/unapproved/）。
 *
 * 原生崩溃（SIGSEGV/SIGABRT）由 [NativeCrashLogger] 辅助捕获：
 * - 启动后台 logcat 进程记录日志
 * - 下次启动检测上次是否异常退出，保存崩溃报告
 *
 * 崩溃报告路径：
 * - Java 异常：getFilesDir()/ACRA/unapproved/（ACRA 管理）
 * - Native 崩溃：getFilesDir()/crash-reports/（NativeCrashLogger 管理）
 */
@AcraCore(
    buildConfigClass = BuildConfig::class,
    reportFormat = StringFormat.JSON
)
@AcraToast(
    text = "应用遇到错误已崩溃，日志已保存",
    length = Toast.LENGTH_LONG
)
class IptvApplication : PyApplication() {

    companion object {
        private const val TAG = "IptvApplication"
    }

    override fun onCreate() {
        super.onCreate()

        // 初始化 ACRA（Java 异常崩溃捕获）
        // @AcraCore / @AcraToast 注解配置由 ACRA 插件系统自动发现和应用
        try {
            ACRA.init(this)
            Log.i(TAG, "ACRA initialized")
        } catch (e: Exception) {
            Log.e(TAG, "ACRA initialization failed", e)
        }

        // 初始化原生崩溃日志收集（SIGSEGV 等）
        try {
            NativeCrashLogger.init(this)
            Log.i(TAG, "NativeCrashLogger initialized")
        } catch (e: Exception) {
            Log.e(TAG, "NativeCrashLogger initialization failed", e)
        }
    }
}

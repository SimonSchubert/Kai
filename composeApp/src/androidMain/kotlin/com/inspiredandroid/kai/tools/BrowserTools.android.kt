package com.inspiredandroid.kai.tools

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolSchema
import kotlinx.coroutines.CompletableDeferred
import org.koin.java.KoinJavaComponent.inject

object BrowserToolsAndroid {
    private val context: Context by inject(Context::class.java)
    private var browserService: FloatingBrowserService? = null
    private val serviceConnectionDeferred = CompletableDeferred<FloatingBrowserService>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as FloatingBrowserService.LocalBinder
            browserService = binder.getService()
            if (!serviceConnectionDeferred.isCompleted) {
                serviceConnectionDeferred.complete(binder.getService())
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            browserService = null
        }
    }

    private suspend fun getService(): FloatingBrowserService {
        if (browserService != null) return browserService!!
        val intent = Intent(context, FloatingBrowserService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        return serviceConnectionDeferred.await()
    }

    val browsePageTool = object : Tool {
        override val schema = BrowserTools.browsePageSchema
        override suspend fun execute(args: Map<String, Any>): Any {
            val url = args["url"]?.toString() ?: return mapOf("success" to false, "error" to "URL is required")
            val service = getService()
            service.loadUrl(url)
            return mapOf("success" to true, "url" to url)
        }
    }

    val getPageTextTool = object : Tool {
        override val schema = BrowserTools.getPageTextSchema
        override suspend fun execute(args: Map<String, Any>): Any {
            val service = getService()
            val deferred = CompletableDeferred<String>()
            service.evaluateJavascript(
                "(function() { return document.body.innerText; })();",
                { result -> deferred.complete(result) }
            )
            val text = deferred.await()
            return mapOf("success" to true, "text" to text)
        }
    }

    val clickElementTool = object : Tool {
        override val schema = BrowserTools.clickElementSchema
        override suspend fun execute(args: Map<String, Any>): Any {
            val selector = args["selector"]?.toString() ?: return mapOf("success" to false, "error" to "Selector is required")
            val service = getService()
            val deferred = CompletableDeferred<String>()
            val script = "document.querySelector('${selector.replace("'", "\\'")}').click(); 'success';"
            service.evaluateJavascript(script, { result -> deferred.complete(result) })
            return mapOf("success" to true)
        }
    }

    val fillFormTool = object : Tool {
        override val schema = BrowserTools.fillFormSchema
        override suspend fun execute(args: Map<String, Any>): Any {
            val selector = args["selector"]?.toString() ?: return mapOf("success" to false, "error" to "Selector is required")
            val value = args["value"]?.toString() ?: return mapOf("success" to false, "error" to "Value is required")
            val service = getService()
            val deferred = CompletableDeferred<String>()
            val script = "document.querySelector('${selector.replace("'", "\\'")}').value = '${value.replace("'", "\\'")}'; 'success';"
            service.evaluateJavascript(script, { result -> deferred.complete(result) })
            return mapOf("success" to true)
        }
    }

    val captureScreenshotTool = object : Tool {
        override val schema = BrowserTools.captureScreenshotSchema
        override suspend fun execute(args: Map<String, Any>): Any {
            val service = getService()
            val deferred = CompletableDeferred<String>()
            service.captureScreenshot { base64 -> deferred.complete(base64) }
            val screenshot = deferred.await()
            return mapOf("success" to true, "screenshot" to screenshot)
        }
    }

    val executeScriptTool = object : Tool {
        override val schema = BrowserTools.executeScriptSchema
        override suspend fun execute(args: Map<String, Any>): Any {
            val script = args["script"]?.toString() ?: return mapOf("success" to false, "error" to "Script is required")
            val service = getService()
            val deferred = CompletableDeferred<String>()
            service.evaluateJavascript(script, { result -> deferred.complete(result) })
            val result = deferred.await()
            return mapOf("success" to true, "result" to result)
        }
    }

    val androidBrowserTools = listOf(
        browsePageTool,
        getPageTextTool,
        clickElementTool,
        fillFormTool,
        captureScreenshotTool,
        executeScriptTool
    )
}

package io.ktor.server.servlet

import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.http.*
import kotlinx.coroutines.experimental.*
import java.io.*
import javax.servlet.http.*
import kotlin.coroutines.experimental.*

interface ServletUpgrade {
    suspend fun performUpgrade(upgrade: OutgoingContent.ProtocolUpgrade,
                               servletRequest: HttpServletRequest,
                               servletResponse: HttpServletResponse,
                               engineContext: CoroutineContext,
                               userContext: CoroutineContext)
}

object DefaultServletUpgrade : ServletUpgrade {
    suspend override fun performUpgrade(upgrade: OutgoingContent.ProtocolUpgrade,
                                        servletRequest: HttpServletRequest,
                                        servletResponse: HttpServletResponse,
                                        engineContext: CoroutineContext,
                                        userContext: CoroutineContext) {

        val handler = servletRequest.upgrade(ServletUpgradeHandler::class.java)
        handler.up = UpgradeRequest(servletResponse, upgrade, engineContext, userContext)
    }
}

// the following types need to be public as they are accessed through reflection

class UpgradeRequest(val response: HttpServletResponse,
                     val upgradeMessage: OutgoingContent.ProtocolUpgrade,
                     val engineContext: CoroutineContext,
                     val userContext: CoroutineContext)

class ServletUpgradeHandler : HttpUpgradeHandler {
    @Volatile
    lateinit var up: UpgradeRequest

    override fun init(webConnection: WebConnection?) {
        if (webConnection == null) {
            throw IllegalArgumentException("Upgrade processing requires WebConnection instance")
        }

        val servletReader = servletReader(webConnection.inputStream)
        val servletWriter = servletWriter(webConnection.outputStream)

        val inputChannel = servletReader.channel
        val outputChannel = servletWriter.channel

        val closeable = Closeable {
            servletWriter.channel.close()
            servletReader.cancel()

            runBlocking {
                servletWriter.join()
                servletReader.join()
            }

            webConnection.close()
        }

        launch(up.userContext, start = CoroutineStart.UNDISPATCHED) {
            up.upgradeMessage.upgrade(inputChannel, outputChannel, closeable, up.engineContext, up.userContext)
        }
    }

    override fun destroy() {
    }
}

package io.ktor.websocket

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.util.*
import java.nio.*
import java.time.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.experimental.*
import kotlin.properties.*

internal class DefaultWebSocketSessionImpl(val raw: WebSocketSession,
                                           val engineContext: CoroutineContext,
                                           val userContext: CoroutineContext,
                                           val pool: ByteBufferPool
) : DefaultWebSocketSession, WebSocketSession by raw {

    private val pinger = AtomicReference<SendChannel<Frame.Pong>?>(null)
    private val closeReasonRef = CompletableDeferred<CloseReason>()
    private val filtered = Channel<Frame>(8)
    private val outgoingToBeProcessed = Channel<Frame>(8)

    override val incoming: ReceiveChannel<Frame> get() = filtered
    override val outgoing: SendChannel<Frame> get() = outgoingToBeProcessed

    override var timeout: Duration = Duration.ofSeconds(15)
    override val closeReason = closeReasonRef
    override var pingInterval: Duration? by Delegates.observable<Duration?>(null, { _, _, _ ->
        runPinger()
    })

    suspend fun run(handler: suspend DefaultWebSocketSession.() -> Unit) {
        runPinger()
        val ponger = ponger(engineContext, this, NoPool)
        val closeSequence = closeSequence(engineContext, raw, { timeout }, { reason ->
            closeReasonRef.complete(reason ?: CloseReason(CloseReason.Codes.NORMAL, ""))
        })

        launch(Unconfined) {
            try {
                var last: ByteBufferBuilder? = null

                raw.incoming.consumeEach { frame ->
                    when (frame) {
                        is Frame.Close -> closeSequence.send(CloseFrameEvent.Received(frame))
                        is Frame.Pong -> pinger.get()?.send(frame)
                        is Frame.Ping -> ponger.send(frame)
                        else -> {
                            if (frame.fin) {
                                last?.let { builder ->
                                    builder.put(frame.buffer)
                                    filtered.send(Frame.byType(true, frame.frameType, builder.build()))
                                    last = null
                                } ?: run {
                                    filtered.send(frame)
                                }
                            } else {
                                (last ?: ByteBufferBuilder().also { last = it }).put(frame.buffer)
                            }
                        }
                    }
                }
            } catch (ignore: ClosedSendChannelException) {
            } catch (t: Throwable) {
                filtered.close(t)
            } finally {
                ponger.close()
                filtered.close()
                closeSequence.close()
            }
        }

        launch(Unconfined) {
            try {
                outgoingToBeProcessed.consumeEach { frame ->
                    when (frame) {
                        is Frame.Close -> closeSequence.send(CloseFrameEvent.ToSend(frame))
                        else -> raw.outgoing.send(frame)
                    }
                }
            } catch (ignore: ClosedSendChannelException) {
            } catch (ignore: ClosedReceiveChannelException) {
            } catch (ignore: CancellationException) {
            } catch (t: Throwable) {
                raw.outgoing.close(t)
            } finally {
                raw.outgoing.close()
            }
        }

        launch(userContext) {
            val t = try {
                handler()
                null
            } catch (t: Throwable) {
                t
            }

            val reason = when (t) {
                null -> CloseReason(CloseReason.Codes.NORMAL, "OK")
                is ClosedReceiveChannelException, is ClosedSendChannelException -> null
                else -> CloseReason(CloseReason.Codes.UNEXPECTED_CONDITION, t.message ?: t.javaClass.name)
            }

            if (t != null) {
                application.log.error("Websocket handler failed", t)
            }

            cancelPinger()

            if (reason != null) {
                closeSequence.send(CloseFrameEvent.ToSend(Frame.Close(reason)))
            } else {
                closeSequence.close()
            }
        }

        try {
            closeReasonRef.await()
//            closeSequence.join()
        } finally {
            cancelPinger()
        }
    }

    private fun runPinger() {
        if (!closeReasonRef.isCompleted) {
            val newPinger = pingInterval?.let { interval -> pinger(engineContext, raw, interval, timeout, pool, raw.outgoing) }
            pinger.getAndSet(newPinger)?.close()
            newPinger?.offer(EmptyPong)
        } else {
            cancelPinger()
        }
    }

    private fun cancelPinger() {
        pinger.getAndSet(null)?.close()
    }

    companion object {
        private val EmptyPong = Frame.Pong(ByteBuffer.allocate(0))
    }
}
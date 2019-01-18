package org.i2peer.network

import com.google.common.collect.Lists
import com.nhaarman.mockito_kotlin.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.i2peer.auth.NoAuthInfo
import org.i2peer.network.tor.TorConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LinkActorsTest {

    lateinit var networkContext: NetworkContext

    @BeforeEach
    fun setDefaultTorConfig() {
        val torConfig = TorConfig(socksPort = 1000, configDir = File("mydir/" + Math.random()))
        networkContext = NetworkContext(torConfig)
    }

    @Test
    fun registerChannel() = runBlocking {
        val deliveryChannelMock: DeliveryChannel = mock()
        val channel = Channel<EventTask>(Channel.UNLIMITED)
        channel.registerChannel(deliveryChannelMock)

        val eventTask = channel.receive()
        assertEquals("Register", eventTask.name)
    }

    @ExperimentalCoroutinesApi
    @Test
    fun unregisterChannel() = runBlocking {
        val deliveryChannelMock: DeliveryChannel = mock()
        val channel = Channel<EventTask>(Channel.UNLIMITED)
        channel.registerChannel(deliveryChannelMock)
        channel.unregisterChannel(deliveryChannelMock)
        assertEquals("Register", channel.receive().name)
        assertEquals("Unregister", channel.receive().name)
    }

    @ObsoleteCoroutinesApi
    @Test
    fun fairLinkCommunicationsSentMatchesBytesWrittenToRemoteChannel() = runBlocking {
        val communicationsPacket = createTestCommunicationsPacket()
        val fairLossActor: SendChannel<EventTask> =
            fairLossPointToPoint(processChannelActor(networkContext), networkContext)
        fairLossActor.sendCommunications(communicationsPacket)

        delay(100)
        val pc = networkContext.channelMap["ikvh2uz76knum6dw"]
        assertNotNull(pc, "Process channel is null")
        val out = ByteArrayInputStream(pc!!.sink.buffer().readByteArray())

        assertEquals(IO.source(out).readCommunications(), communicationsPacket)
    }

    @ObsoleteCoroutinesApi
    @Test
    fun fairLinkCommunicationsLocal() = runBlocking {
        /*
        networkContext.localOnionAddress.addActorChannel("ikvh2uz76knum6dw")
        val communicationsPacket = createTestCommunicationsPacket()
        val fairLossActor: SendChannel<EventTask> = fairLossPointToPoint(processChannelActor(networkContext), networkContext)
        fairLossActor.sendCommunications(communicationsPacket)

        delay(100)
        val pc = networkContext.channelMap["ikvh2uz76knum6dw"]
        assertNotNull(pc, "Process channel is null")
        val out = ByteArrayInputStream(pc!!.sink.buffer().readByteArray())

        assertEquals(IO.source(out).readCommunications(), communicationsPacket)
        */
    }

    @ObsoleteCoroutinesApi
    @Test
    fun stubbornLinkRepeats() = runBlocking {
        /*
        val communicationsPacket = createTestCommunicationsPacket()
        val fairLossActor: SendChannel<EventTask> =
            fairLossPointToPoint(processChannelActor(networkContext), networkContext)
        val stubbornActor = stubbornPointToPoint(fairLossActor, 100)
        stubbornActor.sendCommunications(communicationsPacket)

        delay(1000)
        val pc = networkContext.channelMap["ikvh2uz76knum6dw"]
        assertNotNull(pc, "Process channel is null")
        val out = IO.source(ByteArrayInputStream(pc!!.sink.buffer().readByteArray()))

        var messageCount = 0
        while (!out.exhausted()) {
            messageCount++
            assertEquals(communicationsPacket, out.readCommunications())
        }
        assertEquals(11, messageCount)
        */
    }

    @ObsoleteCoroutinesApi
    @Test
    fun perfectPointToPointDeliversOnce() = runBlocking {
        val communicationsPacket = createTestCommunicationsPacket()
        val fairLossActor: SendChannel<EventTask> =
            fairLossPointToPoint(processChannelActor(networkContext), networkContext)
        val stubbornActor = stubbornPointToPoint(fairLossActor, 100)
        val perfectActor = perfectPointToPoint(stubbornActor)

        val channelMock: Channel<EventTask> = mock()
        whenever(channelMock.send(any())).thenReturn(Unit)

        val deliveryChannelMock: DeliveryChannel = mock() {
            on { match(any()) } doReturn true
        }

        perfectActor.registerChannel(DeliveryChannel(channelMock, Lists.newArrayList(AnyCommunicationTaskMatcher())))
        perfectActor.deliver(communicationsPacket)
        perfectActor.deliver(communicationsPacket)
        perfectActor.deliver(communicationsPacket)

        verify(channelMock, times(1)).send(any())
    }

    @ObsoleteCoroutinesApi
    @Test
    fun perfectPointToPointDeliversUpStack() = runBlocking {
        val communicationsPacket = createTestCommunicationsPacket()
        val fairLossActor: SendChannel<EventTask> =
            fairLossPointToPoint(processChannelActor(networkContext), networkContext)
        val stubbornActor =
            stubbornPointToPoint(fairLossActor, 30000, Lists.newArrayList(AnyCommunicationTaskMatcher()))
        val perfectActor = perfectPointToPoint(stubbornActor, Lists.newArrayList(AnyCommunicationTaskMatcher()))

        val channelMock: Channel<EventTask> = mock()
        whenever(channelMock.send(any())).thenReturn(Unit)

        perfectActor.registerChannel(DeliveryChannel(channelMock, Lists.newArrayList(AnyCommunicationTaskMatcher())))
        fairLossActor.deliver(communicationsPacket)
        delay(1000)
        verify(channelMock, times(1)).send(any())
    }

    @ObsoleteCoroutinesApi
    @Test
    fun fairLossDoesNotDeliverIfMessageNotAddressedToOurHiddenService() = runBlocking {
        networkContext.localOnionAddress.add("ikvh2uz76knum6dw-1")

        val communicationsPacket = createTestCommunicationsPacket()
        val fairLossActor: SendChannel<EventTask> =
            fairLossPointToPoint(processChannelActor(networkContext), networkContext)

        val channelMock: Channel<EventTask> = mock()
        whenever(channelMock.send(any())).thenReturn(Unit)

        fairLossActor.registerChannel(DeliveryChannel(channelMock, Lists.newArrayList(AnyCommunicationTaskMatcher())))
        fairLossActor.deliver(communicationsPacket)

        verify(channelMock, times(0)).send(any())

    }

    @ObsoleteCoroutinesApi
    @Test
    fun fairLossDeliversIfMessageAddressedToOurHiddenService() = runBlocking {
        networkContext.localOnionAddress.add("ikvh2uz76knum6dw")

        val communicationsPacket = createTestCommunicationsPacket()
        val fairLossActor: SendChannel<EventTask> =
            fairLossPointToPoint(processChannelActor(networkContext), networkContext)

        val channelMock: Channel<EventTask> = mock()
        whenever(channelMock.send(any())).thenReturn(Unit)

        fairLossActor.registerChannel(DeliveryChannel(channelMock, Lists.newArrayList(AnyCommunicationTaskMatcher())))
        fairLossActor.deliver(communicationsPacket)

        verify(channelMock, times(1)).send(any())

    }

    private fun createTestCommunicationsPacket(): CommunicationsPacket {
        val targetProcess = Process("myId", "ikvh2uz76knum6dw", "path")
        val message = Message(3, "Hello Onion".toByteArray())
        return CommunicationsPacket("8080", targetProcess, NoAuthInfo(), 1000, message)
    }
}
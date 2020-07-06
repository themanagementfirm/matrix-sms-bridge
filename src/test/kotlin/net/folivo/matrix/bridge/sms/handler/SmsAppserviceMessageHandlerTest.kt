package net.folivo.matrix.bridge.sms.handler

import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.handler.MessageContext
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.room.AppserviceRoom
import net.folivo.matrix.bridge.sms.room.SmsMatrixAppserviceRoomService
import net.folivo.matrix.bridge.sms.user.AppserviceUser
import net.folivo.matrix.bridge.sms.user.MemberOfProperties
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import net.folivo.matrix.core.model.events.m.room.message.UnknownMessageEventContent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class SmsAppserviceMessageHandlerTest {

    @MockK
    lateinit var sendSmsServiceMock: SendSmsService

    @MockK
    lateinit var smsBotMessageHandlerMock: SmsBotMessageHandler

    @MockK
    lateinit var roomServiceMock: SmsMatrixAppserviceRoomService

    @MockK
    lateinit var botPropertiesMock: MatrixBotProperties

    @MockK
    lateinit var smsBridgePropertiesMock: SmsBridgeProperties

    @InjectMockKs
    lateinit var cut: SmsAppserviceMessageHandler

    @MockK
    lateinit var contextMock: MessageContext

    @MockK
    lateinit var roomMock: AppserviceRoom

    @BeforeEach
    fun beforeEach() {
        every { smsBridgePropertiesMock.defaultRoomId } returns "defaultRoomId"
        every { botPropertiesMock.serverName } returns "someServerName"
        every { botPropertiesMock.username } returns "smsbot"
        every { contextMock.roomId } returns "someRoomId"
        every { contextMock.originalEvent.sender } returns "someSender"
        coEvery { roomServiceMock.getRoom("someRoomId", "someSender") }.returns(roomMock)

        coEvery { sendSmsServiceMock.sendSms(any(), any(), any(), any(), any()) } just Runs
        coEvery { smsBotMessageHandlerMock.handleMessageToSmsBot(any(), any(), any(), any()) }.returns(false)
    }

    @Test
    fun `should delegate to SendSmsService when not message for sms bot`() {
        val roomMock1 = mockk<AppserviceRoom> {
            every { members } returns mutableMapOf(
                    mockk<AppserviceUser> {
                        every { userId } returns "someUserId"
                    } to MemberOfProperties(1)
            )
        }
        val roomMock2 = mockk<AppserviceRoom> {
            every { members } returns mutableMapOf(
                    mockk<AppserviceUser> {
                        every { userId } returns "@smsbot:someServerName"
                    } to MemberOfProperties(1),
                    mockk<AppserviceUser> {
                        every { userId } returns "@sms_1234567890:someServerName"
                    } to MemberOfProperties(1)
            )
        }
        coEvery { roomServiceMock.getRoom("someRoomId", "someSender") }.returnsMany(
                roomMock1, roomMock2
        )

        runBlocking { cut.handleMessage(TextMessageEventContent("someBody"), contextMock) }

        coVerify { sendSmsServiceMock.sendSms(roomMock1, "someBody", "someSender", contextMock, true) }

        // also try when more then one member
        runBlocking { cut.handleMessage(TextMessageEventContent("someBody"), contextMock) }

        coVerify { sendSmsServiceMock.sendSms(roomMock2, "someBody", "someSender", contextMock, true) }
    }

    @Test
    fun `should delegate to SendSmsService when message is not TextMessage`() {
        every { roomMock.members } returns mutableMapOf(
                mockk<AppserviceUser> {
                    every { userId } returns "someUserId"
                } to MemberOfProperties(1)
        )

        runBlocking { cut.handleMessage(UnknownMessageEventContent("someBody", "image"), contextMock) }

        coVerify { sendSmsServiceMock.sendSms(roomMock, "someBody", "someSender", contextMock, false) }
    }

    @Test
    fun `should not delegate to SendSmsService when message is NoticeMessage`() {
        every { roomMock.members } returns mutableMapOf(
                mockk<AppserviceUser> {
                    every { userId } returns "someUserId"
                } to MemberOfProperties(1)
        )

        runBlocking { cut.handleMessage(NoticeMessageEventContent("someBody"), contextMock) }

        coVerify { sendSmsServiceMock wasNot Called }
    }

    @Test
    fun `should delegate to SmsBotHandler when room contains bot and not delegate to SendSmsService`() {
        coEvery { smsBotMessageHandlerMock.handleMessageToSmsBot(any(), any(), any(), any()) } returns true

        every { roomMock.members } returns mutableMapOf(
                mockk<AppserviceUser> {
                    every { userId } returns "@smsbot:someServerName"
                } to MemberOfProperties(1)
        )

        runBlocking { cut.handleMessage(TextMessageEventContent("someBody"), contextMock) }

        coVerify { smsBotMessageHandlerMock.handleMessageToSmsBot(roomMock, "someBody", "someSender", contextMock) }
        coVerify { sendSmsServiceMock wasNot Called }
    }

    @Test
    fun `should not delegate to SmsBotHandler when room contains no bot`() {
        every { roomMock.members } returns mutableMapOf(
                mockk<AppserviceUser> {
                    every { userId } returns "@someUser:someServerName"
                } to MemberOfProperties(1)
        )

        runBlocking { cut.handleMessage(TextMessageEventContent("someBody"), contextMock) }

        coVerify { smsBotMessageHandlerMock wasNot Called }
        coVerify { sendSmsServiceMock.sendSms(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should not delegate to SmsBotHandler when message is no text message`() {
        every { roomMock.members } returns mutableMapOf(
                mockk<AppserviceUser> {
                    every { userId } returns "@someUser:someServerName"
                } to MemberOfProperties(1)
        )

        runBlocking { cut.handleMessage(NoticeMessageEventContent("someBody"), contextMock) }

        coVerify { smsBotMessageHandlerMock wasNot Called }
        coVerify { sendSmsServiceMock wasNot Called }
    }

    @Test
    fun `should ignore messages to default room`() {
        every { contextMock.roomId } returns "defaultRoomId"

        runBlocking { cut.handleMessage(TextMessageEventContent("someBody"), contextMock) }

        coVerify { sendSmsServiceMock wasNot Called }
    }
}
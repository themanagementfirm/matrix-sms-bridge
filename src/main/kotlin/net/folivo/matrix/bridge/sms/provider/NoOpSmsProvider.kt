package net.folivo.matrix.bridge.sms.provider

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class NoOpSmsProvider : SmsProvider {
    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    private val errorMessage = "A configured SmsProvider is missing. Please ensure, that you configured a SmsProvider in the configuration file."

    init {
        LOG.error(errorMessage)
    }

    override suspend fun sendSms(receiver: String, body: String) {
        LOG.error(errorMessage)
    }
}
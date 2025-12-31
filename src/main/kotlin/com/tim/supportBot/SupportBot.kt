package com.tim.supportBot

import com.tim.supportBot.entity.SimpleUser
import com.tim.supportBot.repository.ChatSessionRepository
import com.tim.supportBot.repository.UserRepository
import com.tim.supportBot.service.SupportService
import org.springframework.stereotype.Component
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.longpolling.BotSession
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer
import org.telegram.telegrambots.longpolling.starter.AfterBotRegistration
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.meta.generics.TelegramClient



/**
 * Telegram bot implementation responsible for interacting with users via long polling.
 * It delegates business logic to SupportService and persists users via repositories.
 */
@Component
class SupportBot(
    private val userRepository: UserRepository,
    private val chatSessionRepository: ChatSessionRepository,
    private val supportService: SupportService
) : SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    // Initialise Telegram client once with the bot token
    private val telegramClient: TelegramClient = OkHttpTelegramClient(getBotToken())

    /**
     * Returns the bot token.  Read from the BOT_TOKEN environment variable for security.
     */
    override fun getBotToken(): String = System.getenv("BOT_TOKEN") ?: ""

    /**
     * The consumer used by the long polling framework.
     */
    override fun getUpdatesConsumer(): LongPollingUpdateConsumer = this

    /**
     * Main update handler.  Handles commands, language selection, operator registration, message forwarding and session management.
     */
    override fun consume(update: Update) {
        val message = update.message ?: return
        val chatId = message.chatId
        val messageText = message.text
        val fromUsername = message.from?.firstName

        // Ensure a user exists in the database.  Default new users to customers (isOperator = false).
        val user: SimpleUser = supportService.findOrCreateUser(chatId, fromUsername!!, isOperator = false)

        // Register operator command: /operator <lang1,lang2,...>
        if (messageText != null && messageText.startsWith("/operator")) {

            // Only allow the user to register as operator if not already one
            val parts = messageText.trim().split("\\s+".toRegex())

            if (parts.size > 1) {
//                val languages = parts[1].split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }.toMutableSet()
                val languages = parts.drop(1).map { it.trim().lowercase() }.filter { it.isNotBlank() }.toMutableSet()
                user.isOperator = true
                user.supportedLanguages = languages.toMutableSet()
                user.status = "available"
                userRepository.save(user)
                sendMessage(chatId, "You have been registered as an operator with languages: ${languages.joinToString(", ")}")
            } else {
                sendMessage(chatId, "Usage: /operator uz,ru,en")
            }
            return
        }

        // Handle /start command
        if (messageText != null && (messageText == BotButtons.START) || messageText.startsWith("/start")) {

            // Reset any active session for this user
            supportService.getActiveSessionForUser(user)?.let { session ->
                supportService.endSession(session)
            }
            if (user.isOperator) {sendOperatorMenu(chatId)}
            else {
                sendCustomerMenu(chatId)
            }
                return
        }

        // Handle language selection (customer side)
        if (messageText != null && messageText.lowercase() in setOf("uz", "ru", "en")) {

            // Only customers can select a language
            if (user.isOperator) {
                sendMessage(chatId, "Operators cannot select a language.")
                return
            }
            val assignedOperator = supportService.selectLanguageForCustomer(user, messageText.lowercase())
            if (assignedOperator != null) {
                // Notify both parties
                sendMessage(chatId, "You have been connected to an operator. Please describe your issue.")
                sendMessage(assignedOperator.telegramId, "New customer connected: ${user.name ?: user.telegramId}")

            } else {
                sendMessage(chatId, "All operators are currently busy. You have been placed in the queue. Please wait.")

            }
            return
        }

        // Handle /stop command to end session
        if (messageText != null && messageText == BotButtons.STOP) {

            val session = supportService.getActiveSessionForUser(user)
            if (session != null) {
                supportService.endSession(session)
                sendMessage(chatId, "Your support session has ended.")
                val targetId = if (user.isOperator) session.customer.telegramId else session.operator.telegramId
                sendMessage(targetId, "The support session has been closed.")
            } else {
                sendMessage(chatId, "You are not in an active session.")
            }
            return
        }

        // If user is in active session, forward message or media to the counterpart
        val activeSession = supportService.getActiveSessionForUser(user)
        if (activeSession != null) {

            val targetId: Long = if (user.isOperator) activeSession.customer.telegramId else activeSession.operator.telegramId
            // Forward based on content type
            when {
                message.hasText() -> {
                    // avoid forwarding commands
                    if (!messageText!!.startsWith("/")) {

                        sendMessage(targetId, messageText)

                    }
                }
                message.hasPhoto() -> {
                    // Choose the largest photo variant
                    val photoSize = message.photo.maxByOrNull { it.fileSize ?: 0 }
                    if (photoSize != null) {

                        val sendPhoto = SendPhoto.builder()
                            .chatId(targetId.toString())
                            .photo(InputFile(photoSize.fileId))
                            .caption(message.caption ?: "")
                            .build()
                        telegramClient.execute(sendPhoto)

                    }
                }
                message.hasDocument() -> {

                    val doc = message.document
                    val sendDoc = SendDocument.builder()
                        .chatId(targetId.toString())
                        .document(InputFile(doc.fileId))
                        .caption(message.caption ?: "")
                        .build()
                    telegramClient.execute(sendDoc)

                }
                else -> {
                    sendMessage(chatId, "Unsupported message type.")

                }
            }
            return
        }

        // If user is not in a session and not issuing a recognized command, show a hint
        if (messageText != null && !messageText.startsWith("/")) {
            sendMessage(chatId, "Please type /start to begin a support session or /operator languages to register as an operator.")
        }
    }

    /**
     * Sends a language selection keyboard to the user.  The keyboard will disappear after one use.
     */
    private fun sendLanguageSelectionKeyboard(chatId: Long) {
        val keyboardRow = KeyboardRow().apply {
            add("uz")
            add("ru")
            add("en")
        }
        val replyMarkup = ReplyKeyboardMarkup.builder()
            .keyboard(listOf(keyboardRow))
            .resizeKeyboard(true)
            .oneTimeKeyboard(true)
            .build()
        val message = SendMessage.builder()
            .chatId(chatId.toString())
            .text("Please select your preferred language:")
            .replyMarkup(replyMarkup)
            .build()
        try {
            telegramClient.execute(message)
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }


    private fun sendStopSessionKeyboard(chatId: Long) {
        val keyboardRow = KeyboardRow().apply {
            add("tugatish")
        }

        val replyMarkup = ReplyKeyboardMarkup.builder()
        .keyboard(listOf(keyboardRow))
        .resizeKeyboard(true)
        .oneTimeKeyboard(true)
        .build()

        val message = SendMessage.builder()
        .chatId(chatId.toString())
        .text("chatni tugatish uchun 'tugatish' tugmasini bosing")
        .replyMarkup(replyMarkup)
        .build()


        try {
            telegramClient.execute(message)
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }

    /**
     * Sends a simple text message to the given chat id.  Any exceptions are caught and printed.
     */
    private fun sendMessage(chatId: Long, text: String) {
        val msg = SendMessage.builder()
            .chatId(chatId.toString())
            .text(text)
            .build()
        try {
            telegramClient.execute(msg)
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }
    private fun sendKeyboard(chatId: Long, keyboardMarkup: ReplyKeyboardMarkup) {
        val message = SendMessage.builder()
        .chatId(chatId.toString())
        .text("Choose an action")
        .replyMarkup(keyboardMarkup)
        .build()

        try {
            telegramClient.execute(message)
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }


    private fun sendCustomerMenu(chatId: Long) {
        val keyboard = listOf(
            KeyboardRow(listOf(KeyboardButton(BotButtons.START))),
            KeyboardRow(listOf(BotButtons.UZ, BotButtons.RU, BotButtons.EN).map { KeyboardButton(it) })
        )

        val replyMarkup = ReplyKeyboardMarkup.builder()
        .keyboard(keyboard)
        .oneTimeKeyboard(true)
            .resizeKeyboard(true)
            .build()

        sendKeyboard(chatId, replyMarkup)
    }

    private fun sendOperatorMenu(chatId: Long) {


        val keyboard = listOf(
            KeyboardRow(listOf(BotButtons.AVAILABLE, BotButtons.BUSY).map { KeyboardButton(it) }),
            KeyboardRow(listOf(BotButtons.STOP).map { KeyboardButton(it) })
        )

        val replyMarkup = ReplyKeyboardMarkup.builder()
            .keyboard(keyboard)
            .oneTimeKeyboard(true)
            .resizeKeyboard(true)
            .build()

        sendKeyboard(chatId, replyMarkup)
    }



    /**
     * After bot registration hook.  Prints to stdout whether the bot has successfully started.
     */
    @AfterBotRegistration
    fun afterRegistration(botSession: BotSession) {
        println("Registered bot running state is: ${botSession.isRunning}")
    }
}














//
//
//
//
//
//
//
//
//
//package com.tim.supportBot
//
//import com.tim.supportBot.entity.SimpleUser
//import org.springframework.stereotype.Component
//import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
//import org.telegram.telegrambots.longpolling.BotSession
//import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer
//import org.telegram.telegrambots.longpolling.starter.AfterBotRegistration
//import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot
//import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer
//import org.telegram.telegrambots.meta.api.methods.send.SendMessage
//import org.telegram.telegrambots.meta.api.objects.Update
//import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
//import org.telegram.telegrambots.meta.exceptions.TelegramApiException
//import org.telegram.telegrambots.meta.generics.TelegramClient
//import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
//import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
//
//import com.tim.supportBot.repository.UserRepository
//import org.springframework.data.jpa.repository.JpaRepository
//
//@Component
//class SupportBot(
//    private val userRepository: UserRepository,
//) : SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {
//
//    private val telegramClient: TelegramClient
//
//
//    init {
//        telegramClient = OkHttpTelegramClient(botToken)
//    }
//
//    override fun getBotToken(): String {
//        return "7948810036:AAE2pCFjmdMEJ6isr2Qpm-DcoFcIUh8CPXE"
//    }
//
//    override fun getUpdatesConsumer(): LongPollingUpdateConsumer {
//        return this
//    }
//
//    override fun consume(update: Update) {
//        if (update.hasMessage() && update.message.hasText()) {
//            var messageText = update.message.text
//            val chatId = update.message.chatId
//
//            val user = SimpleUser(
//                telegramId = chatId,
//                name = update.message.chat.userName,
//                isOperator = false,
//                status = "available"
//            )
//
//            userRepository.save(user)
//
//            // Handle /start command
//            if (messageText.equals("/start", ignoreCase = true)) {
//                handleStartCommand(chatId)
//            }
//            //get lan choice
//            if(messageText.equals("uz", ignoreCase = true) || messageText.equals("ru", ignoreCase = true) || messageText.equals("en", ignoreCase = true)) {
//
//                var customer = userRepository.findByTelegramId(chatId)
//
//                customer?.name = update.message.chat.userName.toString()
//                customer?.supportedLanguages.run {
//                    customer?.supportedLanguages?.forEach { language ->messageText}
//                }
//                customer?.isOperator = false
//
//                val savedUser = userRepository.save(customer)
//            }
//
//
//        }
//
//    }
//
//    // Handle /start command logic
//    private fun handleStartCommand(chatId: Long) {
//        val messageText = SendMessage.builder()
//            .chatId(chatId.toString())
//            .text("iltimos tilni tanlang")
//            .build()
//
//        // Create the keyboard markup
//        val keyboardMarkup = ReplyKeyboardMarkup.builder()
//            .keyboardRow(KeyboardRow("uz", "ru", "en"))
//            .resizeKeyboard(true)  // Optional: resize the keyboard
//            .oneTimeKeyboard(true) // Optional: hide keyboard after selection
//            .build()
//
//        messageText.replyMarkup = keyboardMarkup
//
//        try {
//            // Send the message with the keyboard
//            telegramClient.execute(messageText)
//        } catch (e: TelegramApiException) {
//            e.printStackTrace()
//        }
//    }
//
//
//
//
//
//
//    @AfterBotRegistration
//    fun afterRegistration(botSession: BotSession) {
//        println("Registered bot running state is: ${botSession.isRunning}")
//    }
//}
//
//
//

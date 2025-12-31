package com.tim.supportBot

import com.tim.supportBot.SupportBotApplication.Companion.logger
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.slf4j.LoggerFactory

@SpringBootApplication
class SupportBotApplication : SpringBootServletInitializer() {

    companion object {
         val logger = LoggerFactory.getLogger(SupportBotApplication::class.java)
    }
}

fun main(args: Array<String>) {
    try {
        // Start the Spring Boot application
        val context: ConfigurableApplicationContext = runApplication<SupportBotApplication>(*args)

        // Print info once the application starts
        logger.info("Server is running...")
        logger.info("Swagger is available at http://localhost:8080/swagger-ui.html")

        // Optional: Keep the application running for debugging
        // context.registerShutdownHook()  // Uncomment if you need shutdown hooks for debugging

    } catch (e: Exception) {
        // Log the exception if the application fails to start
        logger.error("Application failed to start", e)
    }
}

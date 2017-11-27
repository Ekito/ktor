package io.ktor.samples.koin

import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.DefaultHeaders
import io.ktor.samples.koin.KoinModule.Properties.BYE_MSG
import io.ktor.samples.koin.KoinModule.Properties.MY_MODEL
import io.ktor.samples.koin.ext.setProperty
import io.ktor.samples.koin.ext.startKoin
import org.koin.Koin
import org.koin.dsl.module.Module
import org.koin.log.PrintLogger

/**
 * Defines main Ktor application configuration and setup Koin DI framework
 */
fun Application.main() {

    // Initialize Koin logger
    Koin.logger = PrintLogger()

    // Start Koin
    startKoin(arrayListOf(KoinModule()), properties = mapOf(BYE_MSG to "See you soon"))

    // Set initial properties after koin has been started...
    setProperty(MY_MODEL, Model("Initial value"))

    install(DefaultHeaders)
    install(CallLogging)
}

/**
 * Sample data class Model
 */
data class Model(val value: String)

/**
 * Koin module used to describe DI context using Koin DSL.
 */
class KoinModule : Module() {

    // Koin properties names used in the project
    companion object Properties {
        const val HELLO_MSG = "HELLO_MSG"
        const val BYE_MSG = "BYE_MSG"
        const val MY_MODEL = "MY_MODEL"
    }

    // Koin application context
    override fun context() = applicationContext {

        // Business Service binding definition
        provide { BusinessServiceImpl() } bind BusinessService::class
    }
}

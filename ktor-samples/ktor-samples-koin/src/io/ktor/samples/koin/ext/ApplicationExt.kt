package io.ktor.samples.koin.ext

import io.ktor.application.Application
import org.koin.Koin
import org.koin.KoinContext
import org.koin.dsl.module.Module
import org.koin.error.MissingPropertyException
import org.koin.standalone.StandAloneContext

/**
 * Function extensions on [io.ktor.application.Application] used to bring Koin injection capablity to Ktor modules such
 * as Routing modules or coroutines.
 *
 * Koin provides this capabilities to any class which implements [org.koin.standalone.KoinComponent] marker interface.
 * Unfortunately [io.ktor.application.Application] modules can't implement such interface being lambdas.
 *
 * But thanks to Kotlin function extension mechanism, it is really easy to bring this to Ktor Application. The only thing
 * to do is to define the same function extensions to [io.ktor.application.Application] as thoses defined in
 * [org.koin.standalone.KoinComponent].
 */

/**
 * Helper function used to start Koin framework with given list of Koin modules describing the dependency injection
 * statements.
 *
 * @param list - list of Koin modules used by Koin to perform DI
 * @param bindSystemProperties - if set to true automatically bind System Properties to Koin properties mechanism. Default value is false
 * @param properties - Set of properties to bind just before Koin is started (optional)
 */
fun Application.startKoin(list: List<Module>, bindSystemProperties: Boolean = false, properties: Map<String, Any> = HashMap()) {

    val koin = if (bindSystemProperties) {
        // Koin properties will override system properties
        Koin().bindKoinProperties().bindAdditionalProperties(properties).bindSystemProperties()
    } else {
        Koin().bindKoinProperties().bindAdditionalProperties(properties)
    }

    // Build koin context
    StandAloneContext.koinContext = koin.build(list)
}

/**
 * Lazy inject given bean
 *
 * @param name - bean name / optional
 */
inline fun <reified T> Application.inject(name: String = "") = lazy { (StandAloneContext.koinContext as KoinContext).get<T>(name) }

/**
 * Lazy inject given property or throws a MissingPropertyException if property is not found
 *
 * @param key - property key
 */
inline fun <reified T> Application.property(key: String) = lazy { (StandAloneContext.koinContext as KoinContext).getProperty<T>(key) }

/**
 * Lazy inject given property or a default value if property is missing
 *
 * @param key - property key
 * @param defaultValue - default value if property is missing
 */
inline fun <reified T> Application.property(key: String, defaultValue: T) = lazy { (StandAloneContext.koinContext as KoinContext).getProperty(key, defaultValue) }

/**
 * Lazy inject given property for KoinComponent or null if property is not found
 *
 * @param key - key property
 */
inline fun <reified T> Application.propertyOrNull(key: String): Lazy<T?> = lazy {
    try {
        (StandAloneContext.koinContext as KoinContext).getProperty<T>(key)
    } catch (e: MissingPropertyException) {
        null
    }
}

internal fun context() = (StandAloneContext.koinContext as KoinContext)

/**
 * Set a property
 *
 * @param key - property key (must be a String)
 * @param value - property value (can be anything)
 */
fun Application.setProperty(key: String, value: Any) = context().setProperty(key, value)

/**
 * Release a Koin context
 *
 * @param name - context name to release
 */
fun Application.releaseContext(name: String) = context().releaseContext(name)

/**
 * Release properties
 *
 * @param keys - properties keys to release
 */
fun Application.releaseProperties(vararg keys: String) = context().releaseProperties(*keys)
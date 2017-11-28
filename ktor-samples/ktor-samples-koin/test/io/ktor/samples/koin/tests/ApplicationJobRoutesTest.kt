package io.ktor.samples.koin.tests

import io.ktor.application.Application
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.samples.koin.KoinModule
import io.ktor.samples.koin.KoinModule.Properties.BYE_MSG
import io.ktor.samples.koin.KoinModule.Properties.HELLO_MSG
import io.ktor.samples.koin.KoinModule.Properties.MY_MODEL
import io.ktor.samples.koin.Model
import io.ktor.samples.koin.jobRoutes
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import org.junit.Before
import org.junit.Test
import org.koin.Koin
import org.koin.log.PrintLogger
import org.koin.standalone.property
import org.koin.standalone.startKoin
import org.koin.test.KoinTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ApplicationJobRoutesTest : KoinTest {

    @Before
    fun before() {
        Koin.logger = PrintLogger()
        startKoin(arrayListOf(KoinModule()),
                properties = mapOf(HELLO_MSG to "Bonjour", BYE_MSG to "Au revoir", MY_MODEL to Model("Test value")))
    }

    @Test
    fun testRootRequest() = withTestApplication(Application::jobRoutes) {

        with(handleRequest(HttpMethod.Get, "/")) {
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("Hello, World from Ktor and Koin!", response.content)
        }

        with(handleRequest(HttpMethod.Get, "/index.html")) {
            assertFalse(requestHandled)
        }
    }

    @Test
    fun testHiRequest() = withTestApplication(Application::jobRoutes) {

        with(handleRequest(HttpMethod.Get, "/hi")) {
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("Bonjour from Ktor and Koin", response.content)
        }
    }

    @Test
    fun testByeRequest() = withTestApplication(Application::jobRoutes) {

        with(handleRequest(HttpMethod.Get, "/bye")) {
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("Au revoir from Ktor and Koin", response.content)
        }
    }

    @Test
    fun testModelRequest() = withTestApplication(Application::jobRoutes) {

        with(handleRequest(HttpMethod.Get, "/model")) {
            assertEquals(HttpStatusCode.OK, response.status())
            val currentModel by property<Model>(MY_MODEL)

            assertEquals("Test value", currentModel.value)
            assertEquals("Model value = ${currentModel.value}", response.content)
        }

        with(handleRequest(HttpMethod.Get, "/hi")) {
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("Bonjour from Ktor and Koin", response.content)
        }

        with(handleRequest(HttpMethod.Get, "/model")) {
            assertEquals(HttpStatusCode.OK, response.status())
            val currentModel by property<Model>(MY_MODEL)

            assertEquals("Hi already said !", currentModel.value)
            assertEquals("Model value = ${currentModel.value}", response.content)
        }
    }
}

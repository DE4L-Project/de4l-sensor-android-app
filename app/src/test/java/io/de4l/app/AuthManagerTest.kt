package io.de4l.app

import io.de4l.app.auth.AuthManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.Mockito

class AuthManagerTest {

    private lateinit var authManager: AuthManager

    init {
        val application: De4lApplication = Mockito.mock(De4lApplication::class.java)
        authManager = AuthManager(application)
    }


    @Test
    fun test() {
        runBlocking {
            assertNotNull(authManager)
            authManager.refreshToken()
        }
    }

}
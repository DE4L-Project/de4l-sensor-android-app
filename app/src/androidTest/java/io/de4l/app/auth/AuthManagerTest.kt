package io.de4l.app.auth

import android.app.Application
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthManagerTest {
    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    private val authManager: AuthManager = AuthManager(context as Application)

    @Before
    fun setup() {
        authManager.initialize()
    }

    //    @Test(expected = NullPointerException::class)
    @Test
    fun test() = runBlocking {
//        authManager.refreshToken()
    }
}
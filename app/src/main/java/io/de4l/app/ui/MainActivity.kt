package io.de4l.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint
import io.de4l.app.*
import io.de4l.app.auth.AuthManager
import io.de4l.app.auth.TokenRefreshException
import io.de4l.app.ui.event.*
import kotlinx.coroutines.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import javax.inject.Inject
import kotlin.system.measureTimeMillis

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val LOG_TAG: String = MainActivity::class.java.name

    @Inject
    lateinit var authManager: AuthManager

    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)

    private lateinit var bottomNavView: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val time = measureTimeMillis {
            buildLayout()
            checkPermissions()
            loadConfigFromKeycloak()
        }
        Log.v(LOG_TAG, "On create in ${time}ms.")
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        Log.i(LOG_TAG, "onStop")
        EventBus.getDefault().unregister(this)
        super.onStop()
    }

    override fun onDestroy() {
        coroutineScope.cancel()
        super.onDestroy()
    }

    private fun checkPermissions() {
        var requiredPermissions = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions += Manifest.permission.BLUETOOTH_SCAN
            requiredPermissions += Manifest.permission.BLUETOOTH_CONNECT
        }

        val deniedPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (deniedPermissions.isNotEmpty()) {
            Log.w(LOG_TAG, "Missing permissions: $deniedPermissions")
            ActivityCompat.requestPermissions(
                this,
                deniedPermissions.toTypedArray(),
                AppConstants.REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun buildLayout() {
        setContentView(R.layout.activity_main)

        bottomNavView = findViewById<View>(R.id.bottomNavView) as BottomNavigationView
        val navController = findNavController(R.id.nav_host_fragment)
        navController.addOnDestinationChangedListener { controller, destination, arguments ->
            if (destination.id == R.id.splashScreen) {
                bottomNavView.visibility = View.GONE
            } else {
                bottomNavView.visibility = View.VISIBLE
            }
        }

        // Setting Navigation Controller with the BottomNavigationView
        bottomNavView.setupWithNavController(
            navController
        )
    }

    private fun loadConfigFromKeycloak() {
        coroutineScope.launch {
            val progressJob = launch { logTokenProgress() }
            try {
                authManager.getValidAccessToken()
            } catch (e: TokenRefreshException) {
                Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_LONG).show()
            } finally {
                progressJob.cancel()
            }
        }
    }

    private suspend fun logTokenProgress() {
        while (true) {
            Log.i(LOG_TAG, "${Thread.currentThread().name}: Waiting for Token ...")
            delay(1000)
        }
    }

    @Subscribe
    fun onNavigationEvent(event: NavigationEvent) {
        coroutineScope.launch {
            try {
                findNavController(R.id.nav_host_fragment).navigate(
                    event.action,
                    null,
                    event.navOptions
                )
            } catch (e: IllegalArgumentException) {
                Log.w(LOG_TAG, "Navigation: " + e.message)
            }
        }
    }

}
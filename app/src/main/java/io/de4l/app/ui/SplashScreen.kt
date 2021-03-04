package io.de4l.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.de4l.app.AppConstants
import io.de4l.app.R
import kotlinx.coroutines.*

@AndroidEntryPoint
class SplashScreen : Fragment() {

    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
    private val viewModel: SplashScreenViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_splash_screen, container, false)
    }

    override fun onStart() {
        super.onStart()
        coroutineScope.launch {
            delay(AppConstants.SPLASH_SCREEN_DELAY_IN_SECONDS * 1000)
            viewModel.onSplashScreenFinished()

        }


    }

    override fun onStop() {
        coroutineScope.cancel()
        super.onStop()
    }


}
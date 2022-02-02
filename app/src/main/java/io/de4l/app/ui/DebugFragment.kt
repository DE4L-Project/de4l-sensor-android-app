package io.de4l.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.de4l.app.R

@AndroidEntryPoint
class DebugFragment : Fragment() {
    private val LOG_TAG = DebugFragment::class.java.name

    private lateinit var btnAirBeam3Test: Button;
    private val viewModel: DebugViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_debug, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnAirBeam3Test = view.findViewById(R.id.btnAirBeam3TestDebug)
        btnAirBeam3Test.setOnClickListener {
            viewModel.onDeviceConnectClicked()
        }
    }
}
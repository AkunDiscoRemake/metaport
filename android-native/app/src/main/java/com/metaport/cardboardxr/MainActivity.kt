package com.metaport.cardboardxr

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.metaport.cardboardxr.arcore.ARCoreManager
import com.metaport.cardboardxr.ui.HomeUI

class MainActivity : AppCompatActivity() {
    private var arCoreManager: ARCoreManager? = null
    private var homeUI: HomeUI? = null
    private var isARCoreSupported = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (cameraGranted) {
            initARCore()
            initUI()
        } else {
            Toast.makeText(this, "Sem permissão câmera - Modo 3DOF", Toast.LENGTH_LONG).show()
            initUIWithoutCamera()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (hasCameraPermission()) {
                initARCore()
                initUI()
            } else {
                requestPermissions()
            }
        } catch (e: Exception) {
            Log.e("MetaPort", "Erro onCreate", e)
            try { initUIWithoutCamera() } catch (e2: Exception) {
                val tv = android.widget.TextView(this).apply {
                    text = "MetaPort VR\nErro: ${e.message}"
                    textSize = 16f
                    setPadding(50,50,50,50)
                }
                setContentView(tv)
            }
        }
    }

    private fun hasCameraPermission(): Boolean {
        return try {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) { false }
    }

    private fun requestPermissions() {
        try {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        } catch (e: Exception) { initUIWithoutCamera() }
    }

    private fun initARCore() {
        try {
            arCoreManager = ARCoreManager(this)
            isARCoreSupported = arCoreManager?.isARCoreSupported() ?: false
        } catch (e: Exception) {
            isARCoreSupported = false
            arCoreManager = null
        }
    }

    private fun initUI() {
        try {
            homeUI = HomeUI(this, arCoreManager, isARCoreSupported)
            setContentView(homeUI?.getView())
        } catch (e: Exception) { initUIWithoutCamera() }
    }

    private fun initUIWithoutCamera() {
        try {
            homeUI = HomeUI(this, null, false)
            setContentView(homeUI?.getView())
        } catch (e: Exception) {
            val tv = android.widget.TextView(this).apply {
                text = "MetaPort VR v2.3\nFallback UI\n9 Ambientes\n12 Jogos\n6DOF: ${if (isARCoreSupported) "Sim" else "Não"}"
                textSize = 18f
                setPadding(50,50,50,50)
            }
            setContentView(tv)
        }
    }

    override fun onResume() {
        super.onResume()
        try { arCoreManager?.onResume(); homeUI?.onResume() } catch (e: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        try { arCoreManager?.onPause(); homeUI?.onPause() } catch (e: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        try { arCoreManager?.onDestroy(); homeUI?.onDestroy() } catch (e: Exception) {}
    }
}

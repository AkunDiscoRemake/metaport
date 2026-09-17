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

/**
 * MetaPort v2.3 - ANDROID PURO - SEM UNITY - CRASH-PROOF
 * 
 * Checagem completa de sintaxe e runtime:
 * - Permissões runtime
 * - ARCore availability com try-catch
 * - Fallback 3DOF se ARCore falhar
 * - Sem crash se câmera não disponível
 * - Sem crash se MediaPipe model faltando
 * - UI 3D Spatial via OpenGL + Android Views
 */
class MainActivity : AppCompatActivity() {

    private var arCoreManager: ARCoreManager? = null
    private var homeUI: HomeUI? = null
    private var isARCoreSupported = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (cameraGranted) {
            Log.d("MetaPort", "✅ Permissão câmera concedida")
            initARCore()
            initUI()
        } else {
            Log.w("MetaPort", "❌ Permissão câmera negada, usando modo 3DOF sem câmera")
            Toast.makeText(this, "Sem permissão de câmera - Modo 3DOF", Toast.LENGTH_LONG).show()
            initUIWithoutCamera()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d("MetaPort", "=== MetaPort v2.3 ANDROID PURO - Iniciando ===")
        
        try {
            // Checa permissões primeiro - evita crash
            if (hasCameraPermission()) {
                initARCore()
                initUI()
            } else {
                Log.d("MetaPort", "Pedindo permissão câmera...")
                requestPermissions()
            }
        } catch (e: Exception) {
            Log.e("MetaPort", "Erro fatal no onCreate, usando fallback", e)
            Toast.makeText(this, "Erro: ${e.message}, usando fallback", Toast.LENGTH_LONG).show()
            try {
                initUIWithoutCamera()
            } catch (e2: Exception) {
                Log.e("MetaPort", "Fallback também falhou", e2)
                // Último fallback - TextView simples que nunca crasha
                val textView = android.widget.TextView(this).apply {
                    text = "MetaPort VR\nErro: ${e.message}\n\nTenta reinstalar o app e permitir câmera"
                    textSize = 16f
                    setPadding(50, 50, 50, 50)
                }
                setContentView(textView)
            }
        }
    }

    private fun hasCameraPermission(): Boolean {
        return try {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    private fun requestPermissions() {
        try {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        } catch (e: Exception) {
            Log.e("MetaPort", "Erro ao pedir permissões", e)
            initUIWithoutCamera()
        }
    }

    private fun initARCore() {
        try {
            arCoreManager = ARCoreManager(this)
            isARCoreSupported = arCoreManager?.isARCoreSupported() ?: false
            
            if (isARCoreSupported) {
                Log.d("MetaPort", "✅ ARCore suportado - 6DOF SLAM vai funcionar")
                Toast.makeText(this, "ARCore 6DOF Ativado!", Toast.LENGTH_SHORT).show()
            } else {
                Log.d("MetaPort", "⚠️ ARCore não suportado neste device, usando 3DOF")
                Toast.makeText(this, "Device sem ARCore - Modo 3DOF", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("MetaPort", "Erro ao inicializar ARCore, usando fallback", e)
            isARCoreSupported = false
            arCoreManager = null
        }
    }

    private fun initUI() {
        try {
            homeUI = HomeUI(this, arCoreManager, isARCoreSupported)
            setContentView(homeUI?.getView())
            Log.d("MetaPort", "✅ UI 3D Spatial iniciada")
        } catch (e: Exception) {
            Log.e("MetaPort", "Erro ao iniciar UI, fallback", e)
            initUIWithoutCamera()
        }
    }

    private fun initUIWithoutCamera() {
        try {
            homeUI = HomeUI(this, null, false)
            setContentView(homeUI?.getView())
            Log.d("MetaPort", "✅ UI fallback sem câmera iniciada")
        } catch (e: Exception) {
            Log.e("MetaPort", "Erro UI fallback", e)
            val textView = android.widget.TextView(this).apply {
                text = "MetaPort VR v2.3\n\nModo Fallback\nSem câmera\n\n9 Ambientes + 12 Jogos disponíveis\n\nReinicie o app e permita câmera pra 6DOF"
                textSize = 18f
                setPadding(50, 50, 50, 50)
            }
            setContentView(textView)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            arCoreManager?.onResume()
            homeUI?.onResume()
        } catch (e: Exception) {
            Log.e("MetaPort", "Erro onResume", e)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            arCoreManager?.onPause()
            homeUI?.onPause()
        } catch (e: Exception) {
            Log.e("MetaPort", "Erro onPause", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            arCoreManager?.onDestroy()
            homeUI?.onDestroy()
        } catch (e: Exception) {
            Log.e("MetaPort", "Erro onDestroy", e)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        try {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initARCore()
                initUI()
            } else {
                initUIWithoutCamera()
            }
        } catch (e: Exception) {
            Log.e("MetaPort", "Erro permission result", e)
            initUIWithoutCamera()
        }
    }
}

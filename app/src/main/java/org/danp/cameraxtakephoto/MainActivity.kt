package org.danp.cameraxtakephoto

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ocultar la barra de acción
        supportActionBar?.hide()

        // Verifica los permisos de la cámara si se otorgaron todos los permisos,
        // inicia la cámara; de lo contrario, se solicita el permiso
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // configurando en el listener los clics para el botón de captura de fotos,
        // llama a un método takePhoto() que se implementa a continuación
        findViewById<Button>(R.id.camera_capture_button).setOnClickListener {
            takePhoto()
        }
        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun takePhoto() {
        // Obtenemos una referencia estable del caso de uso
        // para una captura de imagen modificable
        val imageCapture = imageCapture ?: return

        // ** Crea un archivo de salida con marca de tiempo para contener la imagen
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + ".jpg"
        )

        // Crear objeto de opciones de salida que contiene archivo + metadatos
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Configura el listener de captura de imágenes,
        // que se activa después de que se haya tomado la foto
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "La captura de foto fallo: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)

                    // establecer el uri guardado en la vista de imagen
                    findViewById<ImageView>(R.id.ImageView).visibility = View.VISIBLE
                    findViewById<ImageView>(R.id.ImageView).setImageURI(savedUri)

                    val msg = "La captura de foto es exitosa: $savedUri"
                    //Toast.makeText(baseContext, msg, Toast.LENGTH_LONG).show()
                    Log.d(TAG, msg)
                }
            })
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {

            // Se utiliza para vincular el lifecycle de las cámaras al propietario del lifecycle.
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // la vista anticipada
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()

            // Seleccionar cámara trasera como predeterminado
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Desvincular casos de uso antes de volver a vincular
                cameraProvider.unbindAll()

                // Vincular casos de uso a la cámara
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    // ** crea una carpeta dentro del almacenamiento interno
    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    // comprueba el permiso de la cámara
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            // Si se otorgaron todos los permisos, inicie la cámara
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                // Si no se otorgan los permisos, muestra un toast para notificar
                // al usuario que no se otorgaron los permisos.
                Toast.makeText(this, "Permisos no otorgados para el usuario.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        private const val TAG = "CameraXGFG"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 20
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
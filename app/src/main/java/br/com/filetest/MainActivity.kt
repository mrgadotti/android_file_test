package br.com.filetest

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import okio.IOException
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {

    private val IMAGE_CAPTURE_CODE = 1001

    private var vFilename: String = ""
    private lateinit var captureIV: ImageView
    private lateinit var uri2: Uri

    // https://medium.com/@abdulhamidrpn/how-to-choose-a-file-from-a-specific-folder-in-android-13-kotlin-f539b24e7c5a
    // https://medium.com/@bssss100/download-save-open-any-file-on-android-with-download-manager-262c8842429a
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        captureIV = findViewById(R.id.captureImageView)
        val buttonMain = findViewById<Button>(R.id.button)

        buttonMain.setOnClickListener {
            openSpecificFolder("")
        }
        val buttonCam = findViewById<Button>(R.id.button_cam)
        buttonCam.setOnClickListener {
            openCamera()
        }
    }

    private fun openCamera() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        vFilename = "pic_" + timeStamp + ".jpg"

        val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "xx/$vFilename")
        if (!(file.parentFile?.exists())!!) {
            file.parentFile?.mkdirs()
        }

        val uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.provider",
            file
        )
        Log.e("URI1", uri.toString())
        uri2 = uri
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
        startActivityForResult(cameraIntent, IMAGE_CAPTURE_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_CAPTURE_CODE && resultCode == RESULT_OK) {

            val file =
                File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "xx/$vFilename")
            val imageUri = Uri.fromFile(file)

            captureIV.setImageURI(imageUri)

        }
    }


    private val requestFileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    saveFileToLocalStorage(uri)
                }
            }
        }

    private fun openSpecificFolder(folderName: String) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"  // Set the MIME type to filter files
            val uri =
                Uri.parse("content://com.android.externalstorage.documents/document/primary:$folderName")
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
        }

        requestFileLauncher.launch(intent)
    }

    private fun saveFileToLocalStorage(uri: Uri) {
        val inputStream = contentResolver.openInputStream(uri)
        val fileName = getFileName(uri) ?: "default_filename"
//        use storage from app
//        val file = File(applicationContext.filesDir, fileName)
//         user exposed directory from app
        val file = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)

        inputStream?.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        Log.d("FILE_SAVED", "File saved at: ${file.absolutePath}")
        Log.d("FILE", "File saved at: ${file.name}")
        //sendFileToApi(file)
    }

    private fun getFileName(uri: Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) return it.getString(nameIndex)
            }
        }
        return null
    }

    private fun sendFileToApi(file: File) {
        val apiUrl = "https://5mgt.requestcatcher.com/test"

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "ticket0.png", // Form field name
                file.name,
                file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url(apiUrl)
            .post(requestBody)
            .build()

        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("API_ERROR", "Failed to send file: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("API_SUCCESS", "File uploaded successfully: ${response.body?.string()}")
                } else {
                    Log.e("API_ERROR", "Error uploading file: ${response.code}")
                }
            }
        })
    }
}
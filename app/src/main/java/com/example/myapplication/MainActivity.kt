package com.example.myapplication

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var imagePreview: ImageView
    private val SELECT_IMAGE_REQUEST_CODE = 1
    private val CAPTURE_IMAGE_REQUEST_CODE = 2
    private val CAMERA_PERMISSION_REQUEST_CODE = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imagePreview = findViewById(R.id.image_preview)
        val selectImageButton: Button = findViewById(R.id.select_image_button)
        val captureImageButton: Button = findViewById(R.id.capture_image_button)
        val sendButton: Button = findViewById(R.id.send_button)
        val resultTextView: TextView = findViewById(R.id.Result)

        selectImageButton.setOnClickListener {
            selectImageFromGallery()
        }

        captureImageButton.setOnClickListener {
            checkCameraPermissionAndCaptureImage()
        }

        sendButton.setOnClickListener {
            // Get the selected/captured image as a Bitmap
            val imageBitmap = (imagePreview.drawable as? BitmapDrawable)?.bitmap
            if (imageBitmap != null) {
                // Send the image to the server
                sendImageToServer(imageBitmap, resultTextView)
            } else {
                Toast.makeText(this, "Please select or capture an image first.", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun selectImageFromGallery() {
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(galleryIntent, SELECT_IMAGE_REQUEST_CODE)
    }

    private fun checkCameraPermissionAndCaptureImage() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            captureImage()
        } else {
            // Request CAMERA permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun captureImage() {
        val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (captureIntent.resolveActivity(packageManager) != null) {
            startActivityForResult(captureIntent, CAPTURE_IMAGE_REQUEST_CODE)
        } else {
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    captureImage()
                } else {
                    Toast.makeText(
                        this,
                        "Camera permission is required to capture images",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                SELECT_IMAGE_REQUEST_CODE -> {
                    val selectedImageUri = data?.data
                    selectedImageUri?.let {
                        imagePreview.setImageURI(it)
                    }
                }

                CAPTURE_IMAGE_REQUEST_CODE -> {
                    val photo = data?.extras?.get("data") as? Bitmap
                    photo?.let {
                        imagePreview.setImageBitmap(it)
                    }
                }
            }
        }
    }

    private fun sendImageToServer(imageBitmap: Bitmap, resultTextView: TextView) {
        val client = OkHttpClient()

        val mediaType = "image/jpeg".toMediaType()

        val byteArrayOutputStream = ByteArrayOutputStream()
        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        val imageByteArray = byteArrayOutputStream.toByteArray()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "image.jpg", RequestBody.create(mediaType, imageByteArray))
            .build()

        val request = Request.Builder()
            .url("http://192.168.179.98:5000/predict") // Replace with your server URL
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    resultTextView.text = "Failed to make the request: ${e.message}"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    try {
                        val responseBody = response.body?.string()
                        val json = JSONObject(responseBody)
                        val result = json.getString("response")

                        runOnUiThread {
                            resultTextView.text = result
                        }
                    } catch (e: JSONException) {
                        runOnUiThread {
                            resultTextView.text = "Error parsing JSON response: ${e.message}"
                        }
                    }
                } else {
                    runOnUiThread {
                        resultTextView.text = "HTTP response code: ${response.code}"
                    }
                }
            }
        })
    }
}
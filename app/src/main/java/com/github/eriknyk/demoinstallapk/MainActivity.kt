package com.github.eriknyk.demoinstallapk

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.github.eriknyk.demoinstallapk.ui.theme.DemoAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.solrudev.ackpine.installer.PackageInstaller
import ru.solrudev.ackpine.installer.parameters.InstallParameters
import ru.solrudev.ackpine.session.SessionResult
import ru.solrudev.ackpine.session.await
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException

class MainActivity : ComponentActivity() {
    lateinit var apkFile: File

    private val requestPackageInstallsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                checkInstallFromUnknownSources {
                    installAPK(apkFile)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val res = resources.openRawResource(R.raw.audiobookshelf)
        val apkFile = File(cacheDir, "app.apk")
        copyStreamToFile(res, apkFile)

        Log.d("MainActivity", "APK file path: ${apkFile.absolutePath}")
        Log.d("MainActivity", "APK file exists: ${apkFile.exists()}")

        enableEdgeToEdge()
        setContent {
            DemoAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        checkInstallFromUnknownSources {
            installAPK(apkFile)
            //installAPK_Other(apkFile)
        }
    }

    private fun checkInstallFromUnknownSources(callback: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (packageManager.canRequestPackageInstalls()) {
                callback()
            } else {
                AlertDialog
                    .Builder(this)
                    .setTitle("Permission Required")
                    .setMessage("Please allow this app to install unknown apps")
                    .setPositiveButton("Allow") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                            .setData(Uri.parse(String.format("package:%s", packageName)))

                        requestPackageInstallsLauncher.launch(intent)
                    }
                    .setNegativeButton("Cancel") { _, _ -> }
                    .show()
            }
        } else {
            callback()
        }
    }

    private fun installAPK(apkFile: File) = lifecycleScope.launch(
        Dispatchers.IO) {
        val packageInstaller = PackageInstaller.getInstance(this@MainActivity.baseContext)
        val apkUri = uriFromFile(applicationContext, apkFile)

        Log.d("MainActivity", "Install APK uri: ${apkUri.path}")

        try {
            val session = packageInstaller.createSession(
                InstallParameters
                    .Builder(apkUri)
                    .setName("release-app.apk")
                    .setRequireUserAction(true)
                    .build()
            )

            val result = session.await()
            when (result) {
                is SessionResult.Success -> {
                    println("Success")
                }

                is SessionResult.Error -> {
                    println(result.cause.message)
                }
            }
        } catch (_: CancellationException) {
            println("Cancelled")
        } catch (exception: Exception) {
            println(exception)
        }
    }

    private fun uriFromFile(context: Context?, file: File): Uri =
        FileProvider.getUriForFile(context!!, context.applicationContext.packageName + ".provider", file)

    private fun installAPK_Other(apkFile: File) {
        if (apkFile.exists()) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uriFromFile(applicationContext, apkFile), "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);

            try {
                startActivityForResult(intent, 1101)
            } catch (e: ActivityNotFoundException) {
                e.printStackTrace()
                Log.e("TAG", "Error in opening the file!")
            }
        } else {
            Toast.makeText(applicationContext, "File not found", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DemoAppTheme {
        Greeting("Android")
    }
}

fun copyStreamToFile(inputStream: InputStream, outputFile: File) {
    inputStream.use { input ->
        val outputStream = FileOutputStream(outputFile)
        outputStream.use { output ->
            val buffer = ByteArray(4 * 1024) // buffer size
            while (true) {
                val byteCount = input.read(buffer)
                if (byteCount < 0) break
                output.write(buffer, 0, byteCount)
            }
            output.flush()
        }
    }
}

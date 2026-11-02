package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.data.SpeedDialDatabase
import com.example.data.SpeedDialRepository
import com.example.ui.SpeedDialScreen
import com.example.ui.SpeedDialViewModel
import com.example.ui.SpeedDialViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    // Lateinit repository / db setup
    private val database by lazy { SpeedDialDatabase.getDatabase(this) }
    private val repository by lazy { SpeedDialRepository(database.dao()) }

    // Instantiate viewmodel
    private val viewModel: SpeedDialViewModel by viewModels {
        SpeedDialViewModelFactory(repository)
    }

    // Storage Access Framework JSON exporting picker
    private val exportJsonLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            try {
                val json = viewModel.exportJson()
                contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write(json.toByteArray())
                }
                Toast.makeText(this, "Configurazione esportata con successo e compatibile col Web!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Errore durante l'esportazione: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Storage Access Framework JSON importing content chooser
    private val importJsonLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                contentResolver.openInputStream(it)?.use { stream ->
                    val json = stream.bufferedReader().use { r -> r.readText() }
                    viewModel.importJson(json,
                        onSuccess = {
                            Toast.makeText(this, "Configurazione Web importata con successo!", Toast.LENGTH_SHORT).show()
                        },
                        onError = { err ->
                            Toast.makeText(this, err, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Errore di lettura file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check if DB is empty to populate clean defaults
        viewModel.checkAndLoadDefaultData()

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SpeedDialScreen(
                        viewModel = viewModel,
                        onImportClick = {
                            importJsonLauncher.launch("application/json")
                        },
                        onExportClick = {
                            exportJsonLauncher.launch("speeddial_pro_data.json")
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

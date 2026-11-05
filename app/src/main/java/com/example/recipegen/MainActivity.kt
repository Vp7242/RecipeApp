package com.example.recipegen

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: RecipeViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { RecipeScreen(vm) } }
    }
}

@Composable
fun RecipeScreen(vm: RecipeViewModel) {
    val state by vm.state.collectAsState()
    RecipeScreen(
        state = state,
        onAnalyze = vm::analyze,
        onPick = vm::pick,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeScreen(
    state: UiState,
    onAnalyze: (Uri) -> Unit = {},
    onPick: (Recipe) -> Unit = {},
) {
    val context = LocalContext.current
    var cuisine by remember { mutableStateOf("All") }
    var selectedTab by remember { mutableIntStateOf(0) }

    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let(onAnalyze)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onAnalyze)
    }

    fun launchCamera() {
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        val file = File.createTempFile("food_", ".jpg", dir)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraUri = uri
        camera.launch(uri)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Recipe from Photo") }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    label = { Text("Search") },
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        context.startActivity(Intent(context, SearchActivity::class.java))
                    },
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("History") },
                    selected = selectedTab == 2,
                    onClick = {
                        selectedTab = 2
                        context.startActivity(Intent(context, HistoryActivity::class.java))
                    },
                )
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = ::launchCamera, Modifier.weight(1f)) {
                        Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(8.dp)); Text("Camera")
                    }
                    OutlinedButton(
                        onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text("Gallery")
                    }
                }
            }
            state.image?.let { uri ->
                item {
                    AsyncImage(
                        model = uri, contentDescription = "Selected dish",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)),
                    )
                }
            }
            if (state.loading) item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            }
            item { Text(state.source, style = MaterialTheme.typography.labelSmall) }
            if (state.labels.isNotEmpty()) item {
                Text("Detected: " + state.labels.take(6).joinToString(", "),
                    style = MaterialTheme.typography.bodySmall)
            }
            state.message?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.error) } }
            if (state.image == null && !state.loading) item {
                Text("Take or choose a photo of a dish, or pick a dish below.")
            }
            items(state.recipes) { RecipeCard(it) }

            item {
                Text("Not right? Pick a dish", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Indian", "Global").forEach { c ->
                        FilterChip(selected = cuisine == c, onClick = { cuisine = c }, label = { Text(c) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecipeBook.all.filter { cuisine == "All" || it.cuisine == cuisine }.forEach { r ->
                        AssistChip(onClick = { onPick(r) }, label = { Text(r.title) })
                    }
                }
            }
        }
    }
}

@Composable
fun RecipeCard(recipe: Recipe) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(recipe.title, style = MaterialTheme.typography.titleLarge)
            Text("Ingredients", style = MaterialTheme.typography.titleMedium)
            recipe.ingredients.forEach { Text("• $it") }
            Text("Instructions", style = MaterialTheme.typography.titleMedium)
            recipe.steps.forEachIndexed { i, s -> Text("${i + 1}. $s") }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RecipeScreenPreview() {
    MaterialTheme {
        RecipeScreen(
            state = UiState(
                source = "Food model (on-device ONNX)",
                labels = listOf("Margherita Pizza 95%", "Cheese 80%"),
                recipes = RecipeBook.all.take(2),
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RecipeCardPreview() {
    MaterialTheme {
        RecipeCard(recipe = RecipeBook.all.first())
    }
}

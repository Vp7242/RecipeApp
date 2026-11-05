package com.example.recipegen

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class UiState(
    val image: Uri? = null,
    val loading: Boolean = false,
    val source: String = "",
    val labels: List<String> = emptyList(),
    val recipes: List<Recipe> = emptyList(),
    val message: String? = null,
)

class RecipeViewModel(app: Application) : AndroidViewModel(app) {
    // 1) Dedicated food model (food_model.onnx in assets) - identifies the dish and its ingredients.
    private val foodModel: FoodClassifier? =
        if (FoodClassifier.isAvailable(app)) runCatching { FoodClassifier(app) }.getOrNull() else null

    // 2) Fallback when no model file is bundled: generic ML Kit labels matched to the recipe book.
    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(0.35f).build()
    )

    private val _state = MutableStateFlow(UiState(source = sourceName()))
    val state: StateFlow<UiState> = _state

    private fun sourceName() =
        if (foodModel != null) "Food model (on-device ONNX)" else "Generic ML Kit labels (add food_model.onnx for dish + ingredient detection)"

    fun analyze(uri: Uri) {
        _state.value = UiState(image = uri, loading = true, source = sourceName())
        viewModelScope.launch {
            try {
                if (foodModel != null) {
                    val preds = withContext(Dispatchers.Default) { foodModel.classify(uri, 3) }
                    _state.update {
                        it.copy(
                            loading = false,
                            labels = preds.map { (l, c) -> "${IngredientBook.pretty(l)} ${(c * 100).roundToInt()}%" },
                            recipes = IngredientBook.toRecipes(preds),
                        )
                    }
                } else {
                    val input = InputImage.fromFilePath(getApplication(), uri)
                    val labels = labeler.process(input).await().map { it.text to it.confidence }
                    val matches = RecipeBook.match(labels)
                    _state.update {
                        it.copy(
                            loading = false,
                            labels = labels.map { l -> l.first },
                            recipes = matches,
                            message = if (matches.isEmpty()) "Couldn't recognise a dish. Pick one from the list below." else null,
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, message = "Analysis failed: ${e.message}") }
            }
        }
    }

    fun pick(recipe: Recipe) = _state.update { it.copy(recipes = listOf(recipe), message = null) }

    override fun onCleared() { foodModel?.close() }
}

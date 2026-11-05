# Recipe from Photo – Kotlin Android app (on-device ML)

## Two modes
- **Full ML mode** – when `app/src/main/assets/food_model.onnx` + `labels.txt` exist, an ONNX Runtime
  classifier (`FoodClassifier.kt`) identifies the dish (top-3 with confidence) and `IngredientBook.kt`
  returns its ingredients (and full steps when the recipe book has the dish).
- **Fallback mode** – without those files, ML Kit generic labels are matched to the recipe book.

## Train the model on native Windows (NVIDIA GPU)
1. Install Python 3.11 and the latest NVIDIA driver.
2. PowerShell:
   py -3.11 -m venv venv ; .\venv\Scripts\Activate.ps1
   pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121
   pip install onnx pillow
   python -c "import torch; print(torch.cuda.is_available())"     # must print True
   python training\train_food_model_windows.py
3. It prints validation accuracy each epoch and writes `out\food_model.onnx` + `out\labels.txt`.
4. Copy both into `app\src\main\assets\` and rebuild in Android Studio.

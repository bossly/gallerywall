# Local AI Model Training and Mobile Tracing Guide

This guide walks you through the steps to:
1. Set up a local Python development environment with TensorFlow.
2. Compile and trace the default out-of-the-box model into the app assets folder.
3. Train a custom Conditional Generative Adversarial Network (WGAN-GP) on your own pixel art sprites, exporting the optimized model weights directly for mobile usage.

---

## 🛠️ Step 1: Environment Setup

Ensure you run these steps in a standard shell on your host machine (outside the sandboxed IDE terminal) to guarantee full access to your Python environment and hardware accelerators (CUDA/MPS).

1. Install the required deep learning and imaging dependencies:
   ```bash
   pip3 install tensorflow pillow numpy
   ```

2. Verify that TensorFlow can access your hardware accelerator (e.g. Apple Silicon GPU acceleration via metal plug-in):
   ```bash
   python3 -c "import tensorflow as tf; print('Physical Devices:', tf.config.list_physical_devices())"
   ```

---

## 🚀 Step 2: Generate the Default Asset Model

To compile, trace, and optimize the default model directly into the app assets folder, execute the pre-written script from your project root:

```bash
python3 scratch/generate_default_model.py
```

### What this does:
1. Instantiates a 10-class `PixelArtGenerator` with random weights using Keras.
2. Compiles the model with `noise` shape `(1, 100)` and `label` shape `(1, 1)`.
3. Runs the TensorFlow Lite converter to compile the graph into a lightweight mobile model.
4. Generates and saves `app/src/main/assets/pixel_art_model.tflite`.

Once completed, the Android application will work **instantly out-of-the-box** without any additional downloads!

---

## 🎨 Step 3: Train a Custom Pixel Art WGAN-GP Model

To train the generator on your custom pixel art sprites (e.g. tiles, character sheets, space backgrounds) and export it for mobile use:

### 1. Structure Your Dataset
Organize your training images inside directories named by style/category inside a `dataset` folder:
```text
dataset/
51── forest/
│   ├── grass_tile1.png
│   └── moss_tile2.png
├── cyberpunk/
│   ├── neon_grid1.png
│   └── city_tile2.png
└── space/
    ├── stars1.png
    └── galaxy2.png
```
*Note: Subdirectories are sorted alphabetically to map class label indices: `forest` = Class 0, `cyberpunk` = Class 1, `space` = Class 2, etc.*

### 2. Run the Training Script
Invoke the `train_pixel_art_gan.py` script. The script leverages **Wasserstein GAN with Gradient Penalty (WGAN-GP)** for maximum convergence stability on 64x64 textures:

```bash
python3 scratch/train_pixel_art_gan.py \
  --data_dir dataset \
  --output_path app/src/main/assets/pixel_art_model.tflite \
  --epochs 150 \
  --batch_size 64
```

### 3. Automated Zero-Code Class Mapping inside the App
When training finishes, a companion `.json` mapping file is generated **automatically** (e.g. `pixel_art_model.json`). 

Simply place **both** `pixel_art_model.tflite` and `pixel_art_model.json` inside the app's assets folder (or import them dynamically on the Providers tab). The Android application will dynamically read the mapping on model startup and map your style subdirectories to prompt keywords in real-time with **zero code modifications**!

For example, if you trained categories `cannon`, `chest`, and `coins`, your generated `pixel_art_model.json` will be:
```json
{
  "cannon": 0,
  "chest": 1,
  "coins": 2
}
```
If you request the word "chest" in the automation prompt, the engine will automatically resolve the style class label to index `1`!

---

## 🧪 Step 4: Test the Model on Mac

Use `scratch/infer_pixel_art.py` to run inference against any `.tflite` model directly on your Mac — no Android device needed. Outputs are saved as PNG tiles to `scratch/output/`.

### Install dependencies (one time)
```bash
pip3 install tensorflow pillow numpy
```

### Basic usage

```bash
# Single tile, random class
python3 scratch/infer_pixel_art.py

# Fixed seed for reproducible results
python3 scratch/infer_pixel_art.py --prompt "ocean" --seed 42 --scale 6

# 6 tiles stitched into one grid image
python3 scratch/infer_pixel_art.py --count 6 --scale 4 --grid --prompt "coin"
```

### All flags

| Flag | Default | Description |
|---|---|---|
| `--model` | `app/src/main/assets/pixel_art_model.tflite` | Path to the `.tflite` model file |
| `--prompt` | *(random class)* | Text keyword → class label (same mapping as the app) |
| `--label` | — | Override the class index directly (0–9) |
| `--count` | `1` | Number of tiles to generate |
| `--scale` | `1` | Upscale factor 1–12 (NEAREST interpolation for crisp pixel art) |
| `--seed` | *(random)* | Set for reproducible noise |
| `--grid` | off | Save all tiles as a single stitched grid PNG |
| `--out-dir` | `scratch/output` | Output directory for saved PNGs |

### Example output filenames

```
scratch/output/tile_label0_seed430837803_scale4x.png
scratch/output/grid_6tiles_label0_scale4x.png
```

### Prompt → class label mapping

The script uses the same keyword table as `MLImageEngine.kt`:

| Keyword(s) | Label |
|---|---|
| `coin` | 0 |
| `map` | 1 |
| `pirate`, `sea`, `ship` | 2 |
| `forest`, `green`, `tree`, `nature` | 0 |
| `cyber`, `neon`, `synth`, `future` | 1 |
| `space`, `star`, `galaxy`, `night` | 2 |
| `castle`, `dungeon`, `stone`, `retro` | 3 |
| `desert`, `sand`, `gold`, `sun` | 4 |
| `ocean`, `water` | 5 |
| `snow`, `ice`, `winter`, `cold` | 6 |
| `lava`, `fire`, `magma`, `red` | 7 |
| `candy`, `pink`, `sweet`, `cute` | 8 |
| *(anything else)* | 0 |

---

## ⚡ Performance Optimization Tips

* **Apple Silicon (Macs)**: The training script leverages GPU acceleration automatically on supported hardware through standard TensorFlow Metal plugins.
* **Palette Variety**: Use the app's visual Color Picker to apply real-time harmonious blends. The model generates raw structural grayscale pixel art shapes, which are then mapped to your custom color arrays dynamically on-device in under 5 milliseconds!

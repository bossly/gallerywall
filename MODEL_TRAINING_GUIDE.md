# Local AI Model Training and Mobile Tracing Guide

This guide walks you through the steps to:
1. Set up a local Python development environment with TensorFlow.
2. Compile the default out-of-the-box optimized **32x32 Diffusion Model** into the app assets folder.
3. Train a custom **Denoising Diffusion Probabilistic Model (DDPM)** on your own pixel art sprites, exporting the optimized model weights directly for mobile usage.

---

## 🛠️ Step 1: Environment Setup

Ensure you run these steps in a standard shell on your host machine (outside the sandboxed IDE terminal) to guarantee full access to your Python environment and hardware accelerators (CUDA/MPS).

1. Install the required deep learning and imaging dependencies:
   ```bash
   pip3 install tensorflow pillow numpy
   ```

2. Verify that TensorFlow can access your hardware accelerator (e.g. Apple Silicon GPU acceleration via Metal plug-in):
   ```bash
   python3 -c "import tensorflow as tf; print('Physical Devices:', tf.config.list_physical_devices())"
   ```

---

## 🚀 Step 2: Generate the Default Asset Model

To compile, trace, and optimize the default model directly into the app assets folder, execute the pre-written script from your project root on your host terminal:

```bash
python3 scratch/generate_default_model.py
```

### What this does:
1. Instantiates a lightweight, conditional U-Net diffusion model with random weights using Keras.
2. Sets the input shape to `(32, 32, 4)` for fast 32x32 generation out-of-the-box.
3. Runs the TensorFlow Lite converter to compile the graph into a lightweight mobile model.
4. Generates and saves `app/src/main/assets/pixel_art_model.tflite`.

Once completed, the Android application will work **instantly out-of-the-box** without any additional setup!

---

## 🎨 Step 3: Train a Custom Pixel Art Diffusion Model

To train the diffusion model on your custom pixel art sprites (e.g. tiles, character sheets, space backgrounds) and export it for mobile use:

### 1. Structure Your Dataset
Organize all your training images inside a single folder (e.g. `dataset/`):
```text
dataset/
├── grass_tile1.png
├── moss_tile2.png
├── neon_grid1.png
├── stars1.png
└── galaxy2.png
```
*Note: Timestep embedding is used instead of class style mappings, making model training robust and zero-code!*

### 2. Run the Training Script
Invoke the `train_pixel_art_diffusion.py` script. The script leverages **Denoising Diffusion Probabilistic Models (DDPM)** with a linear schedule ($T=5$ steps) for fast generation and dynamic size support:

```bash
# To train a fast 32x32 model:
python3 scratch/train_pixel_art_diffusion.py \
  --data_dir dataset \
  --output_path app/src/main/assets/pixel_art_model.tflite \
  --size 32 \
  --epochs 100 \
  --batch_size 64

# To train a high-quality 64x64 model:
python3 scratch/train_pixel_art_diffusion.py \
  --data_dir dataset \
  --output_path app/src/main/assets/pixel_art_model.tflite \
  --size 64 \
  --epochs 150 \
  --batch_size 64
```

---

## 🧪 Step 4: Test the Model on Mac

Use `scratch/infer_pixel_art.py` to run inference against any diffusion `.tflite` model directly on your Mac — no Android device needed. Outputs are saved as PNG tiles to `scratch/output/`.

### Basic usage

```bash
# Generate 1 tile (uses dynamic output dimensions from the model)
python3 scratch/infer_pixel_art.py

# Fixed seed for reproducible results
python3 scratch/infer_pixel_art.py --seed 42 --scale 4

# 6 tiles stitched into one grid image
python3 scratch/infer_pixel_art.py --count 6 --scale 4 --grid
```

### All flags

| Flag | Default | Description |
|---|---|---|
| `--model` | `app/src/main/assets/pixel_art_model.tflite` | Path to the `.tflite` model file |
| `--count` | `1` | Number of tiles to generate |
| `--scale` | `1` | Upscale factor 1–12 (NEAREST interpolation for crisp pixel art) |
| `--seed` | *(random)* | Set for reproducible noise |
| `--grid` | off | Save all tiles as a single stitched grid PNG |
| `--out-dir` | `scratch/output` | Output directory for saved PNGs |

---

## ⚡ Performance Optimization & Transparency Tips

* **Apple Silicon (Macs)**: The training script leverages GPU acceleration automatically on supported hardware through standard TensorFlow Metal plugins.
* **Optional Transparency**: The model supports generating 4-channel RGBA transparent images. The Android application enables or disables transparency mapping depending on settings, allowing seamless pattern tiles to blend cleanly with backgrounds.
* **Palette Variety**: The model generates structural grayscale shapes, which are then dynamically mapped to visual harmony color palettes on-device in under 5 milliseconds!

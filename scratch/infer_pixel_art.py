#!/usr/bin/env python3
"""
infer_pixel_art.py — Run the trained pixel_art_model.tflite on Mac using a 5-step DDPM loop.

Usage:
    python3 scratch/infer_pixel_art.py                          # generate 1 image
    python3 scratch/infer_pixel_art.py --count 8                # generate 8 tiles
    python3 scratch/infer_pixel_art.py --scale 4                # 4× upscale
    python3 scratch/infer_pixel_art.py --seed 42                # fixed seed
    python3 scratch/infer_pixel_art.py --grid                   # save all as a single grid image
    python3 scratch/infer_pixel_art.py --model path/to/my.tflite  # custom model path

Dependencies (install once):
    pip3 install tensorflow pillow numpy
"""

import argparse
import os
import random
import sys
from pathlib import Path

def load_model(model_path: str):
    """Load a .tflite model for desktop inference."""
    try:
        import tensorflow as tf
    except ImportError:
        print("ERROR: TensorFlow not installed. Run: pip3 install tensorflow pillow numpy")
        sys.exit(1)

    if not os.path.exists(model_path):
        print(f"ERROR: Model file not found: {model_path}")
        sys.exit(1)

    print(f"Loading TensorFlow Lite interpreter from: {model_path}")
    interpreter = tf.lite.Interpreter(model_path=str(model_path))
    interpreter.allocate_tensors()
    print("Model loaded successfully.")
    return interpreter


def generate_tile(interpreter, seed: int | None = None):
    """Run 5-step DDPM sampling loop and return a (H, W, 4) float array in [-1, 1]."""
    import numpy as np

    if seed is not None:
        np.random.seed(seed)
        random.seed(seed)

    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    # Find input indices dynamically by name
    x_t_idx = next(x['index'] for x in input_details if any(k in x['name'].lower() for k in ['x_t', 'image', 'input']))
    t_idx = next(x['index'] for x in input_details if any(k in x['name'].lower() for k in ['t', 'step']))

    # Get input shape [1, H, W, 4]
    x_t_shape = input_details[x_t_idx]['shape']
    height, width = x_t_shape[1], x_t_shape[2]

    # Precompute DDPM schedule parameters for T=5 steps
    T = 5
    betas = np.linspace(0.1, 0.9, T, dtype=np.float32)
    alphas = 1.0 - betas
    alphas_cumprod = np.cumprod(alphas)
    alphas_cumprod_prev = np.append(1.0, alphas_cumprod[:-1])

    sqrt_recip_alphas = np.sqrt(1.0 / alphas)
    sqrt_one_minus_alphas_cumprod = np.sqrt(1.0 - alphas_cumprod)
    posterior_variance = betas * (1.0 - alphas_cumprod_prev) / (1.0 - alphas_cumprod)
    sqrt_posterior_variance = np.sqrt(posterior_variance)

    # Start with pure normal Gaussian noise x_T ~ N(0, 1)
    x_t = np.random.normal(size=(1, height, width, 4)).astype(np.float32)

    for t in reversed(range(T)):
        t_data = np.array([[t]], dtype=np.int32)

        interpreter.set_tensor(x_t_idx, x_t)
        interpreter.set_tensor(t_idx, t_data)

        # Invoke model forward pass
        interpreter.invoke()

        # Output: predicted noise [1, H, W, 4]
        noise_pred = interpreter.get_tensor(output_details[0]['index'])

        # Compute x_{t-1} using DDPM update step with intermediate clipping
        recip_alpha = sqrt_recip_alphas[t]
        beta_term = betas[t] / sqrt_one_minus_alphas_cumprod[t]
        sigma = sqrt_posterior_variance[t] if t > 0 else 0.0
        z = np.random.normal(size=x_t.shape).astype(np.float32) if t > 0 else 0.0

        x_t = recip_alpha * (x_t - beta_term * noise_pred) + sigma * z
        x_t = np.clip(x_t, -1.0, 1.0)

    # Return the squeezed output [H, W, 4]
    return x_t[0]


def tensor_to_pil(tensor, scale: int = 1):
    """Convert a (H, W, 4) float array in [-1, 1] to a PIL Image, optionally upscaled."""
    from PIL import Image
    import numpy as np

    height, width, _ = tensor.shape
    # Rescale output [-1, 1] → [0, 255]
    pixel_array = np.clip((tensor + 1.0) * 127.5, 0, 255).astype(np.uint8)
    img = Image.fromarray(pixel_array, mode='RGBA')

    if scale > 1:
        new_size = (width * scale, height * scale)
        img = img.resize(new_size, Image.NEAREST)  # NEAREST keeps the pixel-art crisp

    return img


def save_grid(images, out_path: str, cols: int = 4):
    """Stitch a list of PIL images into a single grid image and save it."""
    from PIL import Image
    import math

    rows = math.ceil(len(images) / cols)
    w, h = images[0].size
    grid = Image.new("RGBA", (cols * w, rows * h), color=(0, 0, 0, 0))

    for idx, img in enumerate(images):
        col = idx % cols
        row = idx // cols
        grid.paste(img, (col * w, row * h))

    grid.save(out_path)
    return grid


def main():
    parser = argparse.ArgumentParser(
        description="Generate pixel art tiles from a DDPM .tflite model on Mac.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "--model",
        default="app/src/main/assets/pixel_art_model.tflite",
        help="Path to the .tflite model file (default: app/src/main/assets/pixel_art_model.tflite)",
    )
    parser.add_argument(
        "--count",
        type=int,
        default=1,
        help="Number of tiles to generate (default: 1).",
    )
    parser.add_argument(
        "--scale",
        type=int,
        default=1,
        choices=range(1, 13),
        metavar="1-12",
        help="Upscale factor applied to the model output (1=native, 2=2x, 4=4x, etc.). Default: 1.",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=None,
        help="Random seed for reproducible generation. Omit for random.",
    )
    parser.add_argument(
        "--grid",
        action="store_true",
        help="Save all generated tiles as a single grid image instead of individual files.",
    )
    parser.add_argument(
        "--out-dir",
        default="scratch/output",
        help="Directory to write PNG files into (default: scratch/output).",
    )
    args = parser.parse_args()

    # Resolve paths relative to project root
    project_root = Path(__file__).parent.parent
    model_path = (project_root / args.model).resolve()

    # Load model and determine input shape dynamically
    interpreter = load_model(str(model_path))
    input_details = interpreter.get_input_details()
    x_t_idx = next(x['index'] for x in input_details if any(k in x['name'].lower() for k in ['x_t', 'image', 'input']))
    x_t_shape = input_details[x_t_idx]['shape']
    height, width = x_t_shape[1], x_t_shape[2]

    print(f"Dynamic Model Resolution: {width}x{height} pixels")
    print(f"Count : {args.count}  |  Scale: {args.scale}×  |  Output size: {width * args.scale}×{height * args.scale}px")

    out_dir = (project_root / args.out_dir).resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    images = []
    for i in range(args.count):
        seed = args.seed if args.seed is not None else random.randint(0, 2**31 - 1)
        tensor = generate_tile(interpreter, seed=seed)
        img = tensor_to_pil(tensor, scale=args.scale)
        images.append((img, seed))
        if not args.grid:
            fname = f"tile_seed{seed}_scale{args.scale}x.png"
            fpath = out_dir / fname
            img.save(str(fpath))
            print(f"  [{i+1}/{args.count}] Saved → {fpath}")

    if args.grid:
        cols = min(4, args.count)
        fname = f"grid_{args.count}tiles_scale{args.scale}x.png"
        fpath = out_dir / fname
        save_grid([img for img, _ in images], str(fpath), cols=cols)
        print(f"Grid saved → {fpath}")

    print("\nDone! ✓")


if __name__ == "__main__":
    main()

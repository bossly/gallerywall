#!/usr/bin/env python3
"""
infer_pixel_art.py — Run the trained pixel_art_model.tflite on Mac and save PNG tiles.

Usage:
    python3 scratch/infer_pixel_art.py                          # random prompt, 1 image
    python3 scratch/infer_pixel_art.py --prompt forest          # specific prompt class
    python3 scratch/infer_pixel_art.py --count 8                # generate 8 tiles
    python3 scratch/infer_pixel_art.py --scale 4                # 4× upscale (256×256)
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

def prompt_to_label(prompt: str, model_path: Path | None = None) -> int:
    """Map a text prompt to a class label index using the model's companion JSON mapper."""
    p = prompt.lower()
    
    if model_path is not None:
        json_path = model_path.with_suffix('.json')
        if json_path.exists():
            try:
                import json
                with open(json_path, 'r') as f:
                    mapping = json.load(f)
                for keyword, label in mapping.items():
                    if keyword.lower() in p:
                        return int(label)
            except Exception as e:
                print(f"WARNING: Failed to parse companion JSON mapping: {e}")
                
    return 0  # default fallback


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


def generate_tile(interpreter, label: int, seed: int | None = None):
    """Run one forward pass and return a (64, 64, 4) float array in [-1, 1]."""
    import numpy as np

    if seed is not None:
        np.random.seed(seed)
        random.seed(seed)

    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    # Find input indices dynamically by name
    noise_idx = next(x['index'] for x in input_details if 'noise' in x['name'].lower())
    label_idx = next(x['index'] for x in input_details if 'label' in x['name'].lower())

    # Create input tensors
    noise_data = np.random.normal(size=(1, 100)).astype(np.float32)
    label_data = np.array([[label]], dtype=np.int32)

    # Set input tensors
    interpreter.set_tensor(noise_idx, noise_data)
    interpreter.set_tensor(label_idx, label_data)

    # Invoke TFLite model execution
    interpreter.invoke()

    # Retrieve output [1, 64, 64, 4] and squeeze batch dimension
    output_data = interpreter.get_tensor(output_details[0]['index'])
    return output_data[0]  # → [64, 64, 4]


def tensor_to_pil(tensor, scale: int = 1):
    """Convert a (64, 64, 4) float array in [-1, 1] to a PIL Image, optionally upscaled."""
    from PIL import Image
    import numpy as np

    # Rescale tanh output [-1, 1] → [0, 255]
    pixel_array = np.clip((tensor + 1.0) * 127.5, 0, 255).astype(np.uint8)
    img = Image.fromarray(pixel_array, mode='RGBA')

    if scale > 1:
        new_size = (64 * scale, 64 * scale)
        img = img.resize(new_size, Image.NEAREST)  # NEAREST keeps the pixel-art look crisp

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
        description="Generate pixel art tiles from a .tflite model on Mac.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "--model",
        default="app/src/main/assets/pixel_art_model.tflite",
        help="Path to the .tflite model file (default: app/src/main/assets/pixel_art_model.tflite)",
    )
    parser.add_argument(
        "--prompt",
        default="",
        help="Text prompt to select a style class (e.g. 'forest', 'neon', 'ocean'). "
             "Leave empty for random class.",
    )
    parser.add_argument(
        "--label",
        type=int,
        default=None,
        help="Directly specify the class label integer (overrides --prompt).",
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
        help="Upscale factor applied to the 64×64 output (1=64px, 2=128px, 4=256px … 12=768px). Default: 1.",
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

    # ── Resolve model path relative to the project root ───────────────────────
    project_root = Path(__file__).parent.parent
    model_path = (project_root / args.model).resolve()

    # ── Resolve label ──────────────────────────────────────────────────────────
    if args.label is not None:
        label = args.label
        prompt_display = f"label={label}"
    elif args.prompt:
        label = prompt_to_label(args.prompt, model_path)
        prompt_display = f"'{args.prompt}' → label={label}"
    else:
        label = random.randint(0, 9)
        prompt_display = f"random → label={label}"

    print(f"Prompt: {prompt_display}")
    print(f"Count : {args.count}  |  Scale: {args.scale}×  |  Output size: {64 * args.scale}×{64 * args.scale}px")

    # ── Load model ─────────────────────────────────────────────────────────────
    interpreter = load_model(str(model_path))

    # ── Generate ───────────────────────────────────────────────────────────────
    out_dir = (project_root / args.out_dir).resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    images = []
    for i in range(args.count):
        seed = args.seed if args.seed is not None else random.randint(0, 2**31 - 1)
        tensor = generate_tile(interpreter, label=label, seed=seed)
        img = tensor_to_pil(tensor, scale=args.scale)
        images.append((img, seed))
        if not args.grid:
            fname = f"tile_label{label}_seed{seed}_scale{args.scale}x.png"
            fpath = out_dir / fname
            img.save(str(fpath))
            print(f"  [{i+1}/{args.count}] Saved → {fpath}")

    # ── Grid output ───────────────────────────────────────────────────────────
    if args.grid:
        cols = min(4, args.count)
        fname = f"grid_{args.count}tiles_label{label}_scale{args.scale}x.png"
        fpath = out_dir / fname
        save_grid([img for img, _ in images], str(fpath), cols=cols)
        print(f"Grid saved → {fpath}")

    print("\nDone! ✓")


if __name__ == "__main__":
    main()

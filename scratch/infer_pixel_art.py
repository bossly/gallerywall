#!/usr/bin/env python3
"""
infer_pixel_art.py — Run the trained pixel_art_model.ptl on Mac and save PNG tiles.

Usage:
    python3 scratch/infer_pixel_art.py                          # random prompt, 1 image
    python3 scratch/infer_pixel_art.py --prompt forest          # specific prompt class
    python3 scratch/infer_pixel_art.py --count 8                # generate 8 tiles
    python3 scratch/infer_pixel_art.py --scale 4                # 4× upscale (256×256)
    python3 scratch/infer_pixel_art.py --seed 42                # fixed seed
    python3 scratch/infer_pixel_art.py --grid                   # save all as a single grid image
    python3 scratch/infer_pixel_art.py --model path/to/my.ptl   # custom model path

Dependencies (install once):
    pip3 install torch torchvision pillow
"""

import argparse
import os
import random
import sys
from pathlib import Path

# ── Prompt → class label map (mirrors MLImageEngine.kt) ───────────────────────
PROMPT_MAP = {
    "coin":    0,
    "map":     1,
    "pirate":  2,
    "sea":     2,
    "ship":    2,
    "forest":  0,
    "green":   0,
    "tree":    0,
    "nature":  0,
    "cyber":   1,
    "neon":    1,
    "synth":   1,
    "future":  1,
    "space":   2,
    "star":    2,
    "galaxy":  2,
    "night":   2,
    "castle":  3,
    "dungeon": 3,
    "stone":   3,
    "retro":   3,
    "desert":  4,
    "sand":    4,
    "gold":    4,
    "sun":     4,
    "ocean":   5,
    "water":   5,
    "snow":    6,
    "ice":     6,
    "winter":  6,
    "cold":    6,
    "lava":    7,
    "fire":    7,
    "magma":   7,
    "red":     7,
    "candy":   8,
    "pink":    8,
    "sweet":   8,
    "cute":    8,
}


def prompt_to_label(prompt: str) -> int:
    """Map a text prompt to a class label index (same logic as Kotlin mapPromptToClassLabel)."""
    p = prompt.lower()
    for keyword, label in PROMPT_MAP.items():
        if keyword in p:
            return label
    return 0  # default fallback


def load_model(model_path: str):
    """Load a .ptl (PyTorch Lite / TorchScript) model for desktop inference."""
    try:
        import torch
    except ImportError:
        print("ERROR: PyTorch not installed. Run: pip3 install torch torchvision pillow")
        sys.exit(1)

    if not os.path.exists(model_path):
        print(f"ERROR: Model file not found: {model_path}")
        sys.exit(1)

    print(f"Loading model from: {model_path}")
    model = torch.jit.load(model_path, map_location="cpu")
    model.eval()
    print("Model loaded successfully.")
    return model


def generate_tile(model, label: int, seed: int | None = None):
    """Run one forward pass and return a (3, 64, 64) float tensor in [-1, 1]."""
    import torch

    if seed is not None:
        torch.manual_seed(seed)
        random.seed(seed)

    noise = torch.randn(1, 100)                              # z ~ N(0,1), shape [1, 100]
    label_tensor = torch.tensor([label], dtype=torch.long)  # class label, shape [1]

    with torch.no_grad():
        output = model(noise, label_tensor)  # shape [1, 3, 64, 64]

    return output.squeeze(0)  # → [3, 64, 64]


def tensor_to_pil(tensor, scale: int = 1):
    """Convert a (3, 64, 64) tensor in [-1, 1] to a PIL Image, optionally upscaled."""
    from PIL import Image
    import torch

    # Rescale tanh output [-1, 1] → [0, 255]
    pixel_array = ((tensor + 1.0) * 127.5).clamp(0, 255).byte()
    # Rearrange from CHW → HWC
    img_np = pixel_array.permute(1, 2, 0).numpy()
    img = Image.fromarray(img_np)

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
    grid = Image.new("RGB", (cols * w, rows * h), color=(20, 20, 20))

    for idx, img in enumerate(images):
        col = idx % cols
        row = idx // cols
        grid.paste(img, (col * w, row * h))

    grid.save(out_path)
    return grid


def main():
    parser = argparse.ArgumentParser(
        description="Generate pixel art tiles from a .ptl model on Mac.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "--model",
        default="app/src/main/assets/pixel_art_model.ptl",
        help="Path to the .ptl model file (default: app/src/main/assets/pixel_art_model.ptl)",
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
        label = prompt_to_label(args.prompt)
        prompt_display = f"'{args.prompt}' → label={label}"
    else:
        label = random.randint(0, 9)
        prompt_display = f"random → label={label}"

    print(f"Prompt: {prompt_display}")
    print(f"Count : {args.count}  |  Scale: {args.scale}×  |  Output size: {64 * args.scale}×{64 * args.scale}px")

    # ── Load model ─────────────────────────────────────────────────────────────
    model = load_model(str(model_path))

    # ── Generate ───────────────────────────────────────────────────────────────
    out_dir = (project_root / args.out_dir).resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    images = []
    for i in range(args.count):
        seed = args.seed if args.seed is not None else random.randint(0, 2**31 - 1)
        tensor = generate_tile(model, label=label, seed=seed)
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

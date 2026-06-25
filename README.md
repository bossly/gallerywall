![Header](/fastlane/metadata/android/en-US/images/featureGraphic.png)

# GalleryWall: AI Wallpaper Generator (Local-First)

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![The most recent tag](https://img.shields.io/github/v/release/bossly/gallerywall.svg?logo=github)](https://github.com/bossly/gallerywall/tags)
[![API](https://img.shields.io/badge/API-36%2B-orange.svg?logo=android)](https://android-arsenal.com/api?level=36)
[![StandWithUkraine](https://raw.githubusercontent.com/vshymanskyy/StandWithUkraine/main/badges/StandWithUkraine.svg)](https://github.com/vshymanskyy/StandWithUkraine/blob/main/docs/README.md)

Starting with **Version 3.0**, GalleryWall has pivoted to leverage a **generative on-device AI model**. GalleryWall is an open-source, local-first Android agentic app that generates beautiful wallpapers using AI models. It performs local inference directly on your device to ensure privacy, zero latency, and offline support.

## Downloads

[![GooglePlay](https://play.google.com/intl/en_us/badges/images/badge_new.png)](https://play.google.com/store/apps/details?id=com.baysoft.gallerywall)

## Screenshots

| ![Gallery](/fastlane/metadata/android/en-US/images/phoneScreenshots/screen1.png) | ![Providers](/fastlane/metadata/android/en-US/images/phoneScreenshots/screen2.png) | ![Automation screen](/fastlane/metadata/android/en-US/images/phoneScreenshots/screen3.png) |
|-------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|

## Features

- **Generative On-Device Models**: Performs local inference to generate custom wallpapers directly on your device without sending private data to external servers.
- **Three-Tab Navigation**:
  - **Gallery**: View and manage recently generated wallpapers grouped by date and filtered by provider.
  - **Providers**: Manage wallpaper providers. Select the local model provider by default, choose local models, or download new ones directly from GitHub.
  - **Automation**: Configure agentic background automation to periodically refresh your wallpaper using a prompt builder and custom intervals.
- **Image Filters & Custom Styling**:
  - Apply post-processing filters: **Black & White**, **Sepia**, **Invert**, and **Blur**.
  - Scaling and styling options: **Fit**, **Fill**, and **Seamless Pattern Tile**.
- **Seamless repeating patterns**:
  - Applying a Gradient Map (Half-Cosine falloff filter) to the edges of the Bitmap scales down the image's opacity radially toward the borders. This eliminates the visible hard stitching line or seam, allowing any generated image to tile seamlessly as a background.
- Direct boot support (immediately active after device restart)
- No ads and no analytics.

## Contribution

If you run into a bug or miss a feature, please [open an issue](https://github.com/bossly/gallerywall/issues) in this repository.

### Custom Providers & Extensibility

GalleryWall supports a modular strategy pattern for adding custom wallpaper generators. Developers can register custom providers (for example, remote cloud generators powered by the **Firebase AI SDK** / **Vertex AI Imagen**) using the compile-time and runtime registry.

For a detailed step-by-step walkthrough on creating your own custom wallpaper provider, see the [Custom Wallpaper Provider Guide](../custom_provider.md).

---

## Local AI Model Setup

By default, GalleryWall uses local on-device models (such as Stable Diffusion). 
- Stable Diffusion model file (`stable_diffusion_v1_5.zip` ~1.78 GB) and other model configurations are managed via a JSON manifest.
- Model list format example:
  ```json
  {
    "fileSize": 1789496203,
    "url": "https://huggingface.co/runwayml/stable-diffusion-v1-5/resolve/main/v1-5-pruned.safetensors?download=true",
    "sha256": "a0978e01159311d0a29e078b0dbf9a5c40d35f1e8e3f47cc0d3446c722f8316f"
  }
  ```

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

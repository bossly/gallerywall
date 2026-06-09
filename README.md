# GalleryWall

[![Actions Status](https://github.com/bossly/gallerywall/workflows/Create%20Release/badge.svg)](https://github.com/bossly/gallerywall/actions)
[![The most recent tag](https://img.shields.io/github/v/release/bossly/gallerywall.svg?logo=github)](https://github.com/bossly/gallerywall/tags)
[![API](https://img.shields.io/badge/API-36%2B-orange.svg?logo=android)](https://android-arsenal.com/api?level=36)
[![StandWithUkraine](https://raw.githubusercontent.com/vshymanskyy/StandWithUkraine/main/badges/StandWithUkraine.svg)](https://github.com/vshymanskyy/StandWithUkraine/blob/main/docs/README.md)

[![GooglePlay](https://play.google.com/intl/en_us/badges/images/badge_new.png)](https://play.google.com/store/apps/details?id=com.baysoft.gallerywall)

| ![Primary screen](screens/screen1.png) | ![Secondary screen](screens/screen2.png) | ![Widget screen](screens/screen3.png) |
|-|-|-|

Starting with **Version 3.0**, GalleryWall has pivoted to leverage a **generative on-device AI model** (using TensorFlow Lite) to generate custom wallpapers locally on your phone (no cloud, no subscription).

### Key Features in v3.0:
- **Generative On-Device Models**: Performs local inference (e.g., using lightweight Diffusion/GAN models via TensorFlow Lite) to generate crisp, high-quality pixel art and custom wallpapers directly on your device.
- **Three-Tab Navigation**:
  - **Recents**: View and manage recently generated wallpapers grouped by date and filtered by provider.
  - **Providers**: List and download locally-run model packages from GitHub.
  - **Automation**: Configure settings to periodically change your wallpaper using custom prompts and custom intervals.
- **Seamless Tile Patterns**: Built-in support to create seamlessly repeating tile patterns by applying gradient maps to edges.
- **Image Filters**: Apply post-processing filters like Black & White, Sepia, Invert, Blur, and customize tile alignment (scale, rotate, blend tiles, border color).
- **Optimization**: Highly optimized for low battery and resource usage using Android's system `WorkManager` APIs. Fully compliant with modern Android standards, targeting Android 15+ and supporting 16 KB page-aligned native libraries.

# References

* [4pda](http://4pda.ru/forum/index.php?showtopic=158065&st=2660#entry29540977) - 21/02/2014
* [http://androidforums.com](https://androidforums.com/threads/free-automation-wallpaper-switcher.831706/#post6458208) - 25/02/2014
* [reddit: AppHunt](https://www.reddit.com/r/AppHunt/comments/l66fjs/gallerywall_automated_wallpaper/)
* [reddit: fossdroid](https://www.reddit.com/r/fossdroid/comments/l66has/apache_20_gallerywall_opensourced_automated/)
* [reddit: androidapps](https://www.reddit.com/r/androidapps/comments/l628f4/gallerywall_opensourced_automated_wallpaper_app/)
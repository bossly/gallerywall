# GalleryWall

[![Actions Status](https://github.com/bossly/gallerywall/workflows/Create%20Release/badge.svg)](https://github.com/bossly/gallerywall/actions)
[![The most recent tag](https://img.shields.io/github/v/release/bossly/gallerywall.svg?logo=github)](https://github.com/bossly/gallerywall/tags)
[![API](https://img.shields.io/badge/API-36%2B-orange.svg?logo=android)](https://android-arsenal.com/api?level=36)
[![StandWithUkraine](https://raw.githubusercontent.com/vshymanskyy/StandWithUkraine/main/badges/StandWithUkraine.svg)](https://github.com/vshymanskyy/StandWithUkraine/blob/main/docs/README.md)

[![GooglePlay](https://play.google.com/intl/en_us/badges/images/badge_new.png)](https://play.google.com/store/apps/details?id=com.baysoft.gallerywall)

| ![Primary screen](screens/screen1.png) | ![Secondary screen](screens/screen2.png) | ![Widget screen](screens/screen3.png) |
|-|-|-|

# v3.0 - AI Wallpaper Generator (Local-First)

## Overview

Starting with **Version 3.0**, GalleryWall has pivoted to leverage a **generative on-device AI model**.

GalleryWall is an open-source Android app that generate wallpaper using ai models.

- **Generative On-Device Models**: Performs local inference custom wallpapers directly on your device.

- **Three-Tab Navigation**:
  - **Recents**: View and manage recently generated wallpapers grouped by date and filtered by provider.
  - **Providers**: List of providers with local model provider by default. When local provider is selected allow to select local models or download from GitHub to device.
  - **Automation**: Configure settings to periodically change your wallpaper using prompt builder and custom intervals using selected provider.
  - **Image Filters**: Apply post-processing filters like Black & White, Sepia, Invert, Blur, and customize scaling, rotation, tiling options with blending and tile alignment.
  - **Seamless Patterns**: To create a seamless repeating pattern from an image, you can apply a Gradient Map (also known as a Half-Cosine falloff filter) to the edges of your Bitmap. This algorithm scales down the image's opacity radially toward the borders, eliminating the visible hard "seam" or stitching line.The following Kotlin code utilizes.

## Design

Use Material 3 Expressive
Support screens: phones, tablets, TV, Android Auto and WearOS 

ideas from:
 https://github.com/patzly/doodle-android


 ```
 Code refactoring:
 
 Add guide how to add and implement Remote Provider. Guide should include:
 - architecture of Remote Provider
 - Remote Provider interface
 - Remote Provider implementation
 
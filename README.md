# GalleryWall

[![Actions Status](https://github.com/bossly/gallerywall/workflows/Android%20CI/badge.svg)](https://github.com/bossly/gallerywall/actions) 
[![Build status](https://build.appcenter.ms/v0.1/apps/8fb9dae8-8516-4110-805d-54b677ecc1a6/branches/master/badge)](https://appcenter.ms)

[![GooglePlay](https://play.google.com/intl/en_us/badges/images/badge_new.png)](https://play.google.com/store/apps/details?id=com.baysoft.gallerywall)

| ![Primary screen](screens/screen1.png) | ![Secondary screen](screens/screen2.png) | ![Widget screen](screens/screen3.png) |
|-|-|-|

Android application to refresh your wallpaper with in duration that can be set in settings. Each wallpaper picture comes from the free source following the any topic that been typed as search query.

It's optimized for the low battery usage using latest system's API.

# Setup 

Currently there only Pixabay service used to fetch wallpaper images.

Edit `~/.gradle/gradle.properties` with your api key from https://pixabay.com/service/about/api/


```groovy
api_pixabay=<your api key>
```

# References

* [4pda](http://4pda.ru/forum/index.php?showtopic=158065&st=2660#entry29540977) - 21/02/2014
* [http://androidforums.com](https://androidforums.com/threads/free-automation-wallpaper-switcher.831706/#post6458208) - 25/02/2014

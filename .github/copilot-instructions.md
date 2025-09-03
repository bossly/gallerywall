# Project overview 


## Fastlane

Fastlane is used to keep metadata for playstore which includes: tilte, description, screenshots and changelog

### When adding new feature

- run ui test and generate new screenshots in metadata/android/images
- update full_description.txt with one line text in features section

### When changing build number in `build.gradle`

- add changelog with <build number>.txt adding short description text with changes
- 
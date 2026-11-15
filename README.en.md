<p align="center"><img src="docs/images/notier-logo.png" width="80" alt="Notier logo, a white N on a black background"></p>

<h1 align="center">Notier</h1>

<p align="center">A minimal Markdown notebook for Android. Pick a folder, write your notes, and read them in preview.</p>

<p align="center"><a href="README.md">Türkçe</a> · <b>English</b> · <a href="README.ru.md">Русский</a></p>

![Notier interface illustration showing the light note list and the dark Markdown editor with sharp pixel-style icons.](docs/images/notier-overview.svg)

<sub>Interface illustration · the light note list and dark Markdown editor.</sub>

**Kotlin · Android 8 or later · GNU GPL v3**

[APK releases](https://github.com/nullnode1/notier-app/releases) · [Build and use guide (Turkish)](docs/BUILD.tr.md)

## What you can do

- **Work from a folder.** Pick a folder on your device, search your notes, and sort by title or date. Choose a classic list, compact list, or two-column grid.
- **Write and read Markdown.** Switch between source and preview. Use shortcuts for headings, tasks, links, and code, then copy a code block with one tap in preview.
- **Browse Drive folders.** With your permission, Notier opens existing folders and lets you create, move, or trash Markdown notes. Folder cards count Markdown notes.
- **Make it yours.** Choose a light or dark theme, 13 typefaces, and Lucide, crisp pixel, or bold icons. Adjust text, interface, and button sizes separately. The interface supports 42 languages.

## Write your first note

1. Use the folder button at the top left to choose a folder on your device. Notier lists its `.md` and `.markdown` files.
2. Tap **+**, enter a title, and write your note. **Save** uses the title as the filename.
3. Tap the **eye** to read the formatted preview or the **pencil** to return to Markdown source. Existing notes open in preview.
4. To make a separate file, use **Save as** in the note menu. Long-press a note in the list to pin, rename, or delete it.

~~~markdown
# The weekend

- [x] Choose a route
- [ ] Pack the bag

> Keep short notes here.
~~~

### Local folders and Google Drive

| Task | On-device folder | Google Drive |
| --- | --- | --- |
| Access | Grant folder access through the Android picker | Choose an account and grant access in the cloud view |
| Note location | The folder you picked on your device | The Drive folder open in Notier |
| Reload | Pull down or tap the refresh icon | Use the same controls to request a fresh Drive listing |

Preview supports tables, task lists, code blocks, and links. Android System WebView can load remote images, audio, and video referenced by a note. To disconnect your Google account, use **Drive account → Sign out of Drive**.

## Install on Android

Open the signed APK from the repository’s **Releases** section on your Android device. At first launch, use the folder button at the top left and choose where to keep your notes. Drive is optional. Open the cloud view to choose a Google account and grant access.

## Build with Termux

Extract the project under your Termux home directory. Adjust the `cd` command if you used a different folder name.

```bash
pkg install openjdk-21 aapt2 d8 apksigner python curl unzip
cd ~/Notier
bash derle.sh
```

The build writes `build/Notier.apk`. For Drive in a self-built APK, register its Android package and signing certificate in Google Cloud. See the [build and OAuth guide in Turkish](docs/BUILD.tr.md).

## Where to start in the code

Notier uses native Android views written in Kotlin. `MainActivity.kt` coordinates the screens and navigation. File access, Drive, draft storage, and Markdown rendering live in separate modules.

| First change | Source |
| --- | --- |
| A filename or extension rule | [NoteNames.kt](app/src/main/kotlin/app/notier/NoteNames.kt) |
| An editor shortcut | [MarkdownShortcut.kt](app/src/main/kotlin/app/notier/MarkdownShortcut.kt) |
| Local saving and draft recovery | [Notes.kt](app/src/main/kotlin/app/notier/Notes.kt) and [DraftStore.kt](app/src/main/kotlin/app/notier/DraftStore.kt) |
| Google Drive requests | [DriveApi.kt](app/src/main/kotlin/app/notier/DriveApi.kt) |
| Markdown preview | [Markdown.kt](app/src/main/kotlin/app/notier/Markdown.kt) |

For a first contribution, save the sample note locally. Open it, edit it, save a copy, and reopen that copy. Include your Android version, Notier version, and steps to reproduce when reporting a problem. [Report an issue](https://github.com/nullnode1/notier-app/issues).


## Privacy and license

Open **Settings → About** for the version, developer, GitHub repository, and offline policy text.

- The [Privacy Policy](docs/PRIVACY.md) explains what Notier accesses on the device and in Drive, how it handles that data, and how to revoke access.
- Notier’s source is released under the [GNU General Public License, version 3](LICENSE). See [NOTICE](NOTICE) for the limited Google Play services linking permission and third-party notices.

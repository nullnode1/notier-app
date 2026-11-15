# Notier Privacy Policy

[English](PRIVACY.md) · [Türkçe](PRIVACY.tr.md) · [Русский](PRIVACY.ru.md)

Last updated 15 November 2026.

## Notes on your device

Notier is a Markdown editor for Android. It opens notes in the folder you choose through Android’s file picker. Android may remember this folder or individual file permissions so you can open them again. Notier keeps settings, pinned-note references, and any unsaved draft in its private app storage on your device. The app can be used with local files without a Google account.

## Google Drive

Drive is optional. If you connect it, Google handles account selection and authorization. Notier uses the `https://www.googleapis.com/auth/drive` scope to browse existing folders, identify Markdown notes, open and save them, and perform changes you request, such as moving a note or sending it to Trash. Notier reads file and folder metadata, such as names, types, identifiers, modification dates, and capabilities, to build the list and count notes. It reads Markdown content when you open a Drive note.

To restore a session, Notier can keep the selected Google account name and last Drive folder identifier in private app storage. The access token used by the app and its short-lived listing cache are held in memory. Google Play services manages authorization on the device. If you open notes through Android’s file picker instead, Notier may retain the grants and references Android provides for the files you selected.

## How data is used and shared

Notier makes Drive requests directly from your device to Google. The app has no Notier backend, advertising SDK, or usage-analytics service. Notier does not send Drive notes to its developer. If you choose to share or export a note through Android, the destination you select receives it. Markdown previews can request remote images, audio, or video from the services referenced in a note. Those services can receive your IP address and ordinary request data when the media loads.

Notier’s use and transfer of information received from Google APIs adheres to the [Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy), including its Limited Use requirements.

## Your choices and deletion

In Notier, open **Google Drive → Drive account → Sign out of Drive** to clear the direct Drive session and request that Google revoke the app’s access. You can check or remove access in your [Google Account connections](https://myaccount.google.com/connections). Files in Drive remain in your Drive account unless you move them to Trash or delete them. For files added through Android’s picker, use **Remove from list** to remove the entry and release its saved grant.

Clearing Notier’s app data in Android settings removes local app settings, remembered entries, and unsaved drafts. Notes you saved as files in device folders or Drive can be managed from those locations.

## Contact

For privacy questions, use the **Issues** section of the Notier repository. Describe the issue without posting note contents, account credentials, or access tokens.

package app.notier

import android.accounts.Account
import android.accounts.AccountManager
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.text.Editable
import android.text.Html
import android.text.InputType
import android.text.TextWatcher
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.MimeTypeMap
import android.webkit.WebChromeClient
import android.webkit.WebMessage
import android.webkit.WebMessagePort
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.BaseAdapter
import android.widget.AbsListView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.HorizontalScrollView
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.AccountPicker
import com.google.android.gms.common.api.Scope
import java.io.ByteArrayInputStream
import java.text.Collator
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.max
import kotlin.math.roundToInt

internal class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("notier", MODE_PRIVATE) }
    private val notes by lazy { Notes(contentResolver) }
    private val drive by lazy { DriveDocuments(contentResolver, notes) }
    private val drafts by lazy { DraftStore(filesDir) }
    private val css by lazy { assets.open("preview.css").bufferedReader().use { it.readText() } }
    private val copyIcon by lazy { assets.open("copy.svg").bufferedReader().use { it.readText() } }
    private val copyScript by lazy { assets.open("copy.js").bufferedReader().use { it.readText() } }
    private val fonts = mutableMapOf<NoteFont, Typeface>()
    private val boldFonts = mutableMapOf<NoteFont, Typeface>()
    private val appIcons by lazy { AppIcons(this) }
    private val titleOrder by lazy { Collator.getInstance(appLocale).apply { strength = Collator.SECONDARY } }
    private val dateFormat by lazy { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, appLocale) }
    private val appLocale get() = resources.configuration.locales[0]
    private var notierTheme = NotierTheme.LIGHT
    private val palette get() = notierTheme.palette
    private var noteFont = NoteFont.GEIST
    private var preferredIcons: IconStyle? = null
    private val iconStyle get() = preferredIcons ?: if (notierTheme.pixel) IconStyle.PIXEL else IconStyle.LUCIDE
    private var textScale = 1f
    private var interfaceScale = 1f
    private var buttonScale = 1f
    private var cornerScale = 1f
    private var listLayout = 0
    private var pinned = setOf<String>()
    private val driveCountWorker = Executors.newSingleThreadExecutor()
    private var driveCountTask: Future<*>? = null
    private var driveCounts = emptyMap<String, Int>()
    private val main = Handler(Looper.getMainLooper())
    private val saveDraft = Runnable { persistDraft() }
    private var folderUri: Uri? = null
    private var folderName = ""
    private var localHasSubfolders = false
    private var files = emptyList<NoteFile>()
    private var driveFiles = emptyList<NoteFile>()
    private var driveScreen = false
    private var driveUnavailable = 0
    private var driveLoading = false
    private var folderLoading = false
    private var driveApi: DriveApi? = null
    private var driveFolder: DriveItem? = null
    private var driveRoot: DriveItem? = null
    private var driveFolderId = "root"
    private var driveAccountName: String? = null
    private var pendingDriveAccount: String? = null
    private val drivePages = android.util.LruCache<String, DrivePage>(12)
    private val driveCountCache = android.util.LruCache<String, Int>(128)
    private var reconnectDrive = false
    private var authorizingDrive = false
    private var driveConsentPending = false
    private var driveNeedsConsent = false
    private var driveRenewalAttempted = false
    private var driveRequest = 0
    private var folderError: String? = null
    private var query = ""
    private var sortByTitle = false
    private var homeListIndex = 0
    private var homeListOffset = 0
    private var startLoading = true
    private var folderRequest = 0
    private var busy = false
    private var draft: Draft? = null
    private var editing = false
    private var leavingAfterSave = false
    private var pickerPending = false
    private var keepNoteAfterRecreate = false
    private var resumed = false
    private var pendingUpdates = false
    private var shownDialog: AlertDialog? = null
    private var lastForegroundAttempt: Long? = null
    private var pendingCopyUri: Uri? = null
    private var pendingCopyFlags = 0
    private var applyingEdit = false
    private val undo = ArrayDeque<EditorChange>()
    private val redo = ArrayDeque<EditorChange>()

    private lateinit var root: FrameLayout
    private lateinit var homeList: AbsListView
    private lateinit var loadingRow: View
    private lateinit var emptyPanel: View
    private lateinit var emptyIcon: ImageView
    private lateinit var homeAdapter: NoteAdapter
    private lateinit var emptyText: TextView
    private lateinit var emptyTitle: TextView
    private lateinit var emptyAction: TextView
    private lateinit var homeSubtitle: TextView
    private lateinit var driveNav: LinearLayout
    private lateinit var driveNavLabel: TextView
    private lateinit var driveNavUp: ImageButton
    private lateinit var driveNavRoot: ImageButton
    private lateinit var driveNavAdd: ImageButton
    private lateinit var searchField: EditText
    private lateinit var editor: EditText
    private lateinit var markdownBar: HorizontalScrollView
    private lateinit var undoButton: ImageButton
    private lateinit var redoButton: ImageButton
    private lateinit var titleField: EditText
    private lateinit var modeTitle: TextView
    private lateinit var noteSubtitle: TextView
    private lateinit var saveButton: ImageButton
    private lateinit var contentsButton: ImageButton
    private lateinit var content: FrameLayout
    private lateinit var fullscreen: FrameLayout
    private var preview: WebView? = null
    private var outline = emptyList<OutlineHeading>()
    private var copyPort: WebMessagePort? = null
    private var fullScreenCallback: WebChromeClient.CustomViewCallback? = null

    override fun attachBaseContext(base: Context) {
        val language = base.getSharedPreferences("notier", MODE_PRIVATE).getString("language", "tr")
        val configuration = android.content.res.Configuration(base.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language?.takeIf { code -> languages.any { it.first == code } } ?: "tr"))
        super.attachBaseContext(base.createConfigurationContext(configuration))
    }

    override fun onCreate(state: Bundle?) {
        notierTheme = NotierTheme.from(prefs.getString("theme", null))
        setTheme(notierTheme.style)
        super.onCreate(state)
        pendingUpdates = state == null && intent?.action == Updates.OPEN_UPDATES
        Updates.ensureScheduled(this)
        folderUri = prefs.getString("folder", null)?.let(Uri::parse)
        driveScreen = prefs.getBoolean("drive_screen", false)
        reconnectDrive = prefs.getBoolean("direct_drive", false)
        driveConsentPending = state?.getBoolean("driveConsentPending") ?: false
        authorizingDrive = driveConsentPending
        driveAccountName = prefs.getString("drive_account_name", null)
        pendingDriveAccount = state?.getString("pendingDriveAccount")
        driveFolderId = prefs.getString("drive_folder", "root") ?: "root"
        noteFont = NoteFont.from(prefs.getString("note_font", null))
        preferredIcons = prefs.getString("icon_style", null)?.let(IconStyle::from)
        loadScales()
        pinned = prefs.getStringSet("pinned", emptySet()).orEmpty().toSet()
        sortByTitle = prefs.getBoolean("sort_by_title", false)
        query = state?.getString("query").orEmpty()
        homeListIndex = state?.getInt("homeListIndex") ?: 0
        homeListOffset = state?.getInt("homeListOffset") ?: 0
        pickerPending = state?.getBoolean("pickerPending") ?: false
        leavingAfterSave = state?.getBoolean("leavingAfterSave") ?: false
        root = FrameLayout(this).apply {
            setBackgroundColor(palette.paper)
            isFocusableInTouchMode = true
        }
        setContentView(root)
        configureInsets()
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) { navigateBack() }
        }
        showHome()
        background({ drafts.read() }) { result ->
            startLoading = false
            val recovered = result.getOrNull()
            if (recovered != null && draft == null) {
                showNote(recovered, state?.getBoolean("editing") ?: false)
                if (driveScreen && reconnectDrive) connectDrive(interactive = false)
                val position = state?.getInt("editorSelection", -1) ?: -1
                if (position >= 0) editor.setSelection(position.coerceAtMost(editor.length()))
                if (!recovered.dirty && !pickerPending) persistDraft()
            } else if (draft == null) {
                updateHome()
                reloadNotes()
            }
            if (result.isFailure) toast(s(R.string.draft_error))
            pendingCopyUri?.let { uri ->
                pendingCopyUri = null
                onCopySelected(uri, pendingCopyFlags)
            }
            showLaunchPanel()
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        showLaunchPanel()
        preview?.onResume()
        if (!startLoading && draft == null && !busy) reloadNotes()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::root.isInitialized) root.post { showLaunchPanel() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action != Updates.OPEN_UPDATES) return
        pendingUpdates = true
        showLaunchPanel()
    }

    private fun showLaunchPanel() {
        if (!resumed || startLoading || pickerPending || isFinishing || isDestroyed) return
        if (shownDialog?.isShowing == true) return
        if (pendingUpdates) {
            pendingUpdates = false
            showUpdates(fromSettings = false)
            return
        }
        if (!prefs.getBoolean("welcome_completed", false)) {
            if (shownDialog?.isShowing != true && draft == null && !busy) showWelcome()
            return
        }
        if (draft == null && !busy) {
            val release = Updates.cached(this).announcement(Updates.installedVersion(this))
            if (release != null && release.announcement !in deferredAnnouncements &&
                prefs.getString("read_release_announcement", null) != release.announcement) {
                deferredAnnouncements.add(release.announcement)
                showReleaseNotes(release, automatic = true)
            }
        }
        refreshReleaseAnnouncements()
    }

    private fun refreshReleaseAnnouncements() {
        if (!Updates.online(this)) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (lastForegroundAttempt?.let { now - it < 5 * 60_000L } == true) return
        if (System.currentTimeMillis() - Updates.cached(this).checkedAt in 0 until Updates.FOREGROUND_INTERVAL) return
        lastForegroundAttempt = now
        fetchReleases { showLaunchPanel() }
    }

    private fun fetchReleases(deliver: (Updates.Check) -> Unit) {
        val context = applicationContext
        kotlin.concurrent.thread(name = "Notier GitHub releases") {
            val result = Updates.check(context)
            main.post { if (!isDestroyed && !isFinishing) deliver(result) }
        }
    }

    private fun showWelcome() {
        val rows = settingsColumn()
        val dialog = settingsDialog(s(R.string.welcome_title), rows)
        val welcome = label(s(R.string.welcome_description), 15f, palette.muted).apply {
            setPaddingRelative(dp(6), dp(5), dp(6), dp(14))
        }
        rows.addView(welcome)
        var childOpened = false
        fun option(title: Int, icon: Int, detail: String = "", open: () -> Unit) {
            rows.addView(settingsRow(s(title), detail, leading = icon, navigate = true).apply {
                setOnClickListener {
                    childOpened = true
                    dialog.dismiss()
                    open()
                }
            })
        }
        val language = currentLanguage()
        option(R.string.theme, R.drawable.ic_sun_moon, s(notierTheme.title)) {
            val themes = NotierTheme.entries
            showChoices(R.string.theme, themes.map { s(it.title) }, themes.indexOf(notierTheme), ::showWelcome) {
                changeTheme(themes[it])
                showWelcome()
            }
        }
        option(R.string.language, R.drawable.ic_languages, languages[language].second) {
            chooseLanguage(::showWelcome)
        }
        option(R.string.release_notes, R.drawable.ic_book_open) { showReleaseNotes(up = ::showWelcome) }
        option(R.string.check_updates, R.drawable.ic_cloud_download) { showUpdates(returnTo = ::showWelcome) }
        rows.addView(dialogAction(R.string.welcome_start) { dialog.dismiss() })
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener {
            if (!childOpened && !isChangingConfigurations && !isDestroyed && !isFinishing) {
                prefs.edit().putBoolean("welcome_completed", true).apply()
                root.post { showLaunchPanel() }
            }
        }
        showDialog(dialog)
    }

    override fun onPause() {
        resumed = false
        if (draft == null && !startLoading) rememberListPosition()
        main.removeCallbacks(saveDraft)
        persistDraft()
        preview?.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("query", query)
        outState.putInt("homeListIndex", homeListIndex)
        outState.putInt("homeListOffset", homeListOffset)
        if (draft != null && ::editor.isInitialized) outState.putInt("editorSelection", editor.selectionEnd)
        outState.putBoolean("editing", editing)
        outState.putBoolean("pickerPending", pickerPending)
        outState.putBoolean("driveConsentPending", driveConsentPending)
        outState.putString("pendingDriveAccount", pendingDriveAccount)
        outState.putBoolean("leavingAfterSave", leavingAfterSave)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        destroyPreview()
        driveCountWorker.shutdownNow()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun configureInsets() {
        val isLight = notierTheme != NotierTheme.DARK
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(palette.paper))
        if (Build.VERSION.SDK_INT >= 29) {
            root.isForceDarkAllowed = false
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT < 30) {
            window.statusBarColor = palette.paper
            window.navigationBarColor = palette.paper
            val lightBars = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            val current = window.decorView.systemUiVisibility
            window.decorView.systemUiVisibility = if (isLight) current or lightBars else current and lightBars.inv()
            return
        }
        window.setDecorFitsSystemWindows(false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        val lightBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(
            if (isLight) lightBars else 0, lightBars
        )
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsets.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, max(bars.bottom, keyboard.bottom))
            WindowInsets.CONSUMED
        }
        root.requestApplyInsets()
    }

    private fun showHome() {
        draft = null
        editing = false
        destroyPreview()
        root.removeAllViews()
        val home = FrameLayout(this)
        root.addView(home, frameMatch())
        home.post { if (draft == null) showLaunchPanel() }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        home.addView(column, frameMatch())

        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(dp(16), dp(12), dp(16), dp(4))
        }
        top.addView(ImageView(this).apply {
            setImageResource(R.drawable.app_icon)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, linear(30, 30).apply { marginEnd = dp(10) })
        top.addView(label("Notier", 24f, bold = true).apply {
            letterSpacing = -.035f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        top.addView(icon(R.drawable.ic_list, s(R.string.navigation), 20, quiet = true) { anchor ->
            showMenu(anchor, listOf(
                MenuAction(R.string.local_notes, R.drawable.ic_hard_drive, selected = !driveScreen) { switchStorage(false) },
                MenuAction(R.string.drive_area, R.drawable.ic_cloud, selected = driveScreen) { switchStorage(true) },
                if (driveScreen) MenuAction(R.string.drive_account, R.drawable.ic_log_in) { showDriveAccount() }
                else MenuAction(R.string.choose_folder, R.drawable.ic_folder_open) { chooseFolder() },
                MenuAction(R.string.updates, R.drawable.ic_cloud_download) { showUpdates(fromSettings = false) },
                MenuAction(R.string.release_notes, R.drawable.ic_book_open) { showReleaseNotes() },
                MenuAction(R.string.about_source, R.drawable.ic_code) { openProjectLink(Updates.PROJECT, onlineRequired = true) },
                MenuAction(R.string.project_site, R.drawable.ic_link_2) { openProjectLink(Updates.SITE, onlineRequired = true) }
            ))
        }, buttonLayout())
        top.addView(icon(R.drawable.ic_settings, s(R.string.settings), 21) { showSettings() }, buttonLayout())
        column.addView(top, LinearLayout.LayoutParams(MATCH, WRAP))

        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(dp(16), dp(4), dp(16), dp(4))
        }
        val pickerLabel = if (driveScreen) R.string.drive_account else R.string.choose_folder
        bar.addView(icon(if (driveScreen) R.drawable.ic_cloud else R.drawable.ic_folder_open, s(pickerLabel), 21) {
            if (driveScreen) showDriveAccount() else chooseFolder()
        }, buttonLayout())
        val searchBox = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = searchSurface(false)
        }
        val magnifier = FrameLayout(this).apply {
            addView(glyph(R.drawable.ic_search, palette.muted), FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
        }
        searchBox.addView(magnifier, LinearLayout.LayoutParams(dp(32), MATCH))
        searchField = EditText(this).apply {
            setTextColor(palette.ink)
            setHintTextColor(palette.muted)
            hint = s(if (driveScreen) R.string.search_drive_hint else R.string.search_hint)
            textSize = 15f * textScale
            setSingleLine()
            background = null
            inputType = InputType.TYPE_CLASS_TEXT
            applyTextFont()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPaddingRelative(0, 0, dp(8), 0)
            setText(query)
            setOnFocusChangeListener { _, focused -> searchBox.background = searchSurface(focused) }
        }
        searchBox.addView(searchField, LinearLayout.LayoutParams(0, MATCH, 1f))
        val clearSearch = icon(R.drawable.ic_x, s(R.string.clear_search), 16, quiet = true) {
            searchField.setText("")
        }
        clearSearch.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
        searchBox.addView(clearSearch, LinearLayout.LayoutParams(px(44f), MATCH))
        searchField.apply {
            watch {
                homeListIndex = 0
                homeListOffset = 0
                query = it
                clearSearch.visibility = if (it.isEmpty()) View.GONE else View.VISIBLE
                updateHome()
                homeList.setSelection(0)
            }
            setOnEditorActionListener { _, action, _ ->
                if (action != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
                hideKeyboard()
                searchField.clearFocus()
                true
            }
        }
        bar.addView(searchBox, LinearLayout.LayoutParams(0, buttonDp(48), 1f).apply {
            marginStart = dp(6)
        })
        column.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(10))
        }
        val headingRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        headingRow.addView(label(s(if (driveScreen) R.string.drive_area else R.string.notes), if (driveScreen) 27f else 34f, bold = true).apply {
            letterSpacing = -.035f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            accessibilityHeadingIfSupported()
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fun controlSize() = LinearLayout.LayoutParams(
            buttonDp(44).coerceAtMost(px((resources.configuration.screenWidthDp / 8f).coerceAtLeast(44f))), buttonDp(44))
        controls.addView(icon(R.drawable.ic_arrow_down_up, s(R.string.sort_notes), 18, quiet = true) { anchor ->
            showMenu(anchor, listOf(
                MenuAction(R.string.newest, R.drawable.ic_clock_3, selected = !sortByTitle) { setSortOrder(false) },
                MenuAction(R.string.sort_title, R.drawable.ic_arrow_down_a_z, selected = sortByTitle) { setSortOrder(true) }
            ))
        }, controlSize())
        val refreshButton = icon(R.drawable.ic_refresh_cw, s(R.string.refresh_notes), 15, quiet = true) {
            refreshFromGesture()
        }
        controls.addView(refreshButton, controlSize())
        controls.addView(icon(
            if (driveScreen) R.drawable.ic_hard_drive else R.drawable.ic_cloud,
            s(if (driveScreen) R.string.local_notes else R.string.drive_area), 19, quiet = true
        ) {
            switchStorage(!driveScreen)
        }, controlSize())
        headingRow.addView(controls, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(4) })
        heading.addView(headingRow)
        homeSubtitle = label("", 14f, palette.muted).apply {
            setPadding(0, dp(4), 0, 0)
            maxLines = if (driveScreen) 2 else 1
            ellipsize = TextUtils.TruncateAt.END
        }
        heading.addView(homeSubtitle)
        column.addView(heading)

        driveNavLabel = label("", 14f, bold = true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(dp(9), 0, dp(6), 0)
        }
        driveNavUp = icon(R.drawable.ic_arrow_up, s(R.string.parent_folder), 18, quiet = true) {
            driveFolder?.parent?.let(::openDriveFolder)
        }
        driveNavRoot = icon(R.drawable.ic_hard_drive, s(R.string.my_drive), 18, quiet = true) { openDriveFolder("root") }
        driveNavAdd = icon(R.drawable.ic_folder_plus, s(R.string.new_folder), 18, quiet = true) { createDriveFolder() }
        driveNav = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(palette.surface, 13, palette.line)
            minimumHeight = buttonDp(44)
            addView(driveNavUp, controlSize())
            addView(driveNavRoot, controlSize())
            addView(driveNavLabel, LinearLayout.LayoutParams(0, MATCH, 1f))
            addView(driveNavAdd, controlSize())
        }
        column.addView(driveNav, LinearLayout.LayoutParams(MATCH, WRAP).apply {
            marginStart = dp(16)
            marginEnd = dp(16)
            bottomMargin = dp(8)
        })

        val listArea = RefreshArea(this, ::refreshFromGesture)
        column.addView(listArea, LinearLayout.LayoutParams(MATCH, 0, 1f).apply {
            bottomMargin = buttonDp(64) + dp(24)
        })
        val listColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(dp(16), 0, dp(16), 0)
        }
        listArea.addView(listColumn, FrameLayout.LayoutParams(MATCH, WRAP))
        homeAdapter = NoteAdapter()
        homeList = (if (listLayout == 2) GridView(this).apply {
            numColumns = 2
            horizontalSpacing = dp(8)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
        } else ListView(this).apply { divider = null }).apply {
            adapter = homeAdapter
            selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            isDrawSelectorOnTop = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            setOnItemClickListener { _, _, position, _ ->
                if (!busy && !startLoading && (!driveScreen || (!driveLoading && !authorizingDrive))) openNote(homeAdapter.items[position])
            }
            setOnItemLongClickListener { _, row, position, _ ->
                if (!busy && !startLoading && (!driveScreen || (!driveLoading && !authorizingDrive))) showNoteActions(row, homeAdapter.items[position])
                true
            }
        }
        listArea.scrollingList = homeList
        listColumn.addView(homeList, LinearLayout.LayoutParams(MATCH, WRAP, 1f))
        emptyIcon = glyph(R.drawable.ic_file_text, palette.muted)
        emptyTitle = label("", 19f, bold = true).apply { gravity = Gravity.CENTER }
        emptyText = label("", 14f, palette.muted).apply {
            gravity = Gravity.CENTER
            lineHeightIfSupported(24)
        }
        emptyAction = label("", 14f, palette.onAccent, bold = true).apply {
            gravity = Gravity.CENTER
            minimumHeight = buttonDp(44)
            setPaddingRelative(dp(16), dp(8), dp(16), dp(8))
            background = pressSurface(palette.accent, 12)
            isFocusable = true
        }
        emptyPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPaddingRelative(dp(18), dp(21), dp(18), dp(21))
            background = rounded(palette.surface, 16, palette.line)
            addView(emptyIcon, linear(25, 25).apply { bottomMargin = dp(10) })
            addView(emptyTitle, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(emptyText, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(5) })
            addView(emptyAction, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(12) })
        }
        listColumn.addView(emptyPanel, LinearLayout.LayoutParams(MATCH, WRAP))
        loadingRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(10))
            addView(ProgressBar(this@MainActivity).apply {
                indeterminateTintList = ColorStateList.valueOf(palette.muted)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, linear(16, 16).apply { marginEnd = dp(8) })
            addView(label(s(R.string.loading_notes), 13f, palette.muted))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        listColumn.addView(loadingRow, LinearLayout.LayoutParams(MATCH, WRAP))
        listArea.onPull = { fraction ->
            refreshButton.rotation = fraction * 165f
        }

        home.addView(
            icon(R.drawable.ic_plus, s(R.string.new_note), 34) { newNote() }.apply { elevation = dp(2).toFloat() },
            FrameLayout.LayoutParams(buttonDp(64), buttonDp(64), Gravity.BOTTOM or Gravity.END).apply {
                marginEnd = dp(16)
                bottomMargin = dp(16)
            }
        )
        updateHome()
        root.requestFocus()
    }

    private fun showSettings() {
        if (busy || startLoading) return
        hideKeyboard()
        val choices = settingsColumn()
        val dialog = settingsDialog(s(R.string.settings), choices)
        fun row(card: LinearLayout, text: Int, icon: Int, value: String, action: () -> Unit) {
            val item = settingsRow(s(text), value, leading = icon, navigate = true, surface = Color.TRANSPARENT)
            item.setOnClickListener { dialog.dismiss(); action() }
            card.addView(item)
        }

        val appearance = settingsGroup(choices, R.string.appearance)
        val layouts = listOf(s(R.string.layout_list), s(R.string.layout_compact), s(R.string.layout_grid))
        row(appearance, R.string.layout, R.drawable.ic_list, layouts[listLayout]) {
            showChoices(R.string.layout, layouts, listLayout) { index ->
                listLayout = index
                prefs.edit().putInt("list_layout", index).apply()
                refreshScreen()
                showSettings()
            }
        }
        row(appearance, R.string.theme, R.drawable.ic_sun_moon, s(notierTheme.title)) {
            val themes = NotierTheme.entries
            showChoices(R.string.theme, themes.map { s(it.title) }, themes.indexOf(notierTheme)) { index ->
                changeTheme(themes[index])
                showSettings()
            }
        }
        row(appearance, R.string.font, R.drawable.ic_type, fontName(noteFont)) { showFonts() }
        row(appearance, R.string.icon_style, R.drawable.ic_square_mouse_pointer, s(iconStyle.label)) { showIconStyles() }
        row(appearance, R.string.text_size, R.drawable.ic_text_cursor_input, percentage(textScale)) {
            showScaleSlider(R.string.text_size, "text_scale", textScale, 75, 175)
        }
        row(appearance, R.string.interface_size, R.drawable.ic_scaling, percentage(interfaceScale)) {
            showScaleSlider(R.string.interface_size, "interface_scale", interfaceScale, 75, 140)
        }
        row(appearance, R.string.button_size, R.drawable.ic_square_mouse_pointer, percentage(buttonScale)) {
            showScaleSlider(R.string.button_size, "button_scale", buttonScale, 75, 145)
        }
        row(appearance, R.string.corners, R.drawable.ic_square_mouse_pointer, percentage(cornerScale)) {
            showScaleSlider(R.string.corners, "corner_scale", cornerScale, 0, 200)
        }
        val reset = settingsRow(s(R.string.reset_sizes), "", leading = R.drawable.ic_rotate_ccw, surface = Color.TRANSPARENT)
        reset.setOnClickListener {
            dialog.dismiss()
            prefs.edit().remove("text_scale").remove("interface_scale").remove("button_scale").remove("corner_scale").apply()
            loadScales()
            refreshScreen()
            showSettings()
        }
        reset.isEnabled = textScale != 1f || interfaceScale != 1f || buttonScale != 1f || cornerScale != 1f
        reset.alpha = if (reset.isEnabled) 1f else .5f
        appearance.addView(reset)

        val general = settingsGroup(choices, R.string.general)
        if (driveScreen) row(general, R.string.drive_account, R.drawable.ic_cloud,
            s(if (driveApi == null) R.string.drive_connect else R.string.drive_connected)) { showDriveAccount() }
        row(general, R.string.language, R.drawable.ic_languages, languages[currentLanguage()].second) {
            chooseLanguage(::showSettings)
        }
        row(general, R.string.about, R.drawable.ic_circle_help, "") { showAbout() }
        row(general, R.string.updates, R.drawable.ic_cloud_download, Updates.installedVersion(this)) { showUpdates() }
        row(general, R.string.release_notes, R.drawable.ic_book_open, "") { showReleaseNotes(up = ::showSettings) }
        showDialog(dialog)
    }

    private fun currentLanguage() =
        languages.indexOfFirst { it.first == prefs.getString("language", "tr") }.coerceAtLeast(0)

    private fun chooseLanguage(back: () -> Unit) {
        val selected = currentLanguage()
        showChoices(R.string.language, languages.map { it.second }, selected, back) { pick ->
            if (pick == selected) back()
            else {
                rememberSettingsState()
                prefs.edit().putString("language", languages[pick].first).apply()
                recreate()
            }
        }
    }

    private fun showUpdates(fromSettings: Boolean = true, returnTo: () -> Unit = ::showSettings) {
        val rows = settingsColumn()
        val dialog = settingsDialog(s(R.string.updates), rows, up = fromSettings, onUp = returnTo)
        val version = Updates.installedVersion(this)
        val header = label(s(R.string.updates_installed, version), 15f, bold = true).apply {
            setPaddingRelative(dp(12), dp(5), dp(12), dp(5))
        }
        val status = label("", 15f).apply {
            setPaddingRelative(dp(12), dp(3), dp(12), dp(3))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val checked = label("", 12f, palette.muted).apply {
            setPaddingRelative(dp(12), dp(3), dp(12), dp(9))
        }
        val message = label("", 13f, palette.muted).apply {
            setPaddingRelative(dp(12), 0, dp(12), dp(9))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        rows.addView(header)
        rows.addView(status)
        rows.addView(checked)
        rows.addView(message)

        val download = settingsRow(s(R.string.updates_download), "", leading = R.drawable.ic_file_down, navigate = true)
        val openRelease = settingsRow(s(R.string.updates_open), "", leading = R.drawable.ic_link_2, navigate = true)
        var result = Updates.cached(this)
        fun showResult() {
            val newer = result.available(version)
            status.text = when {
                newer != null -> s(R.string.updates_available, newer.tag)
                result.checkedAt == 0L -> ""
                result.release != null -> s(R.string.updates_current)
                else -> s(R.string.updates_no_release)
            }
            status.visibility = if (status.text.isEmpty()) View.GONE else View.VISIBLE
            checked.text = if (result.checkedAt == 0L) s(R.string.updates_never_checked)
                else s(R.string.updates_last_checked, dateFormat.format(Date(result.checkedAt)))
            message.text = when (result.issue) {
                Updates.Issue.OFFLINE -> s(R.string.updates_offline)
                Updates.Issue.RATE_LIMITED -> s(R.string.updates_rate_limited)
                Updates.Issue.UNAVAILABLE -> s(R.string.updates_failed)
                null -> ""
            }
            message.visibility = if (message.text.isEmpty()) View.GONE else View.VISIBLE
            download.visibility = if (newer == null) View.GONE else View.VISIBLE
            openRelease.visibility = if (newer == null) View.GONE else View.VISIBLE
            if (newer != null) {
                download.setOnClickListener { openProjectLink(newer.apk, onlineRequired = true) }
                openRelease.setOnClickListener { openProjectLink(newer.url, onlineRequired = true) }
            }
        }
        val check = settingsRow(s(R.string.check_updates), "", leading = R.drawable.ic_refresh_cw)
        fun runCheck() {
            if (!check.isEnabled) return
            check.isEnabled = false
            check.alpha = .55f
            message.visibility = View.VISIBLE
            message.text = s(R.string.updates_checking)
            fetchReleases { refreshed ->
                if (dialog.isShowing) {
                    check.isEnabled = true
                    check.alpha = 1f
                    result = refreshed
                    showResult()
                }
            }
        }
        check.setOnClickListener { runCheck() }
        rows.addView(check)
        rows.addView(download)
        rows.addView(openRelease)
        showResult()

        val notifications = settingsRow(s(R.string.update_notifications), s(R.string.update_notifications_hint),
            leading = R.drawable.ic_cloud)
        val enabled = Switch(this).apply {
            contentDescription = s(R.string.update_notifications)
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = ColorStateList(states, intArrayOf(palette.onAccent, palette.muted))
            trackTintList = ColorStateList(states, intArrayOf(palette.accent, palette.line))
        }
        fun updateToggle() {
            enabled.isChecked = Updates.notificationsEnabled(this)
        }
        notifications.addView(enabled, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(8) })
        updateToggle()
        notifications.setOnClickListener { enabled.performClick() }
        enabled.setOnClickListener {
            when {
                Updates.notificationsEnabled(this) -> Updates.changeNotifications(this, false)
                Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                    updatePermissionResult = {
                        updateToggle()
                        if (Updates.notificationsEnabled(this) && dialog.isShowing) runCheck()
                    }
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), UPDATE_PERMISSION)
                }
                !Updates.canNotify(this) -> toast(s(R.string.update_permission_denied))
                !Updates.changeNotifications(this, true) -> toast(s(R.string.updates_failed))
                else -> runCheck()
            }
            updateToggle()
        }
        rows.addView(notifications)
        rows.addView(settingsRow(s(R.string.release_notes), "", leading = R.drawable.ic_book_open, navigate = true).apply {
            setOnClickListener {
                dialog.dismiss()
                showReleaseNotes(up = { showUpdates(fromSettings, returnTo) })
            }
        })
        rowProjectWebsite(rows)
        showDialog(dialog)
        if (result.issue != Updates.Issue.OFFLINE && System.currentTimeMillis() - result.checkedAt >= 5 * 60_000L)
            runCheck()
    }

    private fun rowProjectWebsite(rows: LinearLayout) {
        rows.addView(settingsRow(s(R.string.project_site), "", leading = R.drawable.ic_link_2, navigate = true).apply {
            setOnClickListener { openProjectLink(Updates.SITE, onlineRequired = true) }
        })
    }

    private fun dialogAction(title: Int, primary: Boolean = true, action: () -> Unit) =
        label(s(title), 14f, if (primary) palette.onAccent else palette.ink, bold = true).apply {
            gravity = Gravity.CENTER
            background = pressSurface(if (primary) palette.accent else palette.surface, 12)
            minimumHeight = buttonDp(44)
            setPaddingRelative(dp(10), dp(8), dp(10), dp(8))
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(6) }
        }

    private fun showReleaseNotes(release: Updates.Release? = null, up: (() -> Unit)? = null,
                                 automatic: Boolean = false) {
        if (release == null) {
            showReleaseArchive(up)
            return
        }
        val rows = settingsColumn()
        val dialog = settingsDialog(s(R.string.release_notes), rows, up = up != null, onUp = { up?.invoke() })
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(dp(16), dp(16), dp(16), dp(16))
            background = rounded(palette.surface, 17, palette.line)
            val version = if (Updates.versionIsNewer(release.tag, Updates.installedVersion(this@MainActivity)))
                s(R.string.updates_available, release.tag) else release.tag
            val date = runCatching { Date.from(java.time.Instant.parse(release.publishedAt)) }.getOrNull()
            addView(label(listOfNotNull(version, date?.let(dateFormat::format)).joinToString(" · "),
                13f, palette.muted, bold = true))
            addView(label(release.title, 24f, bold = true).apply {
                accessibilityHeadingIfSupported()
                setPaddingRelative(0, dp(7), 0, 0)
            })
        }
        rows.addView(hero, LinearLayout.LayoutParams(MATCH, WRAP))
        if (automatic) {
            rows.addView(dialogAction(R.string.release_read) {
                prefs.edit().putString("read_release_announcement", release.announcement).apply()
                dialog.dismiss()
            })
            rows.addView(dialogAction(R.string.release_later, primary = false) { dialog.dismiss() })
        }
        rows.addView(label("", 15f).apply {
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            setPaddingRelative(dp(5), dp(16), dp(5), dp(8))
            setLineSpacing(dp(3).toFloat(), 1f)
            text = if (release.body.isBlank()) s(R.string.release_no_body) else
                Html.fromHtml(Markdown.article(release.body), Html.FROM_HTML_MODE_LEGACY).trimEnd()
        })
        if (Updates.versionIsNewer(release.tag, Updates.installedVersion(this))) {
            rows.addView(settingsRow(s(R.string.updates_download), "", leading = R.drawable.ic_file_down, navigate = true).apply {
                setOnClickListener { openProjectLink(release.apk, onlineRequired = true) }
            })
        }
        rows.addView(settingsRow(s(R.string.release_repository), "", leading = R.drawable.ic_code, navigate = true).apply {
            setOnClickListener { openProjectLink(release.url, onlineRequired = true) }
        })
        if (automatic) rows.addView(settingsRow(s(R.string.release_archive), "", leading = R.drawable.ic_list, navigate = true).apply {
            setOnClickListener {
                dialog.dismiss()
                showReleaseArchive(null)
            }
        })
        showDialog(dialog)
    }

    private fun showReleaseArchive(up: (() -> Unit)?) {
        val rows = settingsColumn()
        val dialog = settingsDialog(s(R.string.release_archive), rows, up = up != null, onUp = { up?.invoke() })
        val initial = Updates.cached(this)
        fun render(result: Updates.Check, checking: Boolean = false) {
            rows.removeAllViews()
            val message = when {
                checking -> R.string.updates_checking
                result.issue == Updates.Issue.OFFLINE -> R.string.updates_offline
                result.issue == Updates.Issue.RATE_LIMITED -> R.string.updates_rate_limited
                result.issue == Updates.Issue.UNAVAILABLE -> R.string.updates_failed
                result.releases.isEmpty() -> R.string.release_empty
                else -> null
            }
            if (message != null) rows.addView(label(s(message), 14f, palette.muted).apply {
                setPaddingRelative(dp(7), dp(5), dp(7), dp(9))
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            })
            for (release in result.releases) {
                rows.addView(settingsRow(release.title, release.tag, leading = R.drawable.ic_book_open, navigate = true).apply {
                    setOnClickListener {
                        dialog.dismiss()
                        showReleaseNotes(release, up = { showReleaseArchive(up) })
                    }
                })
            }
        }
        render(initial, checking = initial.issue != Updates.Issue.OFFLINE)
        showDialog(dialog)
        if (initial.issue != Updates.Issue.OFFLINE) fetchReleases { if (dialog.isShowing) render(it) }
    }

    private var updatePermissionResult: (() -> Unit)? = null

    override fun onRequestPermissionsResult(request: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(request, permissions, results)
        if (request != UPDATE_PERMISSION) return
        if (!Updates.canNotify(this)) toast(s(R.string.update_permission_denied))
        else if (!Updates.changeNotifications(this, true)) toast(s(R.string.updates_failed))
        updatePermissionResult?.invoke()
        updatePermissionResult = null
    }

    private fun showIconStyles() {
        val rows = settingsColumn()
        val dialog = settingsDialog(s(R.string.icon_style), rows, up = true)
        for (style in IconStyle.entries) {
            val row = settingsRow(s(style.label), "", selected = style == iconStyle,
                leading = R.drawable.ic_folder_open, sampleIconStyle = style)
            row.setOnClickListener {
                dialog.dismiss()
                preferredIcons = style
                prefs.edit().putString("icon_style", style.key).apply()
                refreshScreen()
                showSettings()
            }
            rows.addView(row)
        }
        showDialog(dialog)
    }

    private fun previewCopyIcon() = when (iconStyle) {
        IconStyle.LUCIDE -> copyIcon
        IconStyle.BOLD -> copyIcon.replace("stroke-width=\"2\"", "stroke-width=\"3\"")
        IconStyle.PIXEL -> """
            <svg class="copy-icon" width="24" height="24" viewBox="0 0 16 16" aria-hidden="true"
                 xmlns="http://www.w3.org/2000/svg">
                <path fill="#302726" d="M1 1H12V4H15V15H4V12H1Z"/>
                <path fill="#C38646" d="M2 2H10V4H4V10H2Z"/>
                <path fill="#FFE8B3" d="M5 5H14V14H5Z"/>
                <path fill="#795130" d="M7 7H12V8H7ZM7 10H11V11H7Z"/>
            </svg>
        """.trimIndent()
    }

    private fun showAbout() {
        val rows = settingsColumn()
        val dialog = settingsDialog(s(R.string.about), rows, up = true)
        @Suppress("DEPRECATION")
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "v1"
        rows.addView(settingsRow("Notier", s(R.string.about_version, version), leading = R.drawable.app_icon))
        rows.addView(label(s(R.string.about_description), 14f, palette.muted).apply {
            setPaddingRelative(dp(10), dp(10), dp(10), dp(6))
        })
        val project = settingsGroup(rows, R.string.about_project)
        val legal = settingsGroup(rows, R.string.about_legal)

        fun item(group: LinearLayout, title: Int, detail: String, glyph: Int, action: () -> Unit) {
            group.addView(settingsRow(s(title), detail, leading = glyph, navigate = true, surface = Color.TRANSPARENT).apply {
                setOnClickListener {
                    dialog.dismiss()
                    action()
                }
            })
        }
        item(project, R.string.about_developer, "@nullnode1", R.drawable.ic_code) {
            openProjectLink("https://github.com/nullnode1")
        }
        item(project, R.string.about_source, "nullnode1/notier-app", R.drawable.ic_link_2) {
            openProjectLink(Updates.PROJECT, onlineRequired = true)
        }
        item(project, R.string.project_site, "", R.drawable.ic_link_2) {
            openProjectLink(Updates.SITE, onlineRequired = true)
        }
        item(legal, R.string.about_license, "GNU GPL v3", R.drawable.ic_file_text) {
            showAboutDocument(R.string.about_license, "LICENSE")
        }
        item(legal, R.string.about_privacy, "", R.drawable.ic_eye) {
            val file = when (appLocale.language) {
                "tr" -> "PRIVACY.tr.md"
                "ru" -> "PRIVACY.ru.md"
                else -> "PRIVACY.md"
            }
            showAboutDocument(R.string.about_privacy, file)
        }
        item(legal, R.string.about_notices, "", R.drawable.ic_list) {
            showAboutDocument(R.string.about_notices, "NOTICE")
        }
        showDialog(dialog)
    }

    private fun openProjectLink(url: String, onlineRequired: Boolean = false) {
        if (onlineRequired && !Updates.online(this)) {
            toast(s(R.string.updates_offline))
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            toast(s(R.string.open_link_error))
        }
    }

    private fun showAboutDocument(title: Int, file: String) {
        val rows = settingsColumn()
        val dialog = settingsDialog(s(title), rows, up = true, onUp = ::showAbout)
        val raw = runCatching { assets.open("legal/$file").bufferedReader().use { it.readText() } }.getOrNull()
        if (raw == null) {
            toast(s(R.string.note_open_error))
            showAbout()
            return
        }
        if (file.endsWith(".md")) {
            for (paragraph in raw.trim().split("\n\n")) {
                if (paragraph.startsWith("# ") || paragraph.startsWith("[English]")) continue
                val heading = paragraph.startsWith('#')
                val displayText = paragraph.trimStart('#', ' ')
                    .replace(Regex("\\[([^]]+)]\\((https://[^)]+)\\)"), "$1 ($2)").replace("**", "").replace("`", "")
                rows.addView(label(displayText, if (heading) 17f else 14f, bold = heading).apply {
                    setPaddingRelative(dp(5), dp(7), dp(5), dp(7))
                    if (heading) accessibilityHeadingIfSupported()
                    android.text.util.Linkify.addLinks(this, android.text.util.Linkify.WEB_URLS)
                })
            }
        } else {
            rows.addView(label(raw, 13f).apply {
                tag = NoteFont.MONO
                textDirection = View.TEXT_DIRECTION_LTR
                setPaddingRelative(dp(5), dp(7), dp(5), dp(7))
            })
        }
        showDialog(dialog)
    }

    private fun settingsColumn() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(6), dp(18), dp(12))
    }

    private fun settingsGroup(parent: LinearLayout, title: Int): LinearLayout {
        parent.addView(label(s(title), 13f, palette.muted, bold = true).apply {
            setPaddingRelative(dp(4), dp(9), dp(4), dp(8))
            accessibilityHeadingIfSupported()
        }, LinearLayout.LayoutParams(MATCH, WRAP))
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(palette.surface, 18, palette.line)
            setPadding(dp(5), dp(5), dp(5), dp(5))
            clipToOutline = true
        }
        parent.addView(card, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) })
        return card
    }

    private fun settingsDialog(title: String, rows: LinearLayout, up: Boolean = false, onUp: () -> Unit = ::showSettings): AlertDialog {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(7), 0, dp(8))
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(dp(15), dp(3), dp(8), dp(4))
        }
        header.addView(label(title, 22f, bold = true).apply {
            setPaddingRelative(dp(5), 0, dp(4), 0)
            accessibilityHeadingIfSupported()
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        var dialog: AlertDialog? = null
        header.addView(icon(
            if (up) R.drawable.ic_arrow_left else R.drawable.ic_x,
            s(if (up) R.string.back_to_settings else R.string.close), 18, quiet = true
        ) {
            dialog?.dismiss()
            if (up) onUp()
        }, buttonLayout(44, 44))
        panel.addView(header, LinearLayout.LayoutParams(MATCH, WRAP))
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(rows)
        }
        panel.addView(scroll, LinearLayout.LayoutParams(MATCH, WRAP, 1f))
        return dialogBuilder().setView(panel).create().also {
            dialog = it
            if (up) it.setOnCancelListener { onUp() }
        }
    }

    private fun settingsRow(
        title: String,
        detail: String,
        selected: Boolean = false,
        sampleFont: NoteFont? = null,
        leading: Int? = null,
        navigate: Boolean = false,
        surface: Int = palette.paper,
        sampleIconStyle: IconStyle = iconStyle
    ): LinearLayout = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(if (detail.isEmpty()) 48 else 60)
        setPaddingRelative(dp(10), dp(8), dp(10), dp(8))
        background = pressSurface(if (selected) palette.surface else surface, 12, if (selected) palette.outline else null)
        isFocusable = true
        isSelected = selected
        if (leading != null) {
            val badge = FrameLayout(this@MainActivity).apply {
                background = rounded(if (surface == Color.TRANSPARENT) palette.paper else palette.surface, 10)
                addView(glyph(leading, palette.ink, sampleIconStyle), FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
            }
            addView(badge, linear(34, 34).apply { marginEnd = dp(11) })
        }
        val text = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        text.addView(label(title, 15f, bold = true))
        if (detail.isNotEmpty()) text.addView(label(detail, if (sampleFont == null) 12f else 17f, palette.muted).apply {
            if (sampleFont != null) {
                tag = sampleFont
                applyTextFont()
            }
            setPadding(0, dp(4), 0, 0)
        })
        addView(text, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (selected || navigate) {
            val image = if (selected) R.drawable.ic_check else R.drawable.ic_chevron_right
            addView(glyph(image, if (selected) palette.ink else palette.muted), linear(17, 17).apply { marginStart = dp(8) })
        }
    }

    private fun showChoices(title: Int, options: List<String>, selected: Int, up: () -> Unit = ::showSettings, picked: (Int) -> Unit) {
        val rows = settingsColumn()
        val dialog = settingsDialog(s(title), rows, up = true, onUp = up)
        options.forEachIndexed { index, label ->
            val item = settingsRow(label, "", index == selected)
            item.setOnClickListener { dialog.dismiss(); picked(index) }
            rows.addView(item, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(5) })
        }
        showDialog(dialog)
    }

    private fun fontName(font: NoteFont) = if (font == NoteFont.PIXEL) s(R.string.pixels) else font.title

    private fun showFonts() {
        val rows = settingsColumn()
        val dialog = settingsDialog(s(R.string.font), rows, up = true)
        for (font in NoteFont.entries) {
            val row = settingsRow(fontName(font), s(R.string.font_example), font == noteFont, font)
            row.setOnClickListener {
                dialog.dismiss()
                if (font != noteFont) {
                    noteFont = font
                    prefs.edit().putString("note_font", font.key).apply()
                    applyScreenFonts(root)
                    val current = draft
                    if (current == null) homeAdapter.notifyDataSetChanged()
                    else if (!editing) {
                        destroyPreview()
                        showPreview(current)
                    }
                }
                showSettings()
            }
            rows.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(5) })
        }
        showDialog(dialog)
    }

    private fun showScaleSlider(title: Int, key: String, current: Float, minimum: Int, maximum: Int) {
        val rows = settingsColumn()
        val dialog = settingsDialog(s(title), rows, up = true)
        val preview = label(percentage(current), 26f, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        }
        rows.addView(preview, LinearLayout.LayoutParams(MATCH, WRAP))
        val slider = SeekBar(this).apply {
            max = maximum - minimum
            progress = (current * 100).roundToInt() - minimum
            splitTrack = false
            progressTintList = ColorStateList.valueOf(palette.muted)
            thumbTintList = ColorStateList.valueOf(palette.ink)
            contentDescription = s(title)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(view: SeekBar, value: Int, fromUser: Boolean) {
                    preview.text = percentage((minimum + value) / 100f)
                    if (key == "corner_scale") preview.background = rounded(palette.surface, 16, palette.line, (minimum + value) / 100f)
                }
                override fun onStartTrackingTouch(view: SeekBar) = Unit
                override fun onStopTrackingTouch(view: SeekBar) = Unit
            })
        }
        if (key == "corner_scale") preview.background = rounded(palette.surface, 16, palette.line)
        rows.addView(slider, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8); bottomMargin = dp(14) })
        val apply = settingsRow(s(R.string.apply_setting), "", leading = R.drawable.ic_check)
        apply.setOnClickListener {
            dialog.dismiss()
            prefs.edit().putFloat(key, (minimum + slider.progress) / 100f).apply()
            loadScales()
            refreshScreen()
            showSettings()
        }
        rows.addView(apply)
        showDialog(dialog)
    }

    private fun percentage(value: Float) = java.text.NumberFormat.getPercentInstance(appLocale).format(value.toDouble())

    private fun changeTheme(theme: NotierTheme) {
        if (notierTheme == theme) return
        notierTheme = theme
        prefs.edit().putString("theme", theme.key).apply()
        setTheme(theme.style)
        root.setBackgroundColor(palette.paper)
        configureInsets()
        refreshScreen()
    }

    private fun loadScales() {
        textScale = prefs.getFloat("text_scale", 1f).coerceIn(.75f, 1.75f)
        interfaceScale = prefs.getFloat("interface_scale", 1f).coerceIn(.75f, 1.40f)
        buttonScale = prefs.getFloat("button_scale", 1f).coerceIn(.75f, 1.45f)
        cornerScale = prefs.getFloat("corner_scale", 1f).coerceIn(0f, 2f)
        listLayout = prefs.getInt("list_layout", 0).coerceIn(0, 2)
    }

    private fun refreshScreen() {
        val current = draft
        if (current == null) {
            rememberListPosition()
            showHome()
            restoreListPosition()
        } else {
            val start = editor.selectionStart
            val end = editor.selectionEnd
            val shortcutsOffset = markdownBar.scrollX
            val editorOffset = editor.scrollY
            val previewOffset = preview?.scrollY ?: 0
            destroyPreview()
            showNote(current, editing, resetHistory = false, previewOffset = previewOffset)
            if (start >= 0 && end >= 0) editor.setSelection(start.coerceAtMost(editor.length()), end.coerceAtMost(editor.length()))
            if (editing) {
                val bar = markdownBar
                val field = editor
                bar.post { bar.scrollTo(shortcutsOffset, 0) }
                field.post { field.scrollTo(0, editorOffset) }
            }
        }
    }

    private fun rememberSettingsState() {
        if (draft == null) rememberListPosition()
        keepNoteAfterRecreate = draft != null
        persistDraft()
    }

    private fun updateHome() {
        if (draft != null || !::homeAdapter.isInitialized) return
        val needle = searchKey(query.trim())
        val all = if (driveScreen) driveFiles else files
        val driveOffline = driveScreen && !Updates.online(this)
        val matches = if (needle.isEmpty()) all else all.filter { searchKey(displayTitle(it)).contains(needle) }
        val allNoteCount = all.count { !it.isFolder }
        val sorted = if (sortByTitle) matches.sortedWith { first, second ->
            titleOrder.compare(displayTitle(first), displayTitle(second))
        } else matches
        val (folders, documents) = sorted.partition { it.isFolder }
        val (favorites, regular) = documents.partition { it.uri.toString() in pinned }
        homeAdapter.items = folders + favorites + regular
        homeAdapter.notifyDataSetChanged()
        val loading = startLoading || if (driveScreen) driveLoading || authorizingDrive else folderLoading
        loadingRow.visibility = if (loading) View.VISIBLE else View.GONE
        val countText = if (needle.isNotEmpty()) {
            s(R.string.shown_count, matches.size, all.size)
        } else {
            listOfNotNull(
                quantity(R.plurals.notes_total, allNoteCount),
                folders.size.takeIf { it > 0 }?.let { quantity(R.plurals.folders_total, it) }
            ).joinToString("  ·  ")
        }
        homeSubtitle.text = when {
            driveOffline -> "$countText  ·  ${s(R.string.drive_offline)}"
            driveScreen -> if (driveUnavailable == 0) countText else "$countText ${s(R.string.drive_items_missing)}"
            folderName.isNotEmpty() -> "$folderName  ·  $countText"
            folderUri != null -> "${s(R.string.folder_selected)}  ·  $countText"
            else -> ""
        }
        homeSubtitle.visibility = if (homeSubtitle.text.isEmpty()) View.GONE else View.VISIBLE
        val folder = driveFolder.takeIf { driveScreen && driveApi != null }
        driveNav.visibility = if (folder == null) View.GONE else View.VISIBLE
        if (folder != null) {
            driveNavLabel.text = driveFolderLabel()
            val canNavigateUp = folder.parent != null
            driveNavUp.visibility = if (canNavigateUp) View.VISIBLE else View.GONE
            driveNavRoot.visibility = if (canNavigateUp) View.VISIBLE else View.GONE
            driveNavAdd.visibility = if (folder.flags and Document.FLAG_DIR_SUPPORTS_CREATE != 0) View.VISIBLE else View.GONE
            for (button in listOf(driveNavUp, driveNavRoot, driveNavAdd)) {
                button.isEnabled = !loading
                button.alpha = if (loading) .5f else 1f
            }
        }
        homeList.visibility = if (matches.isEmpty()) View.GONE else View.VISIBLE
        emptyPanel.visibility = if (!loading && matches.isEmpty()) View.VISIBLE else View.GONE
        if (emptyPanel.visibility != View.VISIBLE) return
        val state = when {
            driveOffline && needle.isEmpty() -> EmptyState(R.drawable.ic_cloud, R.string.drive_area,
                s(R.string.drive_offline), R.string.local_notes) { switchStorage(false) }
            driveScreen && driveUnavailable > 0 && !driveNeedsConsent -> EmptyState(R.drawable.ic_refresh_cw,
                R.string.drive_area, s(R.string.drive_access_problem), R.string.refresh_notes, ::refreshFromGesture)
            !driveScreen && folderError != null -> EmptyState(R.drawable.ic_folder_open,
                R.string.folder_selected, requireNotNull(folderError), R.string.choose_folder, ::chooseFolder)
            needle.isNotEmpty() -> EmptyState(R.drawable.ic_search, R.string.no_search_results, "")
            driveScreen && driveFolder == null -> EmptyState(R.drawable.ic_cloud,
                R.string.drive_area, s(if (driveNeedsConsent) R.string.drive_auth_expired else R.string.drive_direct_intro),
                R.string.drive_connect, { connectDrive() })
            !driveScreen && folderUri == null -> EmptyState(R.drawable.ic_folder_open,
                R.string.notes, s(R.string.pick_folder_hint), R.string.choose_folder, ::chooseFolder)
            !driveScreen && localHasSubfolders -> EmptyState(R.drawable.ic_file_text,
                R.string.no_notes_here, s(R.string.subfolders_present))
            driveScreen && driveFolder?.flags?.and(Document.FLAG_DIR_SUPPORTS_CREATE) == 0 ->
                EmptyState(R.drawable.ic_folder_open, R.string.no_notes_here, s(R.string.drive_no_write))
            else -> EmptyState(R.drawable.ic_file_text, R.string.first_note_title,
                s(R.string.first_note_hint), R.string.new_note, ::newNote)
        }
        emptyIcon.setImageDrawable(appIcons.drawable(state.icon, iconStyle))
        emptyIcon.imageTintList = if (shouldTint(state.icon, iconStyle)) ColorStateList.valueOf(palette.muted) else null
        emptyTitle.text = s(state.title)
        emptyText.text = state.message
        emptyText.visibility = if (state.message.isEmpty()) View.GONE else View.VISIBLE
        emptyAction.visibility = if (state.action == null) View.GONE else View.VISIBLE
        emptyAction.text = state.actionLabel?.let { s(it) }.orEmpty()
        emptyAction.setOnClickListener(if (state.action == null) null else View.OnClickListener { state.action.invoke() })
    }

    private data class EmptyState(val icon: Int, val title: Int, val message: String,
        val actionLabel: Int? = null, val action: (() -> Unit)? = null)

    private fun quantity(id: Int, value: Int) = resources.getQuantityString(id, value, value)

    private fun updateFolder() {
        val tree = folderUri ?: return
        val request = ++folderRequest
        folderLoading = true
        updateHome()
        background({ notes.list(tree) }) { result ->
            if (request != folderRequest) return@background
            folderLoading = false
            if (folderUri != tree || draft != null) return@background
            if (result.isSuccess) {
                val folder = result.getOrThrow()
                folderName = folder.name
                files = folder.notes
                localHasSubfolders = folder.hasSubfolders
                folderError = null
            } else {
                files = emptyList()
                folderError = s(R.string.folder_retry)
            }
            updateHome()
            restoreListPosition()
        }
    }

    private fun reloadNotes(force: Boolean = false) {
        if (if (driveScreen) driveLoading || authorizingDrive else folderLoading) return
        if (!driveScreen) updateFolder()
        else if (!Updates.online(this)) updateHome()
        else if (driveApi != null) {
            if (force || drivePages.get(driveFolderId)?.fresh != true || driveFolder == null) refreshDriveFolder()
        }
        else if (reconnectDrive && !driveNeedsConsent) connectDrive(interactive = false)
        else if (!driveNeedsConsent) refreshDrive()
    }

    private fun refreshFromGesture() {
        if (busy || startLoading || authorizingDrive || (if (driveScreen) driveLoading else folderLoading)) return
        rememberListPosition()
        if (driveScreen && driveNeedsConsent && driveApi == null) connectDrive(interactive = false)
        else reloadNotes(force = true)
    }

    private fun switchStorage(toDrive: Boolean) {
        if (busy || startLoading || toDrive == driveScreen) return
        hideKeyboard()
        if (!toDrive) driveCountTask?.cancel(true)
        driveScreen = toDrive
        prefs.edit().putBoolean("drive_screen", toDrive).apply()
        query = ""
        homeListIndex = 0
        homeListOffset = 0
        showHome()
        reloadNotes()
    }

    private fun refreshDrive() {
        val request = ++driveRequest
        driveLoading = true
        updateHome()
        background({ drive.list() }) { result ->
            if (request != driveRequest) return@background
            driveLoading = false
            result.onSuccess {
                driveFiles = it.files
                driveUnavailable = it.unavailable
            }.onFailure { driveUnavailable = 1 }
            if (driveScreen && draft == null) {
                updateHome()
                restoreListPosition()
            }
        }
    }

    private fun showDriveAccount() {
        if (authorizingDrive) return
        val rows = settingsColumn()
        val dialog = settingsDialog(s(R.string.drive_account), rows)
        fun option(id: Int, image: Int, details: String = "", action: () -> Unit) {
            rows.addView(settingsRow(s(id), details, leading = image).apply {
                setOnClickListener { dialog.dismiss(); action() }
            })
        }
        if (driveApi == null) option(R.string.drive_connect, R.drawable.ic_log_in, s(R.string.drive_permission_hint)) { connectDrive() }
        option(R.string.drive_manage, R.drawable.ic_cloud) { launchDrive() }
        if (driveApi == null) option(R.string.drive_file_access, R.drawable.ic_cloud_download) { pickDriveDocuments() }
        if (driveApi != null || reconnectDrive) option(R.string.drive_sign_out, R.drawable.ic_log_out,
            driveAccountName.orEmpty()) { disconnectDrive() }
        showDialog(dialog)
    }

    private fun requireDrive() = driveApi ?: throw DriveRequestFailed(401)

    private fun connectDrive(interactive: Boolean = true) {
        if (authorizingDrive || busy || startLoading) return
        if (!Updates.online(this)) {
            if (interactive) toast(s(R.string.drive_offline))
            return
        }
        if (interactive) {
            driveRenewalAttempted = false
            val picker = AccountPicker.AccountChooserOptions.Builder()
                .setAllowableAccountsTypes(listOf("com.google"))
                .setAlwaysShowAccountPicker(true)
                .setTitleOverrideText(s(R.string.choose_google_account))
                .build()
            try {
                hideKeyboard()
                ++driveRequest
                driveLoading = false
                driveCountTask?.cancel(true)
                authorizingDrive = true
                driveConsentPending = true
                updateHome()
                startActivityForResult(AccountPicker.newChooseAccountIntent(picker), PICK_GOOGLE_ACCOUNT)
            } catch (_: RuntimeException) {
                driveAuthorizationFailed(true)
            }
        } else authorizeDrive(interactive = false, accountName = driveAccountName)
    }

    private fun authorizeDrive(interactive: Boolean, accountName: String?) {
        ++driveRequest
        driveLoading = false
        hideKeyboard()
        authorizingDrive = true
        pendingDriveAccount = accountName
        updateHome()
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DriveApi.SCOPE))).apply {
            accountName?.let { setAccount(Account(it, "com.google")) }
        }.build()
        try {
            Identity.getAuthorizationClient(this).authorize(request)
                .addOnSuccessListener { result ->
                    if (isDestroyed || isFinishing) return@addOnSuccessListener
                    if (!result.hasResolution()) {
                        useDriveAuthorization(result)
                    } else {
                        val resolution = result.pendingIntent
                        if (interactive && resolution != null) {
                            try {
                                driveConsentPending = true
                                startIntentSenderForResult(resolution.intentSender, AUTHORIZE_DRIVE, null, 0, 0, 0)
                            } catch (_: Exception) {
                                driveAuthorizationFailed(interactive)
                            }
                        } else driveAuthorizationFailed(interactive = false)
                    }
                }
                .addOnFailureListener { if (!isDestroyed && !isFinishing) driveAuthorizationFailed(interactive) }
        } catch (_: RuntimeException) {
            driveAuthorizationFailed(interactive)
        }
    }

    private fun driveAuthorizationFailed(interactive: Boolean) {
        driveConsentPending = false
        authorizingDrive = false
        pendingDriveAccount = null
        driveNeedsConsent = reconnectDrive || interactive
        if (driveNeedsConsent) {
            driveFolder = null
            driveCounts = emptyMap()
            driveFiles = emptyList()
            drivePages.evictAll()
            driveCountCache.evictAll()
            driveUnavailable = 0
        }
        if (interactive) toast(s(R.string.drive_auth_error))
        if (driveScreen && draft == null && !reconnectDrive) refreshDrive()
        else updateHome()
    }

    private fun driveAuthorizationResult(result: Int, data: Intent?) {
        driveConsentPending = false
        if (result != RESULT_OK || data == null) {
            driveAuthorizationFailed(false)
            toast(s(R.string.drive_auth_cancel))
            return
        }
        val authorization = runCatching {
            Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data)
        }.getOrNull()
        if (authorization == null) driveAuthorizationFailed(true)
        else useDriveAuthorization(authorization)
    }

    private fun useDriveAuthorization(result: AuthorizationResult) {
        val token = result.accessToken
        if (token.isNullOrBlank() || DriveApi.SCOPE !in result.grantedScopes) {
            driveAuthorizationFailed(true)
            return
        }
        val api = DriveApi(token)
        @Suppress("DEPRECATION")
        val authorizedAccount = pendingDriveAccount ?: result.toGoogleSignInAccount()?.email
        pendingDriveAccount = null
        background({ api.folder("root") }) { loaded ->
            authorizingDrive = false
            loaded.onSuccess { rootFolder ->
                val previousRoot = prefs.getString("drive_root", null)
                if (previousRoot != rootFolder.id) {
                    driveFolderId = rootFolder.id
                    driveFiles = emptyList()
                    driveCounts = emptyMap()
                    drivePages.evictAll()
                    driveCountCache.evictAll()
                    driveFolder = null
                }
                driveRoot = rootFolder
                driveAccountName = authorizedAccount
                prefs.edit().putString("drive_root", rootFolder.id)
                    .putString("drive_folder", driveFolderId).putString("drive_account_name", driveAccountName)
                    .putBoolean("direct_drive", true).apply()
                reconnectDrive = true
                driveNeedsConsent = false
                driveApi = api
                driveFolder = if (driveFolderId == rootFolder.id) rootFolder else driveFolder?.takeIf { it.id == driveFolderId }
                driveUnavailable = 0
                if (driveScreen && draft == null) refreshDriveFolder(knownFolder = rootFolder.takeIf { it.id == driveFolderId })
            }.onFailure {
                driveAuthorizationFailed(false)
                toast(s(R.string.drive_api_failed))
            }
        }
    }

    private fun disconnectDrive() {
        if (authorizingDrive || busy) return
        val account = driveAccountName
        val api = driveApi
        ++driveRequest
        driveApi = null
        driveRoot = null
        driveFolder = null
        driveLoading = false
        driveCountTask?.cancel(true)
        driveCounts = emptyMap()
        driveFiles = emptyList()
        drivePages.evictAll()
        driveCountCache.evictAll()
        reconnectDrive = false
        driveNeedsConsent = false
        driveRenewalAttempted = false
        driveAccountName = null
        prefs.edit().remove("drive_folder").remove("drive_root").remove("drive_account_name")
            .putBoolean("direct_drive", false).apply()
        driveFolderId = "root"
        authorizingDrive = true
        updateHome()
        if (account != null) revokeDrive(account, api)
        else background({ api?.accountEmail() }) { result -> revokeDrive(result.getOrNull(), api) }
    }

    private fun revokeDrive(account: String?, api: DriveApi?) {
        fun finish(revoked: Boolean) {
            if (isDestroyed || isFinishing) return
            authorizingDrive = false
            toast(s(if (revoked) R.string.drive_signed_out else R.string.drive_signout_local))
            if (driveScreen && draft == null) refreshDrive() else updateHome()
        }
        if (account == null) {
            finish(false)
            return
        }
        try {
            val client = Identity.getAuthorizationClient(this)
            val request = RevokeAccessRequest.builder()
                .setAccount(Account(account, "com.google"))
                .setScopes(listOf(Scope(DriveApi.SCOPE))).build()
            client.revokeAccess(request).addOnCompleteListener { result ->
                api?.let {
                    runCatching { client.clearToken(ClearTokenRequest.builder().setToken(it.accessToken).build()) }
                }
                finish(result.isSuccessful)
            }
        } catch (_: RuntimeException) {
            finish(false)
        }
    }

    private fun driveFolderLabel(): String {
        val folder = driveFolder ?: return s(R.string.my_drive)
        return if (folder.id == prefs.getString("drive_root", null)) s(R.string.my_drive) else folder.name
    }

    private fun openDriveFolder(id: String) {
        if (driveApi == null || driveLoading || busy) return
        hideKeyboard()
        val rootFolder = driveRoot
        val destination = if (id == "root") rootFolder?.id ?: id else id
        if (destination == driveFolderId) return
        val cached = drivePages.get(destination)
        val known = cached?.folder ?: rootFolder?.takeIf { it.id == destination } ?: driveFiles.firstOrNull {
            it.isFolder && DriveApi.id(it.uri) == destination
        }?.let { DriveItem(destination, it.name, driveFolder?.id, true, it.flags, it.modified) }
        query = ""
        searchField.setText("")
        homeListIndex = 0
        homeListOffset = 0
        if (cached != null) {
            driveCountTask?.cancel(true)
            val request = ++driveRequest
            showDrivePage(cached)
            updateHome()
            restoreListPosition()
            if (cached.fresh) {
                val api = driveApi ?: return
                loadDriveCounts(api, request, folderIds(cached.files).filterNot { it in driveCounts })
                return
            }
        }
        refreshDriveFolder(destination, knownFolder = known)
    }

    private data class DrivePage(val folder: DriveItem, val files: List<NoteFile>, val loadedAt: Long = SystemClock.elapsedRealtime()) {
        val fresh get() = SystemClock.elapsedRealtime() - loadedAt < 20_000
    }

    private fun folderIds(files: List<NoteFile>) = files.mapNotNull { if (it.isFolder) DriveApi.id(it.uri) else null }

    private fun invalidateDrivePages() {
        driveCountTask?.cancel(true)
        drivePages.evictAll()
        driveCountCache.evictAll()
    }

    private fun showDrivePage(page: DrivePage) {
        val id = page.folder.id
        driveFolder = page.folder
        driveFolderId = id
        driveFiles = page.files
        driveUnavailable = 0
        driveCounts = folderIds(page.files).mapNotNull { folderId -> driveCountCache.get(folderId)?.let { folderId to it } }.toMap()
        if (prefs.getString("drive_folder", null) != id) prefs.edit().putString("drive_folder", id).apply()
    }

    private fun refreshDriveFolder(id: String = driveFolderId, retryRoot: Boolean = true, knownFolder: DriveItem? = null) {
        val api = driveApi ?: return
        if (!Updates.online(this)) {
            updateHome()
            return
        }
        val request = ++driveRequest
        driveCountTask?.cancel(true)
        driveLoading = true
        updateHome()
        background({
            val folder = knownFolder ?: api.folder(id)
            folder to api.children(folder.id)
        }) { result ->
            if (request != driveRequest || driveApi !== api) return@background
            driveLoading = false
            result.onSuccess { (folder, children) ->
                val page = DrivePage(folder, children)
                drivePages.put(folder.id, page)
                driveCountCache.put(folder.id, children.count { !it.isFolder })
                showDrivePage(page)
                driveRenewalAttempted = false
                if (driveScreen) loadDriveCounts(api, request, folderIds(children))
            }.onFailure {
                if (retryRoot && it is DriveRequestFailed && it.status == 404 && id != "root" && (driveFolder == null || driveFolder?.id == id)) {
                    refreshDriveFolder("root", retryRoot = false)
                    return@background
                }
                driveUnavailable = 1
                if (!driveError(it)) toast(s(R.string.drive_api_failed))
            }
            if (driveScreen && draft == null) {
                updateHome()
                restoreListPosition()
            }
        }
    }

    private fun loadDriveCounts(api: DriveApi, request: Int, folders: List<String>) {
        if (folders.isEmpty()) return
        driveCountTask = driveCountWorker.submit {
            for (batch in folders.chunked(18)) {
                if (Thread.currentThread().isInterrupted) break
                val result = runCatching { api.childCounts(batch) }
                if (result.isFailure) break
                main.post {
                    if (isDestroyed || !driveScreen || request != driveRequest || driveApi !== api) return@post
                    val counts = result.getOrThrow()
                    for ((id, count) in counts) driveCountCache.put(id, count)
                    driveCounts = driveCounts + counts
                    if (draft == null) {
                        for (index in 0 until homeList.childCount) {
                            val card = homeList.getChildAt(index) as? NoteCard ?: continue
                            card.updateFolderDetail()
                        }
                    }
                }
            }
        }
    }

    private fun driveError(error: Throwable): Boolean {
        if (error !is DriveRequestFailed) return false
        if (error.status == 401) {
            ++driveRequest
            val request = driveRequest
            val expiredToken = driveApi?.accessToken
            driveApi = null
            driveCountTask?.cancel(true)
            driveLoading = false
            driveNeedsConsent = true
            if (expiredToken != null && !driveRenewalAttempted) {
                driveRenewalAttempted = true
                authorizingDrive = true
                updateHome()
                fun resumeConnection() {
                    if (isDestroyed || isFinishing || request != driveRequest) return
                    authorizingDrive = false
                    connectDrive(interactive = false)
                    updateHome()
                }
                try {
                    Identity.getAuthorizationClient(this).clearToken(ClearTokenRequest.builder().setToken(expiredToken).build())
                        .addOnCompleteListener { resumeConnection() }
                } catch (_: RuntimeException) {
                    resumeConnection()
                }
                if (draft != null) toast(s(R.string.drive_retry_save))
                return true
            }
            driveFolder = null
            driveFiles = emptyList()
            updateHome()
            toast(s(R.string.drive_auth_expired))
        } else toast(s(R.string.drive_api_failed))
        return true
    }

    private fun createDriveFolder() {
        if (busy || driveLoading || authorizingDrive) return
        val parent = driveFolder ?: return
        if (parent.flags and Document.FLAG_DIR_SUPPORTS_CREATE == 0) {
            toast(s(R.string.drive_no_write))
            return
        }
        promptName(R.string.new_folder, "", R.string.folder_name) { raw ->
            val name = validFolderName(raw) ?: return@promptName false
            busy = true
            background({ requireDrive().createFolder(parent.id, name) }) { result ->
                busy = false
                if (result.isSuccess) {
                    invalidateDrivePages()
                    toast(s(R.string.folder_created))
                    refreshDriveFolder()
                } else if (!driveError(result.exceptionOrNull()!!)) toast(s(R.string.drive_api_failed))
            }
            true
        }
    }

    private fun launchDrive() {
        val intent = packageManager.getLaunchIntentForPackage("com.google.android.apps.docs")
        if (intent == null) {
            toast(s(R.string.drive_install_error))
            return
        }
        try { startActivity(intent) }
        catch (_: ActivityNotFoundException) { toast(s(R.string.drive_install_error)) }
    }

    private fun pickDriveDocuments() {
        if (busy || startLoading) return
        hideKeyboard()
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(DOCUMENT_GRANTS)
            driveFiles.firstOrNull()?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it.uri) }
        }
        launchPicker(intent, PICK_DRIVE)
    }

    private fun addDriveDocuments(data: Intent) {
        val uris = buildSet {
            data.data?.let(::add)
            data.clipData?.let { clip -> for (index in 0 until clip.itemCount) clip.getItemAt(index).uri?.let(::add) }
        }
        val driveUris = uris.filter(DriveDocuments::isDrive)
        if (driveUris.isEmpty()) {
            toast(s(R.string.drive_pick_only))
            return
        }
        busy = true
        background({
            var added = 0
            var invalidType = 0
            for (uri in driveUris) {
                val note = runCatching { notes.document(uri) }.getOrNull() ?: continue
                if (!note.name.isMarkdownName()) {
                    invalidType++
                    continue
                }
                if (runCatching { drive.remember(uri, data.flags) }.isSuccess) added++
            }
            added to invalidType
        }) { result ->
            busy = false
            val (added, invalidType) = result.getOrDefault(0 to 0)
            toast(s(when {
                added == uris.size -> R.string.drive_added
                invalidType == uris.size -> R.string.markdown_only
                added == 0 -> R.string.drive_access_problem
                else -> R.string.drive_partial
            }))
            refreshDrive()
        }
    }

    private fun chooseFolder() {
        if (busy || startLoading) return
        hideKeyboard()
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            folderUri?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
        }
        launchPicker(intent, PICK_FOLDER)
    }

    private fun selectFolder(data: Intent, uri: Uri) {
        val previous = folderUri
        val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        busy = true
        background({
            contentResolver.takePersistableUriPermission(uri, flags)
            try {
                notes.list(uri)
            } catch (error: Exception) {
                if (previous != uri) releaseGrant(uri)
                throw error
            }
        }) { result ->
            busy = false
            result.onSuccess { folder ->
                folderUri = uri
                prefs.edit().putString("folder", uri.toString()).apply()
                if (previous != null && previous != uri) releaseGrant(previous)
                folderName = folder.name
                files = folder.notes
                localHasSubfolders = folder.hasSubfolders
                folderError = null
                homeListIndex = 0
                homeListOffset = 0
                searchField.setText("")
                updateHome()
            }.onFailure { toast(s(R.string.folder_other)) }
        }
    }

    private fun restoreListPosition() {
        val position = homeListIndex.coerceAtMost((homeAdapter.count - 1).coerceAtLeast(0))
        val list = homeList
        if (list is ListView) list.setSelectionFromTop(position, homeListOffset) else list.setSelection(position)
    }

    private fun openNote(file: NoteFile, edit: Boolean = false) {
        if (file.isFolder) {
            openDriveFolder(requireNotNull(DriveApi.id(file.uri)))
            return
        }
        hideKeyboard()
        rememberListPosition()
        busy = true
        background({
            val id = DriveApi.id(file.uri)
            if (id != null) requireDrive().read(id) else notes.read(file.uri)
        }) { result ->
            busy = false
            result.onSuccess { text ->
                showNote(Draft(file.uri, file.name, text, text, local = !driveScreen), edit = edit, animate = true)
            }.onFailure { if (!driveError(it)) toast(s(R.string.note_open_error)) }
        }
    }

    private fun showNoteActions(anchor: View, file: NoteFile) {
        val key = file.uri.toString()
        val fixed = key in pinned
        val direct = DriveApi.id(file.uri) != null
        val canEdit = !driveScreen || file.supports(Document.FLAG_SUPPORTS_WRITE)
        val actions = buildList {
            add(when {
                file.isFolder -> MenuAction(R.string.drive_open_folder, R.drawable.ic_folder_open) { openNote(file) }
                canEdit -> MenuAction(R.string.edit, R.drawable.ic_pencil) { openNote(file, edit = true) }
                else -> MenuAction(R.string.preview, R.drawable.ic_eye) { openNote(file) }
            })
            if (!driveScreen || file.supports(Document.FLAG_SUPPORTS_RENAME)) {
                add(MenuAction(R.string.rename, R.drawable.ic_file_text) { renameFromList(file) })
            }
            if (direct && file.supports(DriveApi.CAN_MOVE)) {
                add(MenuAction(R.string.drive_move, R.drawable.ic_folder_open) { chooseMoveDestination(file) })
            }
            if (!file.isFolder) add(MenuAction(if (fixed) R.string.unpin else R.string.pin,
                if (fixed) R.drawable.ic_pin_off else R.drawable.ic_pin) {
                setPinned(if (fixed) pinned - key else pinned + key)
                homeListIndex = 0
                homeListOffset = 0
                updateHome()
                restoreListPosition()
            })
            if (driveScreen && !direct) {
                add(MenuAction(R.string.drive_remove_link, R.drawable.ic_x) { removeDriveLink(file) })
            }
            if (!driveScreen || file.supports(Document.FLAG_SUPPORTS_DELETE)) {
                add(MenuAction(if (direct) R.string.drive_trash else R.string.delete, R.drawable.ic_trash_2) { confirmDelete(file) })
            }
        }
        showMenu(anchor, actions)
    }

    private fun removeDriveLink(file: NoteFile) {
        if (!releaseGrant(file.uri)) {
            toast(s(R.string.drive_access_problem))
            return
        }
        setPinned(pinned - file.uri.toString())
        driveFiles = driveFiles.filterNot { it.uri == file.uri }
        updateHome()
        refreshDrive()
        toast(s(R.string.drive_removed))
    }

    private fun setSortOrder(byTitle: Boolean) {
        sortByTitle = byTitle
        prefs.edit().putBoolean("sort_by_title", sortByTitle).apply()
        homeListIndex = 0
        homeListOffset = 0
        updateHome()
        restoreListPosition()
    }

    private data class MenuAction(val label: Int, val icon: Int, val selected: Boolean = false, val action: () -> Unit)

    private fun showMenu(anchor: View, actions: List<MenuAction>) {
        if (busy || startLoading) return
        val surface = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = rounded(palette.paper, 20, palette.line)
            clipToOutline = true
        }
        val rootScreen = IntArray(2).also(root::getLocationOnScreen)
        val rootWindow = IntArray(2).also(root::getLocationInWindow)
        val anchorScreen = IntArray(2).also(anchor::getLocationOnScreen)
        val visible = Rect().also(root::getWindowVisibleDisplayFrame)
        val gap = px(8f)
        val leftLimit = maxOf(visible.left, rootScreen[0] + root.paddingLeft) + gap
        val rightLimit = minOf(visible.right, rootScreen[0] + root.width - root.paddingRight) - gap
        val topLimit = maxOf(visible.top, rootScreen[1] + root.paddingTop) + gap
        val bottomLimit = minOf(visible.bottom, rootScreen[1] + root.height - root.paddingBottom) - gap
        if (rightLimit <= leftLimit || bottomLimit <= topLimit) return
        val width = dp(252).coerceAtMost(rightLimit - leftLimit)
        val scroll = ScrollView(this).apply {
            background = rounded(palette.paper, 20)
            clipToOutline = true
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            addView(surface)
        }
        val popup = PopupWindow(scroll, width, WRAP, true).apply {
            setBackgroundDrawable(rounded(palette.paper, 20))
            elevation = dp(9).toFloat()
            isOutsideTouchable = true
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        }
        for (action in actions) {
            val row = settingsRow(
                s(action.label), "", selected = action.selected,
                leading = action.icon, surface = palette.paper
            )
            row.setOnClickListener {
                popup.dismiss()
                if (!busy) action.action()
            }
            surface.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        surface.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        val below = (bottomLimit - anchorScreen[1] - anchor.height - gap).coerceAtLeast(0)
        val above = (anchorScreen[1] - gap - topLimit).coerceAtLeast(0)
        val openBelow = surface.measuredHeight <= below || below >= above
        popup.height = surface.measuredHeight.coerceAtMost(if (openBelow) below else above).coerceAtLeast(1)
        val preferredLeft = if (anchor.layoutDirection == View.LAYOUT_DIRECTION_RTL) anchorScreen[0]
            else anchorScreen[0] + anchor.width - width
        val left = preferredLeft.coerceIn(leftLimit, rightLimit - width)
        val top = if (openBelow) bottomLimit - below else (anchorScreen[1] - gap - popup.height).coerceAtLeast(topLimit)
        popup.showAtLocation(root, Gravity.TOP or Gravity.LEFT,
            left - rootScreen[0] + rootWindow[0], top - rootScreen[1] + rootWindow[1])
    }

    private fun promptName(dialogTitle: Int, initial: String, hint: Int = R.string.file_name,
                           onCancel: () -> Unit = {}, save: (String) -> Boolean) {
        val field = EditText(this).apply {
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            textSize = 16f * textScale
            setText(initial)
            this.hint = s(hint)
            selectAll()
            applyTextFont()
        }
        val box = FrameLayout(this).apply {
            setPadding(dp(22), dp(4), dp(22), 0)
            addView(field, FrameLayout.LayoutParams(MATCH, WRAP))
        }
        val dialog = dialogBuilder().setTitle(s(dialogTitle)).setView(box)
            .setNegativeButton(s(R.string.cancel)) { _, _ -> onCancel() }
            .setOnCancelListener { onCancel() }.setPositiveButton(s(R.string.save), null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (save(field.text.toString())) dialog.dismiss()
                else field.error = s(R.string.valid_name)
            }
        }
        showDialog(dialog)
    }

    private fun renameFromList(file: NoteFile) {
        val tree = if (driveScreen) null else folderUri ?: return
        val direct = DriveApi.id(file.uri)
        val initial = if (file.isFolder) file.name else title(file.name)
        promptName(R.string.rename, initial, if (file.isFolder) R.string.folder_name else R.string.file_name) { raw ->
            val name = (if (file.isFolder) validFolderName(raw) else normalizedName(raw, file.name)) ?: return@promptName false
            rememberListPosition()
            busy = true
            background({
                if (direct != null) requireDrive().rename(direct, name).note()
                else notes.rename(file, name, tree)?.also { drive.rememberRename(file.uri, it.uri) }
            }) { result ->
                busy = false
                result.onSuccess { renamed ->
                    if (renamed == null) {
                        toast(s(R.string.title_duplicate))
                    } else {
                        migratePin(file.uri, renamed.uri)
                        if (direct != null) invalidateDrivePages()
                        if (driveScreen) driveFiles = driveFiles.map { if (it.uri == file.uri) renamed else it }
                        else files = files.map { if (it.uri == file.uri) renamed else it }
                        updateHome()
                        restoreListPosition()
                        reloadNotes()
                        toast(s(R.string.rename_saved))
                    }
                }.onFailure { if (!driveError(it)) toast(s(R.string.rename_error)) }
            }
            true
        }
    }

    private fun validFolderName(value: String) = value.trim().takeIf {
        it.isNotEmpty() && it.length <= 120 && it.none { char -> char.isISOControl() || char == '/' }
    }

    private fun chooseMoveDestination(file: NoteFile, parent: String = driveFolderId, destination: String = "root") {
        val id = DriveApi.id(file.uri) ?: return
        val api = driveApi ?: return
        busy = true
        background({
            val folder = api.folder(destination)
            folder to api.children(folder.id).filter { it.isFolder && DriveApi.id(it.uri) != id }
        }) { result ->
            busy = false
            result.onSuccess { (folder, subfolders) ->
                val rows = settingsColumn()
                val dialog = settingsDialog(s(R.string.drive_select_destination), rows)
                val label = if (folder.id == prefs.getString("drive_root", null)) s(R.string.my_drive) else folder.name
                rows.addView(settingsRow(label, "", leading = R.drawable.ic_folder_open))
                fun option(text: String, icon: Int, onClick: () -> Unit) {
                    val row = settingsRow(text, "", leading = icon)
                    row.setOnClickListener { dialog.dismiss(); onClick() }
                    rows.addView(row)
                }
                folder.parent?.let {
                    option(s(R.string.parent_folder), R.drawable.ic_arrow_up) { chooseMoveDestination(file, parent, it) }
                }
                if (folder.id != parent && folder.flags and Document.FLAG_DIR_SUPPORTS_CREATE != 0) {
                    option(s(R.string.drive_move_here), R.drawable.ic_check) {
                        busy = true
                        background({ api.move(id, parent, folder.id) }) { moved ->
                            busy = false
                            if (moved.isSuccess) {
                                invalidateDrivePages()
                                toast(s(R.string.drive_move_done))
                                refreshDriveFolder()
                            } else if (!driveError(moved.exceptionOrNull()!!)) toast(s(R.string.drive_api_failed))
                        }
                    }
                }
                for (subfolder in subfolders) {
                    option(subfolder.name, R.drawable.ic_folder_open) {
                        chooseMoveDestination(file, parent, requireNotNull(DriveApi.id(subfolder.uri)))
                    }
                }
                if (subfolders.isEmpty()) rows.addView(label(s(R.string.drive_move_empty), 13f, palette.muted).apply {
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                })
                showDialog(dialog)
            }.onFailure { if (!driveError(it)) toast(s(R.string.drive_api_failed)) }
        }
    }

    private fun confirmDelete(file: NoteFile) {
        val id = DriveApi.id(file.uri)
        showDialog(dialogBuilder().setTitle(s(if (file.isFolder) R.string.drive_trash else R.string.delete_title))
            .setMessage(s(if (id != null) R.string.drive_trash_message else if (driveScreen) R.string.drive_delete_message else R.string.delete_message, file.name))
            .setNegativeButton(s(R.string.cancel), null)
            .setPositiveButton(s(if (id != null) R.string.drive_trash else R.string.delete)) { _, _ ->
                rememberListPosition()
                busy = true
                background({ if (id != null) requireDrive().trash(id) else notes.delete(file.uri) }) { result ->
                    busy = false
                    if (result.isSuccess) {
                        if (id != null) invalidateDrivePages()
                        setPinned(pinned - file.uri.toString())
                        if (driveScreen) {
                            driveFiles = driveFiles.filterNot { it.uri == file.uri }
                            if (id == null) releaseGrant(file.uri)
                        } else files = files.filterNot { it.uri == file.uri }
                        updateHome()
                        restoreListPosition()
                        reloadNotes()
                        toast(s(R.string.deleted))
                    } else if (!driveError(result.exceptionOrNull()!!)) toast(s(R.string.delete_error))
                }
            }.create())
    }

    private fun setPinned(value: Set<String>) {
        pinned = value
        prefs.edit().putStringSet("pinned", value).apply()
    }

    private fun migratePin(old: Uri?, new: Uri?) {
        if (old == null || new == null || old == new) return
        val key = old.toString()
        if (key in pinned) setPinned(pinned - key + new.toString())
    }

    private fun newNote() {
        if (busy || startLoading) return
        if (driveScreen && (driveLoading || authorizingDrive)) return
        if (driveScreen && reconnectDrive && driveApi == null) {
            connectDrive()
            return
        }
        val parent = driveFolder
        if (driveScreen && driveApi != null && parent == null) return
        if (driveScreen && parent != null && parent.flags and Document.FLAG_DIR_SUPPORTS_CREATE == 0) {
            toast(s(R.string.drive_no_write))
            return
        }
        hideKeyboard()
        rememberListPosition()
        showNote(Draft(null, s(R.string.new_note), "", "", local = !driveScreen && folderUri != null,
            driveParent = if (driveScreen) parent?.id else null), edit = true)
    }

    private fun rememberListPosition() {
        homeListIndex = homeList.firstVisiblePosition
        homeListOffset = homeList.getChildAt(0)?.top ?: 0
    }

    private fun showNote(value: Draft, edit: Boolean, resetHistory: Boolean = true, previewOffset: Int = 0,
                         animate: Boolean = false) {
        if (resetHistory) {
            undo.clear()
            redo.clear()
        }
        draft = value
        root.removeAllViews()
        val screen = FrameLayout(this)
        root.addView(screen, frameMatch())
        if (animate && ValueAnimator.areAnimatorsEnabled()) {
            screen.alpha = 0f
            screen.translationY = dp(7).toFloat()
            screen.animate().alpha(1f).translationY(0f).setDuration(175)
                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        screen.addView(column, frameMatch())

        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        bar.addView(icon(R.drawable.ic_arrow_left, s(R.string.back_to_notes), 21) { navigateBack() }, buttonLayout())
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(6), 0)
        }
        modeTitle = label("", 20f, bold = true).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            accessibilityHeadingIfSupported()
        }
        noteSubtitle = label("", 12f, palette.muted)
        heading.addView(modeTitle)
        heading.addView(noteSubtitle)
        bar.addView(heading, LinearLayout.LayoutParams(0, WRAP, 1f))
        contentsButton = icon(R.drawable.ic_list_ordered, s(R.string.toc), 20, quiet = true) {
            showContents()
        }.apply { visibility = View.GONE }
        bar.addView(contentsButton, buttonLayout(44))
        val more = icon(R.drawable.ic_ellipsis, s(R.string.note_actions), 21, quiet = true) { anchor ->
            if (busy) return@icon
            showMenu(anchor, listOf(
                MenuAction(R.string.save_as, R.drawable.ic_file_down) { saveCopy() },
                MenuAction(R.string.share_markdown, R.drawable.ic_share_2) { shareNote() },
                MenuAction(R.string.settings, R.drawable.ic_settings) { showSettings() }
            ))
        }
        bar.addView(more, buttonLayout())
        column.addView(bar)

        val modes = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setPadding(dp(16), 0, dp(16), dp(4))
        }
        titleField = EditText(this).apply {
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_NEXT
            hint = s(R.string.note_title)
            contentDescription = s(R.string.note_title)
            textSize = 16f * textScale
            setTextColor(palette.ink)
            setHintTextColor(palette.muted)
            setText(value.heading ?: if (value.uri != null) title(value.name) else "")
            tag = true
            applyTextFont()
            background = searchSurface(false)
            setPaddingRelative(dp(10), 0, dp(10), 0)
            setOnFocusChangeListener { _, focused -> updateTitleStyle(focused) }
            setOnEditorActionListener { _, action, _ ->
                if (action != EditorInfo.IME_ACTION_NEXT) return@setOnEditorActionListener false
                editor.requestFocus()
                true
            }
            watch { newTitle ->
                val now = draft ?: return@watch
                val originalTitle = if (now.uri != null) title(now.name) else ""
                draft = now.copy(heading = newTitle.takeUnless { it == originalTitle })
                queueDraftSave()
            }
        }
        titleField.minimumHeight = buttonDp(48)
        val titleLayout = LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(4) }
        modes.addView(titleField, titleLayout)
        val modeButtons = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.END }
        modeRead = icon(R.drawable.ic_eye, s(R.string.preview), 19) { switchMode(false) }
        modeEdit = icon(R.drawable.ic_pencil, s(R.string.edit), 19) { switchMode(true) }
        saveButton = icon(R.drawable.ic_save, s(R.string.save), 19) { saveNote() }
        modeButtons.addView(modeRead, buttonLayout())
        modeButtons.addView(modeEdit, buttonLayout())
        modeButtons.addView(saveButton, buttonLayout().apply { marginStart = dp(2) })
        modes.addView(modeButtons, LinearLayout.LayoutParams(WRAP, WRAP))
        fun fitModes(width: Int) {
            val stack = width - modes.paddingLeft - modes.paddingRight < buttonDp(48) * 3 + dp(112)
            val target = if (stack) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            if (target == modes.orientation) return
            modes.orientation = target
            titleField.layoutParams = LinearLayout.LayoutParams(if (stack) MATCH else 0, WRAP, if (stack) 0f else 1f).apply {
                marginEnd = if (stack) 0 else dp(4)
                bottomMargin = if (stack) dp(3) else 0
            }
        }
        val initialWidth = root.width.takeIf { it > 0 } ?: px(resources.configuration.screenWidthDp.toFloat())
        fitModes(initialWidth)
        modes.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) fitModes(right - left)
        }
        column.addView(modes, LinearLayout.LayoutParams(MATCH, WRAP))
        markdownBar = createMarkdownBar()
        column.addView(markdownBar, LinearLayout.LayoutParams(MATCH, WRAP).apply {
            marginStart = dp(16)
            marginEnd = dp(16)
            topMargin = dp(2)
            bottomMargin = dp(6)
        })
        column.addView(View(this).apply { setBackgroundColor(palette.line) }, LinearLayout.LayoutParams(MATCH, dp(1)).apply {
            marginStart = dp(16)
            marginEnd = dp(16)
            bottomMargin = dp(10)
        })

        content = FrameLayout(this)
        column.addView(content, LinearLayout.LayoutParams(MATCH, 0, 1f))
        editor = EditText(this).apply {
            setTextColor(palette.ink)
            setHintTextColor(palette.muted)
            hint = s(R.string.editor_hint)
            textSize = 16f * textScale
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(22), dp(10), dp(22), dp(24))
            background = null
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            applyTextFont()
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setHorizontallyScrolling(false)
            isVerticalScrollBarEnabled = true
            setLineSpacing(dp(4).toFloat(), 1f)
            setText(value.text)
            setSelection(length())
            addTextChangedListener(object : TextWatcher {
                private var removed = ""
                private var originalCursor = 0
                override fun beforeTextChanged(text: CharSequence, start: Int, count: Int, after: Int) {
                    if (!applyingEdit) {
                        removed = text.subSequence(start, start + count).toString()
                        originalCursor = selectionEnd.coerceAtLeast(0)
                    }
                }
                override fun onTextChanged(text: CharSequence, start: Int, before: Int, count: Int) {
                    if (!applyingEdit) {
                        recordChange(EditorChange(start, removed, text.subSequence(start, start + count).toString(), originalCursor, start + count))
                    }
                }
                override fun afterTextChanged(value: Editable?) = Unit
            })
            watch { text ->
                val active = draft ?: return@watch
                draft = active.copy(text = text)
                queueDraftSave()
            }
        }
        content.addView(editor, frameMatch())
        fullscreen = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        root.addView(fullscreen, frameMatch())
        switchMode(edit, force = true, previewOffset = previewOffset)
        root.requestFocus()
    }

    private lateinit var modeRead: ImageButton
    private lateinit var modeEdit: ImageButton

    private fun createMarkdownBar(): HorizontalScrollView {
        val strip = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(dp(4), dp(3), dp(4), dp(3))
        }
        fun separator() = View(this).apply { background = rounded(palette.outline, 1) }
        fun addControl(control: View) {
            strip.addView(control, buttonLayout(43, 44).apply { marginEnd = dp(2) })
        }
        fun addSeparator() {
            strip.addView(separator(), linear(1, 18).apply { marginStart = dp(3); marginEnd = dp(5) })
        }
        for (shortcut in MarkdownShortcut.entries) {
            if (shortcut == MarkdownShortcut.STRIKE || shortcut == MarkdownShortcut.BULLET ||
                shortcut == MarkdownShortcut.INLINE_CODE) addSeparator()
            val description = s(shortcut.label)
            val resource = shortcut.icon
            val chip = if (resource != null) {
                icon(resource, description, 18, quiet = true) { applyShortcut(shortcut) }
            } else label(shortcut.symbol, 13f, bold = true).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                contentDescription = description
                    isFocusable = true
                setOnClickListener { applyShortcut(shortcut) }
            }
            val horizontal = chip.paddingStart
            val vertical = chip.paddingTop
            chip.background = InsetDrawable(pressSurface(palette.paper, 10, palette.line), dp(2))
            chip.setPaddingRelative(horizontal, vertical, horizontal, vertical)
            addControl(chip)
        }
        addSeparator()
        undoButton = icon(R.drawable.ic_undo_2, s(R.string.undo), 17, quiet = true) { replayEdit(undo, redo, true) }
        redoButton = icon(R.drawable.ic_redo_2, s(R.string.redo), 17, quiet = true) { replayEdit(redo, undo, false) }
        addControl(undoButton)
        addControl(redoButton)
        updateHistoryButtons()
        return HorizontalScrollView(this).apply {
            contentDescription = s(R.string.toolbar)
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            background = rounded(palette.surface, 15, palette.line)
            clipToOutline = true
            addView(strip, FrameLayout.LayoutParams(WRAP, WRAP))
        }
    }

    private fun applyShortcut(shortcut: MarkdownShortcut) {
        if (busy || !editing) return
        val start = editor.selectionStart.coerceAtLeast(0)
        val end = editor.selectionEnd.coerceAtLeast(0)
        val hint = s(if (shortcut == MarkdownShortcut.IMAGE) R.string.image_alt else R.string.link_text)
        val edit = shortcut.apply(editor.text, start, end, hint)
        val removed = editor.text.subSequence(edit.start, edit.end).toString()
        val change = EditorChange(edit.start, removed, edit.replacement, end, edit.selectEnd)
        replaceEditorText(edit.start, edit.end, edit.replacement)
        editor.requestFocus()
        editor.setSelection(edit.selectStart, edit.selectEnd)
        recordChange(change)
    }

    private fun replaceEditorText(start: Int, end: Int, replacement: String) {
        applyingEdit = true
        try { editor.text.replace(start, end, replacement) } finally { applyingEdit = false }
    }

    private fun recordChange(change: EditorChange) {
        if (change.before == change.after) return
        redo.clear()
        if (change.before.length + change.after.length > 200_000) undo.clear()
        else {
            undo.addLast(change)
            if (undo.size > 100) undo.removeFirst()
        }
        updateHistoryButtons()
    }

    private fun replayEdit(from: ArrayDeque<EditorChange>, to: ArrayDeque<EditorChange>, reverse: Boolean) {
        if (busy || !editing) return
        val change = from.removeLastOrNull() ?: return
        val existing = if (reverse) change.after else change.before
        val replacement = if (reverse) change.before else change.after
        val end = change.start + existing.length
        if (end > editor.length() || editor.text.subSequence(change.start, end).toString() != existing) {
            undo.clear()
            redo.clear()
            updateHistoryButtons()
            return
        }
        replaceEditorText(change.start, end, replacement)
        editor.requestFocus()
        editor.setSelection((if (reverse) change.oldCursor else change.newCursor).coerceIn(0, editor.length()))
        to.addLast(change)
        updateHistoryButtons()
    }

    private fun updateHistoryButtons() {
        undoButton.isEnabled = undo.isNotEmpty()
        redoButton.isEnabled = redo.isNotEmpty()
        undoButton.alpha = if (undoButton.isEnabled) 1f else .35f
        redoButton.alpha = if (redoButton.isEnabled) 1f else .35f
    }

    private data class EditorChange(val start: Int, val before: String, val after: String, val oldCursor: Int, val newCursor: Int)

    private fun switchMode(edit: Boolean, force: Boolean = false, previewOffset: Int = 0) {
        if ((!force && (editing == edit || busy)) || draft == null) return
        editing = edit
        markdownBar.visibility = if (edit) View.VISIBLE else View.GONE
        modeTitle.text = if (edit) s(R.string.editing) else s(R.string.preview)
        modeRead.isSelected = !edit
        modeEdit.isSelected = edit
        modeRead.toolStyle(selected = !edit)
        modeEdit.toolStyle(selected = edit)
        titleField.isFocusable = edit
        titleField.isFocusableInTouchMode = edit
        titleField.isCursorVisible = edit
        titleField.isLongClickable = edit
        titleField.isClickable = true
        titleField.setOnClickListener {
            if (!editing && !busy) {
                switchMode(true)
                titleField.requestFocus()
                keyboard.showSoftInput(titleField, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        updateTitleStyle()
        if (Build.VERSION.SDK_INT >= 30) {
            modeRead.stateDescription = if (edit) null else s(R.string.selected)
            modeEdit.stateDescription = if (edit) s(R.string.selected) else null
        }
        if (edit) {
            destroyPreview()
            editor.visibility = View.VISIBLE
        } else {
            hideKeyboard()
            titleField.clearFocus()
            titleField.error = null
            editor.clearFocus()
            editor.visibility = View.GONE
            showPreview(draft ?: return, previewOffset)
        }
        updateSaveState()
    }

    private fun updateTitleStyle(focused: Boolean = titleField.hasFocus()) {
        titleField.background = if (editing) searchSurface(focused) else
            pressSurface(Color.TRANSPARENT)
        titleField.setPaddingRelative(dp(10), 0, dp(10), 0)
        titleField.ellipsize = if (editing) null else TextUtils.TruncateAt.END
    }

    private fun queueDraftSave() {
        updateSaveState()
        main.removeCallbacks(saveDraft)
        main.postDelayed(saveDraft, 350)
    }

    private fun showPreview(value: Draft, initialScroll: Int = 0) {
        val view = try {
            WebView(this)
        } catch (_: RuntimeException) {
            content.addView(label(s(R.string.webview_required), 16f, palette.muted).apply {
                setPadding(dp(22), dp(12), dp(22), 0)
            }, frameMatch())
            return
        }
        view.apply {
            setBackgroundColor(palette.paper)
            contentDescription = s(R.string.markdown_preview)
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = false
            settings.mediaPlaybackRequiresUserGesture = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            if (Build.VERSION.SDK_INT >= 33) settings.isAlgorithmicDarkeningAllowed = false
            settings.textZoom = (resources.configuration.fontScale * textScale * 100).roundToInt().coerceIn(70, 300)
        }
        preview = view
        content.addView(view, frameMatch())
        val tree = folderUri.takeIf { value.local }
        val font = noteFont
        val themeCss = notierTheme.previewCss()
        val curve = if (notierTheme.pixel) 0f else cornerScale
        val strings = PreviewStrings(appLocale.toLanguageTag()) { id, args -> s(id, *args) }
        background({
            val styles = "$themeCss\n:root{--curve:$curve;}\n$css\n${font.previewCss()}"
            Markdown.render(value.text, styles, tree != null, previewCopyIcon(), copyScript, strings)
        }) { result ->
            if (preview !== view) return@background
            result.onSuccess { page ->
                view.settings.javaScriptEnabled = page.hasFrames || page.codeBlocks.isNotEmpty() || page.outline.isNotEmpty()
                view.settings.domStorageEnabled = page.hasFrames
                view.webViewClient = PreviewClient(tree, page, font, initialScroll)
                view.webChromeClient = object : WebChromeClient() {
                    override fun onShowCustomView(video: View, callback: CustomViewCallback) {
                        if (fullScreenCallback != null) {
                            callback.onCustomViewHidden()
                            return
                        }
                        fullscreen.addView(video, frameMatch())
                        fullscreen.visibility = View.VISIBLE
                        fullScreenCallback = callback
                    }
                    override fun onHideCustomView() = exitFullscreen()
                }
                view.loadDataWithBaseURL(Markdown.ORIGIN, page.html, "text/html", "UTF-8", Markdown.ORIGIN)
            }.onFailure {
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
                preview = null
                content.addView(label(s(R.string.preview_error), 16f, palette.muted).apply {
                    setPadding(dp(22), dp(12), dp(22), 0)
                }, frameMatch())
            }
        }
    }

    private fun showContents() {
        val view = preview ?: return
        if (outline.isEmpty()) return
        val rows = settingsColumn()
        val dialog = settingsDialog(s(R.string.toc), rows)
        val topLevel = outline.minOf { it.level }
        outline.forEach { heading ->
            val row = settingsRow(heading.title, "", navigate = true)
            row.setPaddingRelative(dp(12 + (heading.level - topLevel) * 7), dp(5), dp(10), dp(5))
            row.setOnClickListener {
                dialog.dismiss()
                if (preview === view) {
                    val id = org.json.JSONObject.quote(heading.id)
                    view.evaluateJavascript("document.getElementById($id)?.scrollIntoView({block:'start'});", null)
                }
            }
            rows.addView(row)
        }
        showDialog(dialog)
    }

    private inner class PreviewClient(
        private val tree: Uri?,
        private val page: Preview,
        private val font: NoteFont,
        private var initialScroll: Int
    ) : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            if (preview !== view || url != Markdown.ORIGIN) return
            outline = page.outline
            contentsButton.visibility = if (outline.isEmpty()) View.GONE else View.VISIBLE
            if (initialScroll > 0) {
                val offset = initialScroll
                initialScroll = 0
                view.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        if (preview === view) view.scrollTo(0, offset)
                    }
                })
            }
            if (page.codeBlocks.isEmpty() || copyPort != null) return
            val ports = view.createWebMessageChannel()
            copyPort = ports[0]
            ports[0].setWebMessageCallback(object : WebMessagePort.WebMessageCallback() {
                override fun onMessage(port: WebMessagePort, message: WebMessage) {
                    if (preview !== view || copyPort !== port) return
                    val index = message.data?.toIntOrNull() ?: return
                    val code = page.codeBlocks.getOrNull(index) ?: return
                    val clipboard = getSystemService(ClipboardManager::class.java)
                    try {
                        clipboard.setPrimaryClip(ClipData.newPlainText(s(R.string.code_label), code))
                        port.postMessage(WebMessage(index.toString()))
                    } catch (_: RuntimeException) {
                        toast(s(R.string.copy_error))
                    }
                }
            })
            view.postWebMessage(WebMessage("notier-code-copy", arrayOf(ports[1])), Uri.parse(Markdown.ORIGIN))
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            if (!request.isForMainFrame) return false
            if (uri.host == Markdown.HOST && uri.path == "/" && uri.fragment != null) return false
            if (request.hasGesture() && uri.scheme in setOf("https", "http", "mailto", "tel") && uri.host != Markdown.HOST) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (_: ActivityNotFoundException) {
                    toast(s(R.string.open_link_error))
                }
            }
            return true
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url
            if (url.host != Markdown.HOST) return null
            val path = url.path.orEmpty()
            if (url.scheme == "https" && url.authority == Markdown.HOST && path == font.previewPath) {
                val stream = font.asset?.let { asset -> runCatching { assets.open(asset) }.getOrNull() }
                if (stream != null) return WebResourceResponse("font/ttf", null, stream)
            }
            val asset = page.localImages[path.removePrefix(Markdown.IMAGE_PATH)]
            val selected = tree
            if (selected != null && path.startsWith(Markdown.IMAGE_PATH) && asset != null) {
                val response = runCatching {
                    val uri = notes.image(selected, asset) ?: return@runCatching null
                    val extension = asset.last().substringAfterLast('.').lowercase(Locale.ROOT)
                    val mime = contentResolver.getType(uri)?.takeIf { it.startsWith("image/") }
                        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?.takeIf { it.startsWith("image/") }
                        ?: return@runCatching null
                    val stream = contentResolver.openInputStream(uri) ?: return@runCatching null
                    WebResourceResponse(mime, null, stream)
                }.getOrNull()
                if (response != null) return response
            }
            return WebResourceResponse("text/plain", "UTF-8", 404, "Not found", emptyMap(), ByteArrayInputStream(byteArrayOf()))
        }
    }

    private fun destroyPreview() {
        outline = emptyList()
        if (::contentsButton.isInitialized) contentsButton.visibility = View.GONE
        exitFullscreen()
        copyPort?.close()
        copyPort = null
        preview?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            view.stopLoading()
            view.destroy()
        }
        preview = null
        if (::content.isInitialized && ::editor.isInitialized && editor.parent === content) {
            for (index in content.childCount - 1 downTo 0) {
                if (content.getChildAt(index) !== editor) content.removeViewAt(index)
            }
        }
    }

    private fun exitFullscreen() {
        val callback = fullScreenCallback ?: return
        fullScreenCallback = null
        fullscreen.removeAllViews()
        fullscreen.visibility = View.GONE
        callback.onCustomViewHidden()
    }

    private fun updateSaveState() {
        val value = draft ?: return
        saveButton.isEnabled = !busy && (value.dirty || value.uri == null)
        saveButton.toolStyle(selected = false, muted = !saveButton.isEnabled)
        noteSubtitle.text = when {
            busy -> s(R.string.saving)
            value.dirty || value.uri == null -> s(R.string.unsaved)
            else -> if (value.name.isMarkdownName()) ".${value.name.substringAfterLast('.')}" else ".md"
        }
    }

    private fun saveNote() {
        val value = draft ?: return
        if (busy) return
        val typed = value.heading
        val enteredName = typed?.let { normalizedName(it, value.name) }
        if (typed != null && enteredName == null) {
            leavingAfterSave = false
            switchMode(true)
            titleField.requestFocus()
            titleField.error = s(R.string.valid_title)
            return
        }
        val tree = if (driveScreen) null else folderUri
        val directFile = value.uri?.let(DriveApi::id)
        val directFolder = value.driveParent
        if ((directFile != null || directFolder != null) && driveApi == null) {
            leavingAfterSave = false
            connectDrive()
            return
        }
        if (value.uri != null) {
            if (!value.dirty) return
            runSave({
                if (directFile != null) {
                    val api = requireDrive()
                    if (!api.writable(directFile)) throw DriveReadOnly()
                    api.update(value, enteredName ?: value.name)
                } else {
                    if (DriveDocuments.isDrive(value.uri) && !drive.writable(value.uri)) throw DriveReadOnly()
                    notes.update(value, enteredName ?: value.name, tree).also { saved ->
                        saved.uri?.let { drive.rememberRename(value.uri, it) }
                    }
                }
            })
            return
        }
        if (tree == null && directFolder == null) {
            exportNote()
            return
        }
        fun persist(name: String) {
            if (directFolder != null) {
                runSave({
                    val made = requireDrive().create(directFolder, name, value.text)
                    value.copy(uri = made.uri, name = made.name, original = value.text, heading = null)
                })
            } else saveNewNote(requireNotNull(tree), value, name)
        }
        if (enteredName != null) persist(enteredName)
        else promptName(R.string.file_name, suggestedName(value), onCancel = { leavingAfterSave = false }) { raw ->
            val name = normalizedName(raw) ?: return@promptName false
            persist(name)
            true
        }
    }

    private fun saveNewNote(tree: Uri, value: Draft, name: String) {
        runSave({
            val uri = notes.create(tree, name, value.text)
            val actualName = runCatching { notes.displayName(uri) }.getOrNull() ?: name
            value.copy(uri = uri, name = actualName, original = value.text, local = true, heading = null)
        })
    }

    private fun shareNote() {
        val value = draft ?: return
        hideKeyboard()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title(suggestedName(value)))
            putExtra(Intent.EXTRA_TEXT, value.text)
        }
        try {
            startActivity(Intent.createChooser(intent, s(R.string.share_markdown)))
        } catch (_: ActivityNotFoundException) {
            toast(s(R.string.open_link_error))
        }
    }

    private fun saveCopy() {
        val current = draft ?: return
        val folder = driveFolder
        if (!driveScreen || folder == null || folder.flags and Document.FLAG_DIR_SUPPORTS_CREATE == 0) {
            exportNote()
            return
        }
        promptName(R.string.save_as, title(suggestedName(current))) { raw ->
            val name = normalizedName(raw) ?: return@promptName false
            runSave({
                requireDrive().create(folder.id, name, current.text)
                current
            }, copyOnly = true)
            true
        }
    }

    private fun exportNote() {
        val active = draft ?: return
        if (busy) return
        hideKeyboard()
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = Notes.MIME
            putExtra(Intent.EXTRA_TITLE, normalizedName(suggestedName(active)) ?: "Not.md")
            addFlags(DOCUMENT_GRANTS)
            if (driveScreen) {
                val initial = active.uri?.takeIf(DriveDocuments::isDrive)
                    ?: driveFiles.firstOrNull { DriveDocuments.isDrive(it.uri) }?.uri
                initial?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
            }
        }
        if (driveScreen && driveFiles.isEmpty()) toast(s(R.string.drive_can_save))
        launchPicker(intent, SAVE_COPY)
    }

    @Suppress("DEPRECATION")
    private fun launchPicker(intent: Intent, request: Int) {
        try {
            pickerPending = true
            startActivityForResult(intent, request)
        } catch (_: ActivityNotFoundException) {
            pickerPending = false
            leavingAfterSave = false
            toast(s(R.string.no_picker))
        }
    }

    @Deprecated("Platform activity results keep this single-activity app independent of AndroidX")
    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (request == PICK_GOOGLE_ACCOUNT) {
            driveConsentPending = false
            val selected = data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)?.takeIf(String::isNotBlank)
            if (result != RESULT_OK || selected == null) driveAuthorizationFailed(false)
            else authorizeDrive(interactive = true, accountName = selected)
            return
        }
        if (request == AUTHORIZE_DRIVE) {
            driveAuthorizationResult(result, data)
            return
        }
        if (request != PICK_FOLDER && request != SAVE_COPY && request != PICK_DRIVE) return
        pickerPending = false
        if (result != RESULT_OK || data == null) {
            leavingAfterSave = false
            if (!startLoading) persistDraft()
            return
        }
        if (request == PICK_DRIVE) {
            addDriveDocuments(data)
            return
        }
        val uri = data.data ?: run {
            leavingAfterSave = false
            return
        }
        if (request == PICK_FOLDER) {
            selectFolder(data, uri)
            return
        }
        val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (startLoading) {
            pendingCopyUri = uri
            pendingCopyFlags = flags
        } else {
            onCopySelected(uri, flags)
        }
    }

    private fun onCopySelected(uri: Uri, flags: Int) {
        val value = draft ?: return
        val tree = folderUri
        val copyOnly = value.uri != null
        val expectedDrive = driveScreen
        runSave({
            var destination = uri
            var name = runCatching { notes.displayName(uri) }.getOrNull()
            if (name != null && !name.isMarkdownName()) {
                val corrected = normalizedName(name) ?: "Not.md"
                destination = DocumentsContract.renameDocument(contentResolver, uri, corrected)
                    ?: error(".md uzantısı eklenemedi.")
                name = runCatching { notes.displayName(destination) }.getOrNull() ?: corrected
            }
            notes.write(destination, value.text)
            if (DriveDocuments.isDrive(destination)) {
                val registered = runCatching { drive.remember(destination, flags) }.isSuccess
                if (!registered) main.post { toast(s(R.string.drive_bookmark_error)) }
            } else if (!copyOnly) runCatching { contentResolver.takePersistableUriPermission(destination, flags) }
            if (expectedDrive && !DriveDocuments.isDrive(destination)) {
                main.post { toast(s(R.string.drive_saved_elsewhere)) }
            }
            if (copyOnly) return@runSave value
            val filename = name ?: normalizedName(suggestedName(value)) ?: "Not.md"
            val local = tree != null && runCatching {
                destination.authority == tree.authority && notes.list(tree).notes.any {
                    DocumentsContract.getDocumentId(it.uri) == DocumentsContract.getDocumentId(destination)
                }
            }.getOrDefault(false)
            value.copy(uri = destination, name = filename, original = value.text, local = local, heading = null)
        }, copyOnly = copyOnly)
    }

    private fun runSave(
        work: () -> Draft,
        copyOnly: Boolean = false
    ) {
        hideKeyboard()
        val snapshot = draft
        busy = true
        editor.isEnabled = false
        titleField.isEnabled = false
        updateSaveState()
        background({
            runCatching { drafts.store(snapshot, keepClean = true) }
            try {
                val saved = work()
                runCatching { drafts.store(if (copyOnly) snapshot else null) }
                saved
            } catch (error: Exception) {
                runCatching { drafts.store((error as? NoteRenameFailed)?.draft ?: snapshot) }
                throw error
            }
        }) { result ->
            busy = false
            editor.isEnabled = true
            titleField.isEnabled = true
            result.onSuccess { saved ->
                if (driveScreen && driveApi != null) {
                    invalidateDrivePages()
                }
                toast(if (copyOnly) s(R.string.copy_saved) else s(R.string.note_saved))
                if (!copyOnly) {
                    migratePin(snapshot?.uri, saved.uri)
                    val leave = leavingAfterSave
                    leavingAfterSave = false
                    val wasEditing = editing
                    main.removeCallbacks(saveDraft)
                    destroyPreview()
                    if (leave) returnHome() else showNote(saved, wasEditing, resetHistory = false)
                }
            }.onFailure { error ->
                leavingAfterSave = false
                if (error is NoteChanged) {
                    showDialog(dialogBuilder().setTitle(s(R.string.file_changed))
                        .setMessage(s(R.string.file_changed_message))
                        .setNegativeButton(s(R.string.cancel), null)
                        .setPositiveButton(s(R.string.save_as)) { _, _ -> exportNote() }
                        .create())
                } else if (error is DriveReadOnly) {
                    showDialog(dialogBuilder().setTitle(s(R.string.drive_read_only))
                        .setMessage(s(R.string.drive_read_only_hint))
                        .setNegativeButton(s(R.string.cancel), null)
                        .setPositiveButton(s(R.string.save_as)) { _, _ -> exportNote() }
                        .create())
                } else if (driveError(error)) {
                    Unit
                } else if (error is NoteRenameFailed) {
                    draft = error.draft
                    toast(if (error.duplicate) s(R.string.title_duplicate) else
                        s(R.string.rename_error))
                } else {
                    toast(s(R.string.save_error))
                }
            }
            updateSaveState()
        }
    }

    @Deprecated("Android 12 and earlier use the legacy back callback")
    override fun onBackPressed() = navigateBack()

    private fun navigateBack() {
        if (fullScreenCallback != null) {
            exitFullscreen()
            return
        }
        if (busy || pickerPending) return
        val value = draft
        if (value == null) {
            if (query.isNotEmpty()) {
                searchField.setText("")
                searchField.clearFocus()
            } else if (driveScreen) {
                val parent = driveFolder?.parent
                if (parent != null) openDriveFolder(parent) else switchStorage(false)
            } else {
                finish()
            }
            return
        }
        if (!value.dirty) {
            returnHome()
            return
        }
        showUnsavedDialog()
    }

    private fun showUnsavedDialog() {
        val choices = settingsColumn()
        val dialog = settingsDialog(s(R.string.note_unsaved), choices)
        choices.addView(label(s(R.string.save_changes), 15f, palette.muted).apply {
            setPaddingRelative(dp(7), dp(3), dp(7), dp(14))
        }, LinearLayout.LayoutParams(MATCH, WRAP))

        fun action(labelId: Int, primary: Boolean = false, onClick: () -> Unit) {
            val action = label(s(labelId), 15f, if (primary) palette.onAccent else palette.ink, bold = primary).apply {
                gravity = Gravity.CENTER
                minimumHeight = buttonDp(48)
                setPaddingRelative(dp(12), dp(11), dp(12), dp(11))
                background = pressSurface(if (primary) palette.accent else palette.surface, 12, palette.line)
                isFocusable = true
                setOnClickListener { dialog.dismiss(); onClick() }
            }
            choices.addView(action, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(6) })
        }
        action(R.string.save, primary = true) {
            leavingAfterSave = true
            saveNote()
        }
        action(R.string.keep_editing) { switchMode(true) }
        action(R.string.discard) { returnHome() }
        showDialog(dialog)
    }

    private fun returnHome() {
        hideKeyboard()
        main.removeCallbacks(saveDraft)
        draft = null
        persistDraft()
        showHome()
        reloadNotes()
    }

    private fun persistDraft() {
        if (startLoading || busy) return
        val snapshot = draft
        val keepClean = pickerPending || keepNoteAfterRecreate
        worker.execute {
            runCatching { drafts.store(snapshot, keepClean) }
        }
    }

    private fun releaseGrant(uri: Uri): Boolean {
        val grant = contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri } ?: return true
        val flags = (if (grant.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
            (if (grant.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        return runCatching { contentResolver.releasePersistableUriPermission(uri, flags) }.isSuccess
    }

    private fun suggestedName(value: Draft): String {
        value.heading?.let { normalizedName(it, value.name) }?.let { return it }
        if (value.uri != null) return value.name
        val heading = value.text.lineSequence().firstOrNull { it.isNotBlank() }
            ?.trim()?.trimStart('#')?.trim()?.take(65)
            ?.filterNot { it.isISOControl() || it in "/\\:*?\"<>|" }
        return heading?.ifBlank { null } ?: s(R.string.new_note)
    }

    private fun title(filename: String) =
        if (filename.isMarkdownName()) filename.substringBeforeLast('.').ifBlank { filename } else filename

    private fun searchKey(text: String) = text.lowercase(Locale.ROOT).replace("i\u0307", "i").replace('ı', 'i')

    private fun displayTitle(file: NoteFile) = if (file.isFolder) file.name else title(file.name)

    private inner class NoteAdapter : BaseAdapter() {
        var items = emptyList<NoteFile>()
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, old: View?, container: ViewGroup): View {
            val card = old as? NoteCard ?: NoteCard()
            card.bind(getItem(position))
            return card
        }
    }

    private inner class NoteCard : FrameLayout(this@MainActivity) {
        private var folderId: String? = null
        private val icon = glyph(R.drawable.ic_file_text, palette.ink)
        private val pin = glyph(R.drawable.ic_pin, palette.muted)
        private val title = label("", if (listLayout == 1) 16f else 18f, bold = true).apply {
            maxLines = if (listLayout == 1) 1 else 2
            if (listLayout == 2) setLines(2)
            ellipsize = TextUtils.TruncateAt.END
        }
        private val detail = label("", 12f, palette.muted).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPaddingRelative(0, dp(4), 0, 0)
        }

        init {
            val grid = listLayout == 2
            val compact = listLayout == 1
            setPadding(0, 0, 0, dp(if (compact) 4 else 7))
            val body = LinearLayout(this@MainActivity).apply {
                orientation = if (grid) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                gravity = if (grid) Gravity.START else Gravity.CENTER_VERTICAL
                minimumHeight = dp(if (compact) 52 else if (grid) 112 else 64)
                background = pressSurface(palette.paper, 15, palette.line)
                isDuplicateParentStateEnabled = true
                setPaddingRelative(dp(11), dp(9), dp(11), dp(9))
            }
            val badge = FrameLayout(this@MainActivity).apply {
                background = rounded(palette.surface, 10)
                addView(icon, FrameLayout.LayoutParams(dp(19), dp(19), Gravity.CENTER))
            }
            val badgeSize = if (compact) 28 else 34
            val details = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPaddingRelative(if (grid) 0 else dp(11), if (grid) dp(8) else 0, 0, 0)
                addView(title, LinearLayout.LayoutParams(MATCH, WRAP))
                addView(detail, LinearLayout.LayoutParams(MATCH, WRAP))
            }
            if (grid) {
                val head = LinearLayout(this@MainActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(badge, linear(badgeSize, badgeSize))
                    addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f))
                    addView(pin, linear(17, 17))
                }
                body.addView(head, LinearLayout.LayoutParams(MATCH, WRAP))
                body.addView(details, LinearLayout.LayoutParams(MATCH, WRAP))
            } else {
                body.addView(badge, linear(badgeSize, badgeSize))
                body.addView(details, LinearLayout.LayoutParams(0, WRAP, 1f))
                body.addView(pin, linear(17, 17).apply { marginStart = dp(6) })
            }
            addView(body, FrameLayout.LayoutParams(MATCH, WRAP))
        }

        fun bind(file: NoteFile) {
            folderId = if (file.isFolder) DriveApi.id(file.uri) else null
            icon.setImageDrawable(appIcons.drawable(if (file.isFolder) R.drawable.ic_folder_open else R.drawable.ic_file_text, iconStyle))
            title.text = displayTitle(file)
            val isPinned = !file.isFolder && file.uri.toString() in pinned
            pin.visibility = if (isPinned) View.VISIBLE else if (listLayout == 2) View.INVISIBLE else View.GONE
            if (Build.VERSION.SDK_INT >= 30) stateDescription = if (isPinned) s(R.string.pinned) else null
            detail.text = when {
                file.isFolder -> ""
                file.modified > 0 -> dateFormat.format(Date(file.modified))
                else -> ""
            }
            if (file.isFolder) updateFolderDetail()
            detail.visibility = if (detail.text.isNotEmpty() || listLayout == 2) View.VISIBLE else View.GONE
            applyScreenFonts(this)
        }

        fun updateFolderDetail() {
            val id = folderId ?: return
            detail.text = driveCounts[id]?.let { quantity(R.plurals.notes_total, it) } ?: "—"
        }
    }

    private fun <T> background(action: () -> T, done: (Result<T>) -> Unit) {
        worker.execute {
            val result = runCatching(action)
            main.post { if (!isDestroyed && !isFinishing) done(result) }
        }
    }

    private val keyboard get() = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

    private fun hideKeyboard() {
        keyboard.hideSoftInputFromWindow(root.windowToken, 0)
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    private fun s(id: Int, vararg args: Any): String = if (args.isEmpty()) getString(id) else getString(id, *args)
    private fun px(value: Float) = (value * resources.displayMetrics.density).roundToInt()
    private fun dp(value: Int) = px(value * interfaceScale)
    private fun buttonDp(value: Int) = px((value * buttonScale).coerceAtLeast(44f))
    private fun buttonLayout(width: Int = 48, height: Int = width) = LinearLayout.LayoutParams(buttonDp(width), buttonDp(height))
    private fun frameMatch() = FrameLayout.LayoutParams(MATCH, MATCH)
    private fun linear(width: Int, height: Int) = LinearLayout.LayoutParams(dp(width), dp(height))

    private fun label(value: String, size: Float, color: Int = palette.ink, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size * textScale
        setTextColor(color)
        if (bold) tag = true
        applyTextFont()
        includeFontPadding = false
    }

    private fun TextView.accessibilityHeadingIfSupported() {
        if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
    }

    private fun TextView.lineHeightIfSupported(value: Int) {
        if (Build.VERSION.SDK_INT >= 28) lineHeight = dp(value)
    }

    private fun EditText.watch(changed: (String) -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                changed(text?.toString().orEmpty())
            }
            override fun afterTextChanged(value: Editable?) = Unit
        })
    }

    private fun typeface(font: NoteFont): Typeface = fonts.getOrPut(font) {
        runCatching { font.load(assets) }.getOrDefault(Typeface.DEFAULT)
    }

    private fun TextView.applyTextFont() {
        val font = tag as? NoteFont ?: noteFont
        typeface = if (tag == true) boldFonts.getOrPut(font) {
            if (Build.VERSION.SDK_INT >= 28) Typeface.create(typeface(font), 670, false)
            else Typeface.create(typeface(font), Typeface.BOLD)
        } else typeface(font)
    }

    private fun applyScreenFonts(view: View) {
        if (view is TextView) view.applyTextFont()
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) applyScreenFonts(view.getChildAt(index))
        }
    }

    private fun dialogBuilder() = AlertDialog.Builder(this, if (notierTheme == NotierTheme.DARK)
        R.style.NotierDialogDark else R.style.NotierDialogLight)

    private fun showDialog(dialog: AlertDialog) {
        dialog.show()
        shownDialog = dialog
        dialog.window?.let { window ->
            window.setBackgroundDrawable(InsetDrawable(rounded(palette.paper, 24, palette.line), dp(8)))
            applyScreenFonts(window.decorView)
        }
        for (which in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)) {
            dialog.getButton(which)?.apply {
                val primary = which == AlertDialog.BUTTON_POSITIVE
                setTextColor(if (primary) palette.onAccent else palette.ink)
                background = InsetDrawable(pressSurface(if (primary) palette.accent else palette.surface, 11), dp(3))
                isAllCaps = false
                setSingleLine(false)
                maxLines = 3
                ellipsize = null
                setPaddingRelative(dp(11), dp(8), dp(11), dp(8))
            }
        }
    }

    private fun rounded(color: Int, radius: Int, border: Int? = null, curve: Float = cornerScale) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = if (notierTheme.pixel) 0f else dp(radius) * curve
        if (border != null) setStroke(px(1f).coerceAtLeast(1), border)
    }

    private fun searchSurface(focused: Boolean) = rounded(palette.surface, 15, if (focused) palette.muted else palette.line)

    private fun pressSurface(color: Int, radius: Int = 12, border: Int? = null) = StateListDrawable().apply {
        val accented = color == palette.accent
        val pressed = if (accented) palette.accentPressed else palette.pressed
        val hovered = if (accented) palette.accentHover else palette.hover
        fun face(fill: Int, edge: Int?, inset: Boolean = false): android.graphics.drawable.Drawable {
            if (!notierTheme.pixel || !accented) return rounded(fill, radius, edge)
            val light = Color.rgb(132, 159, 98)
            val dark = Color.rgb(45, 58, 32)
            val bevel = px(2f).coerceAtLeast(1)
            return LayerDrawable(arrayOf(rounded(dark, 0), rounded(if (inset) dark else light, 0), rounded(fill, 0))).apply {
                setLayerInset(1, bevel, bevel, bevel, bevel)
                setLayerInset(2, bevel * 2, if (inset) bevel * 3 else bevel * 2, bevel * 2, bevel * 3)
            }
        }
        addState(intArrayOf(-android.R.attr.state_enabled), face(color, border, true))
        addState(intArrayOf(android.R.attr.state_pressed), face(pressed, border, true))
        addState(intArrayOf(android.R.attr.state_hovered), face(hovered, border))
        addState(intArrayOf(android.R.attr.state_focused), face(hovered, palette.outline))
        addState(intArrayOf(), face(color, border))
    }

    private fun shouldTint(resource: Int, style: IconStyle) = resource != R.drawable.app_icon &&
        (style != IconStyle.PIXEL || !usesPixelColors(resource))

    private fun glyph(resource: Int, tint: Int, style: IconStyle = iconStyle) = ImageView(this).apply {
        setImageDrawable(appIcons.drawable(resource, style))
        if (shouldTint(resource, style)) imageTintList = ColorStateList.valueOf(tint)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun icon(resource: Int, description: String, size: Int, quiet: Boolean = false, tapped: (View) -> Unit) =
        ImageButton(this).apply {
            setImageDrawable(appIcons.drawable(resource, iconStyle)?.apply { setBounds(0, 0, dp(size), dp(size)) })
            if (shouldTint(resource, iconStyle)) imageTintList = ColorStateList.valueOf(if (quiet) palette.ink else palette.onAccent)
            tag = resource
            scaleType = ImageView.ScaleType.FIT_CENTER
            val inset = px((if (size == 34) 15 else if (quiet && size <= 18) 11 else 12) * buttonScale)
            contentDescription = description
            background = pressSurface(if (quiet) Color.TRANSPARENT else palette.accent, if (size == 34) 19 else 13)
            setPadding(inset, inset, inset, inset)
            setOnClickListener(tapped)
        }

    private fun ImageButton.toolStyle(selected: Boolean, muted: Boolean = false) {
        val color = if (selected) palette.paper else if (muted) palette.disabled else palette.accent
        background = InsetDrawable(pressSurface(color, 12, if (selected) palette.outline else null), dp(3))
        val inset = px(13 * buttonScale)
        setPadding(inset, inset, inset, inset)
        val tint = if (selected) palette.ink else if (muted && notierTheme == NotierTheme.DARK) palette.muted else palette.onAccent
        imageTintList = if (shouldTint(tag as? Int ?: 0, iconStyle)) ColorStateList.valueOf(tint) else null
        imageAlpha = if (muted) 150 else 255
    }

    companion object {
        // Activity recreation must queue draft recovery after the old activity's writes.
        private val worker = Executors.newSingleThreadExecutor()
        private val languages = listOf("tr" to "Türkçe", "en" to "English", "de" to "Deutsch",
            "es" to "Español", "fr" to "Français", "it" to "Italiano", "pt" to "Português",
            "ru" to "Русский", "ar" to "العربية", "ja" to "日本語", "ko" to "한국어", "zh" to "简体中文",
            "nl" to "Nederlands", "sv" to "Svenska", "nb" to "Norsk bokmål", "da" to "Dansk",
            "fi" to "Suomi", "pl" to "Polski", "cs" to "Čeština", "sk" to "Slovenčina",
            "hu" to "Magyar", "ro" to "Română", "uk" to "Українська", "el" to "Ελληνικά",
            "he" to "עברית", "fa" to "فارسی", "hi" to "हिन्दी", "bn" to "বাংলা",
            "ur" to "اردو", "id" to "Bahasa Indonesia", "ms" to "Bahasa Melayu", "vi" to "Tiếng Việt",
            "th" to "ไทย", "fil" to "Filipino", "sw" to "Kiswahili", "sr" to "Српски",
            "bg" to "Български", "hr" to "Hrvatski", "lt" to "Lietuvių", "lv" to "Latviešu",
            "et" to "Eesti", "zh-Hant" to "繁體中文",
            "kk" to "Қазақша · Кирилл",
            "kk-Latn" to "Qazaqşa · Latyn (2021)",
            "ky" to "Кыргызча", "az" to "Azərbaycanca", "tk" to "Türkmençe",
            "uz" to "Oʻzbekcha · Lotin (1995)", "uz-Latn-2026" to "Özbekça · Lotin (2026)",
            "uz-Latn-1993" to "Ōzbekça · Lotin (1993)", "uz-Cyrl" to "Ўзбекча · Кирилл",
            "tg" to "Тоҷикӣ", "fa-AF" to "دری", "ps" to "پښتو")
        private val deferredAnnouncements = mutableSetOf<String>()
        private const val PICK_FOLDER = 11
        private const val SAVE_COPY = 12
        private const val PICK_DRIVE = 13
        private const val AUTHORIZE_DRIVE = 14
        private const val PICK_GOOGLE_ACCOUNT = 15
        private const val UPDATE_PERMISSION = 16
        private const val DOCUMENT_GRANTS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}

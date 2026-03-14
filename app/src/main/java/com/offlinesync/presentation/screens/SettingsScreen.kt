package com.offlinesync.presentation.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.offlinesync.R
import com.offlinesync.utils.LanguageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateHome: () -> Unit = {}
) {
    val context = LocalContext.current
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    
    val currentLang = remember { LanguageManager.getCurrentLanguage(context) }
    val strings = remember(currentLang) { getStringsForLanguage(currentLang) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.settings) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Back",
                            modifier = Modifier.size(48.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateHome) {
                        Icon(
                            Icons.Default.Home, 
                            contentDescription = "Home",
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SettingsItem(
                    icon = Icons.Default.Language,
                    title = strings.language,
                    subtitle = strings.languageName,
                    onClick = { showLanguageDialog = true }
                )
            }
            
            item {
                SettingsItem(
                    icon = Icons.Default.Help,
                    title = strings.help,
                    subtitle = strings.helpTitle,
                    onClick = { showHelpDialog = true }
                )
            }
            
            item {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "OfflineSync",
                    subtitle = "Version 1.0",
                    onClick = { }
                )
            }
        }
    }
    
    // Language selection dialog
    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentLanguage = LanguageManager.getCurrentLanguage(context),
            onLanguageSelected = { lang ->
                LanguageManager.setLanguage(context, lang)
                showLanguageDialog = false
                showRestartDialog = true
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    // Restart dialog
    if (showRestartDialog) {
        RestartDialog(
            onRestart = {
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                System.exit(0)
            },
            onDismiss = { showRestartDialog = false }
        )
    }
    
    // Help dialog
    if (showHelpDialog) {
        HelpDialog(onDismiss = { showHelpDialog = false })
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(text = title)
                Text(
                    text = subtitle,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun LanguageSelectionDialog(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val strings = getStringsForLanguage(LanguageManager.getCurrentLanguage(context))
    
    val languages = listOf(
        LanguageManager.LANGUAGE_EN to "English",
        LanguageManager.LANGUAGE_RU to "Русский",
        LanguageManager.LANGUAGE_UK to "Українська"
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.selectLanguage) },
        text = {
            Column {
                languages.forEach { (code, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageSelected(code) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentLanguage == code,
                            onClick = { onLanguageSelected(code) }
                        )
                        Text(text = name, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

@Composable
fun RestartDialog(
    onRestart: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val strings = getStringsForLanguage(LanguageManager.getCurrentLanguage(context))
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.restartRequired) },
        text = { Text(strings.restartRequired) },
        confirmButton = {
            TextButton(onClick = onRestart) {
                Text(strings.restartNow)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.later)
            }
        }
    )
}

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lang = LanguageManager.getCurrentLanguage(context)
    val strings = getStringsForLanguage(lang)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.helpTitle) },
        text = {
            Text(strings.helpContent)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

data class AppStrings(
    val settings: String,
    val language: String,
    val languageName: String,
    val selectLanguage: String,
    val restartRequired: String,
    val restartNow: String,
    val later: String,
    val help: String,
    val helpTitle: String,
    val helpContent: String,
    val cancel: String,
    val browseFiles: String,
    val backupContacts: String,
    val restoreContacts: String,
    val backupSms: String = "Backup SMS",
    val restoreSms: String = "Restore SMS",
    val startBackup: String = "Start Backup",
    val startRestore: String = "Start Restore",
    val requestContactsPermission: String = "Request Contacts Permission",
    val requestPhoneStatePermission: String = "Request Phone State Permission",
    val requestSmsPermission: String = "Request SMS Permission",
    val selectBackupFile: String = "Select Backup File",
    val requestWriteContactsPermission: String = "Request Write Contacts Permission",
    val restoreToDevice: String = "Restore to Device",
    val restoreToSim: String = "Restore to SIM",
    val selectSimCard: String = "Select SIM Card:",
    val appendSms: String = "Append to existing",
    val replaceSms: String = "Replace all SMS",
    val selectSimToBackup: String = "Select SIM to backup:",
    val allSimCards: String = "All SIM cards",
    val filterBySourceSim: String = "Filter by source SIM:",
    val allSourceSims: String = "All source SIMs",
    val selectTargetSim: String = "Select target SIM for restore:",
    val backupApps: String = "Backup Apps",
    val backupMedia: String = "Backup Media",
    val selectMediaTypes: String = "Select media types",
    val images: String = "Images",
    val videos: String = "Videos",
    val audio: String = "Audio",
    val supportedMessengers: String = "Supported messengers",
    val backupResult: String = "Backup Result",
    val totalFiles: String = "Total files",
    val backedUpFiles: String = "Backed up files",
    val skippedFiles: String = "Skipped files",
    val backedUpSize: String = "Backed up size",
    val sourceDetails: String = "Source details"
)

fun getStringsForLanguage(lang: String): AppStrings {
    return when (lang) {
        LanguageManager.LANGUAGE_RU -> AppStrings(
            settings = "Настройки",
            language = "Язык",
            languageName = "Русский",
            selectLanguage = "Выберите язык",
            restartRequired = "Требуется перезапуск для смены языка",
            restartNow = "Перезапустить",
            later = "Позже",
            help = "Справка",
            helpTitle = "Справка",
            helpContent = "OfflineSync позволяет синхронизировать файлы между Android и компьютером без интернета.\n\n1. Подключите оба устройства к одной WiFi сети\n2. Запустите сервер на Android\n3. Запустите синхронизацию на компьютере\n\nФайлы сохраняются в папке Загрузки/OfflineSync.",
            cancel = "Отмена",
            browseFiles = "Файлы",
            backupContacts = "Резервная копия контактов",
            restoreContacts = "Восстановить контакты",
            backupSms = "Резервная копия SMS",
            restoreSms = "Восстановить SMS",
            startBackup = "Начать резервное копирование",
            startRestore = "Начать восстановление",
            requestContactsPermission = "Разрешение на чтение контактов",
            requestPhoneStatePermission = "Разрешение на чтение состояния телефона",
            requestSmsPermission = "Разрешение на чтение SMS",
            selectBackupFile = "Выбрать файл резервной копии",
            requestWriteContactsPermission = "Разрешение на запись контактов",
            restoreToDevice = "Восстановить на устройство",
            restoreToSim = "Восстановить на SIM",
            selectSimCard = "Выберите SIM карту:",
            appendSms = "Добавить к существующим",
            replaceSms = "Заменить все SMS",
            selectSimToBackup = "Выберите SIM для резервной копии:",
            allSimCards = "Все SIM карты",
            filterBySourceSim = "Фильтр по исходной SIM:",
            allSourceSims = "Все исходные SIM",
            selectTargetSim = "Выберите целевую SIM для восстановления:",
            backupApps = "Резервная копия приложений",
            backupMedia = "Резервная копия медиа",
            selectMediaTypes = "Выберите типы медиа",
            images = "Изображения",
            videos = "Видео",
            audio = "Аудио",
            supportedMessengers = "Поддерживаемые мессенджеры",
            backupResult = "Результат резервного копирования",
            totalFiles = "Всего файлов",
            backedUpFiles = "Скопировано файлов",
            skippedFiles = "Пропущено файлов",
            backedUpSize = "Скопировано",
            sourceDetails = "Источники"
        )
        LanguageManager.LANGUAGE_UK -> AppStrings(
            settings = "Налаштування",
            language = "Мова",
            languageName = "Українська",
            selectLanguage = "Виберіть мову",
            restartRequired = "Потрібен перезапуск для зміни мови",
            restartNow = "Перезапустити",
            later = "Пізніше",
            help = "Допомога",
            helpTitle = "Допомога",
            helpContent = "OfflineSync дозволяє синхронізувати файли між Android та комп'ютером без інтернету.\n\n1. Підключіть обидва пристрої до однієї WiFi мережі\n2. Запустіть сервер на Android\n3. Запустіть синхронізацію на комп'ютері\n\nФайли зберігаються в папці Завантаження/OfflineSync.",
            cancel = "Скасувати",
            browseFiles = "Файли",
            backupContacts = "Резервна копія контактів",
            restoreContacts = "Відновити контакти",
            backupSms = "Резервна копія SMS",
            restoreSms = "Відновити SMS",
            startBackup = "Почати резервне копіювання",
            startRestore = "Почати відновлення",
            requestContactsPermission = "Дозвіл на читання контактів",
            requestPhoneStatePermission = "Дозвіл на читання стану телефону",
            requestSmsPermission = "Дозвіл на читання SMS",
            selectBackupFile = "Вибрати файл резервної копії",
            requestWriteContactsPermission = "Дозвіл на запис контактів",
            restoreToDevice = "Відновити на пристрій",
            restoreToSim = "Відновити на SIM",
            selectSimCard = "Оберіть SIM карту:",
            appendSms = "Додати до існуючих",
            replaceSms = "Замінни всі SMS",
            selectSimToBackup = "Оберіть SIM для резервної копії:",
            allSimCards = "Всі SIM карти",
            filterBySourceSim = "Фільтр за SIM-картою:",
            allSourceSims = "Всі SIM-карти",
            selectTargetSim = "Оберіть цільову SIM для відновлення:",
            backupApps = "Резервна копія додатків",
            backupMedia = "Резервна копія медіа",
            selectMediaTypes = "Виберіть типи медіа",
            images = "Зображення",
            videos = "Відео",
            audio = "Аудіо",
            supportedMessengers = "Підтримувані месенджери",
            backupResult = "Результат резервного копіювання",
            totalFiles = "Всього файлів",
            backedUpFiles = "Скопійовано файлів",
            skippedFiles = "Пропущено файлів",
            backedUpSize = "Скопійовано",
            sourceDetails = "Джерела"
        )
        else -> AppStrings(
            settings = "Settings",
            language = "Language",
            languageName = "English",
            selectLanguage = "Select Language",
            restartRequired = "Restart required to change language",
            restartNow = "Restart Now",
            later = "Later",
            help = "Help",
            helpTitle = "Help",
            helpContent = "OfflineSync allows you to sync files between your Android device and computer without internet.\n\n1. Connect both devices to the same WiFi network\n2. Start the server on Android\n3. Run sync on your computer\n\nFiles are stored in Downloads/OfflineSync folder.",
            cancel = "Cancel",
            browseFiles = "Browse Files",
            backupContacts = "Backup Contacts",
            restoreContacts = "Restore Contacts",
            backupSms = "Backup SMS",
            restoreSms = "Restore SMS",
            startBackup = "Start Backup",
            startRestore = "Start Restore",
            requestContactsPermission = "Request Contacts Permission",
            requestPhoneStatePermission = "Request Phone State Permission",
            requestSmsPermission = "Request SMS Permission",
            selectBackupFile = "Select Backup File",
            requestWriteContactsPermission = "Request Write Contacts Permission",
            restoreToDevice = "Restore to Device",
            restoreToSim = "Restore to SIM",
            selectSimCard = "Select SIM Card:",
            appendSms = "Append to existing",
            replaceSms = "Replace all SMS",
            selectSimToBackup = "Select SIM to backup:",
            allSimCards = "All SIM cards",
            filterBySourceSim = "Filter by source SIM:",
            allSourceSims = "All source SIMs",
            selectTargetSim = "Select target SIM for restore:",
            backupApps = "Backup Apps",
            backupMedia = "Backup Media",
            selectMediaTypes = "Select media types",
            images = "Images",
            videos = "Videos",
            audio = "Audio",
            supportedMessengers = "Supported messengers",
            backupResult = "Backup Result",
            totalFiles = "Total files",
            backedUpFiles = "Backed up files",
            skippedFiles = "Skipped files",
            backedUpSize = "Backed up size",
            sourceDetails = "Source details"
        )
    }
}

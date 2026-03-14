#!/usr/bin/env python3
"""
OfflineSync - Ubuntu Desktop Client
Графический интерфейс для синхронизации файлов между Ubuntu и Android
"""

import tkinter as tk
from tkinter import ttk, messagebox, filedialog
import threading
import os
import sys
from pathlib import Path
from urllib.parse import quote
import zeroconf
import time
import requests
import hashlib
import json
import configparser


LANGUAGE_EN = "en"
LANGUAGE_RU = "ru"
LANGUAGE_UK = "uk"

TRANSLATIONS = {
    LANGUAGE_EN: {
        "app_title": "OfflineSync - Ubuntu ↔ Android",
        "folder": "Folder:",
        "browse": "Browse",
        "devices": "Devices",
        "refresh": "Refresh",
        "not_connected": "Not connected",
        "connected": "Connected",
        "download": "Download ↓",
        "upload": "Upload ↑",
        "stop": "Stop",
        "log": "Log",
        "file_exists": "File exists. Overwrite?",
        "yes": "Yes",
        "no": "No",
        "cancel_all": "Cancel All",
        "searching_devices": "Searching devices...",
        "select_device": "Select a device!",
        "device_selected": "Selected",
        "device_unavailable": "Device unavailable",
        "items_on_android": "Items on Android",
        "created_folder": "Created folder",
        "local_files": "Local files",
        "downloaded": "Downloaded",
        "uploaded": "Uploaded",
        "error": "Error",
        "getting_apps_list": "Getting apps list...",
        "installed_apps": "Installed apps",
        "saved_apps": "Saved apps",
        "error_getting_apps": "Error getting apps",
        "file_exists": "File exists",
        "settings": "Settings",
        "language": "Language",
        "help": "Help",
        "restart_required": "Restart required to apply language change",
        "restart_now": "Restart Now",
        "later": "Later",
        "help_title": "Help",
        "help_content": "OfflineSync allows you to sync files between your Android device and computer without internet.\n\n1. Connect both devices to the same WiFi network\n2. Browse and transfer files\n\nFiles are stored in the OfflineSync folder in your home directory.",
        "english": "English",
        "russian": "Russian",
        "ukrainian": "Ukrainian",
        "select_language": "Select Language",
        "warning": "Warning",
        "done": "Done",
        "cancel": "Cancel",
        "android_files": "Files on Android",
        "local_browser": "Local Files",
        "ftp_refresh": "Refresh",
        "ftp_download": "Download",
        "ftp_upload": "Upload",
        "ftp_current_dir": "Path:",
        "ftp_select_file": "Select file to download",
        "ftp_select_local": "Select local file to upload",
        "select_folder": "Select folder",
        "progress": "Progress",
        "current_operation": "Current:",
        "parent_dir": "Parent",
        "browse_local": "Browse",
        "downloading": "Downloading",
        "uploading": "Uploading",
        "delete": "Delete",
        "delete_confirm": "Delete this?",
        "delete_success": "Deleted successfully",
        "delete_error": "Delete error",
    },
    LANGUAGE_RU: {
        "app_title": "OfflineSync - Ubuntu ↔ Android",
        "folder": "Папка:",
        "browse": "Выбрать",
        "devices": "Устройства",
        "refresh": "Обновить",
        "not_connected": "Не подключено",
        "connected": "Подключено",
        "download": "Скачать ↓",
        "upload": "Загрузить ↑",
        "stop": "Стоп",
        "log": "Лог",
        "file_exists": "Файл существует. Перезаписать?",
        "yes": "Да",
        "no": "Нет",
        "cancel_all": "Отменить все",
        "searching_devices": "Поиск устройств...",
        "select_device": "Выберите устройство!",
        "device_selected": "Выбрано",
        "device_unavailable": "Устройство недоступно",
        "items_on_android": "Элементов на Android",
        "created_folder": "Создана папка",
        "local_files": "Файлов локально",
        "downloaded": "Скачано",
        "uploaded": "Загружено",
        "error": "Ошибка",
        "getting_apps_list": "Получение списка приложений...",
        "installed_apps": "Список установленных приложений",
        "saved_apps": "Сохранено приложений",
        "error_getting_apps": "Ошибка получения приложений",
        "file_exists": "Файл существует",
        "settings": "Настройки",
        "language": "Язык",
        "help": "Справка",
        "restart_required": "Требуется перезапуск для смены языка",
        "restart_now": "Перезапустить",
        "later": "Позже",
        "help_title": "Справка",
        "help_content": "OfflineSync позволяет синхронизировать файлы между Android и компьютером без интернета.\n\n1. Подключите оба устройства к одной WiFi сети\n2. Просматривайте и передавайте файлы\n\nФайлы сохраняются в папке OfflineSync в домашнем каталоге.",
        "english": "Английский",
        "russian": "Русский",
        "ukrainian": "Украинский",
        "select_language": "Выберите язык",
        "warning": "Внимание",
        "done": "Готово",
        "cancel": "Отмена",
        "android_files": "Файлы на Android",
        "local_browser": "Локальные файлы",
        "ftp_refresh": "Обновить",
        "ftp_download": "Скачать",
        "ftp_upload": "Загрузить",
        "ftp_current_dir": "Путь:",
        "ftp_select_file": "Выберите файл для скачивания",
        "ftp_select_local": "Выберите локальный файл для загрузки",
        "select_folder": "Выберите папку",
        "progress": "Прогресс",
        "current_operation": "Текущая:",
        "parent_dir": "Назад",
        "browse_local": "Обзор",
        "downloading": "Скачивание",
        "uploading": "Загрузка",
        "delete": "Удалить",
        "delete_confirm": "Удалить это?",
        "delete_success": "Успешно удалено",
        "delete_error": "Ошибка удаления",
    },
    LANGUAGE_UK: {
        "app_title": "OfflineSync - Ubuntu ↔ Android",
        "folder": "Папка:",
        "browse": "Вибрати",
        "devices": "Пристрої",
        "refresh": "Оновити",
        "not_connected": "Не підключено",
        "connected": "Підключено",
        "download": "Скачати ↓",
        "upload": "Завантажити ↑",
        "stop": "Стоп",
        "log": "Лог",
        "file_exists": "Файл існує. Перезаписати?",
        "yes": "Так",
        "no": "Ні",
        "cancel_all": "Скасувати все",
        "searching_devices": "Пошук пристроїв...",
        "select_device": "Виберіть пристрій!",
        "device_selected": "Вибрано",
        "device_unavailable": "Пристрій недоступний",
        "items_on_android": "Елементів на Android",
        "created_folder": "Створено папку",
        "local_files": "Локальних файлів",
        "downloaded": "Скачано",
        "uploaded": "Завантажено",
        "error": "Помилка",
        "getting_apps_list": "Отримання списку додатків...",
        "installed_apps": "Встановлені додатки",
        "saved_apps": "Збережено додатків",
        "error_getting_apps": "Помилка отримання додатків",
        "file_exists": "Файл існує",
        "settings": "Налаштування",
        "language": "Мова",
        "help": "Допомога",
        "restart_required": "Потрібен перезапуск для зміни мови",
        "restart_now": "Перезапустити",
        "later": "Пізніше",
        "help_title": "Допомога",
        "help_content": "OfflineSync дозволяє синхронізувати файли між Android та комп'ютером без інтернету.\n\n1. Підключіть обидва пристрої до однієї WiFi мережі\n2. Переглядайте та передавайте файли\n\nФайли зберігаються в папці OfflineSync у домашньому каталозі.",
        "english": "Англійська",
        "russian": "Російська",
        "ukrainian": "Українська",
        "select_language": "Виберіть мову",
        "warning": "Попередження",
        "done": "Готово",
        "cancel": "Скасувати",
        "android_files": "Файли на Android",
        "local_browser": "Локальні файли",
        "ftp_refresh": "Оновити",
        "ftp_download": "Скачати",
        "ftp_upload": "Завантажити",
        "ftp_current_dir": "Шлях:",
        "ftp_select_file": "Оберіть файл для скачування",
        "ftp_select_local": "Оберіть локальний файл для завантаження",
        "select_folder": "Оберіть папку",
        "progress": "Прогрес",
        "current_operation": "Поточна:",
        "parent_dir": "Назад",
        "browse_local": "Огляд",
        "downloading": "Скачування",
        "uploading": "Завантаження",
        "delete": "Видалити",
        "delete_confirm": "Видалити це?",
        "delete_success": "Успішно видалено",
        "delete_error": "Помилка видалення",
    }
}


class LanguageManager:
    def __init__(self):
        self.config_file = os.path.expanduser("~/.offlinesync/config")
        self.language = self._load_language()
    
    def _load_language(self):
        try:
            os.makedirs(os.path.dirname(self.config_file), exist_ok=True)
            config = configparser.ConfigParser()
            if os.path.exists(self.config_file):
                config.read(self.config_file)
                if config.has_option('settings', 'language'):
                    return config.get('settings', 'language')
        except:
            pass
        return LANGUAGE_EN
    
    def set_language(self, lang):
        self.language = lang
        try:
            os.makedirs(os.path.dirname(self.config_file), exist_ok=True)
            config = configparser.ConfigParser()
            config.read(self.config_file)
            config['settings'] = {'language': lang}
            with open(self.config_file, 'w') as f:
                config.write(f)
        except:
            pass
    
    def get_current_language(self):
        return self.language
    
    def t(self, key):
        return TRANSLATIONS.get(self.language, TRANSLATIONS[LANGUAGE_EN]).get(key, key)


class WindowManager:
    def __init__(self):
        self.config_file = os.path.expanduser("~/.offlinesync/config")
    
    def save_window_position(self, x, y, width, height):
        try:
            os.makedirs(os.path.dirname(self.config_file), exist_ok=True)
            config = configparser.ConfigParser()
            config.read(self.config_file)
            config['window'] = {
                'x': str(int(x)),
                'y': str(int(y)),
                'width': str(int(width)),
                'height': str(int(height))
            }
            with open(self.config_file, 'w') as f:
                config.write(f)
        except Exception as e:
            print(f"Error saving window position: {e}")
    
    def get_window_geometry(self):
        try:
            if not os.path.exists(self.config_file):
                return "700x550"
            config = configparser.ConfigParser()
            config.read(self.config_file)
            if not config.has_section('window'):
                return "700x550"
            x = config.get('window', 'x', fallback='100')
            y = config.get('window', 'y', fallback='100')
            width = config.get('window', 'width', fallback='700')
            height = config.get('window', 'height', fallback='550')
            return f"{width}x{height}+{x}+{y}"
        except Exception as e:
            print(f"Error loading window position: {e}")
            return "700x550"


class OfflineSyncGUI:
    def __init__(self, root):
        self.root = root
        self.lang = LanguageManager()
        self.window_manager = WindowManager()
        geometry = self.window_manager.get_window_geometry()
        print(f"Loading window geometry: {geometry}")
        self.root.geometry(geometry)
        self.root.title(self.lang.t("app_title"))
        
        self.sync_folder = os.path.expanduser("~/OfflineSync")
        self.android_ip = ""
        self.android_port = 8080
        self.devices = []
        self.base_url = None
        self.pending_uploads = []
        
        self.http_connected = False
        self.http_current_dir = ""
        
        self.cancel_download = False
        self.cancel_upload = False
        self.is_downloading = False
        self.is_uploading = False
        
        self.local_current_dir = ""
        
        self.root.protocol("WM_DELETE_WINDOW", self.on_close)
        
        self.setup_ui()
        
        self.root.update()
        self.root.deiconify()
        
        self.refresh_devices()
    
    def on_close(self):
        try:
            x = self.root.winfo_x()
            y = self.root.winfo_y()
            width = self.root.winfo_width()
            height = self.root.winfo_height()
            print(f"Saving window position: {x}, {y}, {width}, {height}")
            self.window_manager.save_window_position(x, y, width, height)
        except:
            pass
        self.root.destroy()
    
    def setup_ui(self):
        style = ttk.Style()
        style.configure("Compact.TFrame", padding=2)
        
        menubar = tk.Menu(self.root)
        self.root.config(menu=menubar)
        
        settings_menu = tk.Menu(menubar, tearoff=0)
        menubar.add_cascade(label=self.lang.t("settings"), menu=settings_menu)
        settings_menu.add_command(label=self.lang.t("language"), command=self.show_language_dialog)
        settings_menu.add_command(label=self.lang.t("help"), command=self.show_help_dialog)
        
        main_frame = tk.Frame(self.root)
        main_frame.pack(fill=tk.BOTH, expand=True, padx=5, pady=2)
        
        title = tk.Label(main_frame, text="OfflineSync", font=("Helvetica", 14, "bold"))
        title.pack(pady=2)
        
        folder_frame = tk.Frame(main_frame)
        folder_frame.pack(fill=tk.X, pady=2)
        tk.Label(folder_frame, text=self.lang.t("folder")).pack(side=tk.LEFT)
        self.folder_entry = tk.Entry(folder_frame, width=35)
        self.folder_entry.insert(0, self.sync_folder)
        self.folder_entry.pack(side=tk.LEFT, padx=3)
        tk.Button(folder_frame, text=self.lang.t("browse"), command=self.browse_folder).pack(side=tk.LEFT)
        
        device_frame = tk.LabelFrame(main_frame, text=self.lang.t("devices"), padx=5, pady=3)
        device_frame.pack(fill=tk.X, pady=2)
        
        ip_frame = tk.Frame(device_frame)
        ip_frame.pack(fill=tk.X, pady=2)
        tk.Label(ip_frame, text="IP:").pack(side=tk.LEFT)
        self.ip_entry = tk.Entry(ip_frame, width=15)
        self.ip_entry.insert(0, "192.168.1.")
        self.ip_entry.pack(side=tk.LEFT, padx=3)
        tk.Button(ip_frame, text="Connect", command=self.connect_to_ip).pack(side=tk.LEFT, padx=3)
        
        self.device_listbox = tk.Listbox(device_frame, height=3)
        self.device_listbox.pack(fill=tk.X)
        self.device_listbox.bind('<<ListboxSelect>>', self.on_device_select)
        
        tk.Button(device_frame, text=self.lang.t("refresh"), command=self.refresh_devices).pack(pady=2)
        
        self.status_label = tk.Label(main_frame, text=self.lang.t("not_connected"), fg="black", font=("Helvetica", 9))
        self.status_label.pack()
        
        btn_frame = tk.Frame(main_frame)
        btn_frame.pack(pady=2)
        
        self.download_btn = tk.Button(btn_frame, text=self.lang.t("download"), 
                                       command=self.download_selected, state=tk.DISABLED)
        self.download_btn.pack(side=tk.LEFT, padx=2)
        
        self.upload_btn = tk.Button(btn_frame, text=self.lang.t("upload"), 
                                    command=self.upload_selected, state=tk.DISABLED)
        self.upload_btn.pack(side=tk.LEFT, padx=2)
        
        self.stop_btn = tk.Button(btn_frame, text=self.lang.t("stop"), 
                                  command=self.stop_operation, state=tk.DISABLED)
        self.stop_btn.pack(side=tk.LEFT, padx=2)
        
        pans = tk.PanedWindow(main_frame, orient=tk.HORIZONTAL)
        pans.pack(fill=tk.BOTH, expand=True, pady=2)
        
        android_frame = tk.LabelFrame(pans, text=self.lang.t("android_files"), padx=5, pady=3)
        pans.add(android_frame, width=300)
        
        android_top = tk.Frame(android_frame)
        android_top.pack(fill=tk.X)
        
        tk.Label(android_top, text=self.lang.t("ftp_current_dir")).pack(side=tk.LEFT)
        self.ftp_dir_label = tk.Label(android_top, text="/", fg="black", font=("Helvetica", 9))
        self.ftp_dir_label.pack(side=tk.LEFT, padx=3)
        
        android_btn_row = tk.Frame(android_frame)
        android_btn_row.pack(fill=tk.X, pady=2)
        
        tk.Button(android_btn_row, text=self.lang.t("parent_dir"), 
                  command=self.http_cdup).pack(side=tk.LEFT, padx=1)
        tk.Button(android_btn_row, text=self.lang.t("ftp_refresh"), 
                  command=self.http_refresh).pack(side=tk.LEFT, padx=1)
        tk.Button(android_btn_row, text=self.lang.t("delete"), 
                  command=self.http_delete_selected).pack(side=tk.LEFT, padx=1)
        
        android_file_frame = tk.Frame(android_frame)
        android_file_frame.pack(fill=tk.BOTH, expand=True, pady=2)
        
        self.http_file_listbox = tk.Listbox(android_file_frame)
        self.http_file_listbox.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        self.http_file_listbox.bind('<Double-Button-1>', self.http_on_double_click)
        
        android_scroll = tk.Scrollbar(android_file_frame, orient=tk.VERTICAL)
        android_scroll.pack(side=tk.RIGHT, fill=tk.Y)
        self.http_file_listbox.config(yscrollcommand=android_scroll.set)
        android_scroll.config(command=self.http_file_listbox.yview)
        
        local_frame = tk.LabelFrame(pans, text=self.lang.t("local_browser"), padx=5, pady=3)
        pans.add(local_frame)
        
        local_top = tk.Frame(local_frame)
        local_top.pack(fill=tk.X)
        
        tk.Label(local_top, text=self.lang.t("ftp_current_dir")).pack(side=tk.LEFT)
        self.local_dir_label = tk.Label(local_top, text="/", fg="black", font=("Helvetica", 9))
        self.local_dir_label.pack(side=tk.LEFT, padx=3)
        
        local_btn_row = tk.Frame(local_frame)
        local_btn_row.pack(fill=tk.X, pady=2)
        
        tk.Button(local_btn_row, text=self.lang.t("parent_dir"), 
                  command=self.local_cdup).pack(side=tk.LEFT, padx=1)
        tk.Button(local_btn_row, text=self.lang.t("browse_local"), 
                  command=self.local_refresh).pack(side=tk.LEFT, padx=1)
        tk.Button(local_btn_row, text=self.lang.t("delete"), 
                  command=self.local_delete_selected).pack(side=tk.LEFT, padx=1)
        
        local_file_frame = tk.Frame(local_frame)
        local_file_frame.pack(fill=tk.BOTH, expand=True, pady=2)
        
        self.local_file_listbox = tk.Listbox(local_file_frame)
        self.local_file_listbox.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        self.local_file_listbox.bind('<Double-Button-1>', self.local_on_double_click)
        
        local_scroll = tk.Scrollbar(local_file_frame, orient=tk.VERTICAL)
        local_scroll.pack(side=tk.RIGHT, fill=tk.Y)
        self.local_file_listbox.config(yscrollcommand=local_scroll.set)
        local_scroll.config(command=self.local_file_listbox.yview)
        
        log_frame = tk.LabelFrame(main_frame, text=self.lang.t("log"), padx=5, pady=3)
        log_frame.pack(fill=tk.BOTH, expand=True, pady=2)
        
        self.log_text = tk.Text(log_frame, height=4)
        self.log_text.pack(fill=tk.BOTH, expand=True)
        
        progress_frame = tk.Frame(main_frame)
        progress_frame.pack(fill=tk.X, pady=2)
        
        self.progress_label = tk.Label(progress_frame, text="", font=("Helvetica", 8))
        self.progress_label.pack(side=tk.LEFT)
        
        self.progress = ttk.Progressbar(progress_frame, mode='determinate')
        self.progress.pack(fill=tk.X, pady=1)
        
        self.operation_label = tk.Label(progress_frame, text="", font=("Helvetica", 8), fg="black")
        self.operation_label.pack(side=tk.LEFT)

    def update_progress(self, current, total, filename):
        percent = int((current / total) * 100) if total > 0 else 0
        self.progress['value'] = percent
        self.progress_label.config(text=f"{percent}%")
        self.operation_label.config(text=f"{self.lang.t('current_operation')} {filename}")
        self.root.update()

    def show_language_dialog(self):
        dialog = tk.Toplevel(self.root)
        dialog.title(self.lang.t("select_language"))
        dialog.geometry("250x180")
        dialog.transient(self.root)
        dialog.grab_set()
        
        t = self.lang.t
        langs = [
            (LANGUAGE_EN, t("english")),
            (LANGUAGE_RU, t("russian")),
            (LANGUAGE_UK, t("ukrainian"))
        ]
        
        var = tk.StringVar(value=self.lang.get_current_language())
        
        for code, name in langs:
            rb = tk.Radiobutton(dialog, text=name, variable=var, value=code)
            rb.pack(anchor=tk.W, padx=15, pady=3)
        
        def apply():
            self.lang.set_language(var.get())
            dialog.destroy()
            if messagebox.askyesno("", self.lang.t("restart_required")):
                python = sys.executable
                os.execl(python, python, *sys.argv)
        
        btn_frame = tk.Frame(dialog)
        btn_frame.pack(pady=10)
        tk.Button(btn_frame, text="OK", command=apply).pack(side=tk.LEFT, padx=3)
        tk.Button(btn_frame, text=t("cancel"), command=dialog.destroy).pack(side=tk.LEFT, padx=3)

    def show_help_dialog(self):
        dialog = tk.Toplevel(self.root)
        dialog.title(self.lang.t("help_title"))
        dialog.geometry("350x250")
        dialog.transient(self.root)
        
        text = tk.Text(dialog, wrap=tk.WORD, padx=8, pady=8)
        text.pack(fill=tk.BOTH, expand=True)
        text.insert(tk.END, self.lang.t("help_content"))
        text.config(state=tk.DISABLED)
        
        tk.Button(dialog, text="OK", command=dialog.destroy).pack(pady=5)
    
    def log(self, msg):
        self.log_text.insert(tk.END, msg + "\n")
        self.log_text.see(tk.END)
        self.root.update()
    
    def browse_folder(self):
        folder = filedialog.askdirectory(initialdir=self.sync_folder)
        if folder:
            self.sync_folder = folder
            self.folder_entry.delete(0, tk.END)
            self.folder_entry.insert(0, folder)
            self.local_current_dir = folder
            self.local_dir_label.config(text=folder)
            self.local_refresh()
    
    def refresh_devices(self):
        threading.Thread(target=self._refresh_devices, daemon=True).start()
    
    def _refresh_devices(self):
        self.log(self.lang.t("searching_devices"))
        self.devices = []
        zc = zeroconf.Zeroconf()
        try:
            browser = zeroconf.ServiceBrowser(zc, "_offlinesync._tcp.local.", ZeroconfListener(self))
            time.sleep(3)
        finally:
            zc.close()
    
    def on_device_select(self, event):
        selection = self.device_listbox.curselection()
        if selection and selection[0] < len(self.devices):
            device = self.devices[selection[0]]
            self.android_ip = device['ip']
            self.base_url = f"http://{self.android_ip}:{self.android_port}"
            self.status_label.config(text=f"{self.lang.t('connected')}: {self.android_ip}", fg="black")
            self.log(f"{self.lang.t('device_selected')}: {self.android_ip}:{self.android_port}")
            self.http_connected = True
            self.http_current_dir = ""
            self.download_btn.config(state=tk.NORMAL)
            self.upload_btn.config(state=tk.NORMAL)
            self.local_current_dir = self.sync_folder
            self.local_dir_label.config(text=self.local_current_dir)
            self.http_refresh()
            self.local_refresh()
    
    def connect_to_ip(self):
        ip = self.ip_entry.get().strip()
        if not ip:
            return
        
        self.android_ip = ip
        self.base_url = f"http://{self.android_ip}:{self.android_port}"
        
        try:
            r = requests.get(f"{self.base_url}/ping", timeout=3)
            if r.status_code == 200:
                self.status_label.config(text=f"{self.lang.t('connected')}: {self.android_ip}", fg="black")
                self.log(f"{self.lang.t('device_selected')}: {self.android_ip}:{self.android_port}")
                self.http_connected = True
                self.http_current_dir = ""
                self.download_btn.config(state=tk.NORMAL)
                self.upload_btn.config(state=tk.NORMAL)
                self.local_current_dir = self.sync_folder
                self.local_dir_label.config(text=self.local_current_dir)
                self.http_refresh()
                self.local_refresh()
            else:
                self.log(f"{self.lang.t('device_unavailable')}")
        except Exception as e:
            self.log(f"{self.lang.t('error')}: {e}")
            
            self.local_current_dir = self.sync_folder
            self.local_dir_label.config(text=self.local_current_dir)
            
            self.http_refresh()
            self.local_refresh()
    
    def download_selected(self):
        if not self.http_connected or not self.base_url:
            return
        
        selection = self.http_file_listbox.curselection()
        if not selection:
            messagebox.showwarning(self.lang.t("warning"), self.lang.t("ftp_select_file"))
            return
        
        item = self.http_file_listbox.get(selection[0])
        
        folder = self.folder_entry.get()
        if not os.path.isdir(folder):
            os.makedirs(folder, exist_ok=True)
        
        if item.startswith("[DIR]"):
            name = item.replace("[DIR]", "").strip()
            threading.Thread(target=self._http_download_dir, 
                            args=(name, folder), daemon=True).start()
        else:
            filename = item.replace("[FILE]", "").strip()
            threading.Thread(target=self._http_download_file, 
                            args=(filename, folder), daemon=True).start()
    
    def upload_selected(self):
        if not self.http_connected or not self.base_url:
            return
        
        selection = self.local_file_listbox.curselection()
        if not selection:
            messagebox.showwarning(self.lang.t("warning"), self.lang.t("ftp_select_local"))
            return
        
        item = self.local_file_listbox.get(selection[0])
        
        if item.startswith("[DIR]"):
            return
        
        filename = item.replace("[FILE]", "").strip()
        local_path = os.path.join(self.local_current_dir, filename)
        
        threading.Thread(target=self._http_upload_file, 
                        args=(local_path,), daemon=True).start()
    
    def stop_operation(self):
        self.cancel_download = True
        self.cancel_upload = True
        self.log("Stopping operation...")
    
    def set_buttons_for_operation(self, is_running):
        if is_running:
            self.download_btn.config(state=tk.DISABLED)
            self.upload_btn.config(state=tk.DISABLED)
            self.stop_btn.config(state=tk.NORMAL)
        else:
            self.download_btn.config(state=tk.NORMAL if self.http_connected else tk.DISABLED)
            self.upload_btn.config(state=tk.NORMAL if self.http_connected else tk.DISABLED)
            self.stop_btn.config(state=tk.DISABLED)
        self.is_downloading = is_running
        self.is_uploading = is_running
    
    def http_refresh(self):
        if not self.http_connected or not self.base_url:
            return
        
        threading.Thread(target=self._http_refresh, daemon=True).start()
    
    def _http_refresh(self):
        try:
            path_param = f"?path={quote(self.http_current_dir)}" if self.http_current_dir else ""
            url = f"{self.base_url}/browse{path_param}"
            self.root.after(0, lambda: self.log(f"Loading: {url}"))
            
            r = requests.get(url, timeout=10)
            if r.status_code == 200:
                data = r.json()
                items = data.get('items', [])
                
                sorted_items = sorted(items, key=lambda x: (not x.get('isDirectory', False), x.get('name', '').lower()))
                
                self.root.after(0, lambda: self._update_http_list(sorted_items))
        except Exception as e:
            self.root.after(0, lambda: self.log(f"Error: {e}"))
    
    def _update_http_list(self, items):
        self.http_file_listbox.delete(0, tk.END)
        for item in items:
            name = item.get('name', '')
            is_dir = item.get('isDirectory', False)
            prefix = "[DIR]" if is_dir else "[FILE]"
            self.http_file_listbox.insert(tk.END, f"{prefix} {name}")
    
    def http_cdup(self):
        if not self.http_connected:
            return
        
        if self.http_current_dir:
            if "/" in self.http_current_dir:
                self.http_current_dir = self.http_current_dir.rsplit("/", 1)[0]
            else:
                self.http_current_dir = ""
            self.ftp_dir_label.config(text="/" + self.http_current_dir)
            self.http_refresh()
    
    def http_on_double_click(self, event):
        if not self.http_connected:
            return
        
        selection = self.http_file_listbox.curselection()
        if selection:
            item = self.http_file_listbox.get(selection[0])
            if item.startswith("[DIR]"):
                name = item.replace("[DIR]", "").strip()
                if self.http_current_dir:
                    self.http_current_dir = f"{self.http_current_dir}/{name}"
                else:
                    self.http_current_dir = name
                self.ftp_dir_label.config(text="/" + self.http_current_dir)
                self.http_refresh()
    
    def _http_download_file(self, filename, folder):
        self.root.after(0, lambda: self.set_buttons_for_operation(True))
        self.cancel_download = False
        
        try:
            local_path = os.path.join(folder, filename)
            self.log(f"{self.lang.t('downloading')} {filename}...")
            
            url = f"{self.base_url}/download?path={quote(self.http_current_dir + '/' + filename) if self.http_current_dir else quote(filename)}"
            r = requests.get(url, timeout=300, stream=True)
            
            if r.status_code == 200:
                total_size = int(r.headers.get('content-length', 0))
                
                with open(local_path, 'wb') as f:
                    downloaded = 0
                    for chunk in r.iter_content(chunk_size=8192):
                        if self.cancel_download:
                            self.log("Download cancelled")
                            break
                        f.write(chunk)
                        downloaded += len(chunk)
                        if total_size > 0:
                            percent = int((downloaded / total_size) * 100)
                            self.root.after(0, lambda p=percent, n=filename: self.update_progress(p, 100, n))
                
                if not self.cancel_download:
                    self.root.after(0, lambda: self.log(f"Downloaded {filename}"))
                    self.root.after(0, lambda: self.local_refresh())
        except Exception as e:
            self.root.after(0, lambda: self.log(f"Error: {e}"))
        finally:
            self.root.after(0, lambda: self.set_buttons_for_operation(False))
            self.root.after(0, lambda: self.stop_progress())
    
    def _http_download_dir(self, dirname, folder):
        self.root.after(0, lambda: self.set_buttons_for_operation(True))
        self.cancel_download = False
        
        try:
            self.log(f"Downloading folder {dirname}...")
            path = f"{self.http_current_dir}/{dirname}" if self.http_current_dir else dirname
            url = f"{self.base_url}/browse?path={quote(path)}"
            
            r = requests.get(url, timeout=30)
            if r.status_code == 200:
                data = r.json()
                items = data.get('items', [])
                
                local_path = Path(folder) / dirname
                local_path.mkdir(parents=True, exist_ok=True)
                
                total = len(items)
                for idx, item in enumerate(items):
                    if self.cancel_download:
                        break
                    
                    name = item.get('name', '')
                    is_dir = item.get('isDirectory', False)
                    self.root.after(0, lambda i=idx, t=total, n=name: self.update_progress(i+1, t, n))
                    
                    if is_dir:
                        (local_path / name).mkdir(parents=True, exist_ok=True)
                    else:
                        file_url = f"{self.base_url}/download?path={quote(path)}/{quote(name)}"
                        try:
                            rr = requests.get(file_url, timeout=60, stream=True)
                            if rr.status_code == 200:
                                with open(local_path / name, 'wb') as f:
                                    for chunk in rr.iter_content(chunk_size=8192):
                                        f.write(chunk)
                        except:
                            pass
                
                if not self.cancel_download:
                    self.root.after(0, lambda: self.log(f"Downloaded folder {dirname}"))
                    self.root.after(0, lambda: self.local_refresh())
        except Exception as e:
            self.root.after(0, lambda: self.log(f"Error: {e}"))
        finally:
            self.root.after(0, lambda: self.set_buttons_for_operation(False))
            self.root.after(0, lambda: self.stop_progress())
    
    def _http_upload_file(self, filepath):
        self.root.after(0, lambda: self.set_buttons_for_operation(True))
        self.cancel_upload = False
        
        try:
            filename = os.path.basename(filepath)
            self.log(f"{self.lang.t('uploading')} {filename}...")
            
            with open(filepath, 'rb') as f:
                files = {'file': (filename, f)}
                r = requests.post(f"{self.base_url}/upload", files=files, timeout=300)
                
                if r.status_code == 200:
                    self.root.after(0, self.http_refresh)
                    self.root.after(0, lambda: self.log(f"Uploaded {filename}"))
                else:
                    self.root.after(0, lambda: self.log(f"Upload failed: {r.status_code}"))
        except Exception as e:
            self.root.after(0, lambda: self.log(f"Error: {e}"))
        finally:
            self.root.after(0, lambda: self.set_buttons_for_operation(False))
            self.root.after(0, lambda: self.stop_progress())
    
    def local_refresh(self):
        if not self.local_current_dir:
            self.local_current_dir = self.sync_folder
        
        threading.Thread(target=self._local_refresh, daemon=True).start()
    
    def _local_refresh(self):
        try:
            dir_path = Path(self.local_current_dir) if self.local_current_dir else Path(self.sync_folder)
            if not dir_path.exists():
                dir_path = Path(self.sync_folder)
            
            items = []
            for item in dir_path.iterdir():
                prefix = "[DIR]" if item.is_dir() else "[FILE]"
                items.append(f"{prefix} {item.name}")
            
            sorted_items = sorted(items, key=lambda x: (not x.startswith("[DIR]"), x.lower()))
            
            self.root.after(0, lambda: self._update_local_list(sorted_items))
            self.root.after(0, lambda: self.local_dir_label.config(text=str(dir_path)))
        except Exception as e:
            self.root.after(0, lambda: self.log(f"Error: {e}"))
    
    def _update_local_list(self, items):
        self.local_file_listbox.delete(0, tk.END)
        for item in items:
            self.local_file_listbox.insert(tk.END, item)
    
    def local_cdup(self):
        current = Path(self.local_current_dir) if self.local_current_dir else Path(self.sync_folder)
        parent = current.parent
        if parent != current:
            self.local_current_dir = str(parent)
            self.local_dir_label.config(text=self.local_current_dir)
            self.local_refresh()
    
    def local_on_double_click(self, event):
        selection = self.local_file_listbox.curselection()
        if selection:
            item = self.local_file_listbox.get(selection[0])
            if item.startswith("[DIR]"):
                name = item.replace("[DIR]", "").strip()
                self.local_current_dir = str(Path(self.local_current_dir) / name)
                self.local_dir_label.config(text=self.local_current_dir)
                self.local_refresh()
    
    def http_delete_selected(self):
        if not self.http_connected or not self.base_url:
            return
        
        selection = self.http_file_listbox.curselection()
        if not selection:
            return
        
        item = self.http_file_listbox.get(selection[0])
        name = item.replace("[DIR]", "").replace("[FILE]", "").strip()
        
        if not messagebox.askyesno(self.lang.t("warning"), f"{self.lang.t('delete_confirm')} {name}"):
            return
        
        threading.Thread(target=self._http_delete, args=(name, item.startswith("[DIR]")), daemon=True).start()
    
    def _http_delete(self, name, is_dir):
        try:
            path = f"{self.http_current_dir}/{name}" if self.http_current_dir else name
            url = f"{self.base_url}/delete?path={quote(path)}"
            
            r = requests.delete(url, timeout=30)
            if r.status_code == 200:
                self.root.after(0, lambda: self.log(f"{self.lang.t('delete_success')}: {name}"))
                self.root.after(0, self.http_refresh)
            else:
                self.root.after(0, lambda: self.log(f"{self.lang.t('delete_error')}: {r.text}"))
        except Exception as e:
            self.root.after(0, lambda: self.log(f"{self.lang.t('delete_error')}: {e}"))
    
    def local_delete_selected(self):
        selection = self.local_file_listbox.curselection()
        if not selection:
            return
        
        item = self.local_file_listbox.get(selection[0])
        name = item.replace("[DIR]", "").replace("[FILE]", "").strip()
        
        if not messagebox.askyesno(self.lang.t("warning"), f"{self.lang.t('delete_confirm')} {name}"):
            return
        
        try:
            path = Path(self.local_current_dir) / name
            if path.is_dir():
                import shutil
                shutil.rmtree(path)
            else:
                path.unlink()
            self.log(f"{self.lang.t('delete_success')}: {name}")
            self.local_refresh()
        except Exception as e:
            self.log(f"{self.lang.t('delete_error')}: {e}")
    
    def stop_progress(self):
        self.progress['value'] = 0
        self.progress_label.config(text="")
        self.operation_label.config(text="")
        self.cancel_download = False
        self.cancel_upload = False


class ZeroconfListener:
    def __init__(self, gui):
        self.gui = gui
    
    def add_service(self, zc, type_, name):
        info = zc.get_service_info(type_, name)
        if info:
            ip = '.'.join(str(b) for b in info.addresses[0]) if info.addresses else "unknown"
            self.gui.devices.append({'name': name, 'ip': ip, 'port': info.port})
            self.gui.root.after(0, lambda: self.gui.device_listbox.insert(tk.END, f"{ip}:{info.port}"))
            self.gui.log(f"Found: {ip}:{info.port}")
    
    def remove_service(self, zc, type_, name):
        pass
    
    def update_service(self, zc, type_, name):
        pass


def main():
    root = tk.Tk()
    app = OfflineSyncGUI(root)
    root.mainloop()


if __name__ == '__main__':
    main()

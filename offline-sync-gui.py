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
        "sync": "Sync ⇄",
        "log": "Log",
        "file_exists": "File exists. Overwrite?",
        "yes": "Yes",
        "no": "No",
        "cancel_all": "Cancel All",
        "searching_devices": "Searching devices...",
        "select_device": "Select a device!",
        "device_selected": "Selected",
        "sync_started": "=== Sync Started ===",
        "device_unavailable": "Device unavailable",
        "items_on_android": "Items on Android",
        "created_folder": "Created folder",
        "local_files": "Local files",
        "download": "Download",
        "upload": "Upload",
        "downloaded": "Downloaded",
        "uploaded": "Uploaded",
        "error": "Error",
        "getting_apps_list": "Getting apps list...",
        "installed_apps": "Installed apps",
        "saved_apps": "Saved apps",
        "error_getting_apps": "Error getting apps",
        "file_exists": "File exists",
        "sync_complete": "=== Sync Complete ===",
        "settings": "Settings",
        "language": "Language",
        "help": "Help",
        "restart_required": "Restart required to apply language change",
        "restart_now": "Restart Now",
        "later": "Later",
        "help_title": "Help",
        "help_content": "OfflineSync allows you to sync files between your Android device and computer without internet.\n\n1. Connect both devices to the same WiFi network\n2. Start the server on Android\n3. Run sync on your computer\n\nFiles are stored in the OfflineSync folder in your home directory.",
        "english": "English",
        "russian": "Russian",
        "ukrainian": "Ukrainian",
        "select_language": "Select Language",
        "warning": "Warning",
        "done": "Done",
        "cancel": "Cancel",
    },
    LANGUAGE_RU: {
        "app_title": "OfflineSync - Ubuntu ↔ Android",
        "folder": "Папка:",
        "browse": "Выбрать",
        "devices": "Устройства",
        "refresh": "Обновить",
        "not_connected": "Не подключено",
        "connected": "Подключено",
        "sync": "Синхронизировать ⇄",
        "log": "Лог",
        "file_exists": "Файл существует. Перезаписать?",
        "yes": "Да",
        "no": "Нет",
        "cancel_all": "Отменить все",
        "searching_devices": "Поиск устройств...",
        "select_device": "Выберите устройство!",
        "device_selected": "Выбрано",
        "sync_started": "=== Синхронизация ===",
        "device_unavailable": "Устройство недоступно",
        "items_on_android": "Элементов на Android",
        "created_folder": "Создана папка",
        "local_files": "Файлов локально",
        "download": "Скачать",
        "upload": "Загрузить",
        "downloaded": "Скачано",
        "uploaded": "Загружено",
        "error": "Ошибка",
        "getting_apps_list": "Получение списка приложений...",
        "installed_apps": "Список установленных приложений",
        "saved_apps": "Сохранено приложений",
        "error_getting_apps": "Ошибка получения приложений",
        "file_exists": "Файл существует",
        "sync_complete": "=== Синхронизация завершена ===",
        "settings": "Настройки",
        "language": "Язык",
        "help": "Справка",
        "restart_required": "Требуется перезапуск для смены языка",
        "restart_now": "Перезапустить",
        "later": "Позже",
        "help_title": "Справка",
        "help_content": "OfflineSync позволяет синхронизировать файлы между Android и компьютером без интернета.\n\n1. Подключите оба устройства к одной WiFi сети\n2. Запустите сервер на Android\n3. Запустите синхронизацию на компьютере\n\nФайлы сохраняются в папке OfflineSync в домашнем каталоге.",
        "english": "Английский",
        "russian": "Русский",
        "ukrainian": "Украинский",
        "select_language": "Выберите язык",
        "warning": "Внимание",
        "done": "Готово",
        "cancel": "Отмена",
    },
    LANGUAGE_UK: {
        "app_title": "OfflineSync - Ubuntu ↔ Android",
        "folder": "Папка:",
        "browse": "Вибрати",
        "devices": "Пристрої",
        "refresh": "Оновити",
        "not_connected": "Не підключено",
        "connected": "Підключено",
        "sync": "Синхронізувати ⇄",
        "log": "Лог",
        "file_exists": "Файл існує. Перезаписати?",
        "yes": "Так",
        "no": "Ні",
        "cancel_all": "Скасувати все",
        "searching_devices": "Пошук пристроїв...",
        "select_device": "Виберіть пристрій!",
        "device_selected": "Вибрано",
        "sync_started": "=== Синхронізація ===",
        "device_unavailable": "Пристрій недоступний",
        "items_on_android": "Елементів на Android",
        "created_folder": "Створено папку",
        "local_files": "Локальних файлів",
        "download": "Скачати",
        "upload": "Завантажити",
        "downloaded": "Скачано",
        "uploaded": "Завантажено",
        "error": "Помилка",
        "getting_apps_list": "Отримання списку додатків...",
        "installed_apps": "Встановлені додатки",
        "saved_apps": "Збережено додатків",
        "error_getting_apps": "Помилка отримання додатків",
        "file_exists": "Файл існує",
        "sync_complete": "=== Синхронізація завершена ===",
        "settings": "Налаштування",
        "language": "Мова",
        "help": "Допомога",
        "restart_required": "Потрібен перезапуск для зміни мови",
        "restart_now": "Перезапустити",
        "later": "Пізніше",
        "help_title": "Допомога",
        "help_content": "OfflineSync дозволяє синхронізувати файли між Android та комп'ютером без інтернету.\n\n1. Підключіть обидва пристрої до однієї WiFi мережі\n2. Запустіть сервер на Android\n3. Запустіть синхронізацію на комп'ютері\n\nФайли зберігаються в папці OfflineSync у домашньому каталозі.",
        "english": "Англійська",
        "russian": "Російська",
        "ukrainian": "Українська",
        "select_language": "Виберіть мову",
        "warning": "Попередження",
        "done": "Готово",
        "cancel": "Скасувати",
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
                return "700x600"
            config = configparser.ConfigParser()
            config.read(self.config_file)
            if not config.has_section('window'):
                return "700x600"
            x = config.get('window', 'x', fallback='100')
            y = config.get('window', 'y', fallback='100')
            width = config.get('window', 'width', fallback='700')
            height = config.get('window', 'height', fallback='600')
            return f"{width}x{height}+{x}+{y}"
        except Exception as e:
            print(f"Error loading window position: {e}")
            return "700x600"


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
            self.window_manager.save_window_position(x, y, width, height)
        except:
            pass
        self.root.destroy()
    
    def setup_ui(self):
        menubar = tk.Menu(self.root)
        self.root.config(menu=menubar)
        
        settings_menu = tk.Menu(menubar, tearoff=0)
        menubar.add_cascade(label=self.lang.t("settings"), menu=settings_menu)
        settings_menu.add_command(label=self.lang.t("language"), command=self.show_language_dialog)
        settings_menu.add_command(label=self.lang.t("help"), command=self.show_help_dialog)
        
        title = tk.Label(self.root, text="OfflineSync", font=("Helvetica", 18, "bold"))
        title.pack(pady=10)
        
        folder_frame = tk.Frame(self.root)
        folder_frame.pack(fill=tk.X, padx=20, pady=5)
        tk.Label(folder_frame, text=self.lang.t("folder")).pack(side=tk.LEFT)
        self.folder_entry = tk.Entry(folder_frame, width=40)
        self.folder_entry.insert(0, self.sync_folder)
        self.folder_entry.pack(side=tk.LEFT, padx=5)
        tk.Button(folder_frame, text=self.lang.t("browse"), command=self.browse_folder).pack(side=tk.LEFT)
        
        device_frame = tk.LabelFrame(self.root, text=self.lang.t("devices"), padx=10, pady=10)
        device_frame.pack(fill=tk.X, padx=20, pady=10)
        
        self.device_listbox = tk.Listbox(device_frame, height=5)
        self.device_listbox.pack(fill=tk.X)
        self.device_listbox.bind('<<ListboxSelect>>', self.on_device_select)
        
        tk.Button(device_frame, text=self.lang.t("refresh"), command=self.refresh_devices).pack(pady=5)
        
        self.status_label = tk.Label(self.root, text=self.lang.t("not_connected"), fg="gray")
        self.status_label.pack()
        
        btn_frame = tk.Frame(self.root)
        btn_frame.pack(pady=10)
        tk.Button(btn_frame, text=self.lang.t("sync"), bg="#2196F3", fg="white", 
                 command=self.do_sync).pack(side=tk.LEFT, padx=5)
        
        log_frame = tk.LabelFrame(self.root, text=self.lang.t("log"), padx=10, pady=10)
        log_frame.pack(fill=tk.BOTH, expand=True, padx=20, pady=10)
        
        self.log_text = tk.Text(log_frame, height=12)
        self.log_text.pack(fill=tk.BOTH, expand=True)
        
        self.progress = ttk.Progressbar(self.root, mode='indeterminate')
        self.progress.pack(fill=tk.X, padx=20, pady=5)
        
        self.overwrite_frame = tk.Frame(self.root, relief=tk.RIDGE, borderwidth=2)
        tk.Label(self.overwrite_frame, text=self.lang.t("file_exists")).pack(pady=5)
        self.overwrite_file_label = tk.Label(self.overwrite_frame, text="", fg="red")
        self.overwrite_file_label.pack()
        btn_row = tk.Frame(self.overwrite_frame)
        btn_row.pack(pady=5)
        tk.Button(btn_row, text=self.lang.t("yes"), command=lambda: self.handle_overwrite(True)).pack(side=tk.LEFT, padx=5)
        tk.Button(btn_row, text=self.lang.t("no"), command=lambda: self.handle_overwrite(False)).pack(side=tk.LEFT, padx=5)
        tk.Button(btn_row, text=self.lang.t("cancel_all"), command=self.cancel_all_uploads).pack(side=tk.LEFT, padx=5)

    def show_language_dialog(self):
        dialog = tk.Toplevel(self.root)
        dialog.title(self.lang.t("select_language"))
        dialog.geometry("300x200")
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
            rb.pack(anchor=tk.W, padx=20, pady=5)
        
        def apply():
            self.lang.set_language(var.get())
            dialog.destroy()
            if messagebox.askyesno("", self.lang.t("restart_required")):
                python = sys.executable
                os.execl(python, python, *sys.argv)
        
        btn_frame = tk.Frame(dialog)
        btn_frame.pack(pady=20)
        tk.Button(btn_frame, text="OK", command=apply).pack(side=tk.LEFT, padx=5)
        tk.Button(btn_frame, text=t("cancel"), command=dialog.destroy).pack(side=tk.LEFT, padx=5)

    def show_help_dialog(self):
        dialog = tk.Toplevel(self.root)
        dialog.title(self.lang.t("help_title"))
        dialog.geometry("400x300")
        dialog.transient(self.root)
        
        text = tk.Text(dialog, wrap=tk.WORD, padx=10, pady=10)
        text.pack(fill=tk.BOTH, expand=True)
        text.insert(tk.END, self.lang.t("help_content"))
        text.config(state=tk.DISABLED)
        
        tk.Button(dialog, text="OK", command=dialog.destroy).pack(pady=10)
    
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
        if selection:
            device = self.devices[selection[0]]
            self.android_ip = device['ip']
            self.base_url = f"http://{self.android_ip}:{self.android_port}"
            self.status_label.config(text=f"{self.lang.t('connected')}: {self.android_ip}", fg="green")
            self.log(f"{self.lang.t('device_selected')}: {self.android_ip}:{self.android_port}")
    
    def do_sync(self):
        if not self.base_url:
            messagebox.showwarning("Warning", self.lang.t("select_device"))
            return
        
        folder = self.folder_entry.get()
        if not os.path.isdir(folder):
            os.makedirs(folder, exist_ok=True)
        
        self.progress.start()
        threading.Thread(target=self._do_sync, args=(folder,), daemon=True).start()
    
    def _do_sync(self, folder):
        try:
            self.log(self.lang.t("sync_started"))
            
            # Check connection
            r = requests.get(f"{self.base_url}/ping", timeout=5)
            if r.status_code != 200:
                self.log(self.lang.t("device_unavailable"))
                return
            
            # Get list of files/folders from Android
            r = requests.get(f"{self.base_url}/files", timeout=10)
            android_items = []
            if r.status_code == 200:
                try:
                    data = r.json()
                    android_items = data.get('items', [])
                except:
                    pass
            
            self.log(f"{self.lang.t('items_on_android')}: {len(android_items)}")
            
            # Download files and folders
            downloaded = 0
            for item in android_items:
                name = item.get('name', '')
                is_dir = item.get('isDirectory', False)
                
                if name:
                    if is_dir:
                        # Create directory locally
                        local_path = Path(folder) / name
                        local_path.mkdir(parents=True, exist_ok=True)
                        self.log(f"{self.lang.t('created_folder')}: {name}")
                        downloaded += 1
                    else:
                        # Download file
                        if self.download_file(name, folder):
                            downloaded += 1
            
            # Upload files to Android
            uploaded = 0
            local_files = self.get_local_files(folder)
            self.log(f"{self.lang.t('local_files')}: {len(local_files)}")
            
            android_names = set(item.get('name', '') for item in android_items if not item.get('isDirectory', False))
            
            for local_file in local_files:
                rel_path = str(Path(local_file).relative_to(folder))
                
                if rel_path not in android_names:
                    if self.upload_file_with_check(local_file, folder):
                        uploaded += 1
                else:
                    # Check if sizes differ
                    try:
                        r = requests.get(f"{self.base_url}/exists?path={quote(rel_path)}", timeout=5)
                        if r.status_code == 200:
                            # Skip if same size (will be handled by download above)
                            pass
                    except:
                        pass
            
            # Save installed apps list
            self.log(self.lang.t("getting_apps_list"))
            try:
                r = requests.get(f"{self.base_url}/apps", timeout=30)
                if r.status_code == 200:
                    apps_data = r.json()
                    apps_list = apps_data.get('apps', [])
                    
                    apps_file = Path(folder) / "installed_apps.txt"
                    with open(apps_file, 'w', encoding='utf-8') as f:
                        f.write(self.lang.t("installed_apps") + "\n")
                        f.write("=" * 40 + "\n\n")
                        for app in sorted(apps_list, key=lambda x: x.get('name', '')):
                            f.write(f"{app.get('name', '')} ({app.get('package', '')})\n")
                    
                    self.log(f"{self.lang.t('saved_apps')}: {len(apps_list)}")
            except Exception as e:
                self.log(f"{self.lang.t('error_getting_apps')}: {e}")
            
            self.log(f"{self.lang.t('uploaded')}: {uploaded}, {self.lang.t('downloaded')}: {downloaded}")
            self.root.after(0, lambda: messagebox.showinfo(self.lang.t("done"), f"{self.lang.t('uploaded')}: {uploaded}\n{self.lang.t('downloaded')}: {downloaded}"))
        except Exception as e:
            self.log(f"{self.lang.t('error')}: {e}")
            import traceback
            self.log(traceback.format_exc())
        finally:
            self.root.after(0, self.stop_progress)
    
    def get_local_files(self, folder):
        """Recursively get all files"""
        files = []
        folder_path = Path(folder)
        for item in folder_path.rglob('*'):
            if item.is_file():
                files.append(str(item))
        return files
    
    def upload_file_with_check(self, filepath, base_folder):
        """Check if file exists and ask for overwrite"""
        rel_path = str(Path(filepath).relative_to(base_folder))
        
        try:
            # Check if file exists on Android
            r = requests.get(f"{self.base_url}/exists?path={quote(rel_path)}", timeout=5)
            if r.status_code == 200:
                data = r.json()
                if data.get('exists', False):
                    # File exists, ask user
                    self.log(f"{self.lang.t('file_exists')}: {rel_path}")
                    self.pending_uploads.append((filepath, base_folder, rel_path))
                    
                    # Show dialog
                    self.root.after(0, self.show_overwrite_dialog, rel_path)
                    return False
        except:
            pass
        
        return self.upload_file(filepath, base_folder)
    
    def show_overwrite_dialog(self, filename):
        self.overwrite_file_label.config(text=filename)
        self.overwrite_frame.pack(fill=tk.X, padx=20, pady=10)
    
    def handle_overwrite(self, overwrite):
        self.overwrite_frame.pack_forget()
        
        if self.pending_uploads:
            filepath, base_folder, rel_path = self.pending_uploads.pop(0)
            
            if overwrite:
                self.upload_file(filepath, base_folder)
            else:
                # Rename locally and upload
                base_path = Path(base_folder) / rel_path
                base_name = base_path.stem
                ext = base_path.suffix
                counter = 1
                while base_path.exists():
                    if ext:
                        new_name = f"{base_name}_{counter}{ext}"
                    else:
                        new_name = f"{base_name}_{counter}"
                    base_path = base_path.parent / new_name
                    counter += 1
                
                self.log(f"Переименован в: {base_path.name}")
                self.upload_file(str(base_path), base_folder)
            
            # Process remaining
            if self.pending_uploads:
                next_file = self.pending_uploads[0][2]
                self.root.after(0, self.show_overwrite_dialog, next_file)
    
    def cancel_all_uploads(self):
        self.overwrite_frame.pack_forget()
        self.pending_uploads = []
        self.log("Загрузка отменена")
    
    def upload_file(self, filepath, base_folder):
        """Upload file to Android"""
        try:
            rel_path = str(Path(filepath).relative_to(base_folder))
            with open(filepath, 'rb') as f:
                files = {'file': (rel_path, f)}
                r = requests.post(f"{self.base_url}/upload", files=files, timeout=300)
                if r.status_code == 200:
                    self.log(f"Загружено: {rel_path}")
                    return True
                else:
                    self.log(f"Ошибка загрузки: {r.status_code} - {r.text}")
        except Exception as e:
            self.log(f"Ошибка: {e}")
        return False
    
    def download_file(self, filename, base_folder):
        """Download file from Android"""
        try:
            url = f"{self.base_url}/download?path={quote(filename)}"
            self.log(f"Скачивание: {filename}")
            
            r = requests.get(url, timeout=300, stream=True)
            if r.status_code == 200:
                local_path = Path(base_folder) / filename
                local_path.parent.mkdir(parents=True, exist_ok=True)
                
                with open(local_path, 'wb') as f:
                    for chunk in r.iter_content(chunk_size=8192):
                        f.write(chunk)
                self.log(f"Скачано: {filename}")
                return True
            else:
                self.log(f"Ошибка: {r.status_code}")
        except Exception as e:
            self.log(f"Ошибка: {e}")
        return False
    
    def stop_progress(self):
        self.progress.stop()


class ZeroconfListener:
    def __init__(self, gui):
        self.gui = gui
    
    def add_service(self, zc, type_, name):
        info = zc.get_service_info(type_, name)
        if info:
            ip = '.'.join(str(b) for b in info.addresses[0]) if info.addresses else "unknown"
            self.gui.devices.append({'name': name, 'ip': ip, 'port': info.port})
            self.gui.root.after(0, lambda: self.gui.device_listbox.insert(tk.END, f"{ip}:{info.port}"))
            self.gui.log(f"Найдено: {ip}:{info.port}")


def main():
    root = tk.Tk()
    app = OfflineSyncGUI(root)
    root.mainloop()


if __name__ == '__main__':
    main()

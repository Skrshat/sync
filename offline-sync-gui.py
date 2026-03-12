#!/usr/bin/env python3
"""
OfflineSync GUI - Ubuntu Desktop Client
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


class OfflineSyncGUI:
    def __init__(self, root):
        self.root = root
        self.root.title("OfflineSync - Ubuntu ↔ Android")
        self.root.geometry("600x500")
        
        self.sync_folder = os.path.expanduser("~/OfflineSync")
        self.android_ip = ""
        self.android_port = 8080
        self.devices = []
        self.base_url = None
        
        self.setup_ui()
        self.refresh_devices()
    
    def setup_ui(self):
        # Заголовок
        title = tk.Label(self.root, text="OfflineSync", font=("Helvetica", 18, "bold"))
        title.pack(pady=10)
        
        # Папка синхронизации
        folder_frame = tk.Frame(self.root)
        folder_frame.pack(fill=tk.X, padx=20, pady=5)
        tk.Label(folder_frame, text="Папка:").pack(side=tk.LEFT)
        self.folder_entry = tk.Entry(folder_frame, width=40)
        self.folder_entry.insert(0, self.sync_folder)
        self.folder_entry.pack(side=tk.LEFT, padx=5)
        tk.Button(folder_frame, text="Выбрать", command=self.browse_folder).pack(side=tk.LEFT)
        
        # Устройства
        device_frame = tk.LabelFrame(self.root, text="Устройства", padx=10, pady=10)
        device_frame.pack(fill=tk.X, padx=20, pady=10)
        
        self.device_listbox = tk.Listbox(device_frame, height=5)
        self.device_listbox.pack(fill=tk.X)
        self.device_listbox.bind('<<ListboxSelect>>', self.on_device_select)
        
        tk.Button(device_frame, text="Обновить", command=self.refresh_devices).pack(pady=5)
        
        # Статус
        self.status_label = tk.Label(self.root, text="Не подключено", fg="gray")
        self.status_label.pack()
        
        # Кнопки
        btn_frame = tk.Frame(self.root)
        btn_frame.pack(pady=10)
        tk.Button(btn_frame, text="Синхронизировать", bg="#4CAF50", fg="white", command=self.do_sync).pack(side=tk.LEFT, padx=5)
        tk.Button(btn_frame, text="Загрузить ↑", command=self.do_upload).pack(side=tk.LEFT, padx=5)
        tk.Button(btn_frame, text="Скачать ↓", command=self.do_download).pack(side=tk.LEFT, padx=5)
        
        # Лог
        log_frame = tk.LabelFrame(self.root, text="Лог", padx=10, pady=10)
        log_frame.pack(fill=tk.BOTH, expand=True, padx=20, pady=10)
        
        self.log_text = tk.Text(log_frame, height=10)
        self.log_text.pack(fill=tk.BOTH, expand=True)
        
        # Прогресс
        self.progress = ttk.Progressbar(self.root, mode='indeterminate')
        self.progress.pack(fill=tk.X, padx=20, pady=5)
    
    def log(self, msg):
        self.log_text.insert(tk.END, msg + "\n")
        self.log_text.see(tk.END)
        print(msg)
    
    def browse_folder(self):
        folder = filedialog.askdirectory(initialdir=self.sync_folder)
        if folder:
            self.sync_folder = folder
            self.folder_entry.delete(0, tk.END)
            self.folder_entry.insert(0, folder)
    
    def refresh_devices(self):
        self.device_listbox.delete(0, tk.END)
        self.device_listbox.insert(0, "Поиск...")
        threading.Thread(target=self.discover_devices, daemon=True).start()
    
    def discover_devices(self):
        found = []
        
        class MyListener(zeroconf.ServiceListener):
            def add_service(self, zc, type_, name):
                info = zc.get_service_info(type_, name)
                if info:
                    addresses = info.parsed_addresses()
                    if addresses:
                        found.append({
                            'name': name.replace('._offlinesync._tcp.local.', ''),
                            'ip': addresses[0],
                            'port': info.port
                        })
            
            def remove_service(self, zc, type_, name): pass
            def update_service(self, zc, type_, name): pass
        
        try:
            self.log("Поиск устройств...")
            zc = zeroconf.Zeroconf()
            browser = zeroconf.ServiceBrowser(zc, "_offlinesync._tcp.local.", MyListener())
            time.sleep(5)
            zc.close()
        except Exception as e:
            self.log(f"Ошибка поиска: {e}")
        
        self.devices = found
        self.root.after(0, self.update_device_list)
    
    def update_device_list(self):
        self.device_listbox.delete(0, tk.END)
        if self.devices:
            for device in self.devices:
                self.device_listbox.insert(tk.END, f"{device['name']} - {device['ip']}:{device['port']}")
        else:
            self.device_listbox.insert(0, "Устройства не найдены")
    
    def on_device_select(self, event):
        selection = self.device_listbox.curselection()
        if selection:
            index = selection[0]
            if index < len(self.devices):
                device = self.devices[index]
                self.android_ip = device['ip']
                self.android_port = device['port']
                self.log(f"Выбрано: {self.android_ip}:{self.android_port}")
                self.test_connection()
    
    def test_connection(self):
        if not self.android_ip:
            return False
        
        self.base_url = f"http://{self.android_ip}:{self.android_port}"
        self.log(f"Пробуем: {self.base_url}")
        
        try:
            r = requests.get(f"{self.base_url}/ping", timeout=5)
            if r.status_code == 200:
                self.status_label.config(text=f"Подключено: {self.android_ip}:{self.android_port}", fg="green")
                self.log("Подключено!")
                return True
        except Exception as e:
            self.log(f"Ошибка: {e}")
            self.status_label.config(text="Не подключено", fg="red")
        return False
    
    def start_progress(self):
        self.progress.start(10)
    
    def stop_progress(self):
        self.progress.stop()
    
    def do_sync(self):
        if not self.android_ip:
            messagebox.showwarning("Ошибка", "Выберите устройство!")
            return
        
        self.log("=== Синхронизация ===")
        self.start_progress()
        thread = threading.Thread(target=self._do_sync)
        thread.daemon = True
        thread.start()
    
    def _do_sync(self):
        try:
            self.log("Подключение...")
            if not self.test_connection():
                self.root.after(0, lambda: messagebox.showerror("Ошибка", "Не подключено"))
                return
            
            folder = self.folder_entry.get() if hasattr(self, 'folder_entry') else self.sync_folder
            self.log(f"Папка: {folder}")
            sync_folder = Path(folder)
            
            if not sync_folder.exists():
                self.log("Создаем папку...")
                sync_folder.mkdir(parents=True)
            
            files = list(sync_folder.iterdir())
            self.root.after(0, lambda: self.log(f"Файлов локально: {len(files)}"))
            
            uploaded = 0
            for f in files:
                if f.is_file():
                    fname = f.name
                    self.root.after(0, lambda n=fname: self.log(f"Загрузка: {n}"))
                    if self.upload_file(f):
                        uploaded += 1
            
            self.root.after(0, lambda: self.log("Проверка Android..."))
            r = requests.get(f"{self.base_url}/files", timeout=10)
            downloaded = 0
            if r.status_code == 200:
                data = r.json()
                files_list = data.get('files', [])
                
                for f in files_list:
                    filename = f.get('name') if isinstance(f, dict) else f
                    filepath = sync_folder / filename
                    if not filepath.exists():
                        self.root.after(0, lambda n=filename: self.log(f"Скачивание: {n}"))
                        if self.download_file(filename):
                            downloaded += 1
            
            self.root.after(0, lambda: messagebox.showinfo("Готово", f"Загружено: {uploaded}\nСкачано: {downloaded}"))
        except Exception as e:
            self.log(f"Ошибка: {e}")
            import traceback
            self.log(traceback.format_exc())
        finally:
            self.root.after(0, self.stop_progress)
    
    def do_upload(self):
        if not self.android_ip:
            messagebox.showwarning("Ошибка", "Выберите устройство!")
            return
        
        self.log("=== Загрузка ===")
        self.start_progress()
        thread = threading.Thread(target=self._do_upload)
        thread.daemon = True
        thread.start()
    
    def _do_upload(self):
        try:
            if not self.test_connection():
                return
            
            folder = self.folder_entry.get() if hasattr(self, 'folder_entry') else self.sync_folder
            self.log(f"Папка: {folder}")
            sync_folder = Path(folder)
            
            if not sync_folder.exists():
                self.log("Создаем папку...")
                sync_folder.mkdir(parents=True)
            
            files = list(sync_folder.iterdir())
            self.log(f"Найдено файлов: {len(files)}")
            count = 0
            for f in files:
                if f.is_file():
                    fname = f.name
                    self.log(f"Загрузка: {fname}")
                    if self.upload_file(f):
                        count += 1
                        self.log(f"Успешно: {fname}")
                    else:
                        self.log(f"Ошибка: {fname}")
            
            self.root.after(0, lambda: messagebox.showinfo("Готово", f"Загружено: {count}"))
        except Exception as e:
            self.log(f"Ошибка: {e}")
            import traceback
            self.log(traceback.format_exc())
        finally:
            self.root.after(0, self.stop_progress)
    
    def upload_file(self, filepath):
        try:
            name = filepath.name if hasattr(filepath, 'name') else os.path.basename(filepath)
            with open(filepath, 'rb') as f:
                files = {'file': (name, f)}
                r = requests.post(f"{self.base_url}/upload", files=files, timeout=300)
                return r.status_code == 200
        except Exception as e:
            self.log(f"Ошибка загрузки: {e}")
            return False
    
    def do_download(self):
        if not self.android_ip:
            messagebox.showwarning("Ошибка", "Выберите устройство!")
            return
        
        self.log("=== Скачивание ===")
        self.start_progress()
        thread = threading.Thread(target=self._do_download)
        thread.daemon = True
        thread.start()
    
    def _do_download(self):
        try:
            if not self.test_connection():
                return
            
            folder = self.folder_entry.get() if hasattr(self, 'folder_entry') else self.sync_folder
            self.log(f"Папка: {folder}")
            sync_folder = Path(folder)
            
            if not sync_folder.exists():
                self.log("Создаем папку...")
                sync_folder.mkdir(parents=True)
            
            r = requests.get(f"{self.base_url}/files", timeout=10)
            count = 0
            if r.status_code == 200:
                files = r.json().get('files', [])
                self.root.after(0, lambda: self.log(f"Файлов на Android: {len(files)}"))
                
                for f in files:
                    filename = f.get('name') if isinstance(f, dict) else f
                    self.root.after(0, lambda n=filename: self.log(f"Скачивание: {n}"))
                    if self.download_file(filename):
                        count += 1
            
            self.root.after(0, lambda: messagebox.showinfo("Готово", f"Скачано: {count}"))
        except Exception as e:
            self.log(f"Ошибка: {e}")
            import traceback
            self.log(traceback.format_exc())
        finally:
            self.root.after(0, self.stop_progress)
    
    def download_file(self, filename):
        try:
            folder = self.folder_entry.get() if hasattr(self, 'folder_entry') else self.sync_folder
            filepath = Path(folder) / filename
            encoded_name = quote(filename)
            self.log(f"Downloading: {self.base_url}/download/{encoded_name}")
            r = requests.get(f"{self.base_url}/download/{encoded_name}", timeout=300, stream=True)
            self.log(f"Response: {r.status_code}")
            if r.status_code == 200:
                with open(filepath, 'wb') as f:
                    for chunk in r.iter_content(chunk_size=8192):
                        f.write(chunk)
                self.log(f"Downloaded: {filename} -> {filepath}")
                return True
            else:
                self.log(f"Failed: {r.text}")
        except Exception as e:
            self.log(f"Error: {e}")
        return False


def main():
    root = tk.Tk()
    app = OfflineSyncGUI(root)
    root.mainloop()


if __name__ == '__main__':
    main()

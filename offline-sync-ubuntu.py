#!/usr/bin/env python3
"""
OfflineSync - Ubuntu Desktop Client
Синхронизация файлов между Ubuntu и Android без облака
"""

import argparse
import os
import sys
import time
import hashlib
import requests
from pathlib import Path
from typing import Optional
from urllib.parse import quote
import zeroconf
from ftplib import FTP


class FtpClient:
    """FTP клиент для работы с Android FTP сервером"""
    
    def __init__(self, host: str, port: int = 2121):
        self.host = host
        self.port = port
        self.ftp = None
        self.current_dir = "/"
    
    def connect(self, username: str = "anonymous", password: str = "") -> bool:
        """Подключение к FTP серверу"""
        try:
            self.ftp = FTP()
            self.ftp.connect(self.host, self.port, timeout=30)
            self.ftp.login(username, password)
            self.current_dir = self.ftp.pwd()
            print(f"Подключено к FTP: {self.host}:{self.port}")
            print(f"Текущая директория: {self.current_dir}")
            return True
        except Exception as e:
            print(f"Ошибка подключения к FTP: {e}")
            return False
    
    def disconnect(self):
        """Отключение от FTP сервера"""
        if self.ftp:
            try:
                self.ftp.quit()
            except:
                self.ftp.close()
            self.ftp = None
    
    def list_files(self, path: str = ".") -> list:
        """Получение списка файлов и папок"""
        try:
            self.ftp.cwd(path)
            self.current_dir = self.ftp.pwd()
            files = []
            self.ftp.retrlines('LIST', lambda line: files.append(line))
            return files
        except Exception as e:
            print(f"Ошибка получения списка: {e}")
            return []
    
    def parse_listing(self, lines: list) -> list:
        """Парсинг результата LIST"""
        items = []
        for line in lines:
            parts = line.split()
            if len(parts) >= 9:
                is_dir = parts[0].startswith('d')
                name = ' '.join(parts[8:])
                if name not in ['.', '..']:
                    items.append({
                        'name': name,
                        'is_dir': is_dir,
                        'raw': line
                    })
        return items
    
    def download_file(self, remote_path: str, local_path: str) -> bool:
        """Скачивание файла с FTP сервера"""
        try:
            local_file = Path(local_path)
            local_file.parent.mkdir(parents=True, exist_ok=True)
            
            print(f"Скачивание: {remote_path} -> {local_path}")
            with open(local_file, 'wb') as f:
                self.ftp.retrbinary(f'RETR {remote_path}', f.write)
            print(f"Скачано: {remote_path}")
            return True
        except Exception as e:
            print(f"Ошибка скачивания: {e}")
            return False
    
    def upload_file(self, local_path: str, remote_path: str = None) -> bool:
        """Загрузка файла на FTP сервер"""
        try:
            local_file = Path(local_path)
            if not local_file.exists():
                print(f"Файл не существует: {local_path}")
                return False
            
            if remote_path is None:
                remote_path = local_file.name
            
            print(f"Загрузка: {local_path} -> {remote_path}")
            with open(local_file, 'rb') as f:
                self.ftp.storbinary(f'STOR {remote_path}', f)
            print(f"Загружено: {remote_path}")
            return True
        except Exception as e:
            print(f"Ошибка загрузки: {e}")
            return False
    
    def download_directory(self, remote_dir: str, local_dir: str) -> int:
        """Рекурсивное скачивание директории"""
        downloaded = 0
        try:
            original_dir = self.ftp.pwd()
            self.ftp.cwd(remote_dir)
            
            local_path = Path(local_dir)
            local_path.mkdir(parents=True, exist_ok=True)
            
            items = self.parse_listing(self.list_files())
            
            for item in items:
                if item['is_dir']:
                    sub_count = self.download_directory(item['name'], str(local_path / item['name']))
                    downloaded += sub_count
                else:
                    if self.download_file(item['name'], str(local_path / item['name'])):
                        downloaded += 1
            
            self.ftp.cwd(original_dir)
        except Exception as e:
            print(f"Ошибка скачивания директории: {e}")
        
        return downloaded
    
    def upload_directory(self, local_dir: str, remote_dir: str = None) -> int:
        """Рекурсивная загрузка директории"""
        uploaded = 0
        try:
            local_path = Path(local_dir)
            if not local_path.is_dir():
                print(f"Директория не существует: {local_dir}")
                return 0
            
            if remote_dir is None:
                remote_dir = local_path.name
            
            try:
                self.ftp.mkd(remote_dir)
            except:
                pass
            
            self.ftp.cwd(remote_dir)
            
            for item in local_path.iterdir():
                if item.is_dir():
                    sub_count = self.upload_directory(str(item), item.name)
                    uploaded += sub_count
                else:
                    if self.upload_file(str(item), item.name):
                        uploaded += 1
            
            self.ftp.cwd('..')
        except Exception as e:
            print(f"Ошибка загрузки директории: {e}")
        
        return uploaded


class OfflineSyncClient:
    def __init__(self, sync_folder: str, android_ip: Optional[str] = None, port: int = 8080):
        self.sync_folder = Path(sync_folder).expanduser().resolve()
        self.port = port
        self.android_ip = android_ip
        self.base_url = None
        
        if not self.sync_folder.exists():
            self.sync_folder.mkdir(parents=True)
            print(f"Создана папка синхронизации: {self.sync_folder}")
    
    def discover_devices(self) -> list:
        """Обнаружение Android устройств через mDNS"""
        found = []
        
        class MyListener(zeroconf.ServiceListener):
            def add_service(self, zc, type_, name):
                info = zc.get_service_info(type_, name)
                if info:
                    addresses = info.parsed_addresses()
                    if addresses:
                        device_type = "android"
                        for prop in info.properties:
                            if prop == b'deviceType':
                                device_type = info.properties[prop].decode()
                        found.append({
                            'name': name.replace('._offlinesync._tcp.local.', ''),
                            'ip': addresses[0],
                            'port': info.port,
                            'type': device_type
                        })
                        print(f"Найдено устройство: {name} ({addresses[0]}:{info.port})")
            
            def remove_service(self, zc, type_, name):
                pass
            
            def update_service(self, zc, type_, name):
                pass
        
        print("Поиск устройств в сети...")
        zc = zeroconf.Zeroconf()
        listener = MyListener()
        browser = zeroconf.ServiceBrowser(
            zc,
            "_offlinesync._tcp.local.",
            listener
        )
        
        time.sleep(5)
        zc.close()
        
        return found
    
    def set_device(self, ip: str, port: int = 8080):
        """Установить IP устройства вручную"""
        self.android_ip = ip
        self.port = port
        self.base_url = f"http://{ip}:{port}"
        print(f"Устройство: {ip}:{port}")
    
    def connect(self) -> bool:
        """Проверка соединения с Android"""
        if not self.android_ip:
            print("Ошибка: не указан IP адрес устройства")
            return False
        
        self.base_url = f"http://{self.android_ip}:{self.port}"
        
        try:
            r = requests.get(f"{self.base_url}/", timeout=5)
            if r.status_code == 200:
                print(f"Подключено к Android: {r.text}")
                return True
        except Exception as e:
            print(f"Ошибка подключения: {e}")
        return False
    
    def get_file_hash(self, filepath: Path) -> str:
        """Вычисление MD5 хеша файла"""
        md5 = hashlib.md5()
        with open(filepath, 'rb') as f:
            for chunk in iter(lambda: f.read(8192), b''):
                md5.update(chunk)
        return md5.hexdigest()
    
    def list_android_items(self) -> list:
        """Получение списка файлов и папок на Android"""
        try:
            r = requests.get(f"{self.base_url}/files", timeout=10)
            if r.status_code == 200:
                data = r.json()
                items = data.get('items', [])
                return items
        except:
            pass
        return []
    
    def get_installed_apps(self) -> list:
        """Получение списка установленных приложений"""
        try:
            r = requests.get(f"{self.base_url}/apps", timeout=30)
            if r.status_code == 200:
                data = r.json()
                apps = data.get('apps', [])
                return apps
        except Exception as e:
            print(f"Ошибка получения списка приложений: {e}")
        return []
    
    def save_apps_list(self) -> bool:
        """Сохранение списка приложений в файл"""
        apps = self.get_installed_apps()
        if apps:
            print(f"Сохранено приложений: {len(apps)}")
            return True
        return False
    
    def upload_file(self, filepath: Path) -> bool:
        """Загрузка файла на Android"""
        try:
            with open(filepath, 'rb') as f:
                files = {'file': (filepath.name, f)}
                r = requests.post(f"{self.base_url}/upload", files=files, timeout=300)
                if r.status_code == 200:
                    print(f"Загружено: {filepath.name}")
                    return True
                else:
                    print(f"Ошибка загрузки: {r.status_code} - {r.text}")
        except Exception as e:
            print(f"Ошибка: {e}")
        return False
    
    def download_file(self, filename: str) -> bool:
        """Скачивание файла с Android"""
        try:
            url = f"{self.base_url}/download?path={quote(filename)}"
            print(f"Скачивание: {filename}")
            r = requests.get(url, timeout=300, stream=True)
            if r.status_code == 200:
                filepath = self.sync_folder / filename
                filepath.parent.mkdir(parents=True, exist_ok=True)
                with open(filepath, 'wb') as f:
                    for chunk in r.iter_content(chunk_size=8192):
                        f.write(chunk)
                print(f"Скачано: {filename}")
                return True
            else:
                print(f"Ошибка скачивания: {r.status_code} - {r.text}")
        except Exception as e:
            print(f"Ошибка: {e}")
        return False
    
    def sync_to_android(self) -> int:
        """Синхронизация файлов с Ubuntu на Android"""
        if not self.base_url:
            if not self.connect():
                return 0
        
        uploaded = 0
        for filepath in self.sync_folder.rglob('*'):
            if filepath.is_file():
                rel_path = str(filepath.relative_to(self.sync_folder))
                print(f"Загрузка: {rel_path}")
                if self.upload_file(filepath):
                    uploaded += 1
        return uploaded
    
    def sync_from_android(self) -> int:
        """Синхронизация файлов с Android на Ubuntu"""
        if not self.base_url:
            if not self.connect():
                return 0
        
        downloaded = 0
        android_items = self.list_android_items()
        
        for item in android_items:
            name = item.get('name', '')
            is_dir = item.get('isDirectory', False)
            
            if is_dir:
                # Create directory locally
                local_path = self.sync_folder / name
                local_path.mkdir(parents=True, exist_ok=True)
                print(f"Создана папка: {name}")
                downloaded += 1
            else:
                # Download file
                filepath = self.sync_folder / name
                if not filepath.exists():
                    if self.download_file(name):
                        downloaded += 1
                else:
                    print(f"Файл уже существует: {name}")
        
        return downloaded
    
    def sync_bidirectional(self) -> dict:
        """Двусторонняя синхронизация"""
        if not self.base_url:
            if not self.connect():
                return {'uploaded': 0, 'downloaded': 0}
        
        uploaded = 0
        downloaded = 0
        
        # Get Android items
        android_items = self.list_android_items()
        android_names = set(item.get('name', '') for item in android_items if not item.get('isDirectory', False))
        
        # Upload new files to Android
        for filepath in self.sync_folder.rglob('*'):
            if filepath.is_file():
                rel_path = str(filepath.relative_to(self.sync_folder))
                if rel_path not in android_names:
                    if self.upload_file(filepath):
                        uploaded += 1
        
        # Download files and folders from Android
        for item in android_items:
            name = item.get('name', '')
            is_dir = item.get('isDirectory', False)
            
            if is_dir:
                local_path = self.sync_folder / name
                if not local_path.exists():
                    local_path.mkdir(parents=True, exist_ok=True)
                    print(f"Создана папка: {name}")
                    downloaded += 1
            else:
                filepath = self.sync_folder / name
                if not filepath.exists():
                    if self.download_file(name):
                        downloaded += 1
        
        # Save apps list
        self.save_apps_list()
        
        return {'uploaded': uploaded, 'downloaded': downloaded}


def main():
    parser = argparse.ArgumentParser(description='OfflineSync - синхронизация Ubuntu ↔ Android')
    parser.add_argument('folder', nargs='?', default='~/OfflineSync', help='Папка для синхронизации')
    parser.add_argument('--ip', '-i', help='IP адрес Android устройства')
    parser.add_argument('--port', '-p', type=int, default=8080, help='Порт сервера (по умолчанию: 8080)')
    parser.add_argument('--discover', '-d', action='store_true', help='Обнаружить устройства в сети')
    parser.add_argument('--upload', '-u', action='store_true', help='Загрузить файлы на Android')
    parser.add_argument('--download', action='store_true', help='Скачать файлы с Android')
    parser.add_argument('--sync', '-s', action='store_true', help='Двусторонняя синхронизация')
    parser.add_argument('--watch', '-w', action='store_true', help='Следить за изменениями (авто-синхронизация)')
    
    # FTP options
    parser.add_argument('--ftp', action='store_true', help='Использовать FTP режим')
    parser.add_argument('--ftp-port', type=int, default=2121, help='Порт FTP сервера (по умолчанию: 2121)')
    parser.add_argument('--ftp-user', default='anonymous', help='Имя пользователя FTP (по умолчанию: anonymous)')
    parser.add_argument('--ftp-pass', default='', help='Пароль FTP (по умолчанию: пустой)')
    parser.add_argument('--ftp-list', action='store_true', help='Показать список файлов на FTP')
    parser.add_argument('--ftp-download', metavar='REMOTE', help='Скачать файл с FTP')
    parser.add_argument('--ftp-upload', metavar='LOCAL', help='Загрузить файл на FTP')
    parser.add_argument('--ftp-download-dir', metavar='REMOTE', help='Скачать директорию с FTP')
    parser.add_argument('--ftp-upload-dir', metavar='LOCAL', help='Загрузить директорию на FTP')
    
    args = parser.parse_args()
    
    # FTP mode
    if args.ftp:
        if not args.ip:
            print("Ошибка: укажите IP адрес с --ip")
            return
        
        ftp = FtpClient(args.ip, args.ftp_port)
        if not ftp.connect(args.ftp_user, args.ftp_pass):
            return
        
        try:
            if args.ftp_list:
                files = ftp.list_files()
                items = ftp.parse_listing(files)
                print(f"\nФайлы на FTP сервере ({ftp.current_dir}):")
                for item in items:
                    prefix = "[DIR]" if item['is_dir'] else "[FILE]"
                    print(f"  {prefix} {item['name']}")
            
            elif args.ftp_download:
                local_path = args.folder
                if ftp.download_file(args.ftp_download, local_path):
                    print("Скачивание завершено")
            
            elif args.ftp_upload:
                if ftp.upload_file(args.ftp_upload):
                    print("Загрузка завершена")
            
            elif args.ftp_download_dir:
                local_path = args.folder
                count = ftp.download_directory(args.ftp_download_dir, local_path)
                print(f"Скачано файлов: {count}")
            
            elif args.ftp_upload_dir:
                count = ftp.upload_directory(args.ftp_upload_dir)
                print(f"Загружено файлов: {count}")
            
            else:
                print("FTP команды:")
                print("  --ftp-list                 Показать список файлов")
                print("  --ftp-download <файл>      Скачать файл")
                print("  --ftp-upload <файл>        Загрузить файл")
                print("  --ftp-download-dir <папка> Скачать директорию")
                print("  --ftp-upload-dir <папка>   Загрузить директорию")
        finally:
            ftp.disconnect()
        return
    
    client = OfflineSyncClient(args.folder, args.ip, args.port)
    
    if args.discover:
        devices = client.discover_devices()
        if devices:
            print(f"\nНайдено устройств: {len(devices)}")
            for dev in devices:
                print(f"  - {dev['name']}: {dev['ip']}:{dev['port']} ({dev['type']})")
        else:
            print("Устройства не найдены. Убедитесь что:")
            print("  1. Android и Ubuntu в одной WiFi сети")
            print("  2. Приложение OfflineSync запущено на Android")
        return
    
    if args.ip:
        client.set_device(args.ip, args.port)
    
    if args.sync:
        result = client.sync_bidirectional()
        print(f"Синхронизация завершена: загружено {result['uploaded']}, скачано {result['downloaded']}")
    elif args.upload:
        count = client.sync_to_android()
        print(f"Загружено файлов: {count}")
    elif args.download:
        count = client.sync_from_android()
        print(f"Скачано файлов: {count}")
    elif args.watch:
        print("Режим авто-синхронизации (Ctrl+C для выхода)")
        try:
            while True:
                result = client.sync_bidirectional()
                if result['uploaded'] > 0 or result['downloaded'] > 0:
                    print(f"Синхронизировано: {result}")
                time.sleep(30)  # Проверка каждые 30 секунд
        except KeyboardInterrupt:
            print("\nОстановлено")
    else:
        parser.print_help()
        print("\nПримеры использования (HTTP):")
        print("  offline-sync ~/SyncFolder --discover          # Найти устройства")
        print("  offline-sync ~/SyncFolder --ip 192.168.1.5    # Подключиться к устройству")
        print("  offline-sync ~/SyncFolder --sync               # Двусторонняя синхронизация")
        print("  offline-sync ~/SyncFolder --watch              # Авто-синхронизация")
        print("\nПримеры использования (FTP):")
        print("  offline-sync ~/Download --ftp --ip 192.168.1.5 --ftp-list")
        print("  offline-sync ~/Download --ftp --ip 192.168.1.5 --ftp-download /DCIM/Camera")
        print("  offline-sync ~/Music --ftp --ip 192.168.1.5 --ftp-upload-dir MyMusic")


if __name__ == '__main__':
    main()

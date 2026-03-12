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
import zeroconf


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
    
    def list_android_files(self) -> list:
        """Получение списка файлов на Android"""
        try:
            r = requests.get(f"{self.base_url}/files", timeout=10)
            if r.status_code == 200:
                data = r.json()
                files = data.get('files', [])
                return [f.get('name') if isinstance(f, dict) else f for f in files]
        except:
            pass
        return []
    
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
            r = requests.get(f"{self.base_url}/download/{filename}", timeout=300, stream=True)
            if r.status_code == 200:
                filepath = self.sync_folder / filename
                with open(filepath, 'wb') as f:
                    for chunk in r.iter_content(chunk_size=8192):
                        f.write(chunk)
                print(f"Скачано: {filename}")
                return True
            else:
                print(f"Ошибка скачивания: {r.status_code}")
        except Exception as e:
            print(f"Ошибка: {e}")
        return False
    
    def sync_to_android(self) -> int:
        """Синхронизация файлов с Ubuntu на Android"""
        if not self.base_url:
            if not self.connect():
                return 0
        
        uploaded = 0
        for filepath in self.sync_folder.iterdir():
            if filepath.is_file():
                print(f"Загрузка: {filepath.name}")
                if self.upload_file(filepath):
                    uploaded += 1
        return uploaded
    
    def sync_from_android(self) -> int:
        """Синхронизация файлов с Android на Ubuntu"""
        if not self.base_url:
            if not self.connect():
                return 0
        
        downloaded = 0
        android_files = self.list_android_files()
        
        for filename in android_files:
            filepath = self.sync_folder / filename
            should_download = True
            
            if filepath.exists():
                # Сравниваем хеши
                local_hash = self.get_file_hash(filepath)
                # Примечание: на Android нужно получить хеш файла
                # Для простоты - скачиваем если размера отличаются
                print(f"Файл уже существует: {filename}")
            
            if should_download:
                if self.download_file(filename):
                    downloaded += 1
        
        return downloaded
    
    def sync_bidirectional(self) -> dict:
        """Двусторонняя синхронизация"""
        if not self.base_url:
            if not self.connect():
                return {'uploaded': 0, 'downloaded': 0}
        
        uploaded = 0
        downloaded = 0
        
        # Загружаем новые/изменённые файлы на Android
        for filepath in self.sync_folder.iterdir():
            if filepath.is_file():
                if self.upload_file(filepath):
                    uploaded += 1
        
        # Скачиваем файлы с Android
        android_files = self.list_android_files()
        for file_info in android_files:
            if isinstance(file_info, dict):
                filename = file_info.get('name')
            else:
                filename = file_info
            if filename:
                filepath = self.sync_folder / filename
                if not filepath.exists():
                    if self.download_file(filename):
                        downloaded += 1
        
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
    
    args = parser.parse_args()
    
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
        print("\nПримеры использования:")
        print("  offline-sync ~/SyncFolder --discover          # Найти устройства")
        print("  offline-sync ~/SyncFolder --ip 192.168.1.5    # Подключиться к устройству")
        print("  offline-sync ~/SyncFolder --sync               # Двусторонняя синхронизация")
        print("  offline-sync ~/SyncFolder --watch              # Авто-синхронизация")


if __name__ == '__main__':
    main()

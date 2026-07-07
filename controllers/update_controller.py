"""
更新检查控制器 - 负责异步版本检查、在线下载和安装更新
"""

import asyncio
import os
import sys
import platform
import subprocess
import tempfile

from PySide6.QtCore import QThread, Signal, Qt
from PySide6.QtWidgets import QProgressDialog, QMessageBox
from core.log_manager import global_logger as logger
from controllers.main_window_protocol import MainWindowProtocol


def _get_platform_asset_name():
    """返回当前平台对应的 Release asset 文件名"""
    system = platform.system()
    if system == "Windows":
        return "IPTV Scanner Editor Pro-Windows-x86_64.exe"
    elif system == "Darwin":
        return "IPTV Scanner Editor Pro-macOS-ARM64.zip"
    elif system == "Linux":
        machine = platform.machine()
        if machine in ("aarch64", "arm64"):
            return "IPTV Scanner Editor Pro-Linux-ARM64.tar.gz"
        return "IPTV Scanner Editor Pro-Linux-x86_64.tar.gz"
    return None


def _is_frozen():
    """是否以 PyInstaller 打包模式运行"""
    return getattr(sys, 'frozen', False)


class UpdateCheckThread(QThread):
    """版本检查线程"""
    update_found = Signal(str, str, str)  # latest_version, current_version, download_url
    check_completed = Signal(bool, str)

    def run(self):
        loop = None
        try:
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)

            from core.version import CURRENT_VERSION
            current_version = CURRENT_VERSION

            latest_version, download_url, _ = loop.run_until_complete(
                asyncio.wait_for(self._get_latest_version(), timeout=15)
            )

            if latest_version and not latest_version.startswith("("):
                if self._is_newer_version(current_version, latest_version):
                    self.update_found.emit(latest_version, current_version, download_url or "")
                    self.check_completed.emit(True, f"发现新版本: {latest_version}")
                else:
                    self.check_completed.emit(True, "当前已是最新版本")
            else:
                self.check_completed.emit(False, f"版本检查失败: {latest_version}")

        except asyncio.TimeoutError:
            self.check_completed.emit(False, "版本检查超时")
        except Exception as e:
            self.check_completed.emit(False, f"版本检查异常: {str(e)}")
        finally:
            try:
                if loop:
                    loop.close()
            except Exception:
                pass

    async def _get_latest_version(self):
        """从GitHub获取最新版本信息，返回 (版本号, 下载链接, Release页面链接)"""
        try:
            import aiohttp
            async with aiohttp.ClientSession() as session:
                async with session.get(
                    "https://api.github.com/repos/sumingyd/IPTV-Scanner-Editor-Pro/releases/latest",
                    headers={'User-Agent': 'IPTV-Scanner-Editor-Pro'},
                    timeout=aiohttp.ClientTimeout(total=10)
                ) as response:
                    if response.status == 200:
                        data = await response.json()
                        version = data.get('tag_name', '').lstrip('v')
                        release_url = data.get('html_url', '')

                        # 查找当前平台对应的下载链接
                        download_url = release_url
                        asset_name = _get_platform_asset_name()
                        if asset_name:
                            for asset in data.get('assets', []):
                                if asset.get('name') == asset_name:
                                    download_url = asset.get('browser_download_url', release_url)
                                    break

                        return version, download_url, release_url
                    elif response.status == 403:
                        return "(API限制)", None, None
                    else:
                        return "(获取失败)", None, None
        except asyncio.TimeoutError:
            return "(请求超时)", None, None
        except Exception:
            return "(获取失败)", None, None

    def _is_newer_version(self, current_version, latest_version):
        """比较版本号，判断最新版本是否比当前版本新"""
        try:
            current_parts = list(map(int, current_version.split('.')))
            latest_parts = list(map(int, latest_version.split('.')))

            max_length = max(len(current_parts), len(latest_parts))
            current_parts.extend([0] * (max_length - len(current_parts)))
            latest_parts.extend([0] * (max_length - len(latest_parts)))

            for i in range(max_length):
                if latest_parts[i] > current_parts[i]:
                    return True
                elif latest_parts[i] < current_parts[i]:
                    return False
            return False
        except (ValueError, AttributeError):
            import re
            current_digits = re.sub(r'[^0-9.]', '', current_version)
            latest_digits = re.sub(r'[^0-9.]', '', latest_version)
            try:
                current_parts = list(map(int, current_digits.split('.'))) if current_digits else [0]
                latest_parts = list(map(int, latest_digits.split('.'))) if latest_digits else [0]
                max_length = max(len(current_parts), len(latest_parts))
                current_parts.extend([0] * (max_length - len(current_parts)))
                latest_parts.extend([0] * (max_length - len(latest_parts)))
                for i in range(max_length):
                    if latest_parts[i] > current_parts[i]:
                        return True
                    elif latest_parts[i] < current_parts[i]:
                        return False
                return False
            except (ValueError, AttributeError):
                return False


class UpdateDownloadThread(QThread):
    """更新文件下载线程"""
    progress = Signal(int, str)  # percentage (0-100, -1=unknown), message
    download_complete = Signal(str)  # file path
    download_error = Signal(str)

    def __init__(self, url, parent=None):
        super().__init__(parent)
        self._url = url
        self._cancel = False

    def run(self):
        loop = None
        try:
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
            loop.run_until_complete(self._download())
        except Exception as e:
            self.download_error.emit(f"下载失败: {str(e)}")
        finally:
            try:
                if loop:
                    loop.close()
            except Exception:
                pass

    async def _download(self):
        """异步下载更新文件"""
        import aiohttp

        asset_name = _get_platform_asset_name() or "isepp_update_file"
        filepath = os.path.join(tempfile.gettempdir(), asset_name)

        self.progress.emit(0, "正在连接服务器...")

        async with aiohttp.ClientSession() as session:
            async with session.get(
                self._url,
                headers={'User-Agent': 'IPTV-Scanner-Editor-Pro'},
                timeout=aiohttp.ClientTimeout(total=600)
            ) as response:
                if response.status != 200:
                    self.download_error.emit(f"下载失败: HTTP {response.status}")
                    return

                total = int(response.headers.get('Content-Length', 0))
                downloaded = 0

                with open(filepath, 'wb') as f:
                    async for chunk in response.content.iter_chunked(65536):
                        if self._cancel:
                            f.close()
                            try:
                                os.remove(filepath)
                            except Exception:
                                pass
                            return
                        f.write(chunk)
                        downloaded += len(chunk)
                        if total > 0:
                            percent = int(downloaded * 100 / total)
                            msg = f"已下载 {downloaded // 1048576}MB / {total // 1048576}MB"
                            self.progress.emit(percent, msg)
                        else:
                            msg = f"已下载 {downloaded // 1048576}MB"
                            self.progress.emit(-1, msg)

                self.download_complete.emit(filepath)

    def cancel(self):
        """取消下载"""
        self._cancel = True


class UpdateController:
    """更新检查控制器 - 管理版本检查、下载和安装"""

    def __init__(self, main_window: MainWindowProtocol):
        self.window: MainWindowProtocol = main_window
        self._update_checking = False
        self._update_checked = False
        self._check_thread = None
        self._download_url = ""
        self._latest_version = ""
        self._current_version = ""
        self._download_thread = None
        self._progress_dialog = None
        self._pending_download = False

    def check_for_updates(self):
        """异步检查新版本"""
        if self._update_checking:
            return
        if self._update_checked:
            return

        self._update_checking = True
        try:
            old_thread = self._check_thread
            if old_thread and old_thread.isRunning():
                old_thread.quit()
                old_thread.wait(1000)

            self._check_thread = UpdateCheckThread()
            self._check_thread.setParent(self.window)
            self._check_thread.update_found.connect(self._on_update_found)
            self._check_thread.check_completed.connect(self._on_update_check_completed)
            self._check_thread.finished.connect(self._check_thread.deleteLater)
            self._check_thread.start()

        except Exception as e:
            logger.error(f"启动版本检查失败: {str(e)}")
            self._update_checking = False
            self._update_checked = True

    def download_and_install(self):
        """下载并安装更新

        如果已有下载链接则直接开始下载；
        否则先触发版本检查，检查完成后自动开始下载。
        """
        if self._download_url:
            self._start_download()
        else:
            self._pending_download = True
            self._update_checked = False
            self.check_for_updates()

    def _start_download(self):
        """启动下载线程"""
        if self._download_thread and self._download_thread.isRunning():
            return

        language_manager = self.window.language_manager
        tr = language_manager.tr

        self._download_thread = UpdateDownloadThread(self._download_url)
        self._download_thread.setParent(self.window)
        self._download_thread.progress.connect(self._on_download_progress)
        self._download_thread.download_complete.connect(self._on_download_complete)
        self._download_thread.download_error.connect(self._on_download_error)
        self._download_thread.finished.connect(self._download_thread.deleteLater)
        self._download_thread.start()

        # 创建进度对话框
        self._progress_dialog = QProgressDialog(
            tr("update_checking", "Checking for updates..."),
            tr("cancel_button", "Cancel"),
            0, 100, self.window
        )
        self._progress_dialog.setWindowTitle(tr("update_progress_title", "Online Update"))
        self._progress_dialog.setWindowModality(Qt.WindowModality.ApplicationModal)
        self._progress_dialog.setMinimumDuration(0)
        self._progress_dialog.setValue(0)
        self._progress_dialog.canceled.connect(self._on_download_canceled)

    def _on_download_progress(self, percent, message):
        if self._progress_dialog:
            if percent >= 0:
                self._progress_dialog.setValue(percent)
            else:
                # 未知总大小时使用忙碌模式
                self._progress_dialog.setRange(0, 0)
            self._progress_dialog.setLabelText(message)

    def _on_download_complete(self, filepath):
        if self._progress_dialog:
            self._progress_dialog.close()
            self._progress_dialog = None

        language_manager = self.window.language_manager
        tr = language_manager.tr

        # 如果从源码运行（非打包），无法自动安装，打开文件夹让用户手动操作
        if not _is_frozen():
            if platform.system() == "Windows":
                subprocess.Popen(f'explorer /select,"{filepath}"')
            elif platform.system() == "Darwin":
                subprocess.Popen(['open', os.path.dirname(filepath)])
            else:
                subprocess.Popen(['xdg-open', os.path.dirname(filepath)])
            QMessageBox.information(
                self.window,
                tr("update_progress_title", "Online Update"),
                tr("update_complete", "Update downloaded, please restart the application")
            )
            return

        # 询问用户是否安装并重启
        reply = QMessageBox.question(
            self.window,
            tr("update_progress_title", "Online Update"),
            f"{tr('update_complete', 'Update downloaded, please restart the application')}\n"
            f"v{self._current_version} → v{self._latest_version}",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
            QMessageBox.StandardButton.Yes
        )
        if reply == QMessageBox.StandardButton.Yes:
            self._install_update(filepath)

    def _on_download_error(self, message):
        if self._progress_dialog:
            self._progress_dialog.close()
            self._progress_dialog = None

        language_manager = self.window.language_manager
        tr = language_manager.tr
        QMessageBox.critical(
            self.window,
            tr("update_progress_title", "Online Update"),
            f"{tr('update_error', 'Update Failed')}: {message}"
        )

    def _on_download_canceled(self):
        if self._download_thread:
            self._download_thread.cancel()
            self._download_thread = None
        self._progress_dialog = None

    def _install_update(self, filepath):
        """安装更新：根据平台执行不同的安装逻辑"""
        system = platform.system()
        try:
            if system == "Windows":
                self._install_windows(filepath)
            elif system == "Darwin":
                self._install_macos(filepath)
            elif system == "Linux":
                self._install_linux(filepath)
        except Exception as e:
            logger.error(f"安装更新失败: {e}")
            language_manager = self.window.language_manager
            tr = language_manager.tr
            QMessageBox.critical(
                self.window,
                tr("update_progress_title", "Online Update"),
                f"{tr('update_error', 'Update Failed')}: {str(e)}"
            )

    def _install_windows(self, filepath):
        """Windows: 用批处理脚本替换 exe 并重启"""
        current_exe = sys.executable
        app_dir = os.path.dirname(current_exe)
        target_exe = os.path.join(app_dir, "IPTV Scanner Editor Pro.exe")

        bat_content = (
            "@echo off\r\n"
            "timeout /t 2 /nobreak >nul\r\n"
            f'move /y "{filepath}" "{target_exe}"\r\n'
            f'start "" "{target_exe}"\r\n'
            'del "%~f0"\r\n'
        )
        bat_path = os.path.join(tempfile.gettempdir(), "isepp_update.bat")
        with open(bat_path, 'w', encoding='utf-8') as f:
            f.write(bat_content)

        logger.info(f"启动批处理脚本更新: {bat_path}")
        subprocess.Popen(
            ['cmd', '/c', bat_path],
            creationflags=subprocess.CREATE_NO_WINDOW
        )

        from PySide6.QtWidgets import QApplication
        QApplication.quit()

    def _install_macos(self, filepath):
        """macOS: 解压 zip 并替换 .app bundle"""
        # 解压到临时目录
        subprocess.run(
            ['unzip', '-o', filepath, '-d', tempfile.gettempdir()],
            check=True
        )
        new_app = os.path.join(tempfile.gettempdir(), "IPTV Scanner Editor Pro.app")

        # 获取当前 .app 路径
        # sys.executable: /path/to/App.app/Contents/MacOS/App
        app_path = os.path.dirname(os.path.dirname(os.path.dirname(sys.executable)))

        script = (
            "#!/bin/bash\n"
            "sleep 2\n"
            f'rm -rf "{app_path}"\n'
            f'mv "{new_app}" "{app_path}"\n'
            f'open "{app_path}"\n'
            'rm "$0"\n'
        )
        script_path = os.path.join(tempfile.gettempdir(), "isepp_update.sh")
        with open(script_path, 'w') as f:
            f.write(script)
        os.chmod(script_path, 0o755)

        logger.info(f"启动 shell 脚本更新: {script_path}")
        subprocess.Popen(['/bin/bash', script_path])

        from PySide6.QtWidgets import QApplication
        QApplication.quit()

    def _install_linux(self, filepath):
        """Linux: 解压 tar.gz 并替换可执行文件"""
        extract_dir = tempfile.mkdtemp()
        subprocess.run(
            ['tar', 'xzf', filepath, '-C', extract_dir],
            check=True
        )
        new_exe = os.path.join(extract_dir, "IPTV Scanner Editor Pro")
        current_exe = sys.executable

        script = (
            "#!/bin/bash\n"
            "sleep 2\n"
            f'mv -f "{new_exe}" "{current_exe}"\n'
            f'chmod +x "{current_exe}"\n'
            f'nohup "{current_exe}" > /dev/null 2>&1 &\n'
            'rm "$0"\n'
        )
        script_path = os.path.join(tempfile.gettempdir(), "isepp_update.sh")
        with open(script_path, 'w') as f:
            f.write(script)
        os.chmod(script_path, 0o755)

        logger.info(f"启动 shell 脚本更新: {script_path}")
        subprocess.Popen(['/bin/bash', script_path])

        from PySide6.QtWidgets import QApplication
        QApplication.quit()

    def _on_update_found(self, latest_version, current_version, download_url=""):
        """发现新版本时的处理"""
        self._latest_version = latest_version
        self._current_version = current_version
        self._download_url = download_url

        try:
            from ui.styles import AppStyles
            language_manager = self.window.language_manager

            original_title = self.window.windowTitle() or ""
            new_version_text = language_manager.tr(
                "new_version_available", "New Version Available"
            ) or "New Version Available"
            if new_version_text not in original_title:
                new_title = f"{original_title} - {new_version_text} {latest_version}"
                self.window.setWindowTitle(new_title)

            found_text = language_manager.tr('new_version_found', 'New version found')
            cur_text = language_manager.tr('current_version', 'Current Version')
            status_message = f"{found_text} {latest_version} ({cur_text} {current_version})"
            self.window.status_bar.showMessage(status_message, 10000)

            self.window.status_bar.setStyleSheet(AppStyles.statusbar_error_style())

            from PySide6.QtCore import QTimer
            QTimer.singleShot(10000, self.window._reset_statusbar_style)

            logger.info(f"发现新版本: {latest_version} (当前版本: {current_version})")

        except Exception as e:
            logger.error(f"更新界面提示失败: {str(e)}")

        # 如果用户请求了下载（通过关于对话框的"在线更新"按钮），自动开始下载
        if self._pending_download:
            self._pending_download = False
            if self._download_url:
                self._start_download()

    def _on_update_check_completed(self, success, message):
        self._update_checking = False
        self._update_checked = True
        if success:
            logger.info(f"版本检查完成: {message}")
        else:
            logger.warning(f"版本检查失败: {message}")

        # 如果用户请求了下载但未发现新版本
        if self._pending_download:
            self._pending_download = False
            if not self._download_url:
                language_manager = self.window.language_manager
                tr = language_manager.tr
                QMessageBox.information(
                    self.window,
                    tr("update_progress_title", "Online Update"),
                    message if not success else tr("update_success", "已是最新版本")
                )

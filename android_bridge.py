import importlib
import logging
import os
import sys


def _setup_android_paths():
    if not getattr(sys, 'platform', '') == 'android':
        return
    try:
        Python = importlib.import_module('chaquopy.python').Python
        app = Python.getPlatform().getApplication()
        files_dir = app.getFilesDir().getAbsolutePath()
        os.environ.setdefault('IPTV_DATA_DIR', files_dir)
        sys.path.insert(0, files_dir)
    except Exception as e:
        logging.getLogger('android_bridge').warning(f'_setup_android_paths 失败: {e}')


def _setup_android_logging():
    try:
        Log = importlib.import_module('android.util').Log

        class AndroidLogHandler(logging.Handler):
            def emit(self, record):
                msg = self.format(record)
                level = record.levelno
                if level >= logging.ERROR:
                    Log.e(record.name, msg)
                elif level >= logging.WARNING:
                    Log.w(record.name, msg)
                elif level >= logging.INFO:
                    Log.i(record.name, msg)
                elif level >= logging.DEBUG:
                    Log.d(record.name, msg)
                else:
                    Log.v(record.name, msg)
        root = logging.getLogger()
        if not any(isinstance(h, AndroidLogHandler) for h in root.handlers):
            root.addHandler(AndroidLogHandler())
    except Exception as e:
        logging.getLogger('android_bridge').warning(f'_setup_android_logging 失败: {e}')


def _find_mobile_dir():
    try:
        import server
        server_dir = os.path.dirname(os.path.abspath(server.__file__))
        mobile_dir = os.path.join(server_dir, 'mobile')
        if os.path.isdir(mobile_dir):
            return mobile_dir
    except Exception as e:
        logging.getLogger('android_bridge').debug(f'_find_mobile_dir 通过 server 模块查找失败: {e}')
    this_dir = os.path.dirname(os.path.abspath(__file__))
    for candidate in [
        os.path.join(this_dir, 'server', 'mobile'),
        os.path.join(this_dir, 'mobile'),
    ]:
        if os.path.isdir(candidate):
            return candidate
    return None


_server_started = False
_server_loop = None
_server_runner = None


def start_server(host='127.0.0.1', port=8080):
    global _server_started, _server_loop, _server_runner
    if _server_started:
        return
    _server_started = True

    _setup_android_paths()
    _setup_android_logging()

    logger = logging.getLogger('android_bridge')
    logger.info('Starting IPTV server on Android...')

    import asyncio
    from server.context import ServerContext
    from server.routes import create_app
    from aiohttp import web

    data_dir = os.environ.get('IPTV_DATA_DIR', os.path.expanduser('~'))
    # 使用统一辅助函数获取 ISEP 目录（兜底处理）
    from utils.platform_utils import get_android_data_dir
    config_dir = get_android_data_dir() or os.path.join(data_dir, 'ISEP')
    os.makedirs(config_dir, exist_ok=True)

    os.chdir(config_dir)

    ServerContext.get_instance(main_window=None)

    app = create_app()
    mobile_dir = _find_mobile_dir()
    if mobile_dir:
        _register_mobile_routes(app, mobile_dir)
        logger.info(f'Mobile UI served from: {mobile_dir}')
    else:
        logger.warning('Mobile UI directory not found, /mobile/ will not be available')

    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    _server_loop = loop

    async def _run():
        global _server_runner
        runner = web.AppRunner(app)
        _server_runner = runner
        await runner.setup()
        site = web.TCPSite(runner, host, port)
        await site.start()
        logger.info(f'IPTV server running at http://{host}:{port}')
        try:
            while True:
                await asyncio.sleep(3600)
        except asyncio.CancelledError:
            pass
        finally:
            await runner.cleanup()
            _server_runner = None

    try:
        loop.run_until_complete(_run())
    except KeyboardInterrupt:
        pass
    finally:
        loop.close()
        _server_loop = None


def stop_server():
    """停止 Android 端服务器

    Android 端运行在独立事件循环中，通过取消 _run() 协程来优雅停止。
    如果循环未运行则不做操作。
    """
    global _server_started, _server_loop, _server_runner
    if not _server_started:
        return
    _server_started = False
    try:
        if _server_runner:
            loop = _server_loop
            if loop and loop.is_running():
                import asyncio
                asyncio.run_coroutine_threadsafe(_server_runner.cleanup(), loop)
    except Exception as e:
        logging.getLogger('android_bridge').warning(f'stop_server 失败: {e}')


_MIME_TYPES = {
    '.html': 'text/html',
    '.css': 'text/css',
    '.js': 'application/javascript',
    '.json': 'application/json',
    '.png': 'image/png',
    '.jpg': 'image/jpeg',
    '.svg': 'image/svg+xml',
    '.ico': 'image/x-icon',
    '.woff': 'font/woff',
    '.woff2': 'font/woff2',
    '.ttf': 'font/ttf',
    '.webmanifest': 'application/manifest+json',
}


def _register_mobile_routes(app, base_dir):
    base_dir_real = os.path.realpath(base_dir)

    async def _handle_mobile(request):
        from aiohttp import web
        rel_path = request.match_info.get('path', 'index.html')
        if not rel_path or rel_path.endswith('/'):
            rel_path += 'index.html'
        # 安全：防止路径穿越（../../../etc/passwd 等）
        file_path = os.path.realpath(os.path.join(base_dir, rel_path))
        if not file_path.startswith(base_dir_real + os.sep):
            return web.Response(text='403: Forbidden', status=403)
        if not os.path.isfile(file_path):
            return web.Response(text='404: Not Found', status=404)
        ext = os.path.splitext(rel_path)[1].lower()
        content_type = _MIME_TYPES.get(ext, 'application/octet-stream')
        with open(file_path, 'rb') as f:
            content = f.read()
        return web.Response(
            body=content, content_type=content_type,
            headers={
                'Cache-Control': 'no-cache, no-store, must-revalidate',
                'Pragma': 'no-cache',
                'Expires': '0',
            })

    app.router.add_get('/mobile/', _handle_mobile)
    app.router.add_get('/mobile/{path:.*}', _handle_mobile)

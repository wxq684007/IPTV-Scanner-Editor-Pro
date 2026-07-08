from typing import Dict, Any, Optional
from core.log_manager import global_logger as logger


class StreamQualityScorer:
    MAX_LATENCY_MS = 5000
    MIN_BITRATE_KBPS = 500
    RESOLUTION_SCORES = {
        '4k': 100, '2160p': 100,
        '2k': 85, '1440p': 85,
        '1080p': 75, 'fhd': 75,
        '720p': 55, 'hd': 55,
        '576p': 40, 'sd': 40,
        '480p': 25,
        '360p': 15,
        '240p': 5,
    }

    @staticmethod
    def score(latency_ms: Optional[float] = None,
              bitrate_kbps: Optional[float] = None,
              resolution: Optional[str] = None,
              is_valid: Optional[bool] = None) -> Dict[str, Any]:
        if is_valid is False:
            return {'total': 0, 'grade': 'F', 'details': {'valid': False}}

        latency_score = 0.0
        if latency_ms is not None:
            if latency_ms <= 100:
                latency_score = 30
            elif latency_ms <= 500:
                latency_score = 25
            elif latency_ms <= 1000:
                latency_score = 20
            elif latency_ms <= 2000:
                latency_score = 15
            elif latency_ms <= StreamQualityScorer.MAX_LATENCY_MS:
                latency_score = 10
            else:
                latency_score = 5

        bitrate_score = 0.0
        if bitrate_kbps is not None:
            if bitrate_kbps >= 8000:
                bitrate_score = 35
            elif bitrate_kbps >= 4000:
                bitrate_score = 30
            elif bitrate_kbps >= 2000:
                bitrate_score = 25
            elif bitrate_kbps >= 1000:
                bitrate_score = 20
            elif bitrate_kbps >= StreamQualityScorer.MIN_BITRATE_KBPS:
                bitrate_score = 15
            else:
                bitrate_score = 5

        res_score = 0.0
        if resolution:
            res_lower = resolution.lower().replace(' ', '')
            for key, score in StreamQualityScorer.RESOLUTION_SCORES.items():
                if key in res_lower:
                    res_score = score * 0.35
                    break
            if res_score == 0:
                try:
                    h = int(res_lower.split('x')[-1]) if 'x' in res_lower else 0
                    if h >= 2160:
                        res_score = 35
                    elif h >= 1440:
                        res_score = 30
                    elif h >= 1080:
                        res_score = 26
                    elif h >= 720:
                        res_score = 19
                    elif h >= 480:
                        res_score = 14
                    else:
                        res_score = 5
                except (ValueError, IndexError):
                    res_score = 17.5

        total = latency_score + bitrate_score + res_score
        if total >= 85:
            grade = 'A'
        elif total >= 70:
            grade = 'B'
        elif total >= 55:
            grade = 'C'
        elif total >= 35:
            grade = 'D'
        else:
            grade = 'F'

        return {
            'total': round(total, 1),
            'grade': grade,
            'details': {
                'latency_score': round(latency_score, 1),
                'bitrate_score': round(bitrate_score, 1),
                'resolution_score': round(res_score, 1),
                'valid': True,
            }
        }

    @staticmethod
    def score_from_channel(channel: Dict[str, Any]) -> Dict[str, Any]:
        latency = None
        latency_raw = channel.get('latency', '')
        if latency_raw:
            try:
                latency = float(str(latency_raw).replace('ms', '').strip())
            except (ValueError, TypeError):
                pass

        bitrate = None
        br_raw = channel.get('bitrate', '') or channel.get('video_bitrate', '')
        if br_raw:
            try:
                bitrate = float(str(br_raw).replace('kbps', '').replace('Mbps', '').strip())
                if 'Mbps' in str(br_raw):
                    bitrate *= 1000
            except (ValueError, TypeError):
                pass

        resolution = channel.get('resolution', '')
        is_valid = channel.get('valid', None)

        return StreamQualityScorer.score(latency, bitrate, resolution, is_valid)

    @staticmethod
    def score_from_channel_safe(channel: Dict[str, Any]):
        """评分安全版本：valid 为 None（待检测）时返回 None，表示不展示评分条。

        Returns:
            None：频道尚未检测（valid is None），UI 不应绘制评分条
            dict：已检测频道（valid is True/False），返回标准评分结构
        """
        if channel.get('valid') is None:
            return None
        return StreamQualityScorer.score_from_channel(channel)

    @staticmethod
    def score_from_media_info(info: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        """从播放时的实时媒体信息计算质量评分。

        用于播放列表中已播放频道的信号质量指示条。
        评分维度：分辨率(45) + 码率(40) + 帧率(15) = 100

        灵感来源：卫星接收机信号强度/质量条。

        Args:
            info: mpv get_live_media_info() 返回的扁平 dict

        Returns:
            None：媒体信息不足（无分辨率无码率），不应显示评分条
            dict：标准评分结构，额外包含 resolution 字段
        """
        w = info.get('width', 0) or 0
        h = info.get('height', 0) or 0

        # --- 分辨率评分 (最高 45 分) ---
        res_score = 0.0
        if h >= 2160:
            res_score = 45
        elif h >= 1440:
            res_score = 38
        elif h >= 1080:
            res_score = 32
        elif h >= 720:
            res_score = 22
        elif h >= 576:
            res_score = 16
        elif h >= 480:
            res_score = 12
        elif h > 0:
            res_score = 5

        # --- 码率评分 (最高 40 分) ---
        bitrate_bps = info.get('video_bitrate', 0) or 0
        if bitrate_bps == 0:
            bitrate_bps = info.get('demuxer_bitrate', 0) or 0
        # mpv 返回的是 bps，转 kbps
        bitrate_kbps = bitrate_bps / 1000.0 if bitrate_bps > 100 else 0

        br_score = 0.0
        if bitrate_kbps >= 8000:
            br_score = 40
        elif bitrate_kbps >= 4000:
            br_score = 34
        elif bitrate_kbps >= 2000:
            br_score = 28
        elif bitrate_kbps >= 1000:
            br_score = 22
        elif bitrate_kbps >= 500:
            br_score = 16
        elif bitrate_kbps > 0:
            br_score = 6

        # --- 帧率评分 (最高 15 分) ---
        fps = info.get('fps', 0) or 0
        if fps == 0:
            fps = info.get('video_fps', 0) or 0

        fps_score = 0.0
        if fps >= 50:
            fps_score = 15
        elif fps >= 30:
            fps_score = 12
        elif fps >= 25:
            fps_score = 10
        elif fps > 0:
            fps_score = 6

        # 丢帧惩罚（信号不稳定时扣分，模拟卫星信号质量下降）
        frame_drop = info.get('frame_drop_count', 0) or 0
        decoder_drop = info.get('decoder_frame_drop_count', 0) or 0
        total_drops = frame_drop + decoder_drop
        if total_drops > 0:
            penalty = min(total_drops * 0.5, 10)
            fps_score = max(0.0, fps_score - penalty)

        total = res_score + br_score + fps_score

        # 信息不足判定：既没有分辨率也没有码率，不显示评分条
        if res_score == 0 and br_score == 0:
            return None

        if total >= 85:
            grade = 'A'
        elif total >= 70:
            grade = 'B'
        elif total >= 55:
            grade = 'C'
        elif total >= 35:
            grade = 'D'
        else:
            grade = 'F'

        resolution_str = f"{w}x{h}" if w > 0 and h > 0 else ''
        bitrate_str = f"{int(bitrate_kbps)}kbps" if bitrate_kbps > 0 else ''

        return {
            'total': round(total, 1),
            'grade': grade,
            'resolution': resolution_str,
            'bitrate': bitrate_str,
            'details': {
                'resolution_score': round(res_score, 1),
                'bitrate_score': round(br_score, 1),
                'fps_score': round(fps_score, 1),
            }
        }

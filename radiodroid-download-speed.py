#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import json
import sqlite3
import threading
import os
import time
import tkinter as tk
from tkinter import ttk, messagebox, filedialog

import requests

# ------------------------------------------------------------
# 数据库管理类（支持断点续传）
# ------------------------------------------------------------
class RadioDatabase:
    def __init__(self, db_path="radio_stations.db"):
        self.db_path = db_path
        self.conn = None
        self.cursor = None
        self._connect()
        self._create_tables()

    def _connect(self):
        self.conn = sqlite3.connect(self.db_path)
        self.cursor = self.conn.cursor()

    def _create_tables(self):
        # 主表
        self.cursor.execute('''
            CREATE TABLE IF NOT EXISTS stations (
                stationuuid TEXT PRIMARY KEY,
                name TEXT,
                url TEXT,
                homepage TEXT,
                favicon TEXT,
                country TEXT,
                countrycode TEXT,
                state TEXT,
                language TEXT,
                tags TEXT,
                codec TEXT,
                bitrate INTEGER,
                lastcheckok INTEGER,
                lastchangetime TEXT,
                clickcount INTEGER,
                clicktrend INTEGER,
                votes INTEGER
            )
        ''')
        # 进度表（存储最后下载的 offset）
        self.cursor.execute('''
            CREATE TABLE IF NOT EXISTS sync_progress (
                key TEXT PRIMARY KEY,
                value INTEGER
            )
        ''')
        self.conn.commit()

    def close(self):
        if self.conn:
            self.conn.close()

    def get_last_offset(self):
        """获取上次下载的 offset，用于断点续传"""
        self.cursor.execute("SELECT value FROM sync_progress WHERE key='last_offset'")
        row = self.cursor.fetchone()
        return row[0] if row else 0

    def save_offset(self, offset):
        self.cursor.execute("REPLACE INTO sync_progress (key, value) VALUES ('last_offset', ?)", (offset,))
        self.conn.commit()

    def clear_offset(self):
        self.cursor.execute("DELETE FROM sync_progress WHERE key='last_offset'")
        self.conn.commit()

    def get_station_count(self):
        self.cursor.execute("SELECT COUNT(*) FROM stations")
        return self.cursor.fetchone()[0]

    def insert_stations(self, stations_list):
        for s in stations_list:
            self.cursor.execute('''
                INSERT OR IGNORE INTO stations (
                    stationuuid, name, url, homepage, favicon, country, countrycode,
                    state, language, tags, codec, bitrate, lastcheckok, lastchangetime,
                    clickcount, clicktrend, votes
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ''', (
                s.get('stationuuid', ''),
                s.get('name', ''),
                s.get('url', ''),
                s.get('homepage', ''),
                s.get('favicon', ''),
                s.get('country', ''),
                s.get('countrycode', ''),
                s.get('state', ''),
                s.get('language', ''),
                s.get('tags', ''),
                s.get('codec', ''),
                s.get('bitrate', 0),
                1 if s.get('lastcheckok') == 1 else 0,
                s.get('lastchangetime', ''),
                s.get('clickcount', 0),
                s.get('clicktrend', 0),
                s.get('votes', 0)
            ))
        self.conn.commit()

    def get_all_countries(self):
        self.cursor.execute("SELECT DISTINCT country FROM stations WHERE country != '' ORDER BY country")
        return [row[0] for row in self.cursor.fetchall()]

    def get_all_languages(self):
        self.cursor.execute("SELECT DISTINCT language FROM stations WHERE language != '' ORDER BY language")
        return [row[0] for row in self.cursor.fetchall()]

    def filter_stations(self, country="", language="", keyword=""):
        query = "SELECT * FROM stations WHERE 1=1"
        params = []
        if country:
            query += " AND country = ?"
            params.append(country)
        if language:
            query += " AND language = ?"
            params.append(language)
        if keyword:
            query += " AND (name LIKE ? OR tags LIKE ?)"
            like = f"%{keyword}%"
            params.extend([like, like])
        query += " ORDER BY clickcount DESC"
        self.cursor.execute(query, params)
        # 获取列名
        col_names = [desc[0] for desc in self.cursor.description]
        rows = self.cursor.fetchall()
        stations = [dict(zip(col_names, row)) for row in rows]
        return stations


# ------------------------------------------------------------
# 网络同步器（支持断点续传）
# ------------------------------------------------------------
class RadioSyncer:
    def __init__(self, base_url):
        self.base_url = base_url.rstrip('/')
        self.session = requests.Session()
        self.session.headers.update({'User-Agent': 'RadioDBTool/2.0'})

    def get_total_stations(self):
        try:
            resp = self.session.get(f"{self.base_url}/json/stats", timeout=30)
            resp.raise_for_status()
            data = resp.json()
            return data.get('stations', 0)
        except Exception as e:
            raise Exception(f"获取电台总数失败: {e}")

    def fetch_page(self, limit, offset):
        url = f"{self.base_url}/json/stations?limit={limit}&offset={offset}"
        try:
            resp = self.session.get(url, timeout=30)
            resp.raise_for_status()
            return resp.json()
        except Exception as e:
            raise Exception(f"下载失败 (offset={offset}): {e}")

    def sync(self, db, progress_callback, cancel_check=None):
        """执行同步，支持断点续传和取消"""
        limit = 500   # 每页数量
        total = self.get_total_stations()
        if total == 0:
            progress_callback(0, 0, "无法获取电台总数")
            return False, "无法获取电台总数"

        # 获取已下载的 offset
        start_offset = db.get_last_offset()
        if start_offset >= total:
            progress_callback(total, total, "数据已是最新")
            return True, "数据已是最新"

        offset = start_offset
        while offset < total:
            if cancel_check and cancel_check():
                return False, "同步已取消"
            try:
                stations = self.fetch_page(limit, offset)
                if not stations:
                    break
                db.insert_stations(stations)
                offset += len(stations)
                db.save_offset(offset)
                progress_callback(offset, total, f"下载中 ({offset}/{total})")
                time.sleep(0.05)  # 避免请求过快
            except Exception as e:
                return False, str(e)

        # 同步完成，清除 offset 记录（下次重新开始）
        db.clear_offset()
        progress_callback(total, total, "同步完成")
        return True, f"同步完成，共 {total} 个电台"


# ------------------------------------------------------------
# 导出辅助类
# ------------------------------------------------------------
class ExportHelper:
    @staticmethod
    def to_m3u(stations, filepath):
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write("#EXTM3U\n")
            for s in stations:
                name = s.get('name', 'Unknown')
                url = s.get('url', '')
                if url:
                    f.write(f"#EXTINF:-1,{name}\n{url}\n\n")

    @staticmethod
    def to_csv(stations, filepath):
        import csv
        if not stations:
            return
        fieldnames = ['stationuuid', 'name', 'url', 'country', 'language', 'tags', 'clickcount', 'votes']
        with open(filepath, 'w', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            writer.writeheader()
            for s in stations:
                row = {k: s.get(k, '') for k in fieldnames}
                writer.writerow(row)

    @staticmethod
    def to_json(stations, filepath):
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(stations, f, indent=2, ensure_ascii=False)


# ------------------------------------------------------------
# 主 GUI 应用程序
# ------------------------------------------------------------
class RadioDBToolApp:
    def __init__(self, root):
        self.root = root
        self.root.title("RadioDBTool 专业版 - 电台数据库下载与导出")
        self.root.geometry("850x650")
        self.db = RadioDatabase()
        self.syncer = None
        self.sync_thread = None
        self.cancel_requested = False

        self.create_widgets()
        self.refresh_filter_options()

    def create_widgets(self):
        main_frame = ttk.Frame(self.root, padding="10")
        main_frame.pack(fill=tk.BOTH, expand=True)

        # ---------------- 服务器设置区域 ----------------
        server_frame = ttk.LabelFrame(main_frame, text="服务器设置", padding="5")
        server_frame.pack(fill=tk.X, pady=5)

        ttk.Label(server_frame, text="API地址:").grid(row=0, column=0, padx=5, sticky=tk.W)
        self.server_entry = ttk.Entry(server_frame, width=50)
        self.server_entry.grid(row=0, column=1, padx=5, sticky=tk.W)
        self.server_entry.insert(0, "https://de1.api.radio-browser.info")

        self.sync_btn = ttk.Button(server_frame, text="开始/恢复同步", command=self.start_sync)
        self.sync_btn.grid(row=0, column=2, padx=10)

        self.cancel_btn = ttk.Button(server_frame, text="取消同步", command=self.cancel_sync, state=tk.DISABLED)
        self.cancel_btn.grid(row=0, column=3, padx=5)

        self.progress_bar = ttk.Progressbar(server_frame, orient=tk.HORIZONTAL, length=400, mode='determinate')
        self.progress_bar.grid(row=1, column=0, columnspan=4, pady=5, sticky=tk.EW)

        self.status_label = ttk.Label(server_frame, text="就绪")
        self.status_label.grid(row=2, column=0, columnspan=4, sticky=tk.W)

        # ---------------- 筛选与导出区域 ----------------
        filter_frame = ttk.LabelFrame(main_frame, text="筛选条件", padding="5")
        filter_frame.pack(fill=tk.X, pady=5)

        ttk.Label(filter_frame, text="国家:").grid(row=0, column=0, padx=5, pady=2, sticky=tk.W)
        self.country_combo = ttk.Combobox(filter_frame, width=30)
        self.country_combo.grid(row=0, column=1, padx=5, pady=2, sticky=tk.W)
        self.country_combo.bind("<<ComboboxSelected>>", lambda e: self.on_filter_changed())

        ttk.Label(filter_frame, text="语言:").grid(row=1, column=0, padx=5, pady=2, sticky=tk.W)
        self.language_combo = ttk.Combobox(filter_frame, width=30)
        self.language_combo.grid(row=1, column=1, padx=5, pady=2, sticky=tk.W)
        self.language_combo.bind("<<ComboboxSelected>>", lambda e: self.on_filter_changed())

        ttk.Label(filter_frame, text="关键词:").grid(row=2, column=0, padx=5, pady=2, sticky=tk.W)
        self.keyword_entry = ttk.Entry(filter_frame, width=40)
        self.keyword_entry.grid(row=2, column=1, padx=5, pady=2, sticky=tk.W)
        self.keyword_entry.bind("<KeyRelease>", lambda e: self.on_filter_changed())

        ttk.Label(filter_frame, text="导出格式:").grid(row=3, column=0, padx=5, pady=2, sticky=tk.W)
        self.format_var = tk.StringVar(value="M3U")
        format_combo = ttk.Combobox(filter_frame, textvariable=self.format_var, values=["M3U", "CSV", "JSON"], width=10)
        format_combo.grid(row=3, column=1, padx=5, pady=2, sticky=tk.W)

        self.export_btn = ttk.Button(filter_frame, text="导出筛选结果", command=self.export_stations)
        self.export_btn.grid(row=3, column=2, padx=10)

        # ---------------- 预览表格 ----------------
        preview_frame = ttk.LabelFrame(main_frame, text="筛选结果预览（前20条）", padding="5")
        preview_frame.pack(fill=tk.BOTH, expand=True, pady=5)

        columns = ("name", "country", "language", "clickcount", "bitrate")
        self.tree = ttk.Treeview(preview_frame, columns=columns, show="headings")
        self.tree.heading("name", text="电台名称")
        self.tree.heading("country", text="国家")
        self.tree.heading("language", text="语言")
        self.tree.heading("clickcount", text="点击数")
        self.tree.heading("bitrate", text="码率")
        self.tree.column("name", width=300)
        self.tree.column("country", width=100)
        self.tree.column("language", width=100)
        self.tree.column("clickcount", width=80)
        self.tree.column("bitrate", width=60)

        scrollbar = ttk.Scrollbar(preview_frame, orient=tk.VERTICAL, command=self.tree.yview)
        self.tree.configure(yscrollcommand=scrollbar.set)
        self.tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)

    def refresh_filter_options(self):
        """从数据库加载国家、语言列表（异步）"""
        def load():
            countries = self.db.get_all_countries()
            languages = self.db.get_all_languages()
            self.root.after(0, lambda: self._set_filter_options(countries, languages))
        threading.Thread(target=load, daemon=True).start()

    def _set_filter_options(self, countries, languages):
        self.country_combo['values'] = [''] + countries
        self.language_combo['values'] = [''] + languages
        if not countries:
            self.country_combo.set('')
            self.language_combo.set('')

    def on_filter_changed(self):
        """筛选条件变化时更新预览表格"""
        def do_filter():
            country = self.country_combo.get().strip()
            language = self.language_combo.get().strip()
            keyword = self.keyword_entry.get().strip()
            stations = self.db.filter_stations(country, language, keyword)
            self.root.after(0, lambda: self._update_preview(stations[:20]))
        threading.Thread(target=do_filter, daemon=True).start()

    def _update_preview(self, stations):
        for item in self.tree.get_children():
            self.tree.delete(item)
        for s in stations:
            self.tree.insert("", tk.END, values=(
                s.get('name', ''),
                s.get('country', ''),
                s.get('language', ''),
                s.get('clickcount', 0),
                s.get('bitrate', 0)
            ))

    # ------------------ 同步相关 ------------------
    def start_sync(self):
        if self.sync_thread and self.sync_thread.is_alive():
            messagebox.showwarning("提示", "同步任务已在运行中")
            return
        server = self.server_entry.get().strip()
        if not server:
            messagebox.showerror("错误", "请输入服务器地址")
            return

        self.cancel_requested = False
        self.sync_btn.config(state=tk.DISABLED)
        self.cancel_btn.config(state=tk.NORMAL)
        self.progress_bar['value'] = 0
        self.status_label.config(text="正在连接服务器...")

        self.syncer = RadioSyncer(server)
        self.sync_thread = threading.Thread(target=self._sync_worker, daemon=True)
        self.sync_thread.start()

    def _sync_worker(self):
        def progress_callback(current, total, message):
            self.root.after(0, lambda: self._update_progress(current, total, message))

        success, msg = self.syncer.sync(self.db, progress_callback, lambda: self.cancel_requested)
        self.root.after(0, lambda: self._sync_finished(success, msg))

    def _update_progress(self, current, total, message):
        if total > 0:
            percent = int(current / total * 100)
            self.progress_bar['value'] = percent
            self.progress_bar['maximum'] = total
        else:
            self.progress_bar['value'] = 0
        self.status_label.config(text=message)

    def _sync_finished(self, success, msg):
        self.sync_btn.config(state=tk.NORMAL)
        self.cancel_btn.config(state=tk.DISABLED)
        if success:
            messagebox.showinfo("完成", msg)
            self.status_label.config(text=msg)
            self.refresh_filter_options()   # 刷新国家/语言下拉框
            self.on_filter_changed()        # 刷新预览（显示已下载的数据）
        else:
            messagebox.showerror("错误", msg)
            self.status_label.config(text=f"同步失败: {msg}")

    def cancel_sync(self):
        self.cancel_requested = True
        self.cancel_btn.config(state=tk.DISABLED)
        self.status_label.config(text="正在取消...")

    # ------------------ 导出 ------------------
    def export_stations(self):
        country = self.country_combo.get().strip()
        language = self.language_combo.get().strip()
        keyword = self.keyword_entry.get().strip()
        fmt = self.format_var.get().lower()

        if self.db.get_station_count() == 0:
            messagebox.showwarning("无数据", "数据库中没有任何电台，请先同步数据")
            return

        # 异步导出，避免界面卡顿
        def do_export():
            stations = self.db.filter_stations(country, language, keyword)
            if not stations:
                self.root.after(0, lambda: messagebox.showinfo("提示", "没有找到符合条件的电台"))
                return
            # 选择保存文件
            filetypes = []
            ext = ""
            if fmt == "m3u":
                ext = ".m3u"
                filetypes = [("M3U 播放列表", "*.m3u")]
            elif fmt == "csv":
                ext = ".csv"
                filetypes = [("CSV 表格", "*.csv")]
            else:
                ext = ".json"
                filetypes = [("JSON 文件", "*.json")]

            filepath = filedialog.asksaveasfilename(defaultextension=ext, filetypes=filetypes)
            if not filepath:
                return
            try:
                if fmt == "m3u":
                    ExportHelper.to_m3u(stations, filepath)
                elif fmt == "csv":
                    ExportHelper.to_csv(stations, filepath)
                else:
                    ExportHelper.to_json(stations, filepath)
                self.root.after(0, lambda: messagebox.showinfo("成功", f"已导出 {len(stations)} 个电台到\n{filepath}"))
            except Exception as e:
                self.root.after(0, lambda: messagebox.showerror("错误", f"导出失败: {e}"))

        threading.Thread(target=do_export, daemon=True).start()

    def on_closing(self):
        self.db.close()
        self.root.destroy()


# ------------------------------------------------------------
if __name__ == "__main__":
    root = tk.Tk()
    app = RadioDBToolApp(root)
    root.protocol("WM_DELETE_WINDOW", app.on_closing)
    root.mainloop()

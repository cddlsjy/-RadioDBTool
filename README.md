# RadioDBTool

RadioDBTool 是一个用于独立下载和导出 RadioBrowser 数据库的 Android 工具应用。

## 功能特性

- 从 RadioBrowser 服务器同步电台数据库
- 支持按国家、语言、关键词筛选电台
- 支持导出为 M3U、CSV、JSON 格式
- 简洁的 Material Design 界面

## 使用说明

1. **同步数据库**
   - 在服务器地址输入框中输入 RadioBrowser 服务器地址（默认：https://de1.api.radio-browser.info）
   - 点击"同步数据库"按钮开始下载
   - 等待同步完成，进度条会显示下载进度

2. **筛选与导出**
   - 在筛选条件区域输入国家、语言、关键词（留空表示全部）
   - 选择导出格式（M3U、CSV、JSON）
   - 点击"导出筛选结果"按钮
   - 选择保存位置完成导出

## 技术栈

- Android SDK 16+
- Room 数据库
- OkHttp 网络请求
- Gson JSON 解析

## 项目结构

```
app/src/main/
├── java/com/example/radiodbtool/
│   ├── database/          # 数据库层
│   │   ├── RadioDroidDatabase.java
│   │   ├── RadioStation.java
│   │   ├── RadioStationDao.java
│   │   ├── RadioStationRepository.java
│   │   ├── UpdateTimestamp.java
│   │   ├── UpdateTimestampDao.java
│   │   ├── Converters.java
│   │   ├── CountryCount.java
│   │   └── LanguageCount.java
│   ├── station/
│   │   └── DataRadioStation.java
│   ├── HttpClient.java
│   ├── Utils.java
│   ├── ExportHelper.java
│   └── MainActivity.java
├── res/
│   ├── layout/
│   │   └── activity_main.xml
│   └── values/
│       ├── strings.xml
│       ├── colors.xml
│       └── styles.xml
└── AndroidManifest.xml
```

## License

MIT License

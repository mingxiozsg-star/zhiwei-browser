本版修下载与检查更新——下载前并发测速所有线路、自动走最快的一条，中途失败自动换线，同时修掉续传、完整性校验和版本号比较的几处缺陷。

This release fixes downloads and update checks: all mirrors are benchmarked concurrently and the fastest one is used, with automatic failover on failure, plus fixes for resume, integrity checks and version comparison.

- 服务端提前断开、暂停或取消，不再留下「看着已经完成」的坏文件
- A premature disconnect, pause or cancel can no longer leave a truncated file that looks complete.
- 断点续传只在同一条线路上进行，「重试」彻底重来
- Resume only continues on the same mirror, and "Retry" restarts from scratch.
- 检查更新改为多通道并发竞速，谁先返回有效结果用谁
- Update check: all channels race in parallel and the first valid result wins.
- 修复版本号比较，1.10.0 现在会正确判定比 1.8.1 新
- Version comparison fixed: 1.10.0 is now correctly newer than 1.8.1.
- 更新页直接显示该版本的更新说明
- The update page now shows the release notes for that version.
- 安装包约 1.5 MB，覆盖安装不丢数据
- About 1.5 MB, installs over the old version without losing data.
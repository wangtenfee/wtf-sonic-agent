@echo off
echo 启动前先在cmd命令窗口中设置下编码：chcp 65001
chcp 65001
setlocal enabledelayedexpansion

:: 设置目标端口
set PORT=7777

:: 查找占用该端口的进程 PID
echo 正在查找占用端口 %PORT% 的进程...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%PORT%"') do (
    set PID=%%a
    goto KillProcess
)

:: 如果未找到进程
echo 未找到占用端口 %PORT% 的进程。
goto End

:KillProcess
echo 正在终止进程 PID: !PID!
taskkill /PID !PID! /F

:End
echo 操作完成。
pause
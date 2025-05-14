#!/bin/bash

# 检查 sonic-agent 是否运行的脚本

if pgrep -f sonic-agent >/dev/null; then
    # 获取进程详细信息
    pids=$(pgrep -f sonic-agent | tr '\n' ' ')
    echo -e "\033[32m[运行中]\033[0m sonic-agent (PID: ${pids})"
    echo "进程详细信息："
    ps -fp $(pgrep -f sonic-agent)
else
    echo -e "\033[31m[未运行]\033[0m sonic-agent"
fi
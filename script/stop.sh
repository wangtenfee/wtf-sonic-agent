#!/bin/bash

# 优化版 sonic-agent 进程终止脚本

# 查找所有 sonic-agent 进程ID（更安全的pgrep方式）
pids=$(pgrep -f sonic-agent)

if [[ -z "$pids" ]]; then
    echo "No sonic-agent processes found"
    exit 0
fi

echo "Found sonic-agent processes:"
ps -fp $pids | awk 'NR==1 || /sonic-agent/'

# 使用更现代的pkill终止进程
if pkill -f sonic-agent; then
    echo "Successfully terminated sonic-agent processes (PIDs: $pids)"
else
    echo "Failed to terminate some processes" >&2
    exit 1
fi

# 二次确认
remaining=$(pgrep -f sonic-agent)
if [[ -n "$remaining" ]]; then
    echo "Warning: Some processes still running (PIDs: $remaining)" >&2
    exit 2
fi

exit 0
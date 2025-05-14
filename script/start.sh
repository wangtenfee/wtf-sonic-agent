#!/bin/bash

# 设置Java路径（如果需要）
# export JAVA_HOME=/path/to/java
# export PATH=$JAVA_HOME/bin:$PATH

# 设置JVM参数
JVM_OPTS="-Xms1500m -Xmx1500m -XX:ReservedCodeCacheSize=256m -XX:InitialCodeCacheSize=256m -XX:+UnlockExperimentalVMOptions -XX:+UseZGC -XX:ConcGCThreads=1 -XX:ParallelGCThreads=2 -XX:ZCollectionInterval=30 -XX:ZAllocationSpikeTolerance=5 -XX:+UnlockDiagnosticVMOptions -XX:-ZProactive -Xlog:gc:./gc-+HeapDumpOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./errorDump.hprof -Dfile.encoding=utf-8"

# 设置要运行的jar文件
JAR_FILE="sonic-agent-windows-x86_64.jar"

# 检查jar文件是否存在
if [ ! -f "$JAR_FILE" ]; then
    echo "Error: Jar file $JAR_FILE not found!"
    exit 1
fi

# 启动Java程序
echo "Starting application..."
echo "JVM options: $JVM_OPTS"
echo "Jar file: $JAR_FILE"

nohup java -server $JVM_OPTS -jar "$JAR_FILE"  > output.log 2>&1 &

# 保存退出状态
EXIT_STATUS=$?
echo "Application exited with status $EXIT_STATUS"
exit $EXIT_STATUS
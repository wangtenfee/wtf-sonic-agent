@echo off
echo 启动前先在cmd命令窗口中设置下编码：chcp 65001
chcp 65001
start /B javaw -server -Dspring.profiles.active=test  -Xms1500m -Xmx1500m -XX:ReservedCodeCacheSize=256m -XX:InitialCodeCacheSize=256m -XX:+UnlockExperimentalVMOptions -XX:+UseZGC -XX:ConcGCThreads=1 -XX:ParallelGCThreads=2 -XX:ZCollectionInterval=30 -XX:ZAllocationSpikeTolerance=5 -XX:+UnlockDiagnosticVMOptions -XX:-ZProactive -Xlog:gc:./gc-+HeapDumpOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./errorDump.hprof -jar -Dfile.encoding=utf-8 sonic-agent-windows-x86_64.jar > output.log 2>&1
exit
#!/bin/bash
# <!--        [ "windows-x86", "windows-x86_64", "macosx-arm64", "macosx-x86_64", "linux-arm64", "linux-x86", "linux-x86_64" ]-->
# 不同的平台需要用不同的参数编译
mvn clean install -Dmaven.test.skip=true -Dplatform=macosx-arm64
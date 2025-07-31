#!/bin/sh
#
#  Copyright 2023 The original authors
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

JAVA_OPEN=~/.sdkman/candidates/java/25.ea.33-open/bin/java
JAVA_GRAAL=~/.sdkman/candidates/java/25.ea.31-graal/bin/java
JAVA_OPTS="-Xmx4g -Xms4g -XX:+UseParallelGC --enable-preview --enable-native-access=ALL-UNNAMED --add-modules jdk.incubator.vector -Djdk.incubator.vector.VECTOR_ACCESS_OOB_CHECK=0 -Dsun.misc.unsafe.memory.access=allow"

NATIVE_IMAGE=~/.sdkman/candidates/java/25.ea.31-graal/bin/native-image
NATIVE_MARCH=native
NATIVE_IMAGE_OPTS="-H:+UnlockExperimentalVMOptions -H:+VectorAPISupport -H:+ForeignAPISupport -H:TuneInlinerExploration=1 -H:-GenLoopSafepoints --gc=epsilon -O3 -march=$NATIVE_MARCH -R:MaxHeapSize=64m --enable-preview --enable-native-access=ALL-UNNAMED --add-modules jdk.incubator.vector -Djdk.incubator.vector.VECTOR_ACCESS_OOB_CHECK=0 -Dsun.misc.unsafe.memory.access=allow"

JAVA=$JAVA_OPEN
JAVA_CP="-cp build/libs/1brc-walkthrough-1.0-SNAPSHOT.jar"
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


taskset -c 0-7 $* 1> /dev/null

perf stat -e \
task-clock,context-switches,cpu-migrations,page-faults,\
cycles,stalled-cycles-frontend,stalled-cycles-backend,instructions,\
branches,branch-misses,cache-misses,cache-references,\
L1-dcache-loads,L1-dcache-load-misses,L1-dcache-prefetches,\
L1-icache-loads,L1-icache-load-misses,\
dTLB-loads,dTLB-load-misses,iTLB-loads,iTLB-load-misses\
 taskset -c 0-7 $* 1> /dev/null
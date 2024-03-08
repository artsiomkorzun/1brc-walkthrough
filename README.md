# 1brc-walkthrough
The step-by-step walkthrough over 1brc challenge.

## Prerequisites
Install Open JDK and GraalVM JDK using sdkman:
``` bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21.0.2-open
sdk install java 21.0.2-graal
```

Install toolchain for GraalVM native-image: https://www.graalvm.org/22.0/reference-manual/native-image/#prerequisites.

Install hyperfine: https://github.com/sharkdp/hyperfine.

## Run
```bash
 # tests might fail because they are run in one jvm, some solutions do not cleanup resources
./gradlew clean build -x test
 # generates 413 and 10k files
./generate-all.sh
 # evaluates 413 and 10k cases with all solutions
./eval-all.sh 
```

## Results
Results are collected using hyperfine with 3 warmups and 10 measurements. 

### AWS c7a.4xlarge
* CPU: AMD EPYC 9R14
* MEM: DDR5 4800 MT/s 32 GB
* AFFINITY: 0-7

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 125.650 ± 0.740 |             0.0 | 160.107 ± 1.963 |             0.0 | 
| 01 | Substring          | 104.383 ± 0.363 |          -16.93 | 132.624 ± 1.320 |          -17.17 |
| 02 | NoGarbage          |  41.485 ± 0.139 |          -60.26 |  61.337 ± 0.152 |          -53.75 |
| 03 | DirectBuffer       |  39.792 ± 0.264 |           -4.08 |  56.018 ± 0.183 |           -8.67 |
| 04 | MappedSegment      |  40.173 ± 0.203 |            0.96 |  57.840 ± 0.297 |            3.25 |
| 05 | Parallelism        |   5.583 ± 0.378 |          -86.10 |   8.018 ± 0.385 |          -86.14 |
| 06 | HashWhileParsing   |   5.212 ± 0.466 |           -6.64 |   7.649 ± 0.933 |           -4.59 |
| 07 | SimpleMap          |   4.915 ± 0.280 |           -5.70 |   7.211 ± 0.829 |           -5.73 |
| 08 | BranchyTemperature |   4.220 ± 0.181 |          -14.14 |   6.623 ± 0.555 |           -8.15 |
| 09 | UnsafeParsing      |   3.861 ± 0.220 |           -8.51 |   6.035 ± 0.574 |           -8.88 |
| 10 | NoKeyCopy          |   3.416 ± 0.055 |          -11.51 |   5.207 ± 0.023 |          -13.72 |
| 11 | SwarStation        |   2.059 ± 0.070 |          -39.74 |   7.042 ± 0.122 |           35.26 |
| 12 | SwarTemperature    |   1.798 ± 0.021 |          -12.67 |   6.865 ± 0.167 |           -2.52 |
| 13 | BetterHash         |   1.776 ± 0.038 |           -1.22 |   2.698 ± 0.078 |          -60.70 |
| 14 | BetterMap          |   1.727 ± 0.043 |           -2.77 |   2.179 ± 0.029 |          -19.23 |
| 15 | ParallelismSharing |   1.728 ± 0.020 |            0.05 |   2.211 ± 0.031 |            1.47 |
| 16 | ParallelismMerging |   1.725 ± 0.015 |           -0.13 |   2.218 ± 0.030 |            0.31 |
| 17 | Graal JIT          |   1.697 ± 0.033 |           -1.66 |   2.178 ± 0.023 |           -1.80 |
| 18 | Graal AOT          |   1.433 ± 0.002 |          -15.52 |   1.860 ± 0.001 |          -14.62 |
| 19 | BranchyMinMax      |   1.415 ± 0.002 |           -1.31 |   1.820 ± 0.002 |           -2.15 |
| 20 | Branchy08Loop      |   1.372 ± 0.002 |           -3.03 |   1.871 ± 0.003 |            2.80 |
| 21 | Branchy16Loop      |   1.332 ± 0.002 |           -2.87 |   1.995 ± 0.002 |            6.64 |
| 22 | CMOV               |   1.246 ± 0.002 |           -6.50 |   1.931 ± 0.002 |           -3.22 |
| 23 | ILP                |   0.964 ± 0.002 |          -22.64 |   1.788 ± 0.001 |           -7.39 |
| 24 | Subprocess         |   0.834 ± 0.001 |          -13.45 |   1.627 ± 0.003 |           -9.01 |
| 97 | Original           |   0.835 ± 0.002 |            0.09 |   1.635 ± 0.001 |            0.49 |
| 98 | Original + CMOV    |   0.836 ± 0.005 |            0.11 |   1.638 ± 0.001 |            0.19 |
| 99 | Original - Sharing |   0.914 ± 0.020 |            9.48 |   1.709 ± 0.021 |            4.55 |

* (*) hash function is affected, see collision table below.

### MacBook Pro
* CPU: Apple M1 Pro
* MEM: 32 GB
* AFFINITY: 0-9

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 189.903 ± 2.454 |             0.0 | 270.058 ± 2.582 |             0.0 |
| 01 | NoGarbage          |  35.674 ± 0.105 |          -81.21 |  68.160 ± 5.800 |          -74.76 |
| 02 | DirectBuffer       |  31.973 ± 0.060 |          -10.37 |  50.690 ± 0.944 |          -25.63 |
| 03 | MappedSegment      |  32.329 ± 0.937 |            1.11 |  63.941 ± 9.198 |           26.14 |
| 04 | Parallelism        |   4.560 ± 0.476 |          -85.89 |   8.527 ± 0.611 |          -86.66 |
| 05 | HashWhileParsing   |   4.641 ± 0.834 |            1.78 |   7.596 ± 0.500 |          -10.93 |
| 06 | SimpleMap (*)      |   3.782 ± 0.128 |          -18.52 |   5.928 ± 0.480 |          -21.95 |
| 07 | BranchyTemperature |   3.453 ± 0.048 |           -8.70 |   6.980 ± 0.187 |           17.73 |
| 08 | UnsafeParsing      |   3.073 ± 0.027 |          -11.01 |   4.918 ± 0.390 |          -29.53 |
| 09 | NoKeyCopy          |   2.573 ± 0.065 |          -16.27 |   4.311 ± 0.047 |          -12.36 |
| 10 | SwarStation (*)    |   1.899 ± 0.022 |          -26.20 |   9.243 ± 0.160 |          114.42 |
| 11 | SwarTemperature    |   2.067 ± 0.036 |            8.83 |   9.160 ± 0.091 |           -0.90 |
| 12 | BetterHash (*)     |   2.010 ± 0.046 |           -2.72 |   2.574 ± 0.053 |          -71.90 |
| 13 | BetterMap (*)      |   1.798 ± 0.022 |          -10.58 |   2.159 ± 0.057 |          -16.11 |
| 14 | ParallelismSharing |   1.874 ± 0.022 |            4.27 |   2.244 ± 0.090 |            3.92 |
| 15 | ParallelismMerging |   1.879 ± 0.023 |            0.22 |   2.233 ± 0.080 |           -0.52 |
| 16 | Graal JIT          |   1.839 ± 0.043 |           -2.12 |   2.135 ± 0.069 |           -4.37 |
| 17 | Graal AOT          |   1.633 ± 0.009 |          -11.19 |   1.926 ± 0.031 |           -9.77 |
| 18 | BranchyMinMax      |   1.637 ± 0.009 |            0.23 |   1.908 ± 0.014 |           -0.96 |
| 19 | Branchy08Loop      |   1.586 ± 0.007 |           -3.13 |   1.919 ± 0.034 |            0.60 |
| 20 | Branchy16Loop      |   1.600 ± 0.023 |            0.92 |   2.150 ± 0.034 |           12.02 |
| 21 | CMOV               |   1.464 ± 0.046 |           -8.49 |   2.013 ± 0.015 |           -6.36 |
| 22 | ILP                |   0.910 ± 0.018 |          -37.82 |   1.628 ± 0.030 |          -19.14 |
| 23 | Subprocess         |   0.907 ± 0.021 |           -0.36 |   1.596 ± 0.020 |           -1.96 |
| 97 | Original           |   0.978 ± 0.035 |            7.78 |   1.647 ± 0.014 |            3.22 |
| 98 | Original - Sharing |   1.008 ± 0.039 |            3.14 |   1.728 ± 0.026 |            4.87 |
| 99 | Original + CMOV    |   0.870 ± 0.009 |          -10.98 |   1.578 ± 0.018 |           -4.21 |

* (*) hash function is affected, see collision table below.

## Collisions
The table shows the number of collisions when a certain change affected hash function.

| #  | Change      | Stations 413 | Stations 10k |
|----|-------------|-------------:|-------------:|
| 06 | SimpleMap   |            3 |        ~1053 | 
| 10 | SwarStation |            8 |        ~3745 | 
| 12 | BetterHash  |            1 |         ~712 |  
| 13 | BetterMap   |            0 |         ~778 |
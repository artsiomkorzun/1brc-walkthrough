# 1brc-walkthrough
The step-by-step walkthrough over 1brc challenge.

## Preparations
Install Open JDK and GraalVM JDK using sdkman:
``` bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21.0.2-open
sdk install java 21.0.2-graal
```

Install toolchain for GraalVM native-image: https://www.graalvm.org/22.0/reference-manual/native-image/#prerequisites.

Install hyperfine: https://github.com/sharkdp/hyperfine.

## Results
Results are collected using hyperfine with 3 warmups and 10 measurements. The process is pinned to the first 8 cpus using taskset 0-7.

## AWS c7g.4xlarge
* CPU: AMD EPYC 9R14
* MEM: DDR5 4800 MT/s
  
|#|Change|Time (413)|Reduction (413)|Time (10k)|Reduction (10k)|
|-|-|-:|-:|-:|-:|
|00| Baseline             | 145.021 ± 1.141|       0.0| 267.644 ± 2.512|     0.0| 
|01| NoGarbage            |  41.851 ± 0.165|    -71.14|  63.919 ± 0.153|  -76.12|
|02| DirectBuffer         |  41.415 ± 0.497|     -1.04|  59.146 ± 0.139|   -7.47|
|03| MappedSegment        |  40.024 ± 0.398|     -3.36|  69.938 ± 5.085|   18.25|
|04| Parallelism          |   5.763 ± 0.624|    -85.60|   9.079 ± 0.307|  -87.02|
|05| HashWhileParsing     |   5.318 ± 0.520|     -7.73|   8.769 ± 0.851|   -3.41|
|06| SimpleMap            |   5.263 ± 0.331|     -1.03|   8.261 ± 0.608|   -5.80|
|07| BranchyTemperature   |   4.253 ± 0.163|    -19.20|   7.121 ± 0.506|  -13.79|
|08| UnsafeParsing        |   3.894 ± 0.108|     -8.43|   6.610 ± 0.710|   -7.18|
|09| NoKeyCopy            |   3.384 ± 0.032|    -13.10|   7.192 ± 0.075|    8.81|
|10| SwarStation (*)      |   2.103 ± 0.079|    -37.87|   7.286 ± 0.070|    1.30|
|11| SwarTemperature      |   1.850 ± 0.044|    -12.01|   6.991 ± 0.125|   -4.04|
|12| BetterHash           |   1.806 ± 0.052|     -2.40|   2.919 ± 0.148|  -58.25|
|13| BetterMap            |   1.780 ± 0.036|     -1.42|   2.283 ± 0.018|  -21.80|
|14| ParallelismSharing   |   1.766 ± 0.029|     -0.78|   2.327 ± 0.030|    1.94|
|15| ParallelismMerging   |   1.784 ± 0.019|      1.00|   2.348 ± 0.035|    0.91|
|16| Graal JIT            |   1.727 ± 0.019|     -3.17|   2.295 ± 0.029|   -2.28|
|17| Graal AOT            |   1.463 ± 0.006|    -15.31|   1.937 ± 0.006|  -15.58|
|18| BranchyMinMax        |   1.444 ± 0.005|     -1.30|   1.896 ± 0.006|   -2.10|
|19| Branchy08Loop        |   1.394 ± 0.006|     -3.43|   1.928 ± 0.006|    1.68|
|20| Branchy16Loop        |   1.363 ± 0.005|     -2.25|   2.058 ± 0.004|    6.75|
|21| CMOV                 |   1.265 ± 0.003|     -7.16|   2.018 ± 0.025|   -1.99|
|22| ILP                  |   0.997 ± 0.003|    -21.22|   1.870 ± 0.019|   -7.30|
|23| Subprocess           |   0.868 ± 0.006|    -12.94|   1.694 ± 0.010|   -9.41|
|98| Original             |   0.859 ± 0.004|     -1.02|   1.666 ± 0.009|   -1.65|
|99| Original + CMOV      |   0.875 ± 0.007|      1.83|   1.681 ± 0.009|    0.89|

* (*) hash function is affected, improving SwarStation change for 413 dramatically and worsening 10k.

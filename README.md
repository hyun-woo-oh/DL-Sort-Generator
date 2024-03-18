DL-Sort Generator
=======================
This repository contains the configurable dual-layer sorter (DL-Sort) RTL generator written in Chisel.

This work is proposed in the paper entitled "**DL-Sort: A Hybrid Approach to Scalable Hardware-Accelerated Fully-Streaming Sorting**", which will be presented at the *International Symposium on Circuits and Systems 2024* (**ISCAS 2024**).

Our paper has been invited to **IEEE Transactions on Circuits and Systems II: Express Briefs** !.
If you intend to use this hardware sorter in your research, please kindly cite this paper using the text below.

``` Plain Text
H. W. Oh, J. Park and S. E. Lee, "DL-Sort: A Hybrid Approach to Scalable Hardware-Accelerated Fully-Streaming Sorting," in IEEE Transactions on Circuits and Systems II: Express Briefs, doi: 10.1109/TCSII.2024.3377255.
```

Here's the BibTeX Code.
```BibTeX
@ARTICLE{10472626,
  author={Oh, Hyun Woo and Park, Joungmin and Lee, Seung Eun},
  journal={IEEE Transactions on Circuits and Systems II: Express Briefs}, 
  title={DL-Sort: A Hybrid Approach to Scalable Hardware-Accelerated Fully-Streaming Sorting}, 
  year={2024},
  volume={},
  number={},
  pages={1-5},
  doi={10.1109/TCSII.2024.3377255}}
```



## About
DL-Sort is a configurable hardware sorter supporting fully-streaming with low latency and efficient resource usage 
.
We aim to provide efficient sorting acceleration for diverse FPGA-based and ASIC-based systems that require fast, consistent, and resource-efficient sorting.

The configurable parameters of the DL-Sort are as follows:
| Name | Description |
|-|-|
|$E$| The amount of data elements to be sorted. In this version, it must be power-of-2 ($2^n$). |
|$P$| The amount of parallel write/read accesses. In this version, it must be power-of-2 ($2^n)$. <br> For example, if $P=4$, DL-sort can receive and transmit four data simultaneously. <br> This will considerably increase the performance when external data sources can provide parallel streaming data. |
|$w_D$ | Bitwidth of the data to be sorted. Usually 64, 32, 16, and 8. |
|$w_T$*| Bitwidth of the tag. When tagging is enabled, each data automatically receives its own tag for indexing. <br> This feature is useful when the system requires low latency execution and only requires the indexes of the data. <br> As the tags have a smaller size than the data to be sorted, the external component can receive the sorted information faster. |

\* The current version only supports enabling/disabling the tagging feature. When tagging is enabled, the $w_T$ is set to $log_2E$. This allows every data element to have its own key.


## Getting Started (Ubuntu Linux)
Please beware that our primary platform is Ubuntu Linux. We have tested our work in the Apple Silicon MacOS, but running this on another platform may not work.
### Dependencies
#### RTL generation
- JDK 8 or newer
  - We recommend using OpenJDK 19.0.2.
- SBT
  - You can download it [here](https://www.scala-sbt.org/download.html).
#### Simulation
- Verilator 5.009
  - You can see the installation manual [here](https://verilator.org/guide/latest/install.html).
- GTKWave (recommended)
  - You can simply install this using apt-get: ```apt install gtkwave```

### Generating SystemVerilog RTL with Chisel
If you are new to Chisel and require further information, please refer to [this](https://github.com/chipsalliance/chisel) repository.

To start, you can simply use these codes in the terminal to instantiate the DL-Sort.

```bash
git clone https://github.com/hyun-woo-oh/DL-Sort-Generator

cd DL-Sort-Generator
sbt compile && sbt "run 32 0 6 2" # Generate sorter with wD=32, tag disabled, log2(E)=6, log2(P)=2
```
You will be able to find the generated RTL code in this relative path: "generated/DLSorter_32x64P4.sv"

### Simulating generated RTL
If you are new to Verilator, please refer to [this](https://github.com/verilator/verilator) repository.

We have included the simple simulation code based on Verilator 5.009.

The sample RTL is generated with the $w_D=32$, tag disabled, $E=64$, $P=4$ configuration.
To run this code, you can simply execute these codes in the terminal.

```bash
cd ..
cd sim
make
```
After execution, the waveform file (*.vcd) will be generated in this relative path: "log/wave.vcd"
You can check the generated waveform with other software such as **gtkwave**.
```
gtkwave log/wave.vcd
```

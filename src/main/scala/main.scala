package sorter

import util.Try
import java.io.File
import scala.math.pow

import chisel3._
import chisel3.simulator._
import circt.stage.ChiselStage

object Main extends App {
  println("DL-Sort RTL Generator v1.00")
  if (args.size < 4) {
    println("Error: insufficient arguments")
    println("Usage: sbt \"run <data bitwidth> <tag enabled, 0 or 1> <total element count (log2E)> <stream width (log2P)>\"")
    println("    For example, sbt run 32 1 8 2 will generate RTL for 256 elements with 4 streaming I/O width for [32-bit data/10-bit tag] pairs")
  }
  val dataBitwidth = args(0).toInt
  val logE = args(2).toInt
  val logP = args(3).toInt
  val tagBitwidth = if (args(1).toInt == 1) logE else 0
  ChiselStage.emitSystemVerilogFile(
    new DLSorter (
      signed          =false ,
      dir             =false ,
      dataBitwidth    =32,
      tagBitwidth     =tagBitwidth,
      elementCntExp   =logE,
      streamWidthExp  =logP,
      elementDivExp   =0
    ),
    Array("--target-dir", "generated"),
    Array("-disable-all-randomization", "-strip-debug-info")
  )
  Try(new File("generated/DLSorter.sv").renameTo(new File(s"generated/DLSorter_${dataBitwidth}x${pow(2,logE).toInt}P${pow(2,logP).toInt}.sv")))
}

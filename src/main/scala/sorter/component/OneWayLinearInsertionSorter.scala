package sorter

import scala.util.control.Breaks._
import scala.math.pow
import scala.math.log
import scala.math.ceil
import chisel3._
import chisel3.util._
import chisel3.simulator._

import chiselhelper._

class OneWayLinearInsertionSorter (
  signed:           Boolean,
  dir:              Boolean,
  dataBitwidth:     Int,
  tagBitwidth:      Int,
  elementCnt:       Int
  ) extends Module with RequireAsyncReset {
  // IO
  val io = IO(new Bundle {
    val iTag      = Input (UInt(tagBitwidth.W))
    val iData     = Input (UInt(dataBitwidth.W))
    val we        = Input (Bool())
    val clear     = Input (Bool())
    val oEntryArr = Output(Vec(elementCnt, new Entry(dataBitwidth, tagBitwidth)))
  })
  // Parameter: Maximum value of compVal
  val entryDataResetVal = (pow(2,dataBitwidth).toLong-1).U

  //// CU router & temp buffer
  // Definitions: compare result reg., insert data
  val entryRegArr   = RegInit(VecInit.fill(elementCnt)(0.U.asTypeOf(new Entry(dataBitwidth, tagBitwidth))))

  // Definitions: Compare Units, result values
  val CuArr               = Array.fill(elementCnt-1) (Module(new CompareUnit(signed, dir, dataBitwidth)).io)
  val cuResArr            = Wire(Vec(elementCnt-1, Bool()))
  val resArr              = Wire(UInt((elementCnt-1).W))


  ////// Compare unit I/O
  // ResOut
  for (i <- 0 until elementCnt-1)
    cuResArr(i) := CuArr(i).resOut || !entryRegArr(i).valid
  resArr := Cat(cuResArr.slice(0, elementCnt).reverse)

  // CompVal
  for (i <- 0 until elementCnt-1) {
    CuArr(i).data0 := io.iData
    CuArr(i).data1 := entryRegArr(i).data
  }

  //// Next sorted value assign to Entry
  // First Entry
  entryRegArr(0).valid := io.we || (entryRegArr(0).valid && !io.clear)
  when (io.we && (io.clear || resArr(0))) {
    entryRegArr(0).tag  := io.iTag
    entryRegArr(0).data := io.iData
  }
  // Second ~ Last-1 Entry
  for (i <- 1 until elementCnt-1) {
    when (io.clear) {
      entryRegArr(i).valid := 0.B
    }.elsewhen(io.we) {
      when (resArr(i-1,0).orR) {
        entryRegArr(i).valid := entryRegArr(i-1).valid
      }.elsewhen (resArr(i)) {
        entryRegArr(i).valid := 1.B
      }
    }
    when (io.we) {
      when (resArr(i-1,0).orR) {
        entryRegArr(i).tag  := entryRegArr(i-1).tag
        entryRegArr(i).data := entryRegArr(i-1).data
      }.elsewhen (resArr(i)) {
        entryRegArr(i).tag  := io.iTag
        entryRegArr(i).data := io.iData
      }
    }
  }
  // Last Entry
  when (io.clear) {
    entryRegArr(elementCnt-1).valid := 0.B
  }.elsewhen (io.we) {
    when (resArr.orR) {
      entryRegArr(elementCnt-1).valid := entryRegArr(elementCnt-2).valid
    }.otherwise {
      entryRegArr(elementCnt-1).valid := 1.B
    }
  }
  when (io.we) {
    when (resArr.orR) {
      entryRegArr(elementCnt-1).tag  := entryRegArr(elementCnt-2).tag
      entryRegArr(elementCnt-1).data := entryRegArr(elementCnt-2).data
    }.otherwise {
      entryRegArr(elementCnt-1).tag  := io.iTag
      entryRegArr(elementCnt-1).data := io.iData
    }
  }

  // Output signals
  for (i <- 0 until elementCnt) {
    io.oEntryArr(i) := entryRegArr(i)
  }
} 

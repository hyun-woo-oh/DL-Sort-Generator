package sorter

import scala.util.control.Breaks._
import scala.math.pow
import scala.math.log
import scala.math.ceil
import chisel3._
import chisel3.util._
import chisel3.simulator._

import chiselhelper._

class CyclicBitonicMergeNetwork (
  signed:           Boolean,
  dir:              Boolean,
  dataBitwidth:     Int,
  tagBitwidth:      Int,
  elementCntExp:    Int,
  elementDivExp:    Int,
  useIBuff:         Boolean,
  useOBuff:         Boolean,
  useTempRegIMux:   Boolean
  ) extends Module with RequireAsyncReset {
  // IO
  val io = IO(new Bundle{
    val data = new SorterIO(dataBitwidth, tagBitwidth, elementCntExp)
    val ctrl = new Bundle {
      val en      = Input (Bool())
      val cplt    = Output(Bool())
    }
  })

  // Local constant & enumeration definitions
  val elementCnt    = pow(2, elementCntExp).toInt
  val elementDiv    = elementCnt / pow(2, elementDivExp).toInt
  val casuCnt       = elementCnt / 2
  val stepCnt       = (elementCntExp * (elementCntExp + 1) / 2)
  val startStepIdx  = (elementCntExp - elementDivExp) * (elementCntExp - elementDivExp + 1) / 2
  var stateCnt      = stepCnt - startStepIdx
  if (useIBuff == true) { stateCnt = stateCnt + 1 }
  if (useOBuff == true) { stateCnt = stateCnt + 1 }
  val stateBitwidth = (ceil(log(stateCnt) / log(2))).toInt
  val statusIdle    = 0.U
  val statusEnd     = (stateCnt-1).U

  //// CASU router & temp buffer
  // State machine
  val state         = RegInit(0.U(stateBitwidth.W))
  val state_acc     = state + 1.U

  when        (state === statusIdle)  { when (io.ctrl.en === true.B) { state := state_acc }
  }.elsewhen  (state === statusEnd)   { state := statusIdle 
  }.otherwise                         { state := state_acc }

  // Definitions: Entries with valid, tag, data
  val entryRegArr   = RegInit(VecInit.fill(elementCnt)(0.U.asTypeOf(new Entry(dataBitwidth, tagBitwidth))))

  // Definitions: Router output -> CASU input
  val entry0Arr = Wire(Vec(casuCnt, new Entry(dataBitwidth, tagBitwidth)))
  val entry1Arr = Wire(Vec(casuCnt, new Entry(dataBitwidth, tagBitwidth)))

  // Generate table
  val bitonicNetworks = Array.ofDim[Int](stepCnt, casuCnt, 2)
  var bundleUnit = 2
  var stepIdx = 0
  println("==============================")
  println("Bitonic Network Generation")
  for (i <- 0 until elementCntExp) {
    println("==============================")
    println("Stage " + i.toString)
    var lenBundles = (elementCnt / bundleUnit).toInt
    var bundles = Array.ofDim[Int](lenBundles, bundleUnit)
    for (j <- 0 until lenBundles) {
      for (k <- 0 until bundleUnit) { bundles(j)(k) = bundleUnit * j + k }
    }
    println("===============")
    println("Current bundles")
    bundles.map(_.mkString(" ")).foreach(println)
    
    var casuIdx = 0
    for (j <- 0 until lenBundles) {
      val bundle = bundles(j)
      var aIdx = 0
      var bIdx = bundleUnit - 1
      for (k <- 0 until bundleUnit >> 1) {
        bitonicNetworks(stepIdx)(casuIdx)(0) = bundle(aIdx)
        bitonicNetworks(stepIdx)(casuIdx)(1) = bundle(bIdx)
        aIdx += 1
        bIdx -= 1
        casuIdx += 1
      }
    }
    println("===============")
    println("Step 0")
    bitonicNetworks(stepIdx).map(_.mkString(" ")).foreach(println)
    
    stepIdx += 1
    var nIdx = 1
    var bundleUnitChild = bundleUnit >> 1
    while (bundleUnitChild > 1) {
      var lenBundlesChild = (elementCnt / bundleUnitChild).toInt
      bundles = Array.ofDim[Int](lenBundlesChild, bundleUnitChild)
      for (j <- 0 until lenBundlesChild) {
        for (k <- 0 until bundleUnitChild) { bundles(j)(k) = bundleUnitChild * j + k }
      }
      casuIdx = 0
      for (j <- 0 until lenBundlesChild) {
        val bundle = bundles(j)
        val eDiff = bundleUnitChild >> 1
        for (k <- 0 until eDiff) {
          bitonicNetworks(stepIdx)(casuIdx)(0) = bundle(k)
          bitonicNetworks(stepIdx)(casuIdx)(1) = bundle(k + eDiff)
          casuIdx += 1
        }
      }
      println("===============")
      println("Step " + nIdx.toString + " ("+stepIdx.toString+")")
      bitonicNetworks(stepIdx).map(_.mkString(" ")).foreach(println)
      bundleUnitChild >>= 1
      stepIdx += 1
      nIdx += 1
    }
    bundleUnit <<= 1
  }
  println("==============================")
  println("Comparator Input Table Generation")
  println("==============================")
  val compIn0Tbl = Array.ofDim[Int](casuCnt,    pow(2, stateBitwidth).toInt)
  val compIn1Tbl = Array.ofDim[Int](casuCnt,    pow(2, stateBitwidth).toInt)

  // Start index setting
  var startIdx = 0
  var endIdx = statusEnd
  if (useIBuff == true) {
    startIdx += 1
  }

  // If No Input Mux on Temp. Reg. -> Integrated Routing
  if (useTempRegIMux == false) {
    val bitonicNetworksOri = bitonicNetworks.map(_.map(_.clone))
    for (i <- startStepIdx+1 until stepCnt) {
      val prevResTbl = bitonicNetworksOri(i-1).flatten
      for (j <- 0 until casuCnt) {
        breakable {
          for (k <- 0 until elementCnt) {
            if (prevResTbl(k) == bitonicNetworks(i)(j)(0)) {
              bitonicNetworks(i)(j)(0) = k
              break
            }
          }
        }
        breakable {
          for (k <- 0 until elementCnt) {
            if (prevResTbl(k) == bitonicNetworks(i)(j)(1)) {
              bitonicNetworks(i)(j)(1) = k
              break
            }
          }
        }
      }
    }
    println("Integrated Bitonic Network")
  }

  // Comparator Input Table Generation
  for (i <- 0 until casuCnt) {
    for (j <- 0 until startIdx) {
      compIn0Tbl(i)(j) = 2*i + 0
      compIn1Tbl(i)(j) = 2*i + 1
    }
    for (j <- startStepIdx until stepCnt) {
      compIn0Tbl(i)(j - startStepIdx + startIdx) = bitonicNetworks(j)(i)(0)
      compIn1Tbl(i)(j - startStepIdx + startIdx) = bitonicNetworks(j)(i)(1)
    }
  }
  var filledCnt: Int = stepCnt + startIdx - startStepIdx
  val muxCnt: Int = pow(2, stateBitwidth).toInt
  if (filledCnt != muxCnt) {
    if (filledCnt % 2 == 1) {
      for (i <- 0 until casuCnt) {
        compIn0Tbl(i)(filledCnt) = compIn0Tbl(i)(filledCnt-1)
        compIn1Tbl(i)(filledCnt) = compIn1Tbl(i)(filledCnt-1)
      }
      filledCnt += 1
    }
    var remCnt: Int = muxCnt - filledCnt
    var matcher: Int = 2
    while (remCnt > 0) {
      // get least unit that is power of two
      var lastUnit = 0
      breakable {
        do {
          if ((filledCnt & matcher) > 0) {
            lastUnit = matcher
            break
          }
          matcher <<= 1
        } while (true)
      }
      for (i <- 0 until casuCnt) {
        for (j <- filledCnt until filledCnt + lastUnit) {
          compIn0Tbl(i)(j) = compIn0Tbl(i)(j-lastUnit)
          compIn1Tbl(i)(j) = compIn1Tbl(i)(j-lastUnit)
        }
      }
      filledCnt += lastUnit
      remCnt -= lastUnit
    }
  }
  println("Comparator Input 0 Table")
  compIn0Tbl.map(_.mkString(" ")).foreach(println)
  println("Comparator Input 1 Table")
  compIn1Tbl.map(_.mkString(" ")).foreach(println)

  // If Temp. Reg. Input Mux enabled -> Temp. Reg. MUX generation
  val resInTbl   = Array.ofDim[Int](elementCnt, pow(2, stateBitwidth).toInt)
  if (useTempRegIMux == true) {
    println("==============================")
    println("Temporary Register MUX Table Generation")
    println("==============================")
    for (i <- 0 until elementCnt) {
      for (j <- 0 until startIdx) { resInTbl(i)(j) = i }
      for (j <- startStepIdx until stepCnt) {
        breakable {
          for (k <- 0 until casuCnt) {
            if (bitonicNetworks(j)(k)(0) == i) {
              resInTbl(i)(j - startStepIdx + startIdx) = 2*k
              break
            }
            if (bitonicNetworks(j)(k)(1) == i) {
              resInTbl(i)(j - startStepIdx + startIdx) = 2*k+1
              break
            }
          }
        }
      }
      for (j <- startIdx + stepCnt - startStepIdx until pow(2, stateBitwidth).toInt) { resInTbl(i)(j) = i }
    }
    println("Register Input Table")
    resInTbl.map(_.mkString(" ")).foreach(println)
  }

  if (useIBuff == false) {
    for (i <- 0 until casuCnt) {
      val entry0Case  = EntryArrayToMuxCase(state, compIn0Tbl(i).map(x => entryRegArr(x)))
      val entry1Case  = EntryArrayToMuxCase(state, compIn1Tbl(i).map(x => entryRegArr(x)))
      entry0Case(0)   = (state === statusIdle) -> io.data.iEntryArr(compIn0Tbl(i)(0))
      entry1Case(0)   = (state === statusIdle) -> io.data.iEntryArr(compIn1Tbl(i)(0))
      entry0Arr(i)    := MuxCase(0.U.asTypeOf(new Entry(dataBitwidth, tagBitwidth)), entry0Case)
      entry1Arr(i)    := MuxCase(0.U.asTypeOf(new Entry(dataBitwidth, tagBitwidth)), entry1Case)
    }
  }
  else {
    for (i <- 0 until casuCnt) {
      val entry0Case  = EntryArrayToMuxCase(state, compIn0Tbl(i).map(x => entryRegArr(x)))
      val entry1Case  = EntryArrayToMuxCase(state, compIn1Tbl(i).map(x => entryRegArr(x)))
      entry0Arr(i)    := MuxCase(0.U.asTypeOf(new Entry(dataBitwidth, tagBitwidth)), entry0Case)
      entry1Arr(i)    := MuxCase(0.U.asTypeOf(new Entry(dataBitwidth, tagBitwidth)), entry1Case)
    }
  }

  // CASU Initialization
  val CasuArr       = Array.fill(casuCnt) (Module(new CompareAndSwapUnit(signed, dir, dataBitwidth, tagBitwidth)))

  // Definitions: CASU result wire
  val casuResArr    = Wire(Vec(elementCnt, new Entry(dataBitwidth, tagBitwidth)))

  // CASU input/output wire array
  for (i <- 0 until casuCnt) {
    CasuArr(i).iEntry0  := entry0Arr(i)
    CasuArr(i).iEntry1  := entry1Arr(i)
    casuResArr(2*i)     := CasuArr(i).oEntry0
    casuResArr(2*i+1)   := CasuArr(i).oEntry1
  }

  // Casu output wire array
  if (useIBuff == false) {
    if (useTempRegIMux == false)  { for (i <- 0 until elementCnt) { entryRegArr(i) := casuResArr(i) } }
    else                          { for (i <- 0 until elementCnt) { entryRegArr(i) := MuxCase(0.U.asTypeOf(new Entry(dataBitwidth, tagBitwidth)), EntryArrayToMuxCase(state, resInTbl(i).map(x => casuResArr(x)))) } }
  }
  else {
    if (useTempRegIMux == false) {
      for (i <- 0 until elementCnt) {
        when (state === statusIdle)   { entryRegArr(i)  := io.data.iEntryArr(i)
        }.otherwise                   { entryRegArr(i) := casuResArr(i) }
      }
    }
    else {
      for (i <- 0 until elementCnt) {
        when (state === statusIdle)   { entryRegArr(i) := io.data.iEntryArr(i)
        }.otherwise                   { entryRegArr(i)  := MuxCase(0.U.asTypeOf(new Entry(dataBitwidth, tagBitwidth)), EntryArrayToMuxCase(state, resInTbl(i).map(x => casuResArr(x)))) }
      }
    }
  }

  // Output signals
  io.ctrl.cplt := state === statusEnd
  if (useOBuff == false)  { for (i <- 0 until elementCnt) { io.data.oEntryArr(i) := casuResArr(i) } }
  else                    { for (i <- 0 until elementCnt) { io.data.oEntryArr(i) := entryRegArr(i) } }
} 

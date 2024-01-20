package sorter

import scala.math.pow
import chisel3._

class SorterIO (
  dataBitwidth:     Int,
  tagBitwidth:      Int,
  elementCntExpVal: Int
  ) extends Bundle {
  val iEntryArr   = Input (Vec(pow(2, elementCntExpVal).toInt, new Entry(dataBitwidth, tagBitwidth)))
  val oEntryArr   = Output(Vec(pow(2, elementCntExpVal).toInt, new Entry(dataBitwidth, tagBitwidth)))
}

class Entry (
  dataBitwidth:     Int,
  tagBitwidth:      Int
) extends Bundle {
  val valid = Bool()
  val tag   = UInt(tagBitwidth.W)
  val data  = UInt(dataBitwidth.W)
}

object EntryArrayToMuxCase {
    def apply(sel: Bits, arr: Array[Entry]) = {
        val arr_new = Array.ofDim[(Bool, Entry)](arr.length)
        for (i <- 0 until arr.length) {
            arr_new(i) = (sel === i.U) -> arr(i)
        }
        arr_new
    }
}

object GenerateBitonicNetwork {
  def apply(elementCntExp: Int) = {
    val elementCnt = pow(2, elementCntExp).toInt
    val bitonicNetworks = Array.ofDim[Int](elementCntExp*(elementCntExp+1)/2, elementCnt/2, 2)
    var bundleUnit = 2
    var stepIdx = 0

    for (i <- 0 until elementCntExp) {
      var lenBundles = (elementCnt / bundleUnit).toInt
      var bundles = Array.ofDim[Int](lenBundles, bundleUnit)
      for (j <- 0 until lenBundles) {
        for (k <- 0 until bundleUnit) { bundles(j)(k) = bundleUnit*j+k }
      }
      
      var casuIdx = 0
      for (j <- 0 until lenBundles) {
        val bundle = bundles(j)
        var aIdx = 0
        var bIdx = bundleUnit-1
        for (k <- 0 until bundleUnit >> 1) {
          bitonicNetworks(stepIdx)(casuIdx)(0) = bundle(aIdx)
          bitonicNetworks(stepIdx)(casuIdx)(1) = bundle(bIdx)
          aIdx += 1
          bIdx -= 1
          casuIdx += 1
        }
      }
      
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
        bundleUnitChild >>= 1
        stepIdx += 1
      }
      bundleUnit <<= 1
    }
    bitonicNetworks
  }
}

class CompareAndSwapUnit (signed: Boolean, dir: Boolean, dataBitwidth: Int, tagBitwidth: Int, halfMode: Boolean = false) extends Module {
  val iEntry0 = IO(Input(new Entry(dataBitwidth, tagBitwidth)))
  val iEntry1 = IO(Input(new Entry(dataBitwidth, tagBitwidth)))
  val oEntry0 = IO(Output(new Entry(dataBitwidth, tagBitwidth)))
  val oEntry1 = IO(Output(new Entry(dataBitwidth, tagBitwidth)))
  val resOut  = IO(Output(Bool()))

  val comp  = Wire(Bool())
  val swap = Wire(Bool())

  if (signed == false) {
    if (dir == false)     // Ascending order
      comp := iEntry0.data < iEntry1.data
    else                  // Descending order
      comp := iEntry0.data > iEntry1.data
  } else {
    if (dir == false)     // Ascending order
      comp := iEntry0.data.asSInt < iEntry1.data.asSInt
    else
      comp := iEntry0.data.asSInt > iEntry1.data.asSInt
  }

  when (!iEntry1.valid) {
    swap := 0.B
  }.elsewhen (!iEntry0.valid) {
    swap := 1.B
  }.otherwise {
    swap := !comp
  }

/*
  oEntry0.valid := Mux(comp, )
  oEntry0.tag := Mux(comp)
  oEntry0.data
  */

  oEntry0 := Mux(swap, iEntry1, iEntry0)
  if (halfMode == false) {
    oEntry1 := Mux(swap, iEntry0, iEntry1)
    resOut := 0.B
  }
  else {
    oEntry1 := 0.U.asTypeOf(new Entry(dataBitwidth, tagBitwidth))
    resOut := swap
  }
}

class CompareUnit (signed: Boolean, dir: Boolean, dataBitwidth: Int) extends Module with RequireAsyncReset {
  val io = IO(new Bundle {
    val data0  = Input (UInt(dataBitwidth.W))
    val data1  = Input (UInt(dataBitwidth.W))
    val resOut    = Output(Bool())
  })

  if (signed == false) {
    if (dir == false)
      io.resOut := io.data0 < io.data1
    else
      io.resOut := io.data0 > io.data1
  } else {
    if (dir == false)
      io.resOut := io.data0.asSInt < io.data1.asSInt
    else
      io.resOut := io.data0.asSInt > io.data1.asSInt
  }
}
class CompareUnitBB (signed: Boolean, dir: Boolean, dataBitwidth: Int) extends BlackBox () {
  val io = IO(new Bundle {
    val data0  = Input (UInt(dataBitwidth.W))
    val data1  = Input (UInt(dataBitwidth.W))
    val resOut    = Output(Bool())
  })
}

class CompareBundle (signed: Boolean, dir: Boolean, dataBitwidth: Int) extends Bundle {
  val data0  = UInt(dataBitwidth.W)
  val data1  = UInt(dataBitwidth.W)
}
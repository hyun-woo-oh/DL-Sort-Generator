package chiselhelper

import scala.util.control.Breaks._
import scala.math.pow
import chisel3._
import chisel3.util._


object ArrayToMuxCase {
  def apply(sel: Bits, arr: Array[UInt]) = {
    val arr_new = Array.ofDim[(Bool, UInt)](arr.length)
    for (i <- 0 until arr.length) {
      arr_new(i) = (sel === i.U) -> arr(i)
    }
    arr_new
  }
}

object SliceBoolVec {
  def apply(boolVec: Vec[Bool], startIdx: Int, endIdx: Int) = {
    Cat(boolVec.slice(startIdx, endIdx).reverse)
  }
}

object OptimizeMuxArray {
  def apply(arr: Array[Int], caseCnt: Int, filledCnt: Int) = {
    var fCnt = filledCnt
    if (fCnt == 0) {
      for (i <- 0 until caseCnt) {
        arr(i) = -1
      }
    }
    else {
      if (filledCnt % 2 == 1) {
        arr(fCnt) = arr(fCnt-1)
        fCnt += 1
      }
      var remCnt: Int = caseCnt - fCnt
      var matcher: Int = 2
      while (remCnt > 0) {
        // get least unit that is power of two
        var lastUnit = 0
        breakable {
          do {
            if ((fCnt & matcher) > 0) {
              lastUnit = matcher
              break
            }
            matcher <<= 1
          } while (true)
        }
        for  (j <- fCnt until fCnt + lastUnit) {
          arr(j) = arr(j-lastUnit)
        }
        fCnt += lastUnit
        remCnt -= lastUnit
      }
    }
    arr
  }
}

object UnaryPatternSortArray {
  def apply(maxNumber: Int) = {
    val swapArray = (0 to maxNumber).toArray.reverse.map(x => pow(2, maxNumber).toInt - pow(2, x).toInt)
    val numArray = Array.ofDim[Int](maxNumber * maxNumber)
    numArray(0) = 0
    for (i <- 0 until maxNumber) {
      val unit = pow(2, i).toInt
      for (j <- unit until unit*2) {
        numArray(j) = numArray(j-unit) + 1
      }
    }
    val retArray = numArray.map(x => swapArray(x).U)
    retArray
  }
}
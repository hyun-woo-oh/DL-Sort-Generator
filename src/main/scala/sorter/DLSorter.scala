package sorter

import scala.util.control.Breaks._
import scala.math.pow
import scala.math.log
import scala.math.ceil
import chisel3._
import chisel3.util._
import chisel3.simulator._

import chiselhelper._

class DLSorter (
  signed:           Boolean,
  dir:              Boolean,
  dataBitwidth:     Int,
  tagBitwidth:      Int,
  elementCntExp:    Int,
  streamWidthExp:   Int,
  elementDivExp:    Int
) extends Module with RequireAsyncReset {
  // IO
  val io = IO(new Bundle {
    val ctrl = new Bundle {
    val we        = Input(Bool())
    val weEnd    = Input(Bool())
    val clear     = Input(Bool())

    val rReady   = Output(Bool())
    val tagRe    = Input(Bool())
    val dataRe   = Input(Bool())
    val tagLast  = Output(Bool())
    val dataLast = Output(Bool())
    }
    val data = new Bundle {
      val iData  = Input(Vec(pow(2, streamWidthExp).toInt, UInt(dataBitwidth.W)))
      val oTag   = Output(Vec(pow(2, streamWidthExp).toInt, UInt(dataBitwidth.W)))
      val oData  = Output(Vec(pow(2, streamWidthExp).toInt, UInt(dataBitwidth.W)))
    }
  })

  val totalElementCnt = pow(2, elementCntExp).toInt
  val lisElementCnt = totalElementCnt >> (elementDivExp+streamWidthExp)
  val streamWidth   = pow(2, streamWidthExp).toInt
  

  //// Distribution Logic
  val streamCntExp    = elementCntExp - streamWidthExp
  val streamCnt       = pow(2, streamCntExp).toInt
  val elementCounter  = RegInit(0.U(streamCntExp.W))

  val lisIsLast       = io.ctrl.we && (elementCounter(streamCntExp-1-elementDivExp,0) === (lisElementCnt-1).U)
  val lisClearFlag    = RegNext(lisIsLast, 0.B)

  //// Tag Generator
  when (io.ctrl.clear === 1.B || io.ctrl.weEnd) {
    elementCounter := 0.U
  }.elsewhen (io.ctrl.we === 1.B) {
    elementCounter := elementCounter + 1.U
  }


  //// One-Way Linear Insertion Sorter
  val LISorterArr = Array.fill(streamWidth) (Module(new OneWayLinearInsertionSorter(
    signed            =signed,
    dir               =dir,
    dataBitwidth      =dataBitwidth,
    tagBitwidth       =tagBitwidth,
    elementCnt        =lisElementCnt
  )).io)

  for (i <- 0 until streamWidth) {
    LISorterArr(i).iTag   := Cat(elementCounter, i.U(streamWidthExp.W))
    LISorterArr(i).iData  := io.data.iData(i)
    LISorterArr(i).we     := io.ctrl.we
    LISorterArr(i).clear  := io.ctrl.clear || io.ctrl.weEnd || lisClearFlag
  }

  //// Page Distributor (Page Buffer) definitions
  val pageCnt       = pow(2, elementDivExp).toInt
  val pageEntryCnt  = lisElementCnt * streamWidth

  //// Cyclic Bitonic Merge Network definitions
  val CBMNetwork = Module(new CyclicBitonicMergeNetwork (
    signed            =signed,
    dir               =dir,
    dataBitwidth      =dataBitwidth,
    tagBitwidth       =tagBitwidth,
    elementCntExp     =elementCntExp,
    elementDivExp     =elementDivExp+streamWidthExp,
    useIBuff          =false ,
    useOBuff          =false ,
    useTempRegIMux    =false
  )).io

  //// Page Distributtor <-> CBMNetwork Connection
  // page copy flag
  val pageCopyFlag  = RegInit(VecInit.fill(pageCnt)(0.B))
  if (pageCnt > 1) {
    for (i <- 0 until pageCnt) {
      pageCopyFlag(i) := lisIsLast && (elementCounter(streamCntExp-1, streamCntExp-elementDivExp) === i.U(elementDivExp.W))
    }
  }
  else {
    pageCopyFlag(0) := lisIsLast
  }

  if (pageCnt > 1) {
    val entryPageArr  = RegInit(VecInit.fill(pageCnt-1, pageEntryCnt)(0.U.asTypeOf(new Entry(dataBitwidth, tagBitwidth))))

    // page clear flag
    val pageClearFlag = RegNext(io.ctrl.weEnd || pageCopyFlag(pageCnt-1), 0.B)

    // page copy logic
    for (i <- 0 until pageCnt-1) {
      for (j <- 0 until streamWidth) {
        for (k <- 0 until lisElementCnt) {
          when (io.ctrl.clear || pageClearFlag) {
            entryPageArr(i)(lisElementCnt*j+k).valid := 0.B
          }.elsewhen (pageCopyFlag(i)) {
            entryPageArr(i)(lisElementCnt*j+k).valid := 1.B
          }
          when (pageCopyFlag(i)) {
            entryPageArr(i)(lisElementCnt*j+k).tag  := LISorterArr(j).oEntryArr(k).tag
            entryPageArr(i)(lisElementCnt*j+k).data := LISorterArr(j).oEntryArr(k).data
          }
        }
      }
    }
    for (i <- 0 until pageCnt-1) {
      for (j <- 0 until pageEntryCnt) {
        CBMNetwork.data.iEntryArr(i*pageEntryCnt+j) := entryPageArr(i)(j)
      }
    }
  }

  // Always send last page entry (Direct LISorter output reg.)
  for (i <- 0 until streamWidth) {
    for (j <- 0 until lisElementCnt) {
      CBMNetwork.data.iEntryArr((pageCnt-1)*pageEntryCnt+lisElementCnt*i+j) := LISorterArr(i).oEntryArr(j)
    }
  }

  val stageStart = elementCntExp - streamWidthExp - elementDivExp + 1
  val cbmnStateCnt = (elementCntExp + stageStart) * (elementCntExp-stageStart+1)/2 + 1

  // If LIS cycle is shorter than CBMN stage,
  if (streamCnt < cbmnStateCnt) {
      println("Warning: this configuration does not support Fully-Streaming!")
  }

  CBMNetwork.ctrl.en := pageCopyFlag(pageCnt-1) || io.ctrl.weEnd

  //// Result Buffer
  val entryResArr = RegInit(VecInit.fill(totalElementCnt)(0.U.asTypeOf(new Entry(dataBitwidth, tagBitwidth))))

  when (CBMNetwork.ctrl.cplt) {
    entryResArr := CBMNetwork.data.oEntryArr
  }

  //// Output Logic
  io.ctrl.rReady := RegNext(CBMNetwork.ctrl.cplt, 0.B)

  if (tagBitwidth > 0) {
    var tagCntPerIter = dataBitwidth / tagBitwidth
    if (tagCntPerIter > totalElementCnt / streamWidth)
      tagCntPerIter = totalElementCnt / streamWidth
    var tagIterCnt    = streamCnt / tagCntPerIter
    var tagIterLast   = totalElementCnt % (tagCntPerIter * streamWidth)
    if (tagIterLast > 0)
      tagIterCnt += 1

    val outTagCounter   = RegInit(0.U(log2Ceil(tagIterCnt).W))

    when (CBMNetwork.ctrl.cplt) {
      outTagCounter   := 0.U
    }.elsewhen (io.ctrl.tagRe) {
      outTagCounter   := outTagCounter + 1.U
    }
    val tagOutputArr    = Wire(Vec(tagIterCnt * streamWidth, UInt(dataBitwidth.W)))

    for (i <- 0 until tagIterCnt-1) {
      for (j <- 0 until streamWidth) {
        val tmpOffset = tagCntPerIter*streamWidth*i+tagCntPerIter*j
        var tempTags  = Wire(Vec(tagCntPerIter, UInt(tagBitwidth.W)))
        for (k <- 0 until tagCntPerIter) {
          tempTags(k) := entryResArr(tmpOffset+k).tag
        }
        tagOutputArr(i*streamWidth+j) := Cat(tempTags.slice(0, tagCntPerIter))
      }
    }
    if (tagIterLast == 0) {
      for (j <- 0 until streamWidth) {
        val tmpOffset = tagCntPerIter*streamWidth*(tagIterCnt-1)+tagCntPerIter*j
        var tempTags  = Wire(Vec(tagCntPerIter, UInt(tagBitwidth.W)))
        for (k <- 0 until tagCntPerIter) {
          tempTags(k) := entryResArr(tmpOffset+k).tag
        }
        tagOutputArr((tagIterCnt-1)*streamWidth+j) := Cat(tempTags.slice(0, tagCntPerIter))
      }
    }
    else {
      val tagLastChunkCnt = tagIterLast / tagCntPerIter
      val tagLastDataCnt  = tagIterLast % tagCntPerIter
      for (j <- 0 until tagLastChunkCnt) {
        val tmpOffset = tagCntPerIter*streamWidth*(tagIterCnt-1) + tagCntPerIter*j
        var tempTags  = Wire(Vec(tagCntPerIter, UInt(tagBitwidth.W)))
        for (k <- 0 until tagCntPerIter) {
          tempTags(k) := entryResArr(tmpOffset+k).tag
        }
        tagOutputArr((tagIterCnt-1)*streamWidth+j) := Cat(tempTags.slice(0, tagCntPerIter))
      }
      val tmpOffset = tagCntPerIter*streamWidth*(tagIterCnt-1) + tagCntPerIter*tagLastChunkCnt
      var tempTags  = Wire(Vec(tagCntPerIter, UInt(tagBitwidth.W)))
      for (k <- 0 until tagCntPerIter) {
        if (k < tagLastDataCnt)
          tempTags(k) := entryResArr(tmpOffset+k).tag
        else
          tempTags(k) := 0.U
      }
      tagOutputArr((tagIterCnt-1)*streamWidth+tagLastChunkCnt) := Cat(tempTags.slice(0, tagCntPerIter))
      for (j <- tagLastChunkCnt+1 until streamWidth) {
        tagOutputArr((tagIterCnt-1)*streamWidth+j) := 0.U
      }
    }

    for (i <- 0 until streamWidth) {
      io.data.oTag(i) := MuxCase(0.U, ArrayToMuxCase(outTagCounter, (0 until tagIterCnt).toArray.map(x => tagOutputArr(x*streamWidth+i))))
    }
    io.ctrl.tagLast := outTagCounter === (tagIterCnt-1).U
  } else {
    for (i <- 0 until streamWidth) {
      io.data.oTag(i) := 0.U
    }
    io.ctrl.tagLast  := 0.U
  }

  val outDataCounter  = RegInit(0.U(streamCntExp.W))

  when (CBMNetwork.ctrl.cplt) {
    outDataCounter   := 0.U
  }.elsewhen (io.ctrl.dataRe) {
    outDataCounter   := outDataCounter + 1.U
  }
  for (i <- 0 until streamWidth) {
    io.data.oData(i) := MuxCase(0.U, ArrayToMuxCase(outDataCounter, (0 until streamCnt).toArray.map(x => entryResArr(streamWidth*x+i).data)))
  }
  io.ctrl.dataLast := outDataCounter === (streamCnt-1).U

}

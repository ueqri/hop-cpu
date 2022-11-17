package core

import chisel3._
import chisel3.util.log2Ceil

class CoreParameter {
  val IFetchWidth = 1

  val dispatchQueueSize    = 16
  val IssueWidth           = 1
  val IssueWindowSize      = 16
  val IssueWidthBits       = log2Ceil(IssueWidth)
  val IssueWindowIndexBits = log2Ceil(IssueWindowSize)

  val PhyRegNum  = 64
  val PhyRegBits = log2Ceil(PhyRegNum)

  val PhyHiLoRegNum  = 4
  val PhyHiLoRegBits = log2Ceil(PhyHiLoRegNum)

  val CommitWidth       = 1
  val ROBDepth          = 32
  val ROBEntryIndexBits = log2Ceil(ROBDepth)

  val BranchIndexNum  = 4
  val BranchIndexBits = log2Ceil(BranchIndexNum)

  val StoreBufferDepth     = 4
  val StoreBufferIndexBits = log2Ceil(StoreBufferDepth)

  val PCInitValue    = 0xbfc00000L
  val ExceptionEntry = 0xbfc00380L

  val DebugPipeViewLog           = false
  val DebugInvalidInstructionLog = false
  val DebugPrintMem              = false
}

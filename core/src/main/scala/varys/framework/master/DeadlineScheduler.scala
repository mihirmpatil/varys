package varys.framework.master

import scala.collection.mutable.{ArrayBuffer, Map}

import varys.Logging

/**
 * Implementation of a deadline-based coflow scheduler with admission control. 
 */

class DeadlineScheduler extends OrderingBasedScheduler with Logging {

  val CONSIDER_DEADLINE = System.getProperty("varys.master.consdierDeadline", "false").toBoolean
  val DEADLINE_PADDING = System.getProperty("varys.master.deadlinePadding", "0.1").toDouble

  if (!CONSIDER_DEADLINE) {
    logError("varys.master.consdierDeadline must be true for DeadlineScheduler.")
    System.exit(1)
  }

  override def getOrderedCoflows(
      activeCoflows: ArrayBuffer[CoflowInfo]): ArrayBuffer[CoflowInfo] = {
    activeCoflows.sortWith(_.readyTime < _.readyTime)
  }

  override def markForRejection(
      cf: CoflowInfo, 
      sBpsFree: Map[String, Double], 
      rBpsFree: Map[String, Double]): Boolean = {
    
    // FIXME: Using 200 milliseconds, i.e., 25MB size, as threshold
    val minMillis = math.max(cf.calcRemainingMillis(sBpsFree, rBpsFree) * (1 + DEADLINE_PADDING), 200)
    
    val rejected = (cf.curState == CoflowState.READY && minMillis > cf.desc.deadlineMillis)
    if (rejected) {
      val rejectMessage = "Minimum completion time of " + minMillis + " millis is more than the deadline of " + cf.desc.deadlineMillis + " millis"
      logInfo("Marking " + cf + " for rejection => " + rejectMessage)
    }

    rejected
  }

  override def calcFlowRate(
      flowInfo: FlowInfo,
      cf: CoflowInfo,
      minFree: Double): Double = {

    math.min((flowInfo.bytesLeft.toDouble * 8) / (cf.desc.deadlineMillis.toDouble / 1000), minFree)
  }
}

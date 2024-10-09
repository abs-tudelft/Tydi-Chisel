package nl.tudelft

package object tydi_chisel {
  val firNoOptimizationOpts: Array[String] = Array("-disable-opt", "-disable-all-randomization", "-strip-debug-info")
  val firNormalOpts: Array[String]         = Array("-O=debug", "-disable-all-randomization", "-strip-debug-info")
  val firReleaseOpts: Array[String]        = Array("-O=release", "-disable-all-randomization", "-strip-debug-info")

  private[this] var _compatCheckResult: Option[CompatCheckResult.Value] = None

  def compatCheckResult: Option[CompatCheckResult.Value] = _compatCheckResult

  def setCompatCheckResult(value: CompatCheckResult.Value): Unit = {
    _compatCheckResult = Some(value)
  }

  private[this] var _compatCheck: Option[CompatCheck.Value] = None

  def compatCheck: Option[CompatCheck.Value] = _compatCheck

  def setCompatCheck(value: CompatCheck.Value): Unit = {
    _compatCheck = Some(value)
  }
}

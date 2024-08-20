package nl.tudelft

package object tydi_chisel {
  val firNoOptimizationOpts: Array[String] = Array("-disable-opt", "-disable-all-randomization", "-strip-debug-info")
  val firNormalOpts: Array[String]         = Array("-O=debug", "-disable-all-randomization", "-strip-debug-info")
  val firReleaseOpts: Array[String]        = Array("-O=release", "-disable-all-randomization", "-strip-debug-info")

  implicit val typeCheck: CompatCheck.Value = CompatCheck.Strict
  implicit val typeCheckResult: CompatCheckResult.Value = CompatCheckResult.Error
}

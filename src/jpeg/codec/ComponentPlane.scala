package jpeg

/** Mutable component reconstruction surface local to one decode operation. */
final private[jpeg] class ComponentPlane(val width: Int, val height: Int):
  private val values = Array.fill(width * height)(0)

  def apply(x: Int, y: Int): Int = values(y * width + x)

  def write(left: Int, top: Int, block: Block): Unit =
    for y <- 0 until 8; x <- 0 until 8 do values((top + y) * width + left + x) = block(y, x)

  /** Reconstructs an output-space sample using the selected chroma filter. */
  def sample(
      outputX: Int,
      outputY: Int,
      horizontalScale: Double,
      verticalScale: Double,
      filter: ChromaUpsampling
  ): Int = filter match
    case ChromaUpsampling.Nearest  =>
      apply((outputX * horizontalScale).toInt, (outputY * verticalScale).toInt)
    case ChromaUpsampling.Bilinear =>
      val sourceX                      = (outputX + 0.5) * horizontalScale - 0.5
      val sourceY                      = (outputY + 0.5) * verticalScale - 0.5
      val left                         = math.floor(sourceX).toInt
      val top                          = math.floor(sourceY).toInt
      val xWeight                      = sourceX - left
      val yWeight                      = sourceY - top
      def bounded(x: Int, y: Int): Int =
        apply(math.max(0, math.min(width - 1, x)), math.max(0, math.min(height - 1, y)))
      val upper                        = bounded(left, top) * (1.0 - xWeight) + bounded(left + 1, top) * xWeight
      val lower                        = bounded(left, top + 1) * (1.0 - xWeight) + bounded(left + 1, top + 1) * xWeight
      math.round(upper * (1.0 - yWeight) + lower * yWeight).toInt

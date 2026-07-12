package jpeg

/** Human-facing JPEG quality in the inclusive range 1–100.
  *
  * Quality is not part of the JPEG interchange syntax: a file stores only the
  * resulting quantization tables. This value uses the conventional Independent
  * JPEG Group scaling curve so that 50 preserves the T.81 Annex K example table.
  */
opaque type Quality = Int

object Quality:
  val Minimum: Quality = 1
  val Default: Quality = 75
  val Maximum: Quality = 100

  def apply(value: Int): Quality =
    require(value >= Minimum && value <= Maximum, "quality must be in 1..100")
    value

  extension (quality: Quality)
    def value: Int = quality

    /** Scales an 8-bit quantizer while preserving JPEG's `1..255` invariant. */
    def scale(table: Block): Block =
      val factor = if quality < 50 then 5000 / quality else 200 - quality * 2
      table.map(entry => math.max(1, math.min(255, (entry * factor + 50) / 100)))

/** Encoding choices. More options can be added without widening the codec's
  * primary `encode(image)` learning path.
  */
final case class EncoderOptions(quality: Quality = Quality.Default)


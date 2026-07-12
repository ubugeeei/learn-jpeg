package jpeg

/** Separable forward and inverse DCT implementations.
  *
  * The equations follow [[https://www.w3.org/Graphics/JPEG/itu-t81.pdf T.81 A.3.3 and Annex F]]. A
  * two-pass row/column factorization evaluates the same equation with one quarter of the cosine
  * products required by a direct four-loop implementation.
  */
object Dct:
  private val cosine                       = Array.tabulate(8, 8)((frequency, position) =>
    math.cos((2 * position + 1) * frequency * math.Pi / 16.0)
  )
  private inline def scale(i: Int): Double = if i == 0 then 1.0 / math.sqrt(2) else 1.0

  /** Level-shifts unsigned samples by 128 and computes the two-dimensional FDCT. */
  def forward(samples: Block): Block =
    val rows = Array.ofDim[Double](64)
    for y <- 0 until 8; u <- 0 until 8 do
      var sum = 0.0
      var x   = 0
      while x < 8 do
        sum += (samples(y, x) - 128) * cosine(u)(x)
        x += 1
      rows(y * 8 + u) = sum
    Block(for v <- 0 until 8; u <- 0 until 8 yield
      var sum = 0.0
      var y   = 0
      while y < 8 do
        sum += rows(y * 8 + u) * cosine(v)(y)
        y += 1
      math.round(0.25 * scale(u) * scale(v) * sum).toInt)

  /** Computes the IDCT and reverses the level shift, clamping to 8-bit samples. */
  def inverse(coefficients: Block): Block =
    val rows = Array.ofDim[Double](64)
    for v <- 0 until 8; x <- 0 until 8 do
      var sum = 0.0
      var u   = 0
      while u < 8 do
        sum += scale(u) * coefficients(v, u) * cosine(u)(x)
        u += 1
      rows(v * 8 + x) = sum
    Block(for y <- 0 until 8; x <- 0 until 8 yield
      var sum = 0.0
      var v   = 0
      while v < 8 do
        sum += scale(v) * rows(v * 8 + x) * cosine(v)(y)
        v += 1
      math.max(0, math.min(255, math.round(0.25 * sum + 128).toInt)))

/** Quantization tables and zig-zag serialization from T.81 Annex K and A.3.6. */
object Quantization:
  /** Example luminance quantization table from
    * [[https://www.w3.org/Graphics/JPEG/itu-t81.pdf T.81 Annex K.1]].
    */
  val Luminance: Block = Block(Seq(
    16, 11, 10, 16, 24, 40, 51, 61, 12, 12, 14, 19, 26, 58, 60, 55, 14, 13, 16, 24, 40, 57, 69, 56,
    14, 17, 22, 29, 51, 87, 80, 62, 18, 22, 37, 56, 68, 109, 103, 77, 24, 35, 55, 64, 81, 104, 113,
    92, 49, 64, 78, 87, 103, 121, 120, 101, 72, 92, 95, 98, 112, 100, 103, 99
  ))

  /** Example chrominance quantization table from
    * [[https://www.w3.org/Graphics/JPEG/itu-t81.pdf T.81 Annex K.2]].
    *
    * Larger high-frequency divisors discard color detail more aggressively than the luminance
    * table. This reflects human vision and is encoder policy, not a format requirement.
    */
  val Chrominance: Block = Block(Seq(
    17, 18, 24, 47, 99, 99, 99, 99, 18, 21, 26, 66, 99, 99, 99, 99, 24, 26, 56, 99, 99, 99, 99, 99,
    47, 66, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99,
    99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99
  ))

  /** Natural-index lookup in zig-zag transmission order (T.81 Figure A.6). */
  val ZigZag: IArray[Int] = IArray(
    0, 1, 8, 16, 9, 2, 3, 10, 17, 24, 32, 25, 18, 11, 4, 5, 12, 19, 26, 33, 40, 48, 41, 34, 27, 20,
    13, 6, 7, 14, 21, 28, 35, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, 30, 37, 44, 51, 58, 59,
    52, 45, 38, 31, 39, 46, 53, 60, 61, 54, 47, 55, 62, 63
  )

  def quantize(coefficients: Block, table: Block): Block =
    Block((0 until 64).map(i => math.round(coefficients(i).toDouble / table(i)).toInt))

  def dequantize(coefficients: Block, table: Block): Block =
    Block((0 until 64).map(i => coefficients(i) * table(i)))

  def zigZag(block: Block): IndexedSeq[Int] = ZigZag.indices.map(i => block(ZigZag(i)))

  def natural(values: Seq[Int]): Block =
    require(values.size == 64)
    val result = Array.fill(64)(0)
    ZigZag.indices.foreach(i => result(ZigZag(i)) = values(i))
    Block(result.toIndexedSeq)

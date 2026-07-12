package jpeg

/** Reference forward and inverse DCT implementations.
  *
  * The equations follow
  * [[https://www.w3.org/Graphics/JPEG/itu-t81.pdf T.81 A.3.3 and Annex F]].
  * Straightforward floating-point code is intentional: it makes the educational
  * mapping visible and provides deterministic, well-tested behavior.
  */
object Dct:
  private val cosine = Array.tabulate(8, 8)((frequency, position) =>
    math.cos((2 * position + 1) * frequency * math.Pi / 16.0))
  private inline def scale(i: Int): Double = if i == 0 then 1.0 / math.sqrt(2) else 1.0

  /** Level-shifts unsigned samples by 128 and computes the two-dimensional FDCT. */
  def forward(samples: Block): Block = Block(for
    v <- 0 until 8
    u <- 0 until 8
  yield math.round(0.25 * scale(u) * scale(v) * (for
    y <- 0 until 8
    x <- 0 until 8
  yield (samples(y, x) - 128) * cosine(u)(x) * cosine(v)(y)).sum).toInt)

  /** Computes the IDCT and reverses the level shift, clamping to 8-bit samples. */
  def inverse(coefficients: Block): Block = Block(for
    y <- 0 until 8
    x <- 0 until 8
  yield
    val value = 0.25 * (for
      v <- 0 until 8
      u <- 0 until 8
    yield scale(u) * scale(v) * coefficients(v, u) * cosine(u)(x) * cosine(v)(y)).sum
    math.max(0, math.min(255, math.round(value + 128).toInt)))

/** Quantization tables and zig-zag serialization from T.81 Annex K and A.3.6. */
object Quantization:
  val Luminance: Block = Block(Seq(
    16,11,10,16,24,40,51,61, 12,12,14,19,26,58,60,55,
    14,13,16,24,40,57,69,56, 14,17,22,29,51,87,80,62,
    18,22,37,56,68,109,103,77, 24,35,55,64,81,104,113,92,
    49,64,78,87,103,121,120,101, 72,92,95,98,112,100,103,99))

  /** Natural-index lookup in zig-zag transmission order (T.81 Figure A.6). */
  val ZigZag: IArray[Int] = IArray(
    0,1,8,16,9,2,3,10,17,24,32,25,18,11,4,5,12,19,26,33,40,
    48,41,34,27,20,13,6,7,14,21,28,35,42,49,56,57,50,43,36,
    29,22,15,23,30,37,44,51,58,59,52,45,38,31,39,46,53,60,
    61,54,47,55,62,63)

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

package jpeg

/** An immutable 8×8 block in natural (row-major) order.
  *
  * JPEG's DCT operates on 8×8 data units; see
  * [[https://www.w3.org/Graphics/JPEG/itu-t81.pdf T.81 A.3.1 and F.1.1]].
  */
opaque type Block = IArray[Int]

object Block:
  val Side = 8
  val Area = Side * Side

  /** Constructs a block and enforces the format-level size invariant. */
  def apply(values: Seq[Int]): Block =
    require(values.size == Area, s"a JPEG block has $Area coefficients")
    IArray.from(values)

  val Zero: Block = Block(Seq.fill(Area)(0))

  extension (block: Block)
    inline def apply(row: Int, column: Int): Int = block.asInstanceOf[Array[Int]](row * Side + column)
    inline def apply(index: Int): Int = block.asInstanceOf[Array[Int]](index)
    def values: IndexedSeq[Int] = IndexedSeq.tabulate(Area)(block(_))
    def map(f: Int => Int): Block = Block(values.map(f))

/** Image dimensions validated at the public boundary. */
final case class Dimensions private (width: Int, height: Int):
  val blockColumns: Int = (width + 7) / 8
  val blockRows: Int = (height + 7) / 8

object Dimensions:
  def apply(width: Int, height: Int): Dimensions =
    require(width > 0 && width <= 65535, "JPEG width must be in 1..65535")
    require(height > 0 && height <= 65535, "JPEG height must be in 1..65535")
    new Dimensions(width, height)

/** An 8-bit grayscale raster in row-major order. */
final case class GrayImage private (dimensions: Dimensions, samples: IArray[Byte]):
  def width: Int = dimensions.width
  def height: Int = dimensions.height
  def apply(x: Int, y: Int): Int = samples(y * width + x) & 0xff

  /** Splits the raster into blocks, extending edge samples as required by
    * [[https://www.w3.org/Graphics/JPEG/itu-t81.pdf T.81 A.2 and F.1.1.1]].
    */
  def blocks: IndexedSeq[Block] =
    for
      by <- 0 until dimensions.blockRows
      bx <- 0 until dimensions.blockColumns
    yield Block(for
      y <- 0 until 8
      x <- 0 until 8
    yield apply(math.min(bx * 8 + x, width - 1), math.min(by * 8 + y, height - 1)))

object GrayImage:
  def apply(width: Int, height: Int, samples: IterableOnce[Int]): GrayImage =
    val dimensions = Dimensions(width, height)
    val values = samples.iterator.toArray
    require(values.length == width * height, "sample count must equal width × height")
    require(values.forall(v => v >= 0 && v <= 255), "samples must be 8-bit values")
    new GrayImage(dimensions, IArray.from(values.map(_.toByte)))

package jpeg

import java.io.ByteArrayOutputStream

/** Baseline sequential grayscale JPEG encoder.
  *
  * The emitted marker sequence conforms to
  * [[https://www.w3.org/Graphics/JPEG/itu-t81.pdf T.81 Annex B]], while scan coding follows Annex
  * F.1. The API deliberately accepts only `GrayImage`, so unsupported color transforms cannot be
  * selected accidentally.
  */
object JpegEncoder:
  def encode(image: GrayImage, options: EncoderOptions = EncoderOptions()): IArray[Byte] =
    val quantizer          = options.quality.scale(Quantization.Luminance)
    val scan               = PreparedScan(
      image.blocks.map(block => IndexedSeq(0 -> block)),
      quantizer,
      options.restartInterval
    )
    val (dcTable, acTable) = entropyTables(scan, options.optimizeHuffmanTables)
    val out                = ByteArrayOutputStream()
    marker(out, 0xd8)
    segment(out, 0xe0, Seq(0x4a, 0x46, 0x49, 0x46, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0))
    segment(out, 0xdb, 0 +: Quantization.zigZag(quantizer))
    segment(out, 0xc0, Seq(8) ++ u16(image.height) ++ u16(image.width) ++ Seq(1, 1, 0x11, 0))
    dht(out, tableClass = 0, id = 0, dcTable)
    dht(out, tableClass = 1, id = 0, acTable)
    if options.restartInterval > 0 then segment(out, 0xdd, u16(options.restartInterval))
    segment(out, 0xda, Seq(1, 1, 0, 0, 63, 0))
    out.write(scan.write(dcTable, acTable, options.restartInterval).asInstanceOf[Array[Byte]])
    marker(out, 0xd9)
    IArray.from(out.toByteArray)

  /** Encodes grayscale pixels and reinserts ordered application metadata. */
  def encode(image: GrayImage, options: EncoderOptions, metadata: JpegMetadata): IArray[Byte] =
    JpegMetadata.embed(encode(image, options), metadata)

  /** Encodes an RGB image as a three-component JFIF stream. */
  def encode(image: RgbImage, options: EncoderOptions): IArray[Byte] =
    val quantizer          = options.quality.scale(Quantization.Luminance)
    val converted          = IndexedSeq
      .tabulate(image.height, image.width)((y, x) => YCbCr.fromRgb(image(x, y)))
    val components         = options.chromaSubsampling match
      case ChromaSubsampling.FullResolution => IndexedSeq(
          component(image.width, image.height, converted)(_.y),
          component(image.width, image.height, converted)(_.cb),
          component(image.width, image.height, converted)(_.cr)
        )
      case ChromaSubsampling.HalfHorizontal =>
        val paddedWidth = (image.width + 15) / 16 * 16
        IndexedSeq(
          component(paddedWidth, image.height, converted)(_.y),
          downsample(converted, vertical = false)(_.cb),
          downsample(converted, vertical = false)(_.cr)
        )
      case ChromaSubsampling.HalfBothAxes   =>
        val paddedWidth  = (image.width + 15) / 16 * 16
        val paddedHeight = (image.height + 15) / 16 * 16
        IndexedSeq(
          component(paddedWidth, paddedHeight, converted)(_.y),
          downsample(converted, vertical = true)(_.cb),
          downsample(converted, vertical = true)(_.cr)
        )
    val ySampling          = options.chromaSubsampling match
      case ChromaSubsampling.FullResolution => 0x11
      case ChromaSubsampling.HalfHorizontal => 0x21
      case ChromaSubsampling.HalfBothAxes   => 0x22
    val scan               = PreparedScan(
      colorMcus(components, components.map(_.blocks), options.chromaSubsampling),
      quantizer,
      options.restartInterval
    )
    val (dcTable, acTable) = entropyTables(scan, options.optimizeHuffmanTables)
    val out                = ByteArrayOutputStream()
    marker(out, 0xd8)
    segment(out, 0xe0, Seq(0x4a, 0x46, 0x49, 0x46, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0))
    segment(out, 0xdb, 0 +: Quantization.zigZag(quantizer))
    segment(
      out,
      0xc0,
      Seq(8) ++ u16(image.height) ++ u16(image.width) ++
        Seq(3, 1, ySampling, 0, 2, 0x11, 0, 3, 0x11, 0)
    )
    dht(out, 0, 0, dcTable)
    dht(out, 1, 0, acTable)
    if options.restartInterval > 0 then segment(out, 0xdd, u16(options.restartInterval))
    segment(out, 0xda, Seq(3, 1, 0, 2, 0, 3, 0, 0, 63, 0))
    out.write(scan.write(dcTable, acTable, options.restartInterval).asInstanceOf[Array[Byte]])
    marker(out, 0xd9)
    IArray.from(out.toByteArray)

  def encode(image: RgbImage): IArray[Byte] = encode(image, EncoderOptions())

  /** Encodes color pixels and reinserts ordered application metadata. */
  def encode(image: RgbImage, options: EncoderOptions, metadata: JpegMetadata): IArray[Byte] =
    JpegMetadata.embed(encode(image, options), metadata)

  private def entropyTables(scan: PreparedScan, optimize: Boolean): (HuffmanTable, HuffmanTable) =
    if optimize then
      HuffmanOptimizer.optimize(scan.dcFrequencies) -> HuffmanOptimizer.optimize(scan.acFrequencies)
    else StandardTables.LuminanceDc                 -> StandardTables.LuminanceAc

  /** Groups component blocks in the exact interleaved order required for each MCU. */
  private def colorMcus(
      components: IndexedSeq[GrayImage],
      blocks: IndexedSeq[IndexedSeq[Block]],
      subsampling: ChromaSubsampling
  ): IndexedSeq[IndexedSeq[(Int, Block)]] = subsampling match
    case ChromaSubsampling.FullResolution => blocks.head.indices
        .map(index => components.indices.map(component => component -> blocks(component)(index)))
    case mode                             =>
      val lumaBlocksHigh = if mode == ChromaSubsampling.HalfBothAxes then 2 else 1
      val lumaColumns    = components.head.dimensions.blockColumns
      for
        mcuY <- 0 until components(1).dimensions.blockRows
        mcuX <- 0 until components(1).dimensions.blockColumns
      yield
        val luma   =
          for localY <- 0 until lumaBlocksHigh; localX <- 0 until 2 yield
            val index = (mcuY * lumaBlocksHigh + localY) * lumaColumns + mcuX * 2 + localX
            0 -> blocks(0)(index)
        val chroma = (1 to 2).map: component =>
          val index = mcuY * components(component).dimensions.blockColumns + mcuX
          component -> blocks(component)(index)
        luma ++ chroma

  private def component(width: Int, height: Int, source: IndexedSeq[IndexedSeq[YCbCr]])(
      select: YCbCr => Int
  ): GrayImage = GrayImage(
    width,
    height,
    for y <- 0 until height; x <- 0 until width
    yield select(source(math.min(y, source.size - 1))(math.min(x, source.head.size - 1)))
  )

  /** Box-filters chroma horizontally and optionally vertically, extending odd edges. */
  private def downsample(source: IndexedSeq[IndexedSeq[YCbCr]], vertical: Boolean)(
      select: YCbCr => Int
  ): GrayImage =
    val sourceHeight = source.size
    val sourceWidth  = source.head.size
    val width        = (sourceWidth + 1) / 2
    val height       = if vertical then (sourceHeight + 1) / 2 else sourceHeight
    GrayImage(
      width,
      height,
      for
        y <- 0 until height
        x <- 0 until width
      yield
        val values =
          for
            dy <- 0 until (if vertical then 2 else 1)
            dx <- 0 until 2
          yield select(
            source(math.min(y * 2 + dy, sourceHeight - 1))(math.min(x * 2 + dx, sourceWidth - 1))
          )
        (values.sum + values.size / 2) / values.size
    )

  private def dht(out: ByteArrayOutputStream, tableClass: Int, id: Int, table: HuffmanTable): Unit =
    segment(out, 0xc4, Seq((tableClass << 4) | id) ++ table.counts ++ table.symbols)

  private def marker(out: ByteArrayOutputStream, code: Int): Unit =
    out.write(0xff); out.write(code)

  private def segment(out: ByteArrayOutputStream, code: Int, payload: Seq[Int]): Unit =
    marker(out, code)
    u16(payload.size + 2).foreach(out.write)
    payload.foreach(out.write)

  private def u16(value: Int): Seq[Int] = Seq(value >>> 8, value & 0xff)

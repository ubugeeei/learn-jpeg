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
    val quantizer = options.quality.scale(Quantization.Luminance)
    val out       = ByteArrayOutputStream()
    marker(out, 0xd8)
    segment(out, 0xe0, Seq(0x4a, 0x46, 0x49, 0x46, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0))
    segment(out, 0xdb, 0 +: Quantization.zigZag(quantizer))
    segment(out, 0xc0, Seq(8) ++ u16(image.height) ++ u16(image.width) ++ Seq(1, 1, 0x11, 0))
    dht(out, tableClass = 0, id = 0, StandardTables.LuminanceDc)
    dht(out, tableClass = 1, id = 0, StandardTables.LuminanceAc)
    segment(out, 0xda, Seq(1, 1, 0, 0, 63, 0))
    out.write(entropy(image, quantizer).asInstanceOf[Array[Byte]])
    marker(out, 0xd9)
    IArray.from(out.toByteArray)

  /** Encodes an RGB image as a three-component JFIF stream. */
  def encode(image: RgbImage, options: EncoderOptions): IArray[Byte] =
    val quantizer  = options.quality.scale(Quantization.Luminance)
    val converted  = IndexedSeq
      .tabulate(image.height, image.width)((y, x) => YCbCr.fromRgb(image(x, y)))
    val components = options.chromaSubsampling match
      case ChromaSubsampling.FullResolution => IndexedSeq(
          component(image.width, image.height, converted)(_.y),
          component(image.width, image.height, converted)(_.cb),
          component(image.width, image.height, converted)(_.cr)
        )
      case ChromaSubsampling.HalfBothAxes   =>
        val paddedWidth  = (image.width + 15) / 16 * 16
        val paddedHeight = (image.height + 15) / 16 * 16
        IndexedSeq(
          component(paddedWidth, paddedHeight, converted)(_.y),
          downsample(converted)(_.cb),
          downsample(converted)(_.cr)
        )
    val ySampling  = options.chromaSubsampling match
      case ChromaSubsampling.FullResolution => 0x11
      case ChromaSubsampling.HalfBothAxes   => 0x22
    val out        = ByteArrayOutputStream()
    marker(out, 0xd8)
    segment(out, 0xe0, Seq(0x4a, 0x46, 0x49, 0x46, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0))
    segment(out, 0xdb, 0 +: Quantization.zigZag(quantizer))
    segment(
      out,
      0xc0,
      Seq(8) ++ u16(image.height) ++ u16(image.width) ++
        Seq(3, 1, ySampling, 0, 2, 0x11, 0, 3, 0x11, 0)
    )
    dht(out, 0, 0, StandardTables.LuminanceDc)
    dht(out, 1, 0, StandardTables.LuminanceAc)
    segment(out, 0xda, Seq(3, 1, 0, 2, 0, 3, 0, 0, 63, 0))
    out.write(
      colorEntropy(components, quantizer, options.chromaSubsampling).asInstanceOf[Array[Byte]]
    )
    marker(out, 0xd9)
    IArray.from(out.toByteArray)

  def encode(image: RgbImage): IArray[Byte] = encode(image, EncoderOptions())

  private def entropy(image: GrayImage, quantizer: Block): IArray[Byte] =
    val bits       = BitWriter()
    var previousDc = 0
    image.blocks.foreach: samples =>
      val coefficients = Quantization.quantize(Dct.forward(samples), quantizer)
      val ordered      = Quantization.zigZag(coefficients)
      val difference   = ordered.head - previousDc
      previousDc = ordered.head
      val dcCategory   = Magnitude.category(difference)
      StandardTables.LuminanceDc.write(dcCategory, bits)
      bits.write(Magnitude.bits(difference, dcCategory), dcCategory)

      var run = 0
      ordered.tail.foreach: value =>
        if value == 0 then run += 1
        else
          while run >= 16 do
            StandardTables.LuminanceAc.write(0xf0, bits)
            run -= 16
          val category = Magnitude.category(value)
          StandardTables.LuminanceAc.write((run << 4) | category, bits)
          bits.write(Magnitude.bits(value, category), category)
          run = 0
      if run > 0 then StandardTables.LuminanceAc.write(0x00, bits)
    bits.result()

  private def colorEntropy(
      components: IndexedSeq[GrayImage],
      quantizer: Block,
      subsampling: ChromaSubsampling
  ): IArray[Byte] =
    val bits       = BitWriter()
    val previousDc = Array.fill(components.size)(0)
    val blocks     = components.map(_.blocks)
    subsampling match
      case ChromaSubsampling.FullResolution =>
        for blockIndex <- blocks.head.indices; component <- components.indices do
          previousDc(component) =
            writeBlock(blocks(component)(blockIndex), quantizer, previousDc(component), bits)
      case ChromaSubsampling.HalfBothAxes   =>
        val lumaColumns = components.head.dimensions.blockColumns
        for
          mcuY <- 0 until components(1).dimensions.blockRows
          mcuX <- 0 until components(1).dimensions.blockColumns
        do
          for localY <- 0 until 2; localX <- 0 until 2 do
            val index = (mcuY * 2 + localY) * lumaColumns + mcuX * 2 + localX
            previousDc(0) = writeBlock(blocks(0)(index), quantizer, previousDc(0), bits)
          for component <- 1 to 2 do
            val index = mcuY * components(component).dimensions.blockColumns + mcuX
            previousDc(component) =
              writeBlock(blocks(component)(index), quantizer, previousDc(component), bits)
    bits.result()

  private def component(width: Int, height: Int, source: IndexedSeq[IndexedSeq[YCbCr]])(
      select: YCbCr => Int
  ): GrayImage = GrayImage(
    width,
    height,
    for y <- 0 until height; x <- 0 until width
    yield select(source(math.min(y, source.size - 1))(math.min(x, source.head.size - 1)))
  )

  /** Box-filters chroma to half width and height, extending odd edges before averaging. */
  private def downsample(source: IndexedSeq[IndexedSeq[YCbCr]])(select: YCbCr => Int): GrayImage =
    val sourceHeight = source.size
    val sourceWidth  = source.head.size
    val width        = (sourceWidth + 1) / 2
    val height       = (sourceHeight + 1) / 2
    GrayImage(
      width,
      height,
      for
        y <- 0 until height
        x <- 0 until width
      yield
        val values =
          for
            dy <- 0 until 2
            dx <- 0 until 2
          yield select(
            source(math.min(y * 2 + dy, sourceHeight - 1))(math.min(x * 2 + dx, sourceWidth - 1))
          )
        (values.sum + 2) / 4
    )

  private def writeBlock(samples: Block, quantizer: Block, previousDc: Int, bits: BitWriter): Int =
    val ordered    = Quantization.zigZag(Quantization.quantize(Dct.forward(samples), quantizer))
    val difference = ordered.head - previousDc
    val dcCategory = Magnitude.category(difference)
    StandardTables.LuminanceDc.write(dcCategory, bits)
    bits.write(Magnitude.bits(difference, dcCategory), dcCategory)
    var run        = 0
    ordered.tail.foreach: value =>
      if value == 0 then run += 1
      else
        while run >= 16 do
          StandardTables.LuminanceAc.write(0xf0, bits)
          run -= 16
        val category = Magnitude.category(value)
        StandardTables.LuminanceAc.write((run << 4) | category, bits)
        bits.write(Magnitude.bits(value, category), category)
        run = 0
    if run > 0 then StandardTables.LuminanceAc.write(0, bits)
    ordered.head

  private def dht(out: ByteArrayOutputStream, tableClass: Int, id: Int, table: HuffmanTable): Unit =
    segment(out, 0xc4, Seq((tableClass << 4) | id) ++ table.counts ++ table.symbols)

  private def marker(out: ByteArrayOutputStream, code: Int): Unit =
    out.write(0xff); out.write(code)

  private def segment(out: ByteArrayOutputStream, code: Int, payload: Seq[Int]): Unit =
    marker(out, code)
    u16(payload.size + 2).foreach(out.write)
    payload.foreach(out.write)

  private def u16(value: Int): Seq[Int] = Seq(value >>> 8, value & 0xff)

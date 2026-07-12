package jpeg

import scala.collection.mutable

/** Images produced by the baseline decoder. */
enum DecodedImage:
  /** A one-component decoded raster. */
  case Grayscale(image: GrayImage)

  /** A three-component JFIF YCbCr raster converted to RGB. */
  case Color(image: RgbImage)

/** Defensive 8-bit baseline sequential JPEG decoder.
  *
  * It supports one-component grayscale plus three-component YCbCr with arbitrary legal integral
  * sampling factors, including the ubiquitous 4:4:4, 4:2:2, and 4:2:0 layouts. Unknown APP/COM
  * segments are skipped by their declared length.
  */
object JpegDecoder:
  /** Decodes a JPEG only when its frame is grayscale. */
  def decode(input: IArray[Byte]): GrayImage = decodeImage(input) match
    case DecodedImage.Grayscale(image) => image
    case DecodedImage.Color(_)         =>
      throw JpegError("expected grayscale JPEG but found three components")

  /** Decodes color or promotes grayscale to equal RGB channels. */
  def decodeRgb(input: IArray[Byte]): RgbImage = decodeImage(input) match
    case DecodedImage.Color(image)     => image
    case DecodedImage.Grayscale(image) => RgbImage(
        image.width,
        image.height,
        for y <- 0 until image.height; x <- 0 until image.width yield
          val value = image(x, y)
          Rgb(value, value, value)
      )

  /** Decodes while preserving whether the source frame was grayscale or color. */
  def decodeImage(input: IArray[Byte]): DecodedImage =
    val cursor               = Cursor(input.asInstanceOf[Array[Byte]])
    cursor.expectMarker(0xd8)
    var frame: Option[Frame] = None
    val quantization         = mutable.Map.empty[Int, Block]
    val huffman              = mutable.Map.empty[(Int, Int), HuffmanTable]
    while true do
      cursor.nextMarker() match
        case 0xd9                                 => throw JpegError("EOI occurred before a scan")
        case 0xdb                                 => parseDqt(cursor.segment(), quantization)
        case 0xc4                                 => parseDht(cursor.segment(), huffman)
        case 0xc0                                 => frame = Some(parseFrame(cursor.segment()))
        case code @ (0xc1 | 0xc2 | 0xc3 | 0xc5 | 0xc6 | 0xc7 | 0xc9 | 0xca | 0xcb | 0xcd | 0xce |
            0xcf) =>
          throw JpegError(f"unsupported JPEG frame marker FF$code%02X; only SOF0 is supported")
        case 0xda                                 =>
          val current   = frame.getOrElse(throw JpegError("SOS before SOF0"))
          val selectors = parseScan(cursor.segment(), current)
          return decodeScan(
            current,
            selectors,
            cursor.entropyUntilEoi(),
            quantization.toMap,
            huffman.toMap
          )
        case code if code >= 0xd0 && code <= 0xd7 =>
          throw JpegError("restart marker outside entropy data")
        case _                                    => cursor.skipSegment()
    throw JpegError("unreachable")

  private def parseFrame(data: Cursor): Frame =
    if data.u8() != 8 then throw JpegError("only 8-bit sample precision is supported")
    val height     = data.u16()
    val width      = data.u16()
    val dimensions = Dimensions(width, height)
    val count      = data.u8()
    if count != 1 && count != 3 then
      throw JpegError("only grayscale and three-component JPEG are supported")
    val components = IndexedSeq.fill(count):
      val id        = data.u8()
      val sampling  = data.u8()
      val component = Component(id, sampling >>> 4, sampling & 0x0f, data.u8())
      if component.horizontal < 1 || component.horizontal > 4 || component.vertical < 1 ||
        component.vertical > 4
      then throw JpegError("sampling factors must be in 1..4")
      component
    if components.map(_.id).distinct.size != count then
      throw JpegError("duplicate frame component id")
    data.expectEnd()
    Frame(dimensions, components)

  private def parseDqt(data: Cursor, tables: mutable.Map[Int, Block]): Unit =
    while data.remaining > 0 do
      val info = data.u8()
      if (info >>> 4) != 0 then throw JpegError("16-bit quantization tables are unsupported")
      tables(info & 0x0f) = Quantization.natural(Seq.fill(64)(data.u8()))

  private def parseDht(data: Cursor, tables: mutable.Map[(Int, Int), HuffmanTable]): Unit =
    while data.remaining > 0 do
      val info       = data.u8()
      val tableClass = info >>> 4
      if tableClass > 1 then throw JpegError("invalid Huffman table class")
      val counts     = Seq.fill(16)(data.u8())
      tables((tableClass, info & 0x0f)) = HuffmanTable(counts, Seq.fill(counts.sum)(data.u8()))

  private def parseScan(data: Cursor, frame: Frame): Map[Int, Tables] =
    val count     = data.u8()
    if count != frame.components.size then
      throw JpegError("only single interleaved baseline scans are supported")
    val selectors = (0 until count).map: _ =>
      val id     = data.u8()
      val tables = data.u8()
      id -> Tables(tables >>> 4, tables & 0x0f)
    .toMap
    if selectors.size != count || selectors.keys.exists(id => !frame.components.exists(_.id == id))
    then throw JpegError("scan contains unknown or duplicate component")
    if data.u8() != 0 || data.u8() != 63 || data.u8() != 0 then
      throw JpegError("scan is not baseline sequential")
    data.expectEnd()
    selectors

  private def decodeScan(
      frame: Frame,
      selectors: Map[Int, Tables],
      entropy: IArray[Byte],
      quantizers: Map[Int, Block],
      huffman: Map[(Int, Int), HuffmanTable]
  ): DecodedImage =
    val maxH       = frame.components.map(_.horizontal).max
    val maxV       = frame.components.map(_.vertical).max
    val mcuColumns = (frame.dimensions.width + maxH * 8 - 1) / (maxH * 8)
    val mcuRows    = (frame.dimensions.height + maxV * 8 - 1) / (maxV * 8)
    val planes     = frame.components
      .map(c => c.id -> Plane(mcuColumns * c.horizontal * 8, mcuRows * c.vertical * 8)).toMap
    val predictors = mutable.Map.from(frame.components.map(_.id -> 0))
    val bits       = BitReader(entropy)
    for mcuY <- 0 until mcuRows; mcuX <- 0 until mcuColumns; component <- frame.components do
      val selected  = selectors(component.id)
      val dc        = huffman
        .getOrElse((0, selected.dc), throw JpegError(s"missing DC Huffman table ${selected.dc}"))
      val ac        = huffman
        .getOrElse((1, selected.ac), throw JpegError(s"missing AC Huffman table ${selected.ac}"))
      val quantizer = quantizers.getOrElse(
        component.quantizer,
        throw JpegError(s"missing quantization table ${component.quantizer}")
      )
      for localY <- 0 until component.vertical; localX <- 0 until component.horizontal do
        val (block, predictor) = readBlock(bits, predictors(component.id), dc, ac, quantizer)
        predictors(component.id) = predictor
        planes(component.id).write(
          (mcuX * component.horizontal + localX) * 8,
          (mcuY * component.vertical + localY) * 8,
          block
        )
    if frame.components.size == 1 then
      val plane = planes(frame.components.head.id)
      DecodedImage.Grayscale(GrayImage(
        frame.dimensions.width,
        frame.dimensions.height,
        for y <- 0 until frame.dimensions.height; x <- 0 until frame.dimensions.width
        yield plane(x, y)
      ))
    else
      val byId                                              = frame.components.map(c => c.id -> c).toMap
      val yComponent                                        = byId.getOrElse(1, throw JpegError("YCbCr frame requires component 1"))
      val cbComponent                                       = byId.getOrElse(2, throw JpegError("YCbCr frame requires component 2"))
      val crComponent                                       = byId.getOrElse(3, throw JpegError("YCbCr frame requires component 3"))
      def sample(component: Component, x: Int, y: Int): Int =
        planes(component.id)(x * component.horizontal / maxH, y * component.vertical / maxV)
      DecodedImage.Color(RgbImage(
        frame.dimensions.width,
        frame.dimensions.height,
        for y <- 0 until frame.dimensions.height; x <- 0 until frame.dimensions.width
        yield YCbCr(sample(yComponent, x, y), sample(cbComponent, x, y), sample(crComponent, x, y))
          .toRgb
      ))

  private def readBlock(
      input: BitReader,
      previousDc: Int,
      dc: HuffmanTable,
      ac: HuffmanTable,
      quantizer: Block
  ): (Block, Int) =
    val ordered    = Array.fill(64)(0)
    val dcCategory = dc.read(input)
    ordered(0) = previousDc + Magnitude.value(input.read(dcCategory), dcCategory)
    var index      = 1
    while index < 64 do
      val symbol = ac.read(input)
      if symbol == 0 then index = 64
      else if symbol == 0xf0 then index += 16
      else
        index += symbol >>> 4
        val category = symbol & 0x0f
        if category == 0 || index >= 64 then throw JpegError("invalid AC run/category")
        ordered(index) = Magnitude.value(input.read(category), category)
        index += 1
    if index > 64 then throw JpegError("AC run exceeds block")
    Dct.inverse(Quantization.dequantize(Quantization.natural(ordered.toIndexedSeq), quantizer)) ->
      ordered(0)

/** SOF0 metadata for one component, including its MCU sampling geometry. */
final private case class Component(id: Int, horizontal: Int, vertical: Int, quantizer: Int)

/** DC and AC Huffman table selectors carried by SOS. */
final private case class Tables(dc: Int, ac: Int)

/** Validated frame dimensions and ordered components. */
final private case class Frame(dimensions: Dimensions, components: IndexedSeq[Component])

/** Mutable reconstruction surface local to one decode operation. */
final private class Plane(val width: Int, val height: Int):
  private val values                                 = Array.fill(width * height)(0)
  def apply(x: Int, y: Int): Int                     = values(y * width + x)
  def write(left: Int, top: Int, block: Block): Unit =
    for y <- 0 until 8; x <- 0 until 8 do values((top + y) * width + left + x) = block(y, x)

/** A bounded big-endian reader. Child cursors cannot escape marker segment limits. */
final private class Cursor private (
    bytes: Array[Byte],
    private var position: Int,
    private val limit: Int
):
  def remaining: Int                    = limit - position
  def u8(): Int                         =
    if position >= limit then throw JpegError("unexpected end of JPEG data")
    val result = bytes(position) & 0xff
    position += 1
    result
  def u16(): Int                        = (u8() << 8) | u8()
  def expectEnd(): Unit                 =
    if remaining != 0 then throw JpegError("unexpected bytes at end of marker segment")
  def expectMarker(expected: Int): Unit =
    if u8() != 0xff || u8() != expected then throw JpegError(f"expected marker FF$expected%02X")
  def nextMarker(): Int                 =
    if u8() != 0xff then throw JpegError("expected JPEG marker")
    var code = u8()
    while code == 0xff do code = u8()
    if code == 0 then throw JpegError("stuffed byte outside entropy data")
    code
  def segment(): Cursor                 =
    val length = u16()
    if length < 2 || length - 2 > remaining then throw JpegError("invalid marker segment length")
    val child  = Cursor(bytes, position, position + length - 2)
    position += length - 2
    child
  def skipSegment(): Unit               = segment()
  def entropyUntilEoi(): IArray[Byte]   =
    val result = mutable.ArrayBuffer.empty[Byte]
    while remaining > 0 do
      val value = u8()
      if value != 0xff then result += value.toByte
      else
        val next = u8()
        if next == 0 then result += 0xff.toByte
        else if next == 0xd9 then return IArray.from(result)
        else throw JpegError(f"unsupported marker FF$next%02X inside entropy data")
    throw JpegError("entropy data has no EOI marker")

private object Cursor:
  def apply(bytes: Array[Byte]): Cursor                         = Cursor(bytes, 0, bytes.length)
  def apply(bytes: Array[Byte], start: Int, limit: Int): Cursor = new Cursor(bytes, start, limit)

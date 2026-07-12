package jpeg

import scala.collection.mutable

/** Defensive decoder for 8-bit, one-component baseline sequential JPEG.
  *
  * Unknown application/comment segments are skipped as prescribed by marker
  * lengths. Unsupported frame processes and component layouts fail explicitly.
  */
object JpegDecoder:
  def decode(input: IArray[Byte]): GrayImage =
    val bytes = input.asInstanceOf[Array[Byte]]
    val cursor = Cursor(bytes)
    cursor.expectMarker(0xd8)
    var dimensions: Option[Dimensions] = None
    val quantization = mutable.Map.empty[Int, Block]
    val huffman = mutable.Map.empty[(Int, Int), HuffmanTable]
    var decoded: Option[GrayImage] = None
    while decoded.isEmpty do
      cursor.nextMarker() match
        case 0xd9 => throw JpegError("EOI occurred before a scan")
        case 0xdb => parseDqt(cursor.segment(), quantization)
        case 0xc4 => parseDht(cursor.segment(), huffman)
        case 0xc0 => dimensions = Some(parseFrame(cursor.segment()))
        case code @ (0xc1 | 0xc2 | 0xc3 | 0xc5 | 0xc6 | 0xc7 | 0xc9 | 0xca | 0xcb | 0xcd | 0xce | 0xcf) =>
          throw JpegError(f"unsupported JPEG frame marker FF$code%02X; only SOF0 is supported")
        case 0xda =>
          val scan = cursor.segment()
          validateScan(scan)
          val entropy = cursor.entropyUntilEoi()
          decoded = Some(decodeScan(
            dimensions.getOrElse(throw JpegError("SOS before SOF0")), entropy,
            quantization.getOrElse(0, throw JpegError("missing quantization table 0")),
            huffman.getOrElse((0, 0), throw JpegError("missing DC Huffman table 0")),
            huffman.getOrElse((1, 0), throw JpegError("missing AC Huffman table 0"))))
        case code if code >= 0xd0 && code <= 0xd7 => throw JpegError("restart marker outside entropy data")
        case _ => cursor.skipSegment()
    decoded.get

  private def parseFrame(data: Cursor): Dimensions =
    if data.u8() != 8 then throw JpegError("only 8-bit sample precision is supported")
    val height = data.u16()
    val width = data.u16()
    if data.u8() != 1 then throw JpegError("only one-component grayscale JPEG is supported")
    val id = data.u8()
    val sampling = data.u8()
    val table = data.u8()
    if id != 1 || sampling != 0x11 || table != 0 then
      throw JpegError("grayscale component must use id 1, 1×1 sampling, and quantization table 0")
    data.expectEnd()
    Dimensions(width, height)

  private def parseDqt(data: Cursor, tables: mutable.Map[Int, Block]): Unit =
    while data.remaining > 0 do
      val info = data.u8()
      if (info >>> 4) != 0 then throw JpegError("16-bit quantization tables are unsupported")
      val id = info & 0x0f
      tables(id) = Quantization.natural(Seq.fill(64)(data.u8()))

  private def parseDht(data: Cursor, tables: mutable.Map[(Int, Int), HuffmanTable]): Unit =
    while data.remaining > 0 do
      val info = data.u8()
      val tableClass = info >>> 4
      val id = info & 0x0f
      if tableClass > 1 then throw JpegError("invalid Huffman table class")
      val counts = Seq.fill(16)(data.u8())
      tables((tableClass, id)) = HuffmanTable(counts, Seq.fill(counts.sum)(data.u8()))

  private def validateScan(data: Cursor): Unit =
    if data.u8() != 1 || data.u8() != 1 || data.u8() != 0 then
      throw JpegError("scan must select grayscale component 1 and Huffman tables 0")
    if data.u8() != 0 || data.u8() != 63 || data.u8() != 0 then
      throw JpegError("scan is not baseline sequential")
    data.expectEnd()

  private def decodeScan(dimensions: Dimensions, bytes: IArray[Byte], quantizer: Block,
      dcTable: HuffmanTable, acTable: HuffmanTable): GrayImage =
    val input = BitReader(bytes)
    val samples = Array.fill(dimensions.width * dimensions.height)(0)
    var previousDc = 0
    for
      blockY <- 0 until dimensions.blockRows
      blockX <- 0 until dimensions.blockColumns
    do
      val ordered = Array.fill(64)(0)
      val dcCategory = dcTable.read(input)
      val difference = Magnitude.value(input.read(dcCategory), dcCategory)
      ordered(0) = previousDc + difference
      previousDc = ordered(0)
      var index = 1
      while index < 64 do
        val symbol = acTable.read(input)
        if symbol == 0 then index = 64
        else if symbol == 0xf0 then index += 16
        else
          index += symbol >>> 4
          val category = symbol & 0x0f
          if category == 0 || index >= 64 then throw JpegError("invalid AC run/category")
          ordered(index) = Magnitude.value(input.read(category), category)
          index += 1
      if index > 64 then throw JpegError("AC run exceeds block")
      val block = Dct.inverse(Quantization.dequantize(Quantization.natural(ordered.toIndexedSeq), quantizer))
      for
        y <- 0 until 8 if blockY * 8 + y < dimensions.height
        x <- 0 until 8 if blockX * 8 + x < dimensions.width
      do samples((blockY * 8 + y) * dimensions.width + blockX * 8 + x) = block(y, x)
    GrayImage(dimensions.width, dimensions.height, samples)

private final class Cursor private (bytes: Array[Byte], private var position: Int, private val limit: Int):
  def remaining: Int = limit - position
  def u8(): Int =
    if position >= limit then throw JpegError("unexpected end of JPEG data")
    val result = bytes(position) & 0xff
    position += 1
    result
  def u16(): Int = (u8() << 8) | u8()
  def expectEnd(): Unit = if remaining != 0 then throw JpegError("unexpected bytes at end of marker segment")
  def expectMarker(expected: Int): Unit =
    if u8() != 0xff || u8() != expected then throw JpegError(f"expected marker FF$expected%02X")
  def nextMarker(): Int =
    if u8() != 0xff then throw JpegError("expected JPEG marker")
    var code = u8()
    while code == 0xff do code = u8()
    if code == 0 then throw JpegError("stuffed byte outside entropy data")
    code
  def segment(): Cursor =
    val length = u16()
    if length < 2 || length - 2 > remaining then throw JpegError("invalid marker segment length")
    val child = Cursor(bytes, position, position + length - 2)
    position += length - 2
    child
  def skipSegment(): Unit = segment()
  def entropyUntilEoi(): IArray[Byte] =
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
  def apply(bytes: Array[Byte]): Cursor = Cursor(bytes, 0, bytes.length)
  def apply(bytes: Array[Byte], start: Int, limit: Int): Cursor = new Cursor(bytes, start, limit)

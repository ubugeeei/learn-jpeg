package jpeg

import java.nio.charset.StandardCharsets
import scala.collection.mutable

/** Raw payload of one APP0–APP15 segment. */
final case class ApplicationSegment(marker: Int, payload: IArray[Byte]):
  require(marker >= 0xe0 && marker <= 0xef, "application marker must be APP0..APP15")

/** Density metadata from a JFIF APP0 segment. */
final case class JfifInfo(major: Int, minor: Int, densityUnit: Int, xDensity: Int, yDensity: Int)

/** One metadata segment in its original file order. */
enum MetadataSegment:
  case Application(value: ApplicationSegment)
  case Comment(payload: IArray[Byte])

/** Metadata available without entropy-decoding image pixels. */
final case class JpegMetadata(
    orderedSegments: IndexedSeq[MetadataSegment],
    applications: IndexedSeq[ApplicationSegment],
    comments: IndexedSeq[String],
    jfif: Option[JfifInfo],
    exifOrientation: Option[ImageOrientation],
    iccProfile: Option[IArray[Byte]]
)

/** Marker-only metadata inspector for sequential or progressive JPEG streams. */
object JpegMetadata:
  def inspect(input: IArray[Byte]): JpegMetadata =
    val cursor       = MetadataCursor(input.asInstanceOf[Array[Byte]])
    cursor.expect(0xff)
    cursor.expect(0xd8)
    val applications = mutable.ArrayBuffer.empty[ApplicationSegment]
    val comments     = mutable.ArrayBuffer.empty[String]
    val ordered      = mutable.ArrayBuffer.empty[MetadataSegment]
    var reachedScan  = false
    while !reachedScan && cursor.remaining > 0 do
      val marker = cursor.marker()
      marker match
        case 0xda                                                 =>
          cursor.segment()
          reachedScan = true
        case 0xd9                                                 => reachedScan = true
        case code if code >= 0xe0 && code <= 0xef                 =>
          val segment = ApplicationSegment(code, cursor.segment())
          applications += segment
          ordered += MetadataSegment.Application(segment)
        case 0xfe                                                 =>
          val payload = cursor.segment()
          val bytes   = payload.asInstanceOf[Array[Byte]]
          comments += String(bytes, StandardCharsets.ISO_8859_1)
          ordered += MetadataSegment.Comment(payload)
        case code if code == 0xd8 || code >= 0xd0 && code <= 0xd7 => ()
        case _                                                    => cursor.segment()
    if !reachedScan then throw JpegError("JPEG metadata has no SOS or EOI marker")
    val segments     = applications.toIndexedSeq
    JpegMetadata(
      ordered.toIndexedSeq,
      segments,
      comments.toIndexedSeq,
      segments.iterator.flatMap(parseJfif).nextOption(),
      segments.iterator.flatMap(parseExifOrientation).nextOption(),
      assembleIcc(segments)
    )

  /** Inserts metadata after SOI while avoiding a duplicate generated JFIF segment. */
  def embed(encoded: IArray[Byte], metadata: JpegMetadata): IArray[Byte] =
    val source    = encoded.asInstanceOf[Array[Byte]]
    if source.length < 4 || source(0) != 0xff.toByte || source(1) != 0xd8.toByte then
      throw JpegError("metadata can only be embedded in a JPEG stream")
    val insertion = jfifEnd(source).getOrElse(2)
    val output    = java.io.ByteArrayOutputStream(source.length)
    output.write(source, 0, insertion)
    metadata.orderedSegments.foreach:
      case MetadataSegment.Application(segment) if !isJfif(segment) =>
        writeSegment(output, segment.marker, segment.payload)
      case MetadataSegment.Application(_)                           => ()
      case MetadataSegment.Comment(payload)                         => writeSegment(output, 0xfe, payload)
    output.write(source, insertion, source.length - insertion)
    IArray.from(output.toByteArray)

  private def writeSegment(
      output: java.io.ByteArrayOutputStream,
      marker: Int,
      payload: IArray[Byte]
  ): Unit =
    if payload.length > 65533 then throw JpegError("metadata payload exceeds JPEG segment limit")
    output.write(0xff)
    output.write(marker)
    output.write((payload.length + 2) >>> 8)
    output.write((payload.length + 2) & 0xff)
    output.write(payload.asInstanceOf[Array[Byte]])

  private def isJfif(segment: ApplicationSegment): Boolean = segment.marker == 0xe0 &&
    startsWith(segment.payload.asInstanceOf[Array[Byte]], "JFIF\u0000")

  private def jfifEnd(source: Array[Byte]): Option[Int] =
    if source.length < 11 || source(2) != 0xff.toByte || source(3) != 0xe0.toByte then None
    else
      val length = (source(4) & 0xff) << 8 | (source(5) & 0xff)
      val end    = 4 + length
      if length >= 7 && end <= source.length && startsWith(source.drop(6), "JFIF\u0000") then
        Some(end)
      else None

  private def parseJfif(segment: ApplicationSegment): Option[JfifInfo] =
    val bytes = segment.payload.asInstanceOf[Array[Byte]]
    if segment.marker != 0xe0 || bytes.length < 12 || !startsWith(bytes, "JFIF\u0000") then None
    else
      Some(JfifInfo(
        bytes(5) & 0xff,
        bytes(6) & 0xff,
        bytes(7) & 0xff,
        unsigned16(bytes, 8, littleEndian = false),
        unsigned16(bytes, 10, littleEndian = false)
      ))

  private def parseExifOrientation(segment: ApplicationSegment): Option[ImageOrientation] =
    val bytes = segment.payload.asInstanceOf[Array[Byte]]
    if segment.marker != 0xe1 || bytes.length < 14 || !startsWith(bytes, "Exif\u0000\u0000") then
      None
    else
      val base         = 6
      val littleEndian =
        if bytes(base) == 'I'.toByte && bytes(base + 1) == 'I'.toByte then true
        else if bytes(base) == 'M'.toByte && bytes(base + 1) == 'M'.toByte then false
        else throw JpegError("invalid Exif byte order")
      if unsigned16(bytes, base + 2, littleEndian) != 42 then
        throw JpegError("invalid Exif TIFF tag")
      val ifd          = checkedOffset(bytes, base, unsigned32(bytes, base + 4, littleEndian))
      val count        = unsigned16Checked(bytes, ifd, littleEndian)
      (0 until count).iterator.flatMap: index =>
        val entry = ifd + 2 + index * 12
        requireRange(bytes, entry, 12)
        if unsigned16(bytes, entry, littleEndian) == 0x0112 &&
          unsigned16(bytes, entry + 2, littleEndian) == 3 &&
          unsigned32(bytes, entry + 4, littleEndian) == 1
        then
          val value = unsigned16(bytes, entry + 8, littleEndian)
          ImageOrientation.fromExif(value)
        else None
      .nextOption()

  private def assembleIcc(segments: IndexedSeq[ApplicationSegment]): Option[IArray[Byte]] =
    val chunks = segments.flatMap: segment =>
      val bytes = segment.payload.asInstanceOf[Array[Byte]]
      if segment.marker == 0xe2 && bytes.length >= 14 && startsWith(bytes, "ICC_PROFILE\u0000") then
        Some((bytes(12) & 0xff, bytes(13) & 0xff, bytes.drop(14)))
      else None
    if chunks.isEmpty then None
    else
      val totals = chunks.map(_._2).distinct
      if totals.size != 1 || totals.head == 0 then throw JpegError("inconsistent ICC chunk count")
      val total  = totals.head
      if chunks.size != total || chunks.map(_._1).sorted != (1 to total) then
        throw JpegError("missing or duplicate ICC profile chunk")
      Some(IArray.from(chunks.sortBy(_._1).iterator.flatMap(_._3).toArray))

  private def startsWith(bytes: Array[Byte], value: String): Boolean =
    val expected = value.getBytes(StandardCharsets.ISO_8859_1)
    bytes.length >= expected.length && bytes.indices.take(expected.length)
      .forall(i => bytes(i) == expected(i))

  private def checkedOffset(bytes: Array[Byte], base: Int, relative: Long): Int =
    val absolute = base.toLong + relative
    if absolute < base || absolute > Int.MaxValue || absolute >= bytes.length then
      throw JpegError("Exif offset is outside APP1 segment")
    absolute.toInt

  private def unsigned16Checked(bytes: Array[Byte], offset: Int, littleEndian: Boolean): Int =
    requireRange(bytes, offset, 2)
    unsigned16(bytes, offset, littleEndian)

  private def unsigned16(bytes: Array[Byte], offset: Int, littleEndian: Boolean): Int =
    val first  = bytes(offset) & 0xff
    val second = bytes(offset + 1) & 0xff
    if littleEndian then first | second << 8 else first << 8 | second

  private def unsigned32(bytes: Array[Byte], offset: Int, littleEndian: Boolean): Long =
    requireRange(bytes, offset, 4)
    val values = (0 until 4).map(index => bytes(offset + index).toLong & 0xff)
    if littleEndian then values.zipWithIndex.map((value, shift) => value << (shift * 8)).sum
    else values.zipWithIndex.map((value, index) => value << ((3 - index) * 8)).sum

  private def requireRange(bytes: Array[Byte], offset: Int, size: Int): Unit =
    if offset < 0 || size < 0 || offset.toLong + size > bytes.length then
      throw JpegError("Exif field exceeds APP1 segment")

final private class MetadataCursor private (bytes: Array[Byte], private var position: Int):
  def remaining: Int           = bytes.length - position
  def expect(value: Int): Unit = if u8() != value then throw JpegError(f"expected byte $value%02X")
  def marker(): Int            =
    expect(0xff)
    var code = u8()
    while code == 0xff do code = u8()
    if code == 0 then throw JpegError("stuffed byte outside entropy data")
    code
  def segment(): IArray[Byte]  =
    val length = u16()
    if length < 2 || length - 2 > remaining then throw JpegError("invalid metadata segment length")
    val result = IArray.from(bytes.slice(position, position + length - 2))
    position += length - 2
    result
  private def u16(): Int       = u8() << 8 | u8()
  private def u8(): Int        =
    if remaining <= 0 then throw JpegError("unexpected end of JPEG metadata")
    val result = bytes(position) & 0xff
    position += 1
    result

private object MetadataCursor:
  def apply(bytes: Array[Byte]): MetadataCursor = new MetadataCursor(bytes, 0)

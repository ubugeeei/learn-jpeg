package jpeg

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class MetadataSuite extends munit.FunSuite:
  test("inspector extracts JFIF, comments, and preserves unknown APP payloads"):
    val base      = JpegEncoder.encode(GrayImage(8, 8, Seq.fill(64)(128)))
    val comment   = "camera note".getBytes(StandardCharsets.ISO_8859_1)
    val unknown   = Array[Byte](1, 3, 5, 7)
    val bytes     = inject(base, Seq(0xfe -> comment, 0xed -> unknown))
    val metadata  = JpegMetadata.inspect(bytes)
    assertEquals(metadata.jfif.map(info => info.major -> info.minor), Some(1 -> 1))
    assertEquals(metadata.comments, IndexedSeq("camera note"))
    val preserved = metadata.applications.find(_.marker == 0xed).get.payload
    assertEquals(preserved.toSeq, unknown.toSeq)

  test("Exif orientation is decoded in both TIFF byte orders"):
    val base  = JpegEncoder.encode(GrayImage(8, 8, Seq.fill(64)(0)))
    val cases = Seq(
      exif(littleEndian = true, orientation = 6)  -> 6,
      exif(littleEndian = false, orientation = 3) -> 3
    )
    cases.foreach: (payload, expected) =>
      val metadata = JpegMetadata.inspect(inject(base, Seq(0xe1 -> payload)))
      assertEquals(metadata.exifOrientation, Some(expected))

  test("ICC chunks are assembled by sequence number rather than marker order"):
    val base     = JpegEncoder.encode(GrayImage(8, 8, Seq.fill(64)(80)))
    val first    = iccChunk(sequence = 1, total = 2, Array[Byte](10, 20, 30))
    val second   = iccChunk(sequence = 2, total = 2, Array[Byte](40, 50))
    val metadata = JpegMetadata.inspect(inject(base, Seq(0xe2 -> second, 0xe2 -> first)))
    assertEquals(metadata.iccProfile.get.toSeq, Seq[Byte](10, 20, 30, 40, 50))

  test("missing and duplicate ICC chunks are rejected declaratively"):
    val base      = JpegEncoder.encode(GrayImage(8, 8, Seq.fill(64)(10)))
    val malformed = Seq(
      Seq(0xe2 -> iccChunk(1, 2, Array[Byte](1))),
      Seq(0xe2 -> iccChunk(1, 2, Array[Byte](1)), 0xe2 -> iccChunk(1, 2, Array[Byte](2)))
    )
    malformed.foreach: segments =>
      intercept[JpegError](JpegMetadata.inspect(inject(base, segments)))

  test("document API returns pixels and metadata from one caller-owned stream"):
    val base     = JpegEncoder.encode(GrayImage(9, 7, Seq.fill(63)(120)))
    val bytes    = inject(base, Seq(0xfe -> "hello".getBytes(StandardCharsets.ISO_8859_1)))
    val input    = java.io.ByteArrayInputStream(bytes.asInstanceOf[Array[Byte]])
    val document = Jpeg.readDocument(input, DecoderOptions())
    document.image match
      case DecodedImage.Grayscale(image) => assertEquals(image.dimensions, Dimensions(9, 7))
      case DecodedImage.Color(_)         => fail("expected grayscale document")
    assertEquals(document.metadata.comments, IndexedSeq("hello"))

  private def inject(base: IArray[Byte], segments: Seq[(Int, Array[Byte])]): IArray[Byte] =
    val source = base.asInstanceOf[Array[Byte]]
    val output = ByteArrayOutputStream()
    output.write(source, 0, 2)
    segments.foreach: (marker, payload) =>
      output.write(0xff)
      output.write(marker)
      output.write((payload.length + 2) >>> 8)
      output.write((payload.length + 2) & 0xff)
      output.write(payload)
    output.write(source, 2, source.length - 2)
    IArray.from(output.toByteArray)

  private def exif(littleEndian: Boolean, orientation: Int): Array[Byte] =
    val output = ByteArrayOutputStream()
    output.write("Exif\u0000\u0000".getBytes(StandardCharsets.ISO_8859_1))
    output.write(if littleEndian then 'I' else 'M')
    output.write(if littleEndian then 'I' else 'M')
    write16(output, 42, littleEndian)
    write32(output, 8, littleEndian)
    write16(output, 1, littleEndian)
    write16(output, 0x0112, littleEndian)
    write16(output, 3, littleEndian)
    write32(output, 1, littleEndian)
    write16(output, orientation, littleEndian)
    write16(output, 0, littleEndian)
    write32(output, 0, littleEndian)
    output.toByteArray

  private def iccChunk(sequence: Int, total: Int, data: Array[Byte]): Array[Byte] =
    "ICC_PROFILE\u0000".getBytes(StandardCharsets.ISO_8859_1) ++
      Array(sequence.toByte, total.toByte) ++ data

  private def write16(output: ByteArrayOutputStream, value: Int, little: Boolean): Unit =
    val bytes = Seq(value & 0xff, value >>> 8 & 0xff)
    (if little then bytes else bytes.reverse).foreach(output.write)

  private def write32(output: ByteArrayOutputStream, value: Int, little: Boolean): Unit =
    val bytes = (0 until 4).map(shift => value >>> (shift * 8) & 0xff)
    (if little then bytes else bytes.reverse).foreach(output.write)

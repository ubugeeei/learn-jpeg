package jpeg

import java.awt.image.BufferedImage
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.file.Files
import javax.imageio.ImageIO

class CodecSuite extends munit.FunSuite:
  test("encoder emits a complete marker stream"):
    val encoded  = JpegEncoder.encode(GrayImage(8, 8, Seq.fill(64)(128)))
    val unsigned = encoded.toSeq.map(_ & 0xff)
    assertEquals(unsigned.take(2), Seq(0xff, 0xd8))
    assertEquals(unsigned.takeRight(2), Seq(0xff, 0xd9))
    assert(unsigned.sliding(2).contains(Seq(0xff, 0xc0)))
    assert(unsigned.sliding(2).contains(Seq(0xff, 0xda)))

  test("constant images round-trip exactly"):
    for value <- Seq(0, 32, 128, 200, 255) do
      val source  = GrayImage(13, 9, Seq.fill(13 * 9)(value))
      val decoded = JpegDecoder.decode(JpegEncoder.encode(source))
      assertEquals(decoded.width, source.width)
      assertEquals(decoded.height, source.height)
      assertEquals((for y <- 0 until 9; x <- 0 until 13 yield decoded(x, y)).toSet, Set(value))

  test("gradient round-trip has bounded error"):
    val source  =
      GrayImage(17, 11, for y <- 0 until 11; x <- 0 until 17 yield (x * 9 + y * 5) & 0xff)
    val decoded = JpegDecoder.decode(JpegEncoder.encode(source))
    val errors  = for y <- 0 until 11; x <- 0 until 17 yield math.abs(source(x, y) - decoded(x, y))
    assert(errors.max <= 22, s"maximum error was ${errors.max}")
    assert(errors.sum.toDouble / errors.size <= 5.0)

  test("encoded stream is readable by the JDK JPEG implementation"):
    val encoded = JpegEncoder
      .encode(GrayImage(9, 7, for y <- 0 until 7; x <- 0 until 9 yield x * 20 + y))
    val image   = ImageIO.read(ByteArrayInputStream(encoded.asInstanceOf[Array[Byte]]))
    assertEquals(image.getWidth, 9)
    assertEquals(image.getHeight, 7)

  test("decoder reads a grayscale JPEG from the JDK implementation"):
    val image   = BufferedImage(8, 8, BufferedImage.TYPE_BYTE_GRAY)
    for y <- 0 until 8; x <- 0 until 8 do image.getRaster.setSample(x, y, 0, 128)
    val output  = ByteArrayOutputStream()
    assert(ImageIO.write(image, "jpeg", output))
    val decoded = JpegDecoder.decode(IArray.from(output.toByteArray))
    assertEquals((for y <- 0 until 8; x <- 0 until 8 yield decoded(x, y)).toSet, Set(128))

  test("decoder rejects a progressive frame explicitly"):
    val encoded = JpegEncoder.encode(GrayImage(8, 8, Seq.fill(64)(0))).asInstanceOf[Array[Byte]]
      .clone()
    val sof     = encoded.indices.find(i =>
      i + 1 < encoded.length && encoded(i) == 0xff.toByte && encoded(i + 1) == 0xc0.toByte
    ).get
    encoded(sof + 1) = 0xc2.toByte
    val error   = intercept[JpegError](JpegDecoder.decode(IArray.from(encoded)))
    assert(error.message.contains("unsupported JPEG frame"))

  test("decoder rejects truncated input with a domain error"):
    val error = intercept[JpegError](JpegDecoder.decode(IArray(0xff.toByte, 0xd8.toByte)))
    assert(error.message.nonEmpty)

  test("filesystem facade owns path streams and preserves dimensions"):
    val path = Files.createTempFile("learn-jpeg-", ".jpg")
    try
      Jpeg.write(GrayImage(7, 5, Seq.fill(35)(91)), path, EncoderOptions(Quality(90)))
      val decoded = Jpeg.readGray(path)
      assertEquals(decoded.width, 7)
      assertEquals(decoded.height, 5)
    finally Files.deleteIfExists(path)

  test("stream facade rejects input beyond its configured resource limit"):
    val input = ByteArrayInputStream(Array.fill(129)(0.toByte))
    val error = intercept[JpegError](Jpeg.read(input, DecoderOptions(maxInputBytes = 128)))
    assert(error.message.contains("configured limit"))

  test("decoder rejects dimensions beyond its pixel limit before allocating planes"):
    val encoded = JpegEncoder.encode(GrayImage(8, 8, Seq.fill(64)(0))).asInstanceOf[Array[Byte]]
      .clone()
    val sof     = encoded.indices
      .find(index => encoded(index) == 0xff.toByte && encoded(index + 1) == 0xc0.toByte).get
    encoded(sof + 5) = 1
    encoded(sof + 6) = 0
    encoded(sof + 7) = 1
    encoded(sof + 8) = 0
    val error   = intercept[JpegError]:
      JpegDecoder.decodeImage(IArray.from(encoded), maximumPixels = 1000)
    assert(error.message.contains("configured limit"))

  test("stream facade does not close a caller-owned output"):
    val output = ByteArrayOutputStream()
    Jpeg.write(GrayImage(8, 8, Seq.fill(64)(128)), output, EncoderOptions())
    output.write(42)
    assertEquals(output.toByteArray.last, 42.toByte)

  test("restart intervals round-trip and emit cyclic byte-aligned markers"):
    val image = GrayImage(40, 24, for y <- 0 until 24; x <- 0 until 40 yield (x * 5 + y * 3) & 0xff)
    Seq(1, 2, 4, 7).foreach: interval =>
      val encoded = JpegEncoder
        .encode(image, EncoderOptions(Quality(90), restartInterval = interval))
      val markers = encoded.asInstanceOf[Array[Byte]].sliding(2).collect {
        case Array(prefix, code)
            if prefix == 0xff.toByte && code >= 0xd0.toByte && code <= 0xd7.toByte => code & 0xff
      }.toSeq
      assert(markers.nonEmpty)
      assertEquals(markers, markers.indices.map(index => 0xd0 + (index & 7)))
      val decoded = JpegDecoder.decode(encoded)
      assertEquals(decoded.width -> decoded.height, image.width -> image.height)

  test("decoder rejects an out-of-sequence restart marker"):
    val image   = GrayImage(24, 16, Seq.fill(24 * 16)(80))
    val encoded = JpegEncoder.encode(image, EncoderOptions(restartInterval = 1))
      .asInstanceOf[Array[Byte]].clone()
    val restart = encoded.indices
      .find(index => encoded(index) == 0xff.toByte && encoded(index + 1) == 0xd0.toByte).get
    encoded(restart + 1) = 0xd3.toByte
    val error   = intercept[JpegError](JpegDecoder.decode(IArray.from(encoded)))
    assert(error.message.contains("expected restart marker"))

  test("optimized Huffman tables reduce a grayscale scan and remain interoperable"):
    val image     = GrayImage(
      160,
      120,
      for y <- 0 until 120; x <- 0 until 160 yield (x * 3 + y * 5 + (x * y % 17)) & 0xff
    )
    val fixed     = JpegEncoder.encode(
      image,
      EncoderOptions(Quality(82), restartInterval = 7, optimizeHuffmanTables = false)
    )
    val optimized = JpegEncoder
      .encode(image, EncoderOptions(Quality(82), restartInterval = 7, optimizeHuffmanTables = true))
    assert(optimized.length < fixed.length, s"${optimized.length} was not below ${fixed.length}")
    assertEquals(JpegDecoder.decode(optimized).dimensions, image.dimensions)
    val external  = ImageIO.read(ByteArrayInputStream(optimized.asInstanceOf[Array[Byte]]))
    assertEquals(external.getWidth -> external.getHeight, image.width -> image.height)

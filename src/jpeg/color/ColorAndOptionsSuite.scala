package jpeg

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

class ColorAndOptionsSuite extends munit.FunSuite:
  test("quality 50 preserves the Annex K table"):
    assertEquals(Quality(50).scale(Quantization.Luminance).values, Quantization.Luminance.values)
    assertEquals(
      Quality(50).scale(Quantization.Chrominance).values,
      Quantization.Chrominance.values
    )

  test("color streams declare distinct Annex K luminance and chrominance tables"):
    val source  = RgbImage(8, 8, Seq.fill(64)(Rgb(30, 80, 140)))
    val encoded = JpegEncoder
      .encode(source, EncoderOptions(Quality(50), ChromaSubsampling.FullResolution))
      .asInstanceOf[Array[Byte]]
    val dqt     = markerOffsets(encoded, 0xdb)

    assertEquals(dqt.size, 2)
    assertEquals(encoded(dqt(0) + 4) & 0xff, 0)
    assertEquals(encoded(dqt(1) + 4) & 0xff, 1)
    assertEquals(
      encoded.slice(dqt(0) + 5, dqt(0) + 69).map(_ & 0xff).toIndexedSeq,
      Quantization.zigZag(Quantization.Luminance)
    )
    assertEquals(
      encoded.slice(dqt(1) + 5, dqt(1) + 69).map(_ & 0xff).toIndexedSeq,
      Quantization.zigZag(Quantization.Chrominance)
    )

  test("SOF0 routes Y to table 0 and both chroma components to table 1"):
    val source  = RgbImage(8, 8, Seq.fill(64)(Rgb(30, 80, 140)))
    val encoded = JpegEncoder.encode(source).asInstanceOf[Array[Byte]]
    val sof     = markerOffsets(encoded, 0xc0).head

    val descriptors = (0 until 3).map: component =>
      val offset = sof + 10 + component * 3
      (encoded(offset) & 0xff, encoded(offset + 1) & 0xff, encoded(offset + 2) & 0xff)
    assertEquals(descriptors.map((id, _, table) => id -> table), Seq(1 -> 0, 2 -> 1, 3 -> 1))
    assertEquals(JpegDecoder.decodeRgb(IArray.from(encoded)).dimensions, source.dimensions)
    assertEquals(ImageIO.read(ByteArrayInputStream(encoded)).getWidth, source.width)

  test("quality endpoints clamp quantizers to legal 8-bit values"):
    assert(Quality.Minimum.scale(Quantization.Luminance).values.forall(v => v >= 1 && v <= 255))
    assertEquals(Quality.Maximum.scale(Quantization.Luminance).values.toSet, Set(1))

  test("higher quality generally produces a larger gradient stream"):
    val image =
      GrayImage(64, 64, for y <- 0 until 64; x <- 0 until 64 yield (x * 7 + y * 11) & 0xff)
    val low   = JpegEncoder.encode(image, EncoderOptions(Quality(10)))
    val high  = JpegEncoder.encode(image, EncoderOptions(Quality(95)))
    assert(high.length > low.length)

  test("JFIF color conversion recognizes reference colors"):
    val cases = Seq(
      Rgb(0, 0, 0)       -> YCbCr(0, 128, 128),
      Rgb(255, 255, 255) -> YCbCr(255, 128, 128),
      Rgb(255, 0, 0)     -> YCbCr(76, 85, 255)
    )
    cases.foreach((input, expected) => assertEquals(YCbCr.fromRgb(input), expected))

  test("RGB and YCbCr conversion round-trips within rounding error"):
    val colors = Seq(Rgb(0, 0, 0), Rgb(255, 255, 255), Rgb(20, 80, 160), Rgb(240, 30, 90))
    colors.foreach: expected =>
      val actual = YCbCr.fromRgb(expected).toRgb
      assert(math.abs(expected.red - actual.red) <= 1)
      assert(math.abs(expected.green - actual.green) <= 1)
      assert(math.abs(expected.blue - actual.blue) <= 1)

  test("RGB encoder emits a three-component stream readable by ImageIO"):
    val image   = RgbImage(
      13,
      9,
      for
        y <- 0 until 9
        x <- 0 until 13
      yield Rgb(x * 19, y * 27, (x * 11 + y * 7) & 0xff)
    )
    val encoded = JpegEncoder
      .encode(image, EncoderOptions(Quality(90), ChromaSubsampling.FullResolution))
    val decoded = ImageIO.read(ByteArrayInputStream(encoded.asInstanceOf[Array[Byte]]))
    assertEquals(decoded.getWidth, 13)
    assertEquals(decoded.getHeight, 9)
    val center  = decoded.getRGB(6, 4)
    assert(math.abs(((center >>> 16) & 0xff) - 114) <= 12)
    assert(math.abs(((center >>> 8) & 0xff) - 108) <= 12)

  test("RGB codec round-trips a 4:4:4 image"):
    val source  = RgbImage(
      17,
      11,
      for
        y <- 0 until 11
        x <- 0 until 17
      yield Rgb(x * 13, y * 21, (x * 7 + y * 9) & 0xff)
    )
    val decoded = JpegDecoder.decodeRgb(
      JpegEncoder.encode(source, EncoderOptions(Quality(95), ChromaSubsampling.FullResolution))
    )
    val errors  =
      for y <- 0 until source.height; x <- 0 until source.width yield
        val expected = source(x, y)
        val actual   = decoded(x, y)
        Seq(
          math.abs(expected.red - actual.red),
          math.abs(expected.green - actual.green),
          math.abs(expected.blue - actual.blue)
        ).max
    assert(errors.max <= 18, s"maximum channel error was ${errors.max}")

  test("decoder reads externally produced subsampled color JPEG"):
    val source  = BufferedImage(31, 19, BufferedImage.TYPE_INT_RGB)
    for y <- 0 until source.getHeight; x <- 0 until source.getWidth do
      val red   = x * 255 / (source.getWidth - 1)
      val green = y * 255 / (source.getHeight - 1)
      val blue  = (x + y) * 255 / (source.getWidth + source.getHeight - 2)
      source.setRGB(x, y, (red << 16) | (green << 8) | blue)
    val output  = ByteArrayOutputStream()
    assert(ImageIO.write(source, "jpeg", output))
    val decoded = JpegDecoder.decodeRgb(IArray.from(output.toByteArray))
    assertEquals(decoded.width, 31)
    assertEquals(decoded.height, 19)
    val center  = decoded(15, 9)
    assert(math.abs(center.red - 127) <= 12)
    assert(math.abs(center.green - 127) <= 12)
    assert(math.abs(center.blue - 127) <= 12)

  test("4:2:0 encoder handles dimensions smaller than one MCU and odd edges"):
    val dimensions = Seq(1 -> 1, 7 -> 5, 16 -> 16, 17 -> 19)
    dimensions.foreach: (width, height) =>
      val source  = RgbImage(
        width,
        height,
        for y <- 0 until height; x <- 0 until width yield Rgb(x * 9, y * 7, 80)
      )
      val encoded = JpegEncoder
        .encode(source, EncoderOptions(Quality(90), ChromaSubsampling.HalfBothAxes))
      val decoded = JpegDecoder.decodeRgb(encoded)
      assertEquals(decoded.width -> decoded.height, width -> height)

  test("4:2:0 is smaller than 4:4:4 for a photographic-size color gradient"):
    val source     = RgbImage(
      128,
      96,
      for y <- 0 until 96; x <- 0 until 128 yield Rgb(x * 2, y * 2, (x + y) & 0xff)
    )
    val full       = JpegEncoder
      .encode(source, EncoderOptions(Quality(85), ChromaSubsampling.FullResolution))
    val subsampled = JpegEncoder
      .encode(source, EncoderOptions(Quality(85), ChromaSubsampling.HalfBothAxes))
    assert(subsampled.length < full.length)

  test("4:2:2 encoder interoperates and preserves odd dimensions"):
    val cases = Seq(1 -> 1, 15 -> 7, 16 -> 8, 17 -> 9, 33 -> 19)
    cases.foreach: (width, height) =>
      val source   = RgbImage(
        width,
        height,
        for y <- 0 until height; x <- 0 until width
        yield Rgb(x * 255 / math.max(1, width - 1), y * 255 / math.max(1, height - 1), 96)
      )
      val encoded  = JpegEncoder
        .encode(source, EncoderOptions(Quality(90), ChromaSubsampling.HalfHorizontal))
      val external = ImageIO.read(ByteArrayInputStream(encoded.asInstanceOf[Array[Byte]]))
      assertEquals(external.getWidth -> external.getHeight, width -> height)
      val decoded  = JpegDecoder.decodeRgb(encoded)
      assertEquals(decoded.width     -> decoded.height, width     -> height)

  test("subsampling choices produce the expected SOF0 luma sampling byte"):
    val source = RgbImage(16, 16, Seq.fill(256)(Rgb(30, 80, 140)))
    val cases  = Seq(
      ChromaSubsampling.FullResolution -> 0x11,
      ChromaSubsampling.HalfHorizontal -> 0x21,
      ChromaSubsampling.HalfBothAxes   -> 0x22
    )
    cases.foreach: (subsampling, expected) =>
      val bytes = JpegEncoder.encode(source, EncoderOptions(Quality.Default, subsampling))
        .asInstanceOf[Array[Byte]]
      val sof   = bytes.indices
        .find(index => bytes(index) == 0xff.toByte && bytes(index + 1) == 0xc0.toByte).get
      assertEquals(bytes(sof + 11) & 0xff, expected)

  test("restart intervals round-trip in every color sampling mode"):
    val source = RgbImage(
      35,
      21,
      for y <- 0 until 21; x <- 0 until 35 yield Rgb(x * 7, y * 11, (x * 3 + y * 5) & 0xff)
    )
    val modes  = Seq(
      ChromaSubsampling.FullResolution,
      ChromaSubsampling.HalfHorizontal,
      ChromaSubsampling.HalfBothAxes
    )
    for mode <- modes; interval <- Seq(1, 2, 5) do
      val encoded          = JpegEncoder
        .encode(source, EncoderOptions(Quality(90), mode, restartInterval = interval))
      val decoded          = JpegDecoder.decodeRgb(encoded)
      assertEquals(decoded.width     -> decoded.height, source.width     -> source.height)
      val external         = ImageIO.read(ByteArrayInputStream(encoded.asInstanceOf[Array[Byte]]))
      assertEquals(external.getWidth -> external.getHeight, source.width -> source.height)

  test("optimized Huffman tables reduce color output across sampling modes"):
    val source = RgbImage(
      128,
      96,
      for y <- 0 until 96; x <- 0 until 128 yield Rgb(x * 2, y * 2, (x * 5 + y * 3) & 0xff)
    )
    Seq(
      ChromaSubsampling.FullResolution,
      ChromaSubsampling.HalfHorizontal,
      ChromaSubsampling.HalfBothAxes
    ).foreach: mode =>
      val fixed     = JpegEncoder.encode(
        source,
        EncoderOptions(Quality(85), mode, restartInterval = 5, optimizeHuffmanTables = false)
      )
      val optimized = JpegEncoder.encode(
        source,
        EncoderOptions(Quality(85), mode, restartInterval = 5, optimizeHuffmanTables = true)
      )
      assert(optimized.length < fixed.length)
      assertEquals(JpegDecoder.decodeRgb(optimized).dimensions, source.dimensions)

  test("centered bilinear upsampling improves smooth 4:2:0 chroma reconstruction"):
    val source                       = RgbImage(
      64,
      48,
      for y <- 0 until 48; x <- 0 until 64 yield Rgb(x * 4, y * 5, (x * 2 + y * 2) & 0xff)
    )
    val encoded                      = JpegEncoder
      .encode(source, EncoderOptions(Quality(95), ChromaSubsampling.HalfBothAxes))
    val nearest                      = JpegDecoder.decodeRgb(encoded, ChromaUpsampling.Nearest)
    val bilinear                     = JpegDecoder.decodeRgb(encoded, ChromaUpsampling.Bilinear)
    def error(image: RgbImage): Long = (for y <- 0 until source.height; x <- 0 until source.width
    yield
      val expected = source(x, y)
      val actual   = image(x, y)
      math.abs(expected.red - actual.red) + math.abs(expected.green - actual.green) +
        math.abs(expected.blue - actual.blue)
    ).map(_.toLong).sum
    assert(error(bilinear) < error(nearest))

  test("full-resolution color is invariant across upsampling policies"):
    val source  = RgbImage(9, 7, for y <- 0 until 7; x <- 0 until 9 yield Rgb(x * 20, y * 30, 90))
    val encoded = JpegEncoder
      .encode(source, EncoderOptions(Quality(90), ChromaSubsampling.FullResolution))
    val nearest = JpegDecoder.decodeRgb(encoded, ChromaUpsampling.Nearest)
    val smooth  = JpegDecoder.decodeRgb(encoded, ChromaUpsampling.Bilinear)
    assertEquals(nearest.pixels.toSeq, smooth.pixels.toSeq)

  private def markerOffsets(bytes: Array[Byte], marker: Int): IndexedSeq[Int] = bytes.indices
    .filter(index =>
      index + 1 < bytes.length && bytes(index) == 0xff.toByte && (bytes(index + 1) & 0xff) == marker
    ).toIndexedSeq

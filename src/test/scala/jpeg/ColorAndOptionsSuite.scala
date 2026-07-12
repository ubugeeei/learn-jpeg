package jpeg

class ColorAndOptionsSuite extends munit.FunSuite:
  test("quality 50 preserves the Annex K table"):
    assertEquals(Quality(50).scale(Quantization.Luminance).values, Quantization.Luminance.values)

  test("quality endpoints clamp quantizers to legal 8-bit values"):
    assert(Quality.Minimum.scale(Quantization.Luminance).values.forall(v => v >= 1 && v <= 255))
    assertEquals(Quality.Maximum.scale(Quantization.Luminance).values.toSet, Set(1))

  test("higher quality generally produces a larger gradient stream"):
    val image = GrayImage(64, 64, for y <- 0 until 64; x <- 0 until 64 yield (x * 7 + y * 11) & 0xff)
    val low = JpegEncoder.encode(image, EncoderOptions(Quality(10)))
    val high = JpegEncoder.encode(image, EncoderOptions(Quality(95)))
    assert(high.length > low.length)

  test("JFIF color conversion recognizes reference colors"):
    assertEquals(YCbCr.fromRgb(Rgb(0, 0, 0)), YCbCr(0, 128, 128))
    assertEquals(YCbCr.fromRgb(Rgb(255, 255, 255)), YCbCr(255, 128, 128))
    assertEquals(YCbCr.fromRgb(Rgb(255, 0, 0)), YCbCr(76, 85, 255))

  test("RGB and YCbCr conversion round-trips within rounding error"):
    val colors = Seq(Rgb(0, 0, 0), Rgb(255, 255, 255), Rgb(20, 80, 160), Rgb(240, 30, 90))
    colors.foreach: expected =>
      val actual = YCbCr.fromRgb(expected).toRgb
      assert(math.abs(expected.red - actual.red) <= 1)
      assert(math.abs(expected.green - actual.green) <= 1)
      assert(math.abs(expected.blue - actual.blue) <= 1)

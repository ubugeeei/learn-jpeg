package jpeg

class TransformSuite extends munit.FunSuite:
  test("a constant block has only a DC coefficient"):
    val transformed = Dct.forward(Block(Seq.fill(64)(128)))
    assertEquals(transformed.values, Block.Zero.values)

  test("FDCT and IDCT round-trip representative samples"):
    val source        = Block((0 until 64).map(i => (i * 37 + 11) & 0xff))
    val reconstructed = Dct.inverse(Dct.forward(source))
    source.values.zip(reconstructed.values).foreach((expected, actual) =>
      assert(math.abs(expected - actual) <= 1, s"$expected != $actual")
    )

  test("separable FDCT agrees with the direct specification equation"):
    val blocks = (0 until 20).map(seed =>
      Block((0 until 64).map(index => (seed * 53 + index * 37 + index * index * 3) & 0xff))
    )
    blocks.foreach: block =>
      val expected = directForward(block)
      val actual   = Dct.forward(block)
      expected.values.zip(actual.values).foreach: (reference, separated) =>
        assert(math.abs(reference - separated) <= 1)

  test("zig-zag and natural order are inverses"):
    val source = Block(0 until 64)
    assertEquals(Quantization.natural(Quantization.zigZag(source)).values, source.values)

  test("image blocks extend the bottom and right edges"):
    val image = GrayImage(2, 2, Seq(1, 2, 3, 4))
    val block = image.blocks.head
    assertEquals(block(0, 0), 1)
    assertEquals(block(0, 7), 2)
    assertEquals(block(7, 0), 3)
    assertEquals(block(7, 7), 4)

  private def directForward(samples: Block): Block =
    def scale(value: Int): Double = if value == 0 then 1.0 / math.sqrt(2) else 1.0
    Block(for v <- 0 until 8; u <- 0 until 8 yield
      val sum = (for y <- 0 until 8; x <- 0 until 8
      yield (samples(y, x) - 128) * math.cos((2 * x + 1) * u * math.Pi / 16.0) *
        math.cos((2 * y + 1) * v * math.Pi / 16.0)).sum
      math.round(0.25 * scale(u) * scale(v) * sum).toInt)

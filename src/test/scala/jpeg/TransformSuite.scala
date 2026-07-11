package jpeg

class TransformSuite extends munit.FunSuite:
  test("a constant block has only a DC coefficient"):
    val transformed = Dct.forward(Block(Seq.fill(64)(128)))
    assertEquals(transformed, Block.Zero)

  test("FDCT and IDCT round-trip representative samples"):
    val source = Block((0 until 64).map(i => (i * 37 + 11) & 0xff))
    val reconstructed = Dct.inverse(Dct.forward(source))
    source.values.zip(reconstructed.values).foreach((expected, actual) =>
      assert(math.abs(expected - actual) <= 1, s"$expected != $actual"))

  test("zig-zag and natural order are inverses"):
    val source = Block(0 until 64)
    assertEquals(Quantization.natural(Quantization.zigZag(source)), source)

  test("image blocks extend the bottom and right edges"):
    val image = GrayImage(2, 2, Seq(1, 2, 3, 4))
    val block = image.blocks.head
    assertEquals(block(0, 0), 1)
    assertEquals(block(0, 7), 2)
    assertEquals(block(7, 0), 3)
    assertEquals(block(7, 7), 4)

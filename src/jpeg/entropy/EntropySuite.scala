package jpeg

class EntropySuite extends munit.FunSuite:
  test("magnitude categories and complement representation round-trip"):
    for expected <- -1023 to 1023 do
      val category = Magnitude.category(expected)
      assertEquals(Magnitude.value(Magnitude.bits(expected, category), category), expected)

  test("bit writer pads with ones"):
    val writer = BitWriter()
    writer.write(0, 1)
    assertEquals(writer.result().toSeq.map(_ & 0xff), Seq(0x7f))

  test("bit writer stuffs a zero after FF"):
    val writer = BitWriter()
    writer.write(0xff, 8)
    assertEquals(writer.result().toSeq.map(_ & 0xff), Seq(0xff, 0x00))

  test("standard DC Huffman table round-trips every symbol"):
    for symbol <- 0 to 11 do
      val writer  = BitWriter()
      StandardTables.LuminanceDc.write(symbol, writer)
      val encoded = writer.result()
      assertEquals(StandardTables.LuminanceDc.read(BitReader(encoded)), symbol)

  test("invalid Huffman symbol is rejected"):
    intercept[IllegalArgumentException]:
      StandardTables.LuminanceDc.write(99, BitWriter())

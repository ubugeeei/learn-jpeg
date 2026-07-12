package jpeg

class HuffmanOptimizerSuite extends munit.FunSuite:
  test("optimized tables round-trip every supplied symbol"):
    val alphabets = Seq(
      Map(0 -> 1L),
      Map(0 -> 100L, 1 -> 1L),
      (0 until 16).map(symbol => symbol -> (symbol + 1L)).toMap,
      (0 until 256).map(symbol => symbol -> 1L).toMap
    )
    alphabets.foreach: frequencies =>
      val table = HuffmanOptimizer.optimize(frequencies)
      assertEquals(table.counts.length, 16)
      assertEquals(table.counts.sum, frequencies.size)
      frequencies.keys.foreach: symbol =>
        val writer = BitWriter()
        table.write(symbol, writer)
        assertEquals(table.read(BitReader(writer.result())), symbol)

  test("heavily skewed alphabets remain within JPEG's sixteen-bit limit"):
    val frequencies = (0 until 40).map: symbol =>
      symbol -> (if symbol < 2 then 1_000_000L else 1L)
    .toMap
    val table       = HuffmanOptimizer.optimize(frequencies)
    assertEquals(table.counts.drop(16).sum, 0)
    assertEquals(table.counts.sum, frequencies.size)

  test("more frequent symbols never receive longer codes than rare symbols"):
    val frequencies = Map(0 -> 1000L, 1 -> 200L, 2 -> 30L, 3 -> 4L, 4 -> 1L)
    val table       = HuffmanOptimizer.optimize(frequencies)
    val lengths     = table.counts.zipWithIndex.flatMap((count, index) => Seq.fill(count)(index + 1))
    val bySymbol    = table.symbols.zip(lengths).toMap
    assert(bySymbol(0) <= bySymbol(1))
    assert(bySymbol(1) <= bySymbol(2))
    assert(bySymbol(2) <= bySymbol(3))

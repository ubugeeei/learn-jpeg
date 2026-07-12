package jpeg

class RobustnessSuite extends munit.FunSuite:
  test("Huffman tables reject malformed canonical alphabets"):
    val cases = Seq(
      "oversubscribed"   -> (() => HuffmanTable(Seq(3) ++ Seq.fill(15)(0), Seq(1, 2, 3))),
      "duplicate symbol" -> (() => HuffmanTable(Seq(1, 1) ++ Seq.fill(14)(0), Seq(7, 7))),
      "all-one code"     -> (() => HuffmanTable(Seq(2) ++ Seq.fill(15)(0), Seq(0, 1)))
    )
    cases.foreach: (clue, construct) =>
      val error = intercept[JpegError](construct())
      assert(error.message.nonEmpty, clue)

  test("decoder rejects malformed baseline table and frame fields"):
    val grayscale = JpegEncoder
      .encode(GrayImage(8, 8, Seq.fill(64)(64)), EncoderOptions(optimizeHuffmanTables = false))
    val color     = JpegEncoder.encode(
      RgbImage(16, 16, Seq.fill(256)(Rgb(10, 20, 30))),
      EncoderOptions(optimizeHuffmanTables = false)
    )
    val cases     = Seq(
      "zero quantizer"             -> mutateMarker(grayscale, 0xdb, payloadOffset = 1, value = 0),
      "zero height"                -> mutateMarker(grayscale, 0xc0, payloadOffset = 2, value = 0),
      "too many MCU blocks"        -> mutateMarker(color, 0xc0, payloadOffset = 7, value = 0x44),
      "invalid DC category symbol" -> mutateFirstDhtSymbol(grayscale, value = 12)
    )
    cases.foreach: (clue, bytes) =>
      val error = intercept[JpegError](JpegDecoder.decodeImage(bytes, maximumPixels = 1_000_000))
      assert(error.message.nonEmpty, clue)

  test("deterministic byte mutations never leak incidental runtime exceptions"):
    val sources = Seq(
      JpegEncoder.encode(GrayImage(17, 13, for index <- 0 until 221 yield index & 0xff)),
      JpegEncoder.encode(RgbImage(
        19,
        15,
        for
          y <- 0 until 15
          x <- 0 until 19
        yield Rgb(x * 11, y * 17, (x + y) * 7)
      ))
    )
    val masks   = Seq(0x01, 0x20, 0x80, 0xff)
    sources.zipWithIndex.foreach: (source, sourceIndex) =>
      val bytes     = source.asInstanceOf[Array[Byte]]
      val positions = (0 until bytes.length by math.max(1, bytes.length / 80)).take(80)
      for position <- positions; mask <- masks do
        val mutated = bytes.clone()
        mutated(position) = (mutated(position) ^ mask).toByte
        try JpegDecoder.decodeImage(IArray.from(mutated), maximumPixels = 1_000_000)
        catch
          case _: JpegError     => ()
          case error: Throwable => fail(
              s"source=$sourceIndex position=$position mask=$mask leaked ${error.getClass.getName}",
              error
            )

  private def mutateMarker(
      source: IArray[Byte],
      marker: Int,
      payloadOffset: Int,
      value: Int
  ): IArray[Byte] =
    val bytes = source.asInstanceOf[Array[Byte]].clone()
    val index = findMarker(bytes, marker)
    bytes(index + 4 + payloadOffset) = value.toByte
    IArray.from(bytes)

  private def mutateFirstDhtSymbol(source: IArray[Byte], value: Int): IArray[Byte] =
    val bytes = source.asInstanceOf[Array[Byte]].clone()
    val index = findMarker(bytes, 0xc4)
    bytes(index + 4 + 1 + 16) = value.toByte
    IArray.from(bytes)

  private def findMarker(bytes: Array[Byte], marker: Int): Int = bytes.indices.find(index =>
    index + 1 < bytes.length && bytes(index) == 0xff.toByte && bytes(index + 1) == marker.toByte
  ).getOrElse(fail(f"marker FF$marker%02X not found"))

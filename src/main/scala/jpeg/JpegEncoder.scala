package jpeg

import java.io.ByteArrayOutputStream

/** Baseline sequential grayscale JPEG encoder.
  *
  * The emitted marker sequence conforms to
  * [[https://www.w3.org/Graphics/JPEG/itu-t81.pdf T.81 Annex B]], while scan
  * coding follows Annex F.1. The API deliberately accepts only `GrayImage`, so
  * unsupported color transforms cannot be selected accidentally.
  */
object JpegEncoder:
  def encode(image: GrayImage): IArray[Byte] =
    val out = ByteArrayOutputStream()
    marker(out, 0xd8)
    segment(out, 0xe0, Seq(0x4a,0x46,0x49,0x46,0,1,1,0,0,1,0,1,0,0))
    segment(out, 0xdb, 0 +: Quantization.zigZag(Quantization.Luminance))
    segment(out, 0xc0, Seq(8) ++ u16(image.height) ++ u16(image.width) ++ Seq(1, 1, 0x11, 0))
    dht(out, tableClass = 0, id = 0, StandardTables.LuminanceDc)
    dht(out, tableClass = 1, id = 0, StandardTables.LuminanceAc)
    segment(out, 0xda, Seq(1, 1, 0, 0, 63, 0))
    out.write(entropy(image).asInstanceOf[Array[Byte]])
    marker(out, 0xd9)
    IArray.from(out.toByteArray)

  private def entropy(image: GrayImage): IArray[Byte] =
    val bits = BitWriter()
    var previousDc = 0
    image.blocks.foreach: samples =>
      val coefficients = Quantization.quantize(Dct.forward(samples), Quantization.Luminance)
      val ordered = Quantization.zigZag(coefficients)
      val difference = ordered.head - previousDc
      previousDc = ordered.head
      val dcCategory = Magnitude.category(difference)
      StandardTables.LuminanceDc.write(dcCategory, bits)
      bits.write(Magnitude.bits(difference, dcCategory), dcCategory)

      var run = 0
      ordered.tail.foreach: value =>
        if value == 0 then run += 1
        else
          while run >= 16 do
            StandardTables.LuminanceAc.write(0xf0, bits)
            run -= 16
          val category = Magnitude.category(value)
          StandardTables.LuminanceAc.write((run << 4) | category, bits)
          bits.write(Magnitude.bits(value, category), category)
          run = 0
      if run > 0 then StandardTables.LuminanceAc.write(0x00, bits)
    bits.result()

  private def dht(out: ByteArrayOutputStream, tableClass: Int, id: Int, table: HuffmanTable): Unit =
    segment(out, 0xc4, Seq((tableClass << 4) | id) ++ table.counts ++ table.symbols)

  private def marker(out: ByteArrayOutputStream, code: Int): Unit =
    out.write(0xff); out.write(code)

  private def segment(out: ByteArrayOutputStream, code: Int, payload: Seq[Int]): Unit =
    marker(out, code)
    u16(payload.size + 2).foreach(out.write)
    payload.foreach(out.write)

  private def u16(value: Int): Seq[Int] = Seq(value >>> 8, value & 0xff)

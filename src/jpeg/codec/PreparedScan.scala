package jpeg

import java.io.ByteArrayOutputStream
import scala.collection.mutable

/** A magnitude payload following one Huffman-coded symbol. */
final private case class EntropyToken(symbol: Int, value: Int, bitCount: Int)

/** Entropy decisions for one quantized block, independent of a concrete Huffman table. */
final private case class PreparedBlock(dc: EntropyToken, ac: IndexedSeq[EntropyToken])

/** A transformed scan that can be counted and written without repeating the DCT. */
final private[jpeg] case class PreparedScan(
    mcus: IndexedSeq[IndexedSeq[PreparedBlock]],
    dcFrequencies: Map[Int, Long],
    acFrequencies: Map[Int, Long]
):
  /** Writes entropy data, inserting byte-aligned restart markers at MCU boundaries. */
  def write(dcTable: HuffmanTable, acTable: HuffmanTable, restartInterval: Int): IArray[Byte] =
    val output = ByteArrayOutputStream()
    var bits   = BitWriter()
    mcus.zipWithIndex.foreach: (mcu, mcuIndex) =>
      if restartInterval > 0 && mcuIndex > 0 && mcuIndex % restartInterval == 0 then
        output.write(bits.result().asInstanceOf[Array[Byte]])
        output.write(0xff)
        output.write(0xd0 + ((mcuIndex / restartInterval - 1) & 7))
        bits = BitWriter()
      mcu.foreach: block =>
        writeToken(block.dc, dcTable, bits)
        block.ac.foreach(token => writeToken(token, acTable, bits))
    output.write(bits.result().asInstanceOf[Array[Byte]])
    IArray.from(output.toByteArray)

  private def writeToken(token: EntropyToken, table: HuffmanTable, bits: BitWriter): Unit =
    table.write(token.symbol, bits)
    bits.write(token.value, token.bitCount)

private[jpeg] object PreparedScan:
  /** Performs transform, quantization, prediction, and run-length decisions once per source block.
    */
  def apply(
      sourceMcus: IndexedSeq[IndexedSeq[(Int, Block)]],
      quantizer: Block,
      restartInterval: Int
  ): PreparedScan =
    val componentCount = sourceMcus.flatten.map(_._1).max + 1
    val predictors     = Array.fill(componentCount)(0)
    val dcFrequencies  = mutable.Map.empty[Int, Long].withDefaultValue(0L)
    val acFrequencies  = mutable.Map.empty[Int, Long].withDefaultValue(0L)
    val prepared       = sourceMcus.zipWithIndex.map: (mcu, mcuIndex) =>
      if restartInterval > 0 && mcuIndex > 0 && mcuIndex % restartInterval == 0 then
        java.util.Arrays.fill(predictors, 0)
      mcu.map: (component, samples) =>
        val ordered    = Quantization.zigZag(Quantization.quantize(Dct.forward(samples), quantizer))
        val difference = ordered.head - predictors(component)
        predictors(component) = ordered.head
        val dcCategory = Magnitude.category(difference)
        val dc         = EntropyToken(dcCategory, Magnitude.bits(difference, dcCategory), dcCategory)
        dcFrequencies(dc.symbol) += 1
        val ac         = prepareAc(ordered.tail)
        ac.foreach(token => acFrequencies(token.symbol) += 1)
        PreparedBlock(dc, ac)
    PreparedScan(prepared, dcFrequencies.toMap, acFrequencies.toMap)

  private def prepareAc(values: IndexedSeq[Int]): IndexedSeq[EntropyToken] =
    val result = mutable.ArrayBuffer.empty[EntropyToken]
    var run    = 0
    values.foreach: value =>
      if value == 0 then run += 1
      else
        while run >= 16 do
          result += EntropyToken(0xf0, 0, 0)
          run -= 16
        val category = Magnitude.category(value)
        result += EntropyToken((run << 4) | category, Magnitude.bits(value, category), category)
        run = 0
    if run > 0 then result += EntropyToken(0x00, 0, 0)
    result.toIndexedSeq

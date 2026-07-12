package jpeg

import scala.collection.mutable

/** A canonical Huffman table as represented by a DHT marker.
  *
  * `counts(i)` is the number of codes of length `i + 1`. The derivation of canonical codes follows
  * [[https://www.w3.org/Graphics/JPEG/itu-t81.pdf T.81 C.2]].
  */
final class HuffmanTable private (val counts: IArray[Int], val symbols: IArray[Int]):
  private val encodings: Map[Int, (Int, Int)] =
    var code   = 0
    var offset = 0
    (1 to 16).flatMap: length =>
      val entries = (0 until counts(length - 1)).map: _ =>
        val pair = symbols(offset) -> (code, length)
        code += 1
        offset += 1
        pair
      code <<= 1
      entries
    .toMap

  private val decodings: Map[(Int, Int), Int] = encodings.iterator
    .map((symbol, encoded) => encoded -> symbol).toMap

  def write(symbol: Int, output: BitWriter): Unit =
    val (code, length) = encodings.getOrElse(
      symbol,
      throw IllegalArgumentException(f"symbol 0x$symbol%02x is absent from Huffman table")
    )
    output.write(code, length)

  def read(input: BitReader): Int =
    var code   = 0
    var length = 1
    while length <= 16 do
      code = (code << 1) | input.readBit()
      decodings.get((code, length)) match
        case Some(symbol) => return symbol
        case None         => length += 1
    throw JpegError("invalid Huffman code")

object HuffmanTable:
  def apply(counts: Seq[Int], symbols: Seq[Int]): HuffmanTable =
    if counts.length != 16 || counts.exists(_ < 0) then
      throw JpegError("Huffman table requires sixteen non-negative code counts")
    if counts.sum != symbols.length then throw JpegError("Huffman symbol count does not match DHT")
    if symbols.exists(value => value < 0 || value > 255) then
      throw JpegError("Huffman symbols must be unsigned bytes")
    if symbols.distinct.size != symbols.size then throw JpegError("duplicate Huffman symbol")
    var code = 0
    for length <- 1 to 16 do
      val count = counts(length - 1)
      val limit = 1 << length
      if code + count > limit then throw JpegError("oversubscribed Huffman code space")
      if count > 0 && code + count - 1 == limit - 1 then
        throw JpegError("JPEG Huffman table contains a forbidden all-one code")
      code = (code + count) << 1
    new HuffmanTable(IArray.from(counts), IArray.from(symbols))

/** Entropy bit output with JPEG's mandatory `FF 00` byte stuffing (T.81 B.1.1.5). */
final class BitWriter:
  private val bytes       = mutable.ArrayBuffer.empty[Byte]
  private var accumulator = 0
  private var bitCount    = 0

  def write(value: Int, length: Int): Unit =
    require(length >= 0 && length <= 16 && (length == 16 || value >= 0 && value < (1 << length)))
    for shift <- (length - 1) to 0 by -1 do
      accumulator = (accumulator << 1) | ((value >>> shift) & 1)
      bitCount += 1
      if bitCount == 8 then emit(accumulator)

  private def emit(value: Int): Unit =
    bytes += value.toByte
    if (value & 0xff) == 0xff then bytes += 0.toByte
    accumulator = 0
    bitCount = 0

  /** Pads with one-bits as required by T.81 B.1.1.5. */
  def result(): IArray[Byte] =
    if bitCount > 0 then emit((accumulator << (8 - bitCount)) | ((1 << (8 - bitCount)) - 1))
    IArray.from(bytes)

/** Bit input over already unstuffed entropy bytes. */
final class BitReader(bytes: IArray[Byte]):
  private var byteIndex = 0
  private var bitIndex  = 0

  def readBit(): Int =
    if byteIndex >= bytes.length then throw JpegError("unexpected end of entropy data")
    val bit = ((bytes(byteIndex) & 0xff) >>> (7 - bitIndex)) & 1
    bitIndex += 1
    if bitIndex == 8 then
      byteIndex += 1
      bitIndex = 0
    bit

  def read(length: Int): Int =
    require(length >= 0 && length <= 16)
    var value = 0
    for _ <- 0 until length do value = (value << 1) | readBit()
    value

/** Baseline coefficient magnitude representation (T.81 F.1.2.1 and F.1.2.2). */
object Magnitude:
  def category(value: Int): Int =
    if value == 0 then 0 else 32 - Integer.numberOfLeadingZeros(math.abs(value))

  def bits(value: Int, category: Int): Int =
    if value >= 0 then value else value + (1 << category) - 1

  def value(bits: Int, category: Int): Int =
    if category == 0 then 0
    else if bits < (1 << (category - 1)) then bits - (1 << category) + 1
    else bits

/** Structured parse failure rather than an incidental indexing exception. */
final case class JpegError(message: String) extends RuntimeException(message)

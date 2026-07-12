package jpeg

import java.io.{ByteArrayOutputStream, InputStream, OutputStream}
import java.nio.file.{Files, Path}

/** Resource limits applied before parsing untrusted input. */
final case class DecoderOptions(
    maxInputBytes: Int = 64 * 1024 * 1024,
    maxPixels: Long = 100_000_000L
):
  require(maxInputBytes > 0, "maximum input size must be positive")
  require(maxPixels > 0, "maximum pixel count must be positive")

/** Decoded pixels together with application metadata from the same bounded input. */
final case class JpegDocument(image: DecodedImage, metadata: JpegMetadata):
  /** Applies Exif orientation explicitly, leaving stored pixels and metadata unchanged. */
  def orientedImage: DecodedImage = metadata.exifOrientation.fold(image): orientation =>
    image match
      case DecodedImage.Grayscale(value) => DecodedImage
          .Grayscale(ImageOrientation(value, orientation))
      case DecodedImage.Color(value)     => DecodedImage.Color(ImageOrientation(value, orientation))

/** Practical stream and filesystem facade around the pure in-memory codec.
  *
  * The codec core uses immutable byte arrays to keep parsing deterministic. This facade performs
  * bounded I/O and never closes caller-owned streams. Path-based methods own and close the streams
  * they open.
  */
object Jpeg:
  /** Reads and closes a filesystem path. */
  def read(path: Path, options: DecoderOptions = DecoderOptions()): DecodedImage =
    val input = Files.newInputStream(path)
    try read(input, options)
    finally input.close()

  /** Reads a caller-owned stream without closing it. */
  def read(input: InputStream, options: DecoderOptions): DecodedImage = JpegDecoder
    .decodeImage(readBounded(input, options.maxInputBytes), options.maxPixels)

  /** Reads a caller-owned stream with the default resource limit. */
  def read(input: InputStream): DecodedImage = read(input, DecoderOptions())

  /** Reads pixels and metadata while opening the filesystem path only once. */
  def readDocument(path: Path, options: DecoderOptions = DecoderOptions()): JpegDocument =
    val input = Files.newInputStream(path)
    try readDocument(input, options)
    finally input.close()

  /** Reads pixels and metadata without closing the caller-owned stream. */
  def readDocument(input: InputStream, options: DecoderOptions): JpegDocument =
    val bytes = readBounded(input, options.maxInputBytes)
    JpegDocument(JpegDecoder.decodeImage(bytes, options.maxPixels), JpegMetadata.inspect(bytes))

  /** Reads color, promoting grayscale samples to equal RGB channels. */
  def readRgb(path: Path, options: DecoderOptions = DecoderOptions()): RgbImage =
    read(path, options) match
      case DecodedImage.Color(image)     => image
      case DecodedImage.Grayscale(image) => grayscaleToRgb(image)

  /** Reads only grayscale and rejects color rather than silently discarding chroma. */
  def readGray(path: Path, options: DecoderOptions = DecoderOptions()): GrayImage =
    read(path, options) match
      case DecodedImage.Grayscale(image) => image
      case DecodedImage.Color(_)         =>
        throw JpegError("expected grayscale JPEG but found three components")

  def write(image: GrayImage, path: Path, options: EncoderOptions): Unit =
    writeBytes(JpegEncoder.encode(image, options), path)

  def write(image: GrayImage, path: Path): Unit = write(image, path, EncoderOptions())

  def write(image: RgbImage, path: Path, options: EncoderOptions): Unit =
    writeBytes(JpegEncoder.encode(image, options), path)

  def write(image: RgbImage, path: Path): Unit = write(image, path, EncoderOptions())

  /** Re-encodes a document while preserving its ordered APP and COM metadata. */
  def write(document: JpegDocument, path: Path, options: EncoderOptions = EncoderOptions()): Unit =
    val bytes = document.image match
      case DecodedImage.Grayscale(image) => JpegEncoder.encode(image, options, document.metadata)
      case DecodedImage.Color(image)     => JpegEncoder.encode(image, options, document.metadata)
    writeBytes(bytes, path)

  def write(image: GrayImage, output: OutputStream, options: EncoderOptions): Unit = output
    .write(JpegEncoder.encode(image, options).asInstanceOf[Array[Byte]])

  def write(image: RgbImage, output: OutputStream, options: EncoderOptions): Unit = output
    .write(JpegEncoder.encode(image, options).asInstanceOf[Array[Byte]])

  private def writeBytes(bytes: IArray[Byte], path: Path): Unit =
    val output = Files.newOutputStream(path)
    try output.write(bytes.asInstanceOf[Array[Byte]])
    finally output.close()

  private def readBounded(input: InputStream, maximum: Int): IArray[Byte] =
    val output = ByteArrayOutputStream(math.min(maximum, 8192))
    val buffer = Array.ofDim[Byte](8192)
    var total  = 0
    var count  = input.read(buffer)
    while count >= 0 do
      total += count
      if total > maximum then
        throw JpegError(s"JPEG input exceeds configured limit of $maximum bytes")
      output.write(buffer, 0, count)
      count = input.read(buffer)
    IArray.from(output.toByteArray)

  private def grayscaleToRgb(image: GrayImage): RgbImage = RgbImage(
    image.width,
    image.height,
    for y <- 0 until image.height; x <- 0 until image.width yield
      val value = image(x, y)
      Rgb(value, value, value)
  )

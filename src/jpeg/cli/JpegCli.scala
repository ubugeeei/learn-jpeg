package jpeg

import java.awt.image.BufferedImage
import java.nio.file.{Files, Path}
import javax.imageio.ImageIO

/** Dependency-free command-line interface for encoding, decoding, and inspecting JPEG files. */
object JpegCli:
  def main(arguments: Array[String]): Unit = run(arguments.toIndexedSeq) match
    case Right(message) => println(message)
    case Left(message)  =>
      System.err.println(message)
      System.err.println(usage)

  /** Executes a command without terminating the JVM, making the CLI an ordinary testable API. */
  def run(arguments: IndexedSeq[String]): Either[String, String] = arguments match
    case IndexedSeq("encode", input, output, remaining*) =>
      encode(Path.of(input), Path.of(output), remaining.toIndexedSeq)
    case IndexedSeq("decode", input, output)             => decode(Path.of(input), Path.of(output))
    case IndexedSeq("inspect", input)                    => inspect(Path.of(input))
    case _                                               => Left("invalid command or arguments")

  val usage: String = """Usage:
      |  jpeg encode <input-image> <output.jpg> [--quality 1..100] [--sampling 444|422|420]
      |  jpeg decode <input.jpg> <output.png>
      |  jpeg inspect <input.jpg>""".stripMargin

  private def encode(
      input: Path,
      output: Path,
      arguments: IndexedSeq[String]
  ): Either[String, String] =
    for
      options <- parseEncoderOptions(arguments)
      image   <- readBuffered(input)
      rgb      = RgbImage(
                   image.getWidth,
                   image.getHeight,
                   for
                     y <- 0 until image.getHeight
                     x <- 0 until image.getWidth
                   yield
                     val packed = image.getRGB(x, y)
                     Rgb(packed >>> 16 & 0xff, packed >>> 8 & 0xff, packed & 0xff)
                 )
      _       <- attempt(Files.write(output, JpegEncoder.encode(rgb, options).asInstanceOf[Array[Byte]]))
    yield s"encoded ${rgb.width}x${rgb.height} JPEG to $output"

  private def decode(input: Path, output: Path): Either[String, String] =
    for
      document <- attempt(Jpeg.readDocument(input))
      image     = toRgb(document.orientedImage)
      buffered  = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
      _         = for y <- 0 until image.height; x <- 0 until image.width do
                    val pixel = image(x, y)
                    buffered.setRGB(x, y, pixel.red << 16 | pixel.green << 8 | pixel.blue)
      written  <- attempt(ImageIO.write(buffered, "png", output.toFile))
      _        <- if written then Right(()) else Left("no PNG ImageIO writer is available")
    yield s"decoded ${image.width}x${image.height} PNG to $output"

  private def inspect(input: Path): Either[String, String] =
    for document <- attempt(Jpeg.readDocument(input)) yield
      val dimensions = document.image match
        case DecodedImage.Grayscale(image) => image.dimensions
        case DecodedImage.Color(image)     => image.dimensions
      val metadata   = document.metadata
      Seq(
        s"dimensions: ${dimensions.width}x${dimensions.height}",
        s"color: ${document.image.isInstanceOf[DecodedImage.Color]}",
        s"jfif: ${metadata.jfif.map(info => s"${info.major}.${info.minor}").getOrElse("none")}",
        s"exif-orientation: ${metadata.exifOrientation.map(_.exifValue).getOrElse("none")}",
        s"icc-bytes: ${metadata.iccProfile.map(_.length).getOrElse(0)}",
        s"comments: ${metadata.comments.size}",
        s"application-segments: ${metadata.applications.size}"
      ).mkString("\n")

  private def parseEncoderOptions(arguments: IndexedSeq[String]): Either[String, EncoderOptions] =
    var quality  = Quality.Default
    var sampling = ChromaSubsampling.HalfBothAxes
    var index    = 0
    while index < arguments.size do
      arguments(index) match
        case "--quality" if index + 1 < arguments.size  =>
          arguments(index + 1).toIntOption match
            case Some(value) if value >= 1 && value <= 100 => quality = Quality(value)
            case _                                         => return Left("quality must be an integer in 1..100")
          index += 2
        case "--sampling" if index + 1 < arguments.size =>
          sampling = arguments(index + 1) match
            case "444" => ChromaSubsampling.FullResolution
            case "422" => ChromaSubsampling.HalfHorizontal
            case "420" => ChromaSubsampling.HalfBothAxes
            case _     => return Left("sampling must be 444, 422, or 420")
          index += 2
        case option                                     => return Left(s"unknown or incomplete option: $option")
    Right(EncoderOptions(quality, sampling))

  private def readBuffered(path: Path): Either[String, BufferedImage] =
    attempt(ImageIO.read(path.toFile))
      .flatMap(image => Option(image).toRight(s"unsupported or unreadable input image: $path"))

  private def toRgb(image: DecodedImage): RgbImage = image match
    case DecodedImage.Color(value)     => value
    case DecodedImage.Grayscale(value) => RgbImage(
        value.width,
        value.height,
        for y <- 0 until value.height; x <- 0 until value.width yield
          val sample = value(x, y)
          Rgb(sample, sample, sample)
      )

  private def attempt[A](operation: => A): Either[String, A] =
    try Right(operation)
    catch
      case error: JpegError           => Left(error.message)
      case error: java.io.IOException => Left(error.getMessage)

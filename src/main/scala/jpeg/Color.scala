package jpeg

/** An immutable 8-bit sRGB pixel. */
final case class Rgb private (red: Int, green: Int, blue: Int)

object Rgb:
  def apply(red: Int, green: Int, blue: Int): Rgb =
    require(Seq(red, green, blue).forall(v => v >= 0 && v <= 255), "RGB channels must be in 0..255")
    new Rgb(red, green, blue)

/** Full-range JPEG YCbCr sample.
  *
  * Conversion uses the equations in
  * [[https://www.w3.org/Graphics/JPEG/jfif3.pdf JFIF 1.02 section 3]]. JPEG itself
  * defines component coding and does not require a particular color space.
  */
final case class YCbCr private (y: Int, cb: Int, cr: Int)

object YCbCr:
  private def sample(value: Double): Int = math.max(0, math.min(255, math.round(value).toInt))

  def apply(y: Int, cb: Int, cr: Int): YCbCr =
    require(Seq(y, cb, cr).forall(v => v >= 0 && v <= 255), "YCbCr components must be in 0..255")
    new YCbCr(y, cb, cr)

  def fromRgb(rgb: Rgb): YCbCr = YCbCr(
    sample(0.299 * rgb.red + 0.587 * rgb.green + 0.114 * rgb.blue),
    sample(-0.168736 * rgb.red - 0.331264 * rgb.green + 0.5 * rgb.blue + 128),
    sample(0.5 * rgb.red - 0.418688 * rgb.green - 0.081312 * rgb.blue + 128))

  extension (color: YCbCr)
    def toRgb: Rgb =
      val cb = color.cb - 128
      val cr = color.cr - 128
      Rgb(
        sample(color.y + 1.402 * cr),
        sample(color.y - 0.344136 * cb - 0.714136 * cr),
        sample(color.y + 1.772 * cb))

/** An RGB raster whose construction establishes all indexing invariants. */
final case class RgbImage private (dimensions: Dimensions, pixels: IArray[Rgb]):
  def width: Int = dimensions.width
  def height: Int = dimensions.height
  def apply(x: Int, y: Int): Rgb = pixels(y * width + x)

object RgbImage:
  def apply(width: Int, height: Int, pixels: IterableOnce[Rgb]): RgbImage =
    val dimensions = Dimensions(width, height)
    val values = IArray.from(pixels)
    require(values.length == width * height, "pixel count must equal width × height")
    new RgbImage(dimensions, values)

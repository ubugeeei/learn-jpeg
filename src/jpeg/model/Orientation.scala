package jpeg

/** Exif orientation tag values and their display-space coordinate transforms. */
enum ImageOrientation(val exifValue: Int):
  case Normal                   extends ImageOrientation(1)
  case MirrorHorizontal         extends ImageOrientation(2)
  case Rotate180                extends ImageOrientation(3)
  case MirrorVertical           extends ImageOrientation(4)
  case Transpose                extends ImageOrientation(5)
  case Rotate90Clockwise        extends ImageOrientation(6)
  case Transverse               extends ImageOrientation(7)
  case Rotate90CounterClockwise extends ImageOrientation(8)

  /** True when display width and height exchange places. */
  def swapsDimensions: Boolean = exifValue >= 5

object ImageOrientation:
  def fromExif(value: Int): Option[ImageOrientation] = values.find(_.exifValue == value)

  /** Produces display-oriented grayscale pixels without mutating the source raster. */
  def apply(image: GrayImage, orientation: ImageOrientation): GrayImage =
    val (width, height) = orientedDimensions(image.width, image.height, orientation)
    GrayImage(
      width,
      height,
      for y <- 0 until height; x <- 0 until width yield
        val (sourceX, sourceY) = sourceCoordinate(x, y, image.width, image.height, orientation)
        image(sourceX, sourceY)
    )

  /** Produces display-oriented RGB pixels without mutating the source raster. */
  def apply(image: RgbImage, orientation: ImageOrientation): RgbImage =
    val (width, height) = orientedDimensions(image.width, image.height, orientation)
    RgbImage(
      width,
      height,
      for y <- 0 until height; x <- 0 until width yield
        val (sourceX, sourceY) = sourceCoordinate(x, y, image.width, image.height, orientation)
        image(sourceX, sourceY)
    )

  private def orientedDimensions(
      width: Int,
      height: Int,
      orientation: ImageOrientation
  ): (Int, Int) = if orientation.swapsDimensions then height -> width else width -> height

  /** Maps a destination display coordinate back into the stored source raster. */
  private def sourceCoordinate(
      x: Int,
      y: Int,
      width: Int,
      height: Int,
      orientation: ImageOrientation
  ): (Int, Int) = orientation match
    case ImageOrientation.Normal                   => x             -> y
    case ImageOrientation.MirrorHorizontal         => width - 1 - x -> y
    case ImageOrientation.Rotate180                => width - 1 - x -> (height - 1 - y)
    case ImageOrientation.MirrorVertical           => x             -> (height - 1 - y)
    case ImageOrientation.Transpose                => y             -> x
    case ImageOrientation.Rotate90Clockwise        => y             -> (height - 1 - x)
    case ImageOrientation.Transverse               => width - 1 - y -> (height - 1 - x)
    case ImageOrientation.Rotate90CounterClockwise => width - 1 - y -> x

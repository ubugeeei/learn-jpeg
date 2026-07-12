package jpeg

class OrientationSuite extends munit.FunSuite:
  private val source = GrayImage(2, 3, Seq(1, 2, 3, 4, 5, 6))

  test("all eight Exif orientations map a non-square raster declaratively"):
    val cases = Seq(
      ImageOrientation.Normal                   -> (2, 3, Seq(1, 2, 3, 4, 5, 6)),
      ImageOrientation.MirrorHorizontal         -> (2, 3, Seq(2, 1, 4, 3, 6, 5)),
      ImageOrientation.Rotate180                -> (2, 3, Seq(6, 5, 4, 3, 2, 1)),
      ImageOrientation.MirrorVertical           -> (2, 3, Seq(5, 6, 3, 4, 1, 2)),
      ImageOrientation.Transpose                -> (3, 2, Seq(1, 3, 5, 2, 4, 6)),
      ImageOrientation.Rotate90Clockwise        -> (3, 2, Seq(5, 3, 1, 6, 4, 2)),
      ImageOrientation.Transverse               -> (3, 2, Seq(6, 4, 2, 5, 3, 1)),
      ImageOrientation.Rotate90CounterClockwise -> (3, 2, Seq(2, 4, 6, 1, 3, 5))
    )
    cases.foreach: (orientation, expected) =>
      val (width, height, samples) = expected
      val actual                   = ImageOrientation(source, orientation)
      assertEquals(actual.width -> actual.height, width -> height)
      assertEquals((for y <- 0 until height; x <- 0 until width yield actual(x, y)), samples)

  test("Exif values map exactly to the eight orientation cases"):
    assertEquals((1 to 8).flatMap(ImageOrientation.fromExif), ImageOrientation.values.toSeq)
    assertEquals(ImageOrientation.fromExif(0), None)
    assertEquals(ImageOrientation.fromExif(9), None)

  test("RGB orientation preserves whole pixels rather than individual channels"):
    val image  = RgbImage(2, 1, Seq(Rgb(1, 2, 3), Rgb(4, 5, 6)))
    val actual = ImageOrientation(image, ImageOrientation.MirrorHorizontal)
    assertEquals(actual(0, 0), Rgb(4, 5, 6))
    assertEquals(actual(1, 0), Rgb(1, 2, 3))

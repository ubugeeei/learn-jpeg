package jpeg

import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO

class JpegCliSuite extends munit.FunSuite:
  test("encode, inspect, and decode form an end-to-end command workflow"):
    val directory = Files.createTempDirectory("learn-jpeg-cli-")
    val input     = directory.resolve("input.png")
    val jpeg      = directory.resolve("output.jpg")
    val output    = directory.resolve("decoded.png")
    try
      val image = BufferedImage(23, 17, BufferedImage.TYPE_INT_RGB)
      for y <- 0 until image.getHeight; x <- 0 until image.getWidth do
        image.setRGB(x, y, (x * 11 << 16) | (y * 15 << 8) | ((x + y) * 6))
      assert(ImageIO.write(image, "png", input.toFile))

      val encoded = JpegCli.run(
        IndexedSeq("encode", input.toString, jpeg.toString, "--quality", "91", "--sampling", "422")
      )
      assert(encoded.exists(_.contains("23x17")))
      assert(Files.size(jpeg) > 0)

      val inspected = JpegCli.run(IndexedSeq("inspect", jpeg.toString)).toOption.get
      assert(inspected.contains("dimensions: 23x17"))
      assert(inspected.contains("color: true"))

      val decoded = JpegCli.run(IndexedSeq("decode", jpeg.toString, output.toString))
      assert(decoded.exists(_.contains("23x17")))
      val result  = ImageIO.read(output.toFile)
      assertEquals(result.getWidth -> result.getHeight, 23 -> 17)
    finally
      Seq(output, jpeg, input).foreach(Files.deleteIfExists)
      Files.deleteIfExists(directory)

  test("invalid CLI choices return descriptive values instead of terminating the JVM"):
    val cases = Seq(
      IndexedSeq.empty[String]                                    -> "invalid command",
      IndexedSeq("encode", "a", "b", "--quality", "0")            -> "quality",
      IndexedSeq("encode", "a", "b", "--quality", "not-a-number") -> "quality",
      IndexedSeq("encode", "a", "b", "--sampling", "411")         -> "sampling",
      IndexedSeq("encode", "a", "b", "--mystery")                 -> "unknown"
    )
    cases.foreach: (arguments, expected) =>
      val error = JpegCli.run(arguments).left.toOption.get
      assert(error.contains(expected))

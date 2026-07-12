# Command line: use the codec on real files

The library model starts from validated rasters, but a new reader should not need
to write pixel-conversion code before seeing a result. `JpegCli` connects the codec
to JDK ImageIO for common input formats and PNG output without adding a runtime
dependency.

## Encode PNG or another ImageIO image

```shell
sbt 'run encode input.png output.jpg --quality 90 --sampling 420'
```

Sampling accepts:

- `444` — full chroma resolution;
- `422` — half horizontal chroma resolution;
- `420` — half horizontal and vertical chroma resolution.

Quality must be 1 through 100. The regular encoder defaults still apply: optimized
Huffman tables and no restart interval. Advanced callers use `EncoderOptions`
through the library API.

## Inspect a JPEG

```shell
sbt 'run inspect photo.jpg'
```

The command reports stored dimensions, grayscale/color status, JFIF version, Exif
orientation, assembled ICC byte count, comments, and APP segment count. Inspection
uses the same bounded `JpegDocument` path as applications.

## Decode to PNG

```shell
sbt 'run decode photo.jpg output.png'
```

Decode applies Exif orientation explicitly before writing PNG. Grayscale is
promoted to equal RGB channels. PNG is deliberately lossless so this second write
does not add another JPEG generation of quantization loss.

## Why the command parser returns `Either`

`main` only prints success or error text. Actual parsing and execution live in:

```scala
JpegCli.run(arguments): Either[String, String]
```

It does not call `System.exit`, which makes the command reusable from tests,
build tools, and a future native-image wrapper. File I/O and codec errors become
descriptive `Left` values; unknown options are rejected before reading input.

## End-to-end test

`JpegCliSuite` creates a real 23×17 PNG, invokes encode with quality and sampling
options, inspects the JPEG, decodes it to PNG, and opens the output independently
with ImageIO. A table of invalid quality, sampling, and unknown options verifies
failure text without launching another process.

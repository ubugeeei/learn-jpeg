# Production I/O and resource boundaries

The transform pipeline works most clearly with immutable in-memory values, but a
usable library must meet files and streams. `Jpeg` is the boundary between those
concerns; `JpegEncoder` and `JpegDecoder` remain deterministic codec cores.

## Filesystem ownership

```scala
val source: RgbImage = ???
val path = Path.of("result.jpg")

Jpeg.write(source, path, EncoderOptions(Quality(85)))
val decoded = Jpeg.readRgb(path)
```

Path methods open and close their streams. Stream methods never close a stream
owned by the caller. This rule makes composition predictable with archives,
network responses, and buffered pipelines.

```scala
val output = new ByteArrayOutputStream()
Jpeg.write(source, output, EncoderOptions())
// output remains usable here
```

## Bound untrusted inputs before parsing

Dimensions are not the only resource hazard. A syntactically plausible JPEG can
contain huge APP segments before SOF. The facade therefore limits total input
bytes before the parser allocates and processes the immutable buffer:

```scala
val options = DecoderOptions(
  maxInputBytes = 8 * 1024 * 1024,
  maxPixels = 24_000_000
)
val image = Jpeg.read(networkInput, options)
```

The pixel limit is checked immediately after SOF0 dimensions are parsed, before
component planes are allocated. A production service should additionally limit
CPU time and concurrent decodes outside the codec.

## Why decoding still buffers

Marker parsing can stream, but entropy decoding, restart recovery, metadata
retention, and error reporting complicate a truly incremental API. The current
facade bounds then buffers the source. That is an honest and useful middle ground:
safe for ordinary application assets, unsuitable for unbounded multi-gigabyte
streams. A later milestone will expose incremental scan consumption.

## Typed decode results

`DecodedImage` preserves whether SOF described one or three components:

```scala
Jpeg.read(path) match
  case DecodedImage.Grayscale(gray) => consumeGray(gray)
  case DecodedImage.Color(rgb)      => consumeColor(rgb)
```

`readRgb` promotes grayscale by copying its sample into all channels. `readGray`
rejects color rather than silently discarding chroma. Convenience APIs should
make potentially lossy policy visible.

## Executable contract

`CodecSuite` verifies path round trips, maximum input rejection, and caller-owned
stream lifetime. These are declarative API contracts, not incidental coverage of
implementation lines.

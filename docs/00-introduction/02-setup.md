# Run the project

The library intentionally has no runtime dependencies. MUnit is test-only, and
VitePress builds this book.

## Requirements

- JDK 21 or newer;
- sbt 1.11.x (the exact version is pinned in `project/build.properties`);
- Node.js 20 or newer only when serving the book.

```shell
git clone https://github.com/ubugeeei/learn-jpeg.git
cd learn-jpeg
sbt test
```

The test names form a second, executable table of contents. Run one suite while
studying its chapter:

```shell
sbt 'testOnly jpeg.EntropySuite'
sbt 'testOnly jpeg.CodecSuite'
```

## Explore in the REPL

```shell
sbt console
```

```scala
import jpeg.*

val gray = GrayImage(8, 8, Seq.fill(64)(128))
val bytes = JpegEncoder.encode(gray)
bytes.take(2).map(_ & 0xff).toSeq // Seq(255, 216), or FF D8

val decoded = JpegDecoder.decode(bytes)
decoded(0, 0) // 128
```

## Serve the book

```shell
npm install
npm run docs:dev
```

VitePress validates internal links during a production build:

```shell
npm run docs:build
```

CI runs both the Scala tests and the book build. A prose change that breaks the
learning path should therefore fail as visibly as a codec regression.

## Repository map

```text
src/jpeg/
  model/               samples, dimensions, grayscale raster
  transform/           DCT and its colocated TransformSuite
  entropy/             Huffman, bit streams, and EntropySuite
  codec/               encoder, decoder, and interoperability suite
  color/               RGB, YCbCr, quality, and their suite
docs/                  the book you are reading
```

Files stay below roughly 350 lines. Production code and tests are colocated by
domain. `build.sbt` assigns `*Suite.scala` to the Test configuration, so MUnit
does not leak into the runtime artifact. We split by domain pressure, not by
generic names such as `util` or `helpers`.

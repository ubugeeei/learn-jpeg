# learn-jpeg

An educational, dependency-free baseline JPEG codec written in Scala 3.

The implementation favors small, colocated modules: each JPEG concept lives next
to its value types and tests. The accompanying [book](docs/README.md) derives the
codec step by step and links each operation to the governing specification.

## Scope

- 8-bit baseline sequential DCT JPEG (SOF0)
- Huffman entropy coding
- Grayscale encoding and decoding
- Explicit rejection of unsupported JPEG processes

Run the test suite with `sbt test`.

## Command line

```shell
sbt 'run encode input.png output.jpg --quality 90 --sampling 420'
sbt 'run inspect output.jpg'
sbt 'run decode output.jpg decoded.png'
```

The [online-book source](docs/README.md) explains every stage from sample grids to
metadata rewriting and defensive decoding.

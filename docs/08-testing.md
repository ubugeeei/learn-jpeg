# 8. Testing a lossy codec

Exact pixel equality is usually the wrong JPEG assertion. Tests are layered:

- exact invariants for zig-zag permutations, magnitude coding, markers, and
  byte stuffing;
- exact round trips for constant blocks (only DC survives);
- bounded maximum and mean absolute error for gradients;
- malformed and unsupported streams that must fail predictably.

An interoperability suite should additionally encode fixtures here and decode
them with an independent implementation (for example ImageIO), then decode JPEGs
produced by several external encoders. Keep tiny fixtures and record their origin
and license. Fuzzing the bounded marker parser is the natural next hardening step.

Run everything with:

```shell
sbt test
```

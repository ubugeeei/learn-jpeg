# 3. The DCT: describing a block by patterns

## Start with the problem

JPEG receives 64 pixel samples in an 8×8 block. Neighboring samples are often
similar, but Huffman coding alone does not know that. We want a description where
smooth blocks use only a few important numbers.

The **discrete cosine transform** (DCT) rewrites the same block as a mixture of 64
fixed patterns. It is like describing a musical chord by saying how much of each
note it contains.

```text
pixel description                    frequency description

128 128 128 ...                      DC: 0 after level shift
128 128 128 ...       DCT →          AC: 0, 0, 0, ...
...                                   (no changes across the block)
```

## First subtract 128

Input samples use `0..255`, centered around 128. The transform works more neatly
around zero, so JPEG performs a **level shift**:

```scala
val centered = sample - 128
```

Black becomes -128, middle gray becomes 0, and white becomes 127. The decoder
adds 128 after the inverse transform.

## DC and AC in plain language

Coefficient `(0,0)` is called **DC**. It represents the block's overall level.
The other 63 coefficients are called **AC**. They represent changes:

- low-frequency AC: broad, slow shading;
- high-frequency AC: fine edges, texture, and noise.

The electrical names are historical. Here, read DC as “average-like value” and
AC as “variation.”

## The two-dimensional equation

For output position `(u,v)`, the forward transform combines every input `(x,y)`:

```text
F(u,v) = 1/4 · C(u) · C(v) · Σx Σy
         sample(x,y) · cos((2x+1)uπ/16) · cos((2y+1)vπ/16)
```

`C(0)=1/√2`; other `C` values are 1. You do not need to memorize this. Notice
only that each output is a weighted sum of all 64 inputs.

See [T.81 A.3.3 and Annex F](https://www.w3.org/Graphics/JPEG/itu-t81.pdf#page=90)
for the normative equations.

## How the Scala mirrors the equation

`Dct` precomputes the cosine lookup table. `forward` then loops over output
frequency `(u,v)` and sums all input positions `(x,y)`. This reference approach
is slower than an integer fast DCT, but each loop variable maps directly to the
standard and is therefore ideal for learning and testing.

```scala
val coefficients: Block = Dct.forward(samples)
val reconstructed: Block = Dct.inverse(coefficients)
```

## Is the DCT lossy?

Mathematically, no. The inverse DCT recovers the original values. Our implementation
rounds coefficients to integers, so a forward/inverse test permits an error of one.
The deliberate, large loss happens in the next step: quantization.

## Executable checkpoints

`TransformSuite` states two important properties:

1. a block filled with 128 becomes 64 zero coefficients;
2. representative samples survive FDCT→IDCT within one sample value.

If the first fails, check level shifting and scale factors. If only edges show
large errors, check whether `(u,x)` and `(v,y)` were accidentally swapped.
